import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import java.util.Properties
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Duration
import io.circe.parser.decode
import scala.collection.mutable.HashMap
import java.net.http.HttpClient

object Main {

    private val running = new AtomicBoolean(true)
    val consumer: KafkaConsumer[String, String] = {
        val props = new Properties()
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.brokers)
        props.put(ConsumerConfig.GROUP_ID_CONFIG, Config.groupId)
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        new KafkaConsumer[String, String](props)
    }
    val producer: KafkaProducer[String, String] = {
        val props = new Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.brokers)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
        new KafkaProducer[String, String](props)
    }


    val endpoints: HashMap[String, Provider] = HashMap(
        "ollama" -> new OllamaProvider(Config.ollamaBaseUrl)
    )

    implicit val client: HttpClient = HttpClient.newBuilder().build()


    def main(args: Array[String]): Unit = {

        consumer.subscribe(java.util.List.of(Config.queryTopic))

        if (Config.apiServerEnabled) APIBridge.start(producer)

        while (running.get()) {
            consumer.poll(java.time.Duration.ofMillis(1000)).forEach { record =>
                decode[Message](record.value()) match {
                case Right(message) => try {
                    handleMessage(message)
                } catch {
                    case e: Exception => {
                        println(s"Error handling message: ${e.getMessage}")
                    }
                }
                case Left(err) =>
                    running.set(false)
                    throw new RuntimeException(s"Failed to decode message: $err")      
                }
            }
        }
    }

    def handleMessage(message: Message): Unit = {
        message match {
            case query: Query => {
                val providerName = query.model.split("/", 2).headOption.getOrElse(throw new RuntimeException(s"Invalid model name: ${query.model}"))
                val modelName = query.model.split("/", 2).lastOption.getOrElse(throw new RuntimeException(s"Invalid model name: ${query.model}"))
                val provider = endpoints.getOrElse(providerName, throw new RuntimeException(s"Unknown provider: $providerName"))

                val new_query: Query = query.copy(query = provider.insertModelIntoPayload(modelName, query.query))

                val thread = new Thread(new QueryHandler(new_query, producer, consumer, provider))
                thread.setDaemon(true)
                thread.start()  
            }
            case _ => throw new RuntimeException(s"Unknown message type: ${message.getClass.getSimpleName}")
        }
    }
}
