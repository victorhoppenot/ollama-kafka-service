import scala.concurrent.Future
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.URI
import java.time.Duration
import java.net.http.HttpResponse
import io.circe.Json

object OllamaEndpoint {
    def query(q: Query, client: HttpClient): Response = {
        val request = HttpRequest.newBuilder()
        .uri(URI.create(s"${Config.ollamaBaseUrl}/${q.endpoint}"))
        .timeout(Duration.ofSeconds(300))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(q.query.noSpaces))
        .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw new RuntimeException(s"ollama returned ${response.statusCode()}: ${response.body()}")
        }
        Response(
            typ = "llm-response",
            id = q.id,
            response = io.circe.parser.parse(response.body()).getOrElse(Json.Null)
        )   
    }
}
