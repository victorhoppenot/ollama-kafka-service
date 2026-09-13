import io.circe.Json
import io.circe.parser.parse

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration


abstract class HttpProvider(baseUrl: String, name: String) extends Provider {

    override def query(q: Query)(implicit client: HttpClient): Response =
        Response(
            `type` = "llm-response",
            id = q.id,
            response = parse(post(q.endpoint, q.query)).getOrElse(Json.Null)
        )

    protected def post(path: String, body: Json)(implicit client: HttpClient): String =
        send(
            HttpRequest.newBuilder()
                .uri(URI.create(s"$baseUrl/$path"))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.noSpaces))
                .build()
        )

    protected def get(path: String)(implicit client: HttpClient): Json = {
        val body = send(
            HttpRequest.newBuilder()
                .uri(URI.create(s"$baseUrl/$path"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .GET()
                .build()
        )
        parse(body) match {
            case Right(json) => json
            case Left(err)   => throw new RuntimeException(s"Failed to parse $name response: $err")
        }
    }

    private def send(request: HttpRequest)(implicit client: HttpClient): String = {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw new RuntimeException(s"$name returned ${response.statusCode()}: ${response.body()}")
        }
        response.body()
    }
}
