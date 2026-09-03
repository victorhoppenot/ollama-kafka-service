import io.circe.Json
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.generic.semiauto._

trait Message{};

final case class Query (
    model: String,
    id: String,
    endpoint: String,
    query: Json
) extends Message


final case class Response(
    `type`: String,
    id: String,
    response: Json
)

object Message{ 
    implicit val queryDecoder: Decoder[Query] = deriveDecoder
    implicit val messageDecoder: Decoder[Message] = Decoder.instance { c =>
        c.get[String]("type").flatMap {
        case "query" => c.as[Query]
        case other          => Left(DecodingFailure(s"unknown message type: $other", c.history))
        }
    }
}
