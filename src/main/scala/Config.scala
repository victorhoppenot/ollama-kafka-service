import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode

import java.nio.file.{Files, Paths}

object Config {
  val ollamaBaseUrl: String = sys.env.getOrElse("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
  val brokers: String       = sys.env.getOrElse("KAFKA_BROKER_URL", "broker:29092")
  val groupId = sys.env.getOrElse("KAFKA_GROUP_ID", "llm-service")
  val queryTopic: String  = s"${groupId}-query"
  val responseTopic: String    = sys.env.getOrElse("KAFKA_RESPONSE_TOPIC", "llm-responses")
}
