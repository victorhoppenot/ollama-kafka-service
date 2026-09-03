import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.consumer.KafkaConsumer
import io.circe.Json
import org.apache.kafka.clients.producer.ProducerRecord
import java.net.http.HttpRequest
import java.net.URI
import java.time.Duration
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.Encoder
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.Decoder
import io.circe.DecodingFailure


trait Message{};

final case class Query (
    model_group: String,
    id: String,
    endpoint: String,
    query: Json
) extends Message


final case class Response(
    typ: String,
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
class QueryHandler(val query: Query, producer: KafkaProducer[String, String], consumer: KafkaConsumer[String, String]) extends Runnable{
    implicit val responseEncoder: Encoder[Response] = deriveEncoder[Response]

    private val client = java.net.http.HttpClient.newBuilder().build()
    override def run(): Unit = {
        val response = OllamaEndpoint.query(query, client)
        producer.send(new ProducerRecord(Config.responseTopic, query.id, response.asJson.noSpaces))
        producer.flush()

        consumer.commitSync()
    }
}
