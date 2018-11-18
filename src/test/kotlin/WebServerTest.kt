import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(VertxUnitRunner::class)
class WebServerTest {

    @get:Rule
    val rule = RunTestOnContext()

    @Before
    fun deployServer(context: TestContext) {
        val vertx = rule.vertx()
        val server = WebServer()
        vertx.deployVerticle(server, context.asyncAssertSuccess())
    }

    @Test
    fun helloTest(context: TestContext) {
        val vertx = rule.vertx()

        val client = WebClient.create(vertx, WebClientOptions())
        client.get(8888, "localhost", "/api/helloMessage?name=Visitor").send(context.asyncAssertSuccess {
            context.assertNotNull(it.bodyAsJsonObject().getString("message"))
        })

    }

}