import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode

import java.nio.file.{Files, Paths}

object Config {
  val ollamaBaseUrl: String = sys.env.getOrElse("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
  val brokers: String       = sys.env.getOrElse("KAFKA_BROKER_URL", "broker:29092")
  val groupId = sys.env.getOrElse("KAFKA_GROUP_ID", "llm-service")
  val queryTopic: String  = sys.env.getOrElse("KAFKA_TOPIC", "llm-requests")
  val responseTopic: String    = sys.env.getOrElse("KAFKA_RESPONSE_TOPIC", "llm-responses")
  val apiServerEnabled: Boolean = sys.env.getOrElse("API_SERVER_ENABLED", "false").toBoolean
  val apiServerHost: String = sys.env.getOrElse("API_SERVER_HOST", "0.0.0.0")
  val apiServerPort: Int    = sys.env.getOrElse("API_SERVER_PORT", "8080").toInt
  val apiRequestTimeoutSeconds: Long = sys.env.getOrElse("API_REQUEST_TIMEOUT_SECONDS", "300").toLong
}
