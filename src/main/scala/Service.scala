import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import java.util.Properties
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Duration
import io.circe.parser.decode

object Service {

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

    def main(args: Array[String]): Unit = {

        while (running.get()) {
            consumer.poll(java.time.Duration.ofMillis(1000)).forEach { record =>
                decode[Message](record.value()) match {
                case Right(message) => handleMessage(message)
                case Left(err) =>
                    //todo        
                }
            }
        }
    }

    def handleMessage(message: Message): Unit = {
        message match {
            case query: Query => {
                val thread = new Thread(new QueryHandler(query, producer, consumer))
                thread.setDaemon(true)
                thread.start()  
            }
            case _ => // todo
        }
    }
}
