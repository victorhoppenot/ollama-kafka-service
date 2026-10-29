import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, DescribeConsumerGroupsOptions}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.errors.GroupIdNotFoundException
import org.apache.kafka.common.serialization.StringDeserializer

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCode, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._
import io.circe.parser.{decode, parse}
import io.circe.syntax._

import java.time.Duration
import java.util.{Properties, UUID}
import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, Executors, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object APIBridge {

    private implicit val queryEncoder: Encoder[Query] = Encoder.instance { q =>
        Json.obj(
            "type"     -> Json.fromString("query"),
            "model"    -> Json.fromString(q.model),
            "id"       -> Json.fromString(q.id),
            "endpoint" -> Json.fromString(q.endpoint),
            "query"    -> q.query
        )
    }
    private implicit val responseDecoder: Decoder[Response] = deriveDecoder[Response]

    private val running = new AtomicBoolean(false)
    private val pending = new ConcurrentHashMap[String, Promise[Response]]()

    private val consumer: KafkaConsumer[String, String] = {
        val props = new Properties()
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.brokers)
        props.put(ConsumerConfig.GROUP_ID_CONFIG, s"${Config.groupId}-api-${UUID.randomUUID()}")
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        new KafkaConsumer[String, String](props)
    }

    private val admin: Admin = {
        val props = new Properties()
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, Config.brokers)
        Admin.create(props)
    }

    private val timeouts = Executors.newSingleThreadScheduledExecutor { r =>
        val t = new Thread(r, "api-bridge-timeout")
        t.setDaemon(true)
        t
    }

    def start(producer: KafkaProducer[String, String]): Unit = {
        if (!running.compareAndSet(false, true)) return

        val subscribed = new CountDownLatch(1)
        val poller = new Thread(() => pollResponses(subscribed), "api-bridge-consumer")
        poller.setDaemon(true)
        poller.start()

        if (!subscribed.await(30, TimeUnit.SECONDS)) {
            println(s"api bridge: no assignment on ${Config.responseTopic} yet, starting anyway")
        }

        implicit val system: ActorSystem[Nothing] =
            ActorSystem(Behaviors.empty, "api-bridge")

        implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

        Http()
            .newServerAt(Config.apiServerHost, Config.apiServerPort)
            .bind(route(producer))
            .onComplete {
                case Success(binding) =>
                    println(s"api bridge listening on ${binding.localAddress}")
                case Failure(e) =>
                    println(s"api bridge failed to bind: ${e.getMessage}")
            }
    }

    def stop(): Unit = running.set(false)

    private def route(producer: KafkaProducer[String, String])(implicit system: ActorSystem[Nothing]): Route = {
        implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

        post {
            extractUnmatchedPath { path =>
                entity(as[String]) { body =>
                    parse(body) match {
                        case Left(err) =>
                            complete(error(StatusCodes.BadRequest, s"invalid json: ${err.getMessage}"))
                        case Right(payload) =>
                            payload.hcursor.get[String]("model") match {
                                case Left(_) =>
                                    complete(error(StatusCodes.BadRequest, "missing \"model\" field"))
                                case Right(model) =>
                                    val query = Query(
                                        model = model,
                                        id = UUID.randomUUID().toString,
                                        endpoint = path.toString.stripPrefix("/"),
                                        query = payload
                                    )
                                    complete(hasConsumers().flatMap {
                                        case false =>
                                            Future.successful(error(StatusCodes.ServiceUnavailable, "no consumers available"))
                                        case true =>
                                            submit(query, producer).transform {
                                                case Success(response) =>
                                                    Success(HttpResponse(
                                                        StatusCodes.OK,
                                                        entity = HttpEntity(ContentTypes.`application/json`, response.response.noSpaces)
                                                    ))
                                                case Failure(_: TimeoutException) =>
                                                    Success(error(StatusCodes.GatewayTimeout, "timed out waiting for a response"))
                                                case Failure(e) =>
                                                    Success(error(StatusCodes.InternalServerError, e.getMessage))
                                            }
                                    })
                            }
                    }
                }
            }
        }
    }

    private def hasConsumers(): Future[Boolean] = {
        val promise = Promise[Boolean]()
        admin
            .describeConsumerGroups(
                java.util.List.of(Config.groupId),
                new DescribeConsumerGroupsOptions().timeoutMs(Config.apiConsumerCheckTimeoutMs)
            )
            .describedGroups()
            .get(Config.groupId)
            .whenComplete { (group, e) =>
                if (e == null) promise.trySuccess(!group.members().isEmpty)
                else if (e.isInstanceOf[GroupIdNotFoundException] || e.getCause.isInstanceOf[GroupIdNotFoundException])
                    promise.trySuccess(false)
                else {
                    println(s"api bridge: failed to check for consumers: ${e.getMessage}")
                    promise.trySuccess(true)
                }
            }
        promise.future
    }

    private def submit(query: Query, producer: KafkaProducer[String, String]): Future[Response] = {
        val promise = Promise[Response]()
        pending.put(query.id, promise)

        timeouts.schedule(
            new Runnable {
                override def run(): Unit =
                    Option(pending.remove(query.id))
                        .foreach(_.tryFailure(new TimeoutException(s"no response for ${query.id}")))
            },
            Config.apiRequestTimeoutSeconds,
            TimeUnit.SECONDS
        )

        producer.send(
            new ProducerRecord(Config.queryTopic, query.id, query.asJson.noSpaces),
            (_, e) => if (e != null) Option(pending.remove(query.id)).foreach(_.tryFailure(e))
        )

        promise.future
    }

    private def pollResponses(subscribed: CountDownLatch): Unit = {
        consumer.subscribe(java.util.List.of(Config.responseTopic))
        try {
            while (running.get()) {
                consumer.poll(Duration.ofMillis(500)).forEach { record =>
                    decode[Response](record.value()) match {
                        case Right(response) =>
                            Option(pending.remove(response.id)).foreach(_.trySuccess(response))
                        case Left(err) =>
                            println(s"api bridge: failed to decode response: $err")
                    }
                }
                if (subscribed.getCount > 0 && !consumer.assignment().isEmpty) subscribed.countDown()
            }
        } finally {
            subscribed.countDown()
            consumer.close()
        }
    }

    private def error(status: StatusCode, message: String): HttpResponse =
        HttpResponse(
            status,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                Json.obj("error" -> Json.fromString(message)).noSpaces
            )
        )
}
