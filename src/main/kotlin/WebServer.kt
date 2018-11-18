import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.api.RequestParameters
import io.vertx.ext.web.api.contract.RouterFactoryOptions
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

data class VisitorList(
    val visitors: List<String> = listOf()
)

data class Error(
    val code: Int = 500,
    val message: String = "Unknown error"
)

class WebServer(
    private val apiPort: Int = 8888
) : AbstractVerticle() {

    private val logger = LoggerFactory.getLogger(javaClass)!!

    private lateinit var baseRouter: Router

    private val visitors: MutableList<String> = mutableListOf()

    override fun start(future: Future<Void>) {
        super.start()

        baseRouter = Router.router(vertx)
        baseRouter.route().failureHandler {
            val failure = it.failure()
            logger.error("Handling failure", failure)
            if(failure == null) {
                it.response().setStatusMessage("Unknown error").setStatusCode(500).end(JsonObject.mapFrom(Error()).encodePrettily())
            } else {
                val error = Error(500, failure.message ?: "Unknown error")
                it.response().setStatusMessage(error.message).setStatusCode(error.code).end(JsonObject.mapFrom(error).encodePrettily())
            }
        }

        val routerFactoryFuture = Future.future<OpenAPI3RouterFactory>()
        OpenAPI3RouterFactory.create(vertx, "webroot/my-api.yaml", routerFactoryFuture)
        routerFactoryFuture.compose { routerFactory ->
            
            routerFactory.apply { 
                // Enable automatic response when ValidationException is thrown
                options = RouterFactoryOptions().apply {
                    isMountValidationFailureHandler = true
                }
                // Add routes handlers
                addHandlerByOperationId("getHelloMessage") {
                    val params: RequestParameters = it.get("parsedParameters")
                    val name = params.queryParameter("name")
                    logger.info("getHelloMessage called with $name")
                    it.response().end(json { obj(
                        "message" to "Hello $name"
                    ) }.encodePrettily())
                }
                addHandlerByOperationId("signVisitorBook") {
                    val params: RequestParameters = it.get("parsedParameters")
                    val name = params.body().jsonObject.getString("name")
                    logger.info("signVisitorBook called with $name")
                    if (visitors.contains(name)) {
                        it.fail(IllegalArgumentException("Visitor $name already exists"))
                    } else {
                        visitors.add(name)
                        val jsonObject = JsonObject.mapFrom(VisitorList(visitors))
                        it.response().end(jsonObject.encodePrettily())
                    }
                }
                addHandlerByOperationId("clearVisitorBook") {
                    logger.info("clearVisitorBook called")
                    visitors.clear()
                    it.response().setStatusMessage("Visitor book cleared").setStatusCode(200).end(JsonObject().put("message", "done").encodePrettily())
                }

            }

            // Generate the router
            val apiRouter = routerFactory.router
            baseRouter.mountSubRouter("/api", apiRouter)

            // Serve the static pages
            baseRouter.route().handler(StaticHandler.create())

            val server = vertx.createHttpServer()
            val serverFuture = Future.future<HttpServer>()
            server.requestHandler {
                if (logger.isTraceEnabled) {
                    logger.trace("Request $it")
                }
                baseRouter.accept(it)
            }.listen(apiPort, serverFuture)
            serverFuture.map {
                logger.info("Web API started listening on $apiPort")
            }
        }.otherwise {
            logger.error("Could not start web server", it)
            throw it
        }.mapEmpty<Void>().setHandler(future)
    }

}

fun main(args: Array<String>) {

    val logger = LoggerFactory.getLogger("WebServerKt")!!

    val deployFuture = Future.future<String>()
    val vertx = Vertx.vertx()
    val server = WebServer()
    vertx.deployVerticle(server, deployFuture)
    deployFuture.setHandler {
        if (it.failed()) {
            logger.error("Could not start server", it.cause())
            vertx.close()
        } else {
            logger.info("Server started")
        }
    }
}
