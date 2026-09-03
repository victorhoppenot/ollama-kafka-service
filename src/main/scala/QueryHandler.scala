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
import io.circe.syntax.EncoderOps
import io.circe.Decoder
import io.circe.DecodingFailure
import java.net.http.HttpClient

class QueryHandler(val query: Query, producer: KafkaProducer[String, String], consumer: KafkaConsumer[String, String], provider: Provider)(implicit client: HttpClient) extends Runnable{
    implicit val responseEncoder: Encoder[Response] = deriveEncoder[Response]

    override def run(): Unit = {

        val response = provider.query(query)
        producer.send(new ProducerRecord(Config.responseTopic, query.id, response.asJson.noSpaces))
        producer.flush()

        consumer.commitSync()
    }
}
