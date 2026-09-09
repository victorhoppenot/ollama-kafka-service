FROM sbtscala/scala-sbt:eclipse-temurin-17.0.19_10_1.12.15_3.8.4 AS build

WORKDIR /build

COPY project ./project
COPY build.sbt ./
RUN sbt update

COPY src ./src
RUN sbt assembly && \
    cp target/scala-*/llm-service-assembly-*.jar /build/app.jar

FROM ollama/ollama:latest

COPY --from=eclipse-temurin:17-jre-noble /opt/java/openjdk /opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:$PATH"

ENV OLLAMA_HOST=127.0.0.1:11434
ENV OLLAMA_BASE_URL=http://127.0.0.1:11434
ENV KAFKA_BROKER_URL=broker:29092
ENV KAFKA_TOPIC=llm-requests
ENV API_SERVER_PORT=8080

WORKDIR /app

COPY --from=build /build/app.jar ./app.jar
COPY entrypoint.sh ./
RUN chmod +x entrypoint.sh

EXPOSE 11434 8080

ENTRYPOINT ["./entrypoint.sh"]
