import scala.concurrent.Future
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.URI
import java.time.Duration
import java.net.http.HttpResponse
import io.circe.Json
import io.circe.parser.parse

class OllamaProvider(baseUrl: String) extends Provider{
    override def query(q: Query)(implicit client: HttpClient): Response = {
        val request = HttpRequest.newBuilder()
        .uri(URI.create(s"${baseUrl}/${q.endpoint}"))
        .timeout(Duration.ofSeconds(300))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(q.query.noSpaces))
        .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw new RuntimeException(s"ollama returned ${response.statusCode()}: ${response.body()}")
        }
        Response(
            `type` = "llm-response",
            id = q.id,
            response = io.circe.parser.parse(response.body()).getOrElse(Json.Null)
        )   
    }

    override def insertModelIntoPayload(model: String, payload: Json): Json = {
        payload.mapObject(obj => obj.add("model", Json.fromString(model)))
    }
  
    override def activeModels(implicit client: HttpClient): List[String] = {
        val request = HttpRequest.newBuilder()
        .uri(URI.create(s"${baseUrl}/api/ps"))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json")
        .GET()
        .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw new RuntimeException(s"ollama returned ${response.statusCode()}: ${response.body()}")
        }
        parse(response.body()) match {
            case Right(json) => {
                json.hcursor.downField("models").as[List[Json]] match {
                    case Right(models) => models.flatMap(modelJson => modelJson.hcursor.downField("model").as[String].toOption)
                    case Left(err) => throw new RuntimeException(s"Failed to parse ollama response: $err")
                }
            }
            case Left(err) => throw new RuntimeException(s"Failed to parse ollama response: $err")
        }
    }

}
