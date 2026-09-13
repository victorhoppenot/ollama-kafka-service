import io.circe.Json

import java.net.http.HttpClient

class OllamaProvider(baseUrl: String) extends HttpProvider(baseUrl, "ollama") {

    override def insertModelIntoPayload(model: String, payload: Json): Json = {
        payload.mapObject(obj => obj.add("model", Json.fromString(model)))
    }

    override def activeModels(implicit client: HttpClient): List[String] =
        get("api/ps").hcursor.downField("models").as[List[Json]] match {
            case Right(models) => models.flatMap(_.hcursor.downField("model").as[String].toOption)
            case Left(err)     => throw new RuntimeException(s"Failed to parse ollama response: $err")
        }
}
