import io.circe.Json

import java.net.http.HttpClient

class LlamaCppProvider(baseUrl: String) extends HttpProvider(baseUrl, "llama.cpp") {

    override def insertModelIntoPayload(model: String, payload: Json): Json = {
        payload.mapObject(obj => obj.add("model", Json.fromString(model)))
    }

    override def activeModels(implicit client: HttpClient): List[String] =
        get("v1/models").hcursor.downField("data").as[List[Json]] match {
            case Right(models) => models.flatMap(_.hcursor.downField("id").as[String].toOption)
            case Left(err)     => throw new RuntimeException(s"Failed to parse llama.cpp response: $err")
        }
}
