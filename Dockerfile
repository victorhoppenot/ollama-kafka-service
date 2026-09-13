FROM sbtscala/scala-sbt:eclipse-temurin-17.0.19_10_1.12.15_3.8.4 AS build

WORKDIR /build

COPY project ./project
COPY build.sbt ./
RUN sbt update

COPY src ./src
RUN sbt assembly && \
    cp target/scala-*/llm-service-assembly-*.jar /build/app.jar

FROM debian:bookworm-slim AS llama

ARG LLAMA_RELEASE=prism-b10683-d8f26ee
ARG LLAMA_ASSET=llama-prism-b10683-d8f26ee-bin-linux-cuda-12.8-x64

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

RUN curl -fL --retry 3 -o /tmp/llama.tar.gz \
      "https://github.com/PrismML-Eng/llama.cpp/releases/download/${LLAMA_RELEASE}/${LLAMA_ASSET}.tar.gz" && \
    mkdir -p /opt/llama && \
    tar xzf /tmp/llama.tar.gz -C /opt/llama --strip-components=1 && \
    rm /tmp/llama.tar.gz

FROM nvidia/cuda:12.8.1-runtime-ubuntu24.04

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates libgomp1 && \
    rm -rf /var/lib/apt/lists/*

COPY --from=eclipse-temurin:17-jre-noble /opt/java/openjdk /opt/java/openjdk
COPY --from=llama /opt/llama /opt/llama

ENV PATH="/opt/java/openjdk/bin:/opt/llama:$PATH"
ENV LD_LIBRARY_PATH="/opt/llama:$LD_LIBRARY_PATH"

ENV LLAMA_MODEL_REPO=prism-ml/Ternary-Bonsai-27B-gguf
ENV LLAMA_MODEL_FILE=Ternary-Bonsai-27B-PQ2_0.gguf
ENV LLAMA_MODEL_DIR=/models
ENV LLAMA_MODEL_ALIAS=qwen-ternary
ENV LLAMA_PORT=8000
ENV LLAMA_CTX_SIZE=32768
ENV LLAMA_BASE_URL=http://127.0.0.1:8000

ENV KAFKA_BROKER_URL=broker:29092
ENV KAFKA_TOPIC=llm-requests
ENV API_SERVER_PORT=8080

WORKDIR /app

COPY --from=build /build/app.jar ./app.jar
COPY entrypoint.sh ./
RUN chmod +x entrypoint.sh

EXPOSE 8000 8080

ENTRYPOINT ["./entrypoint.sh"]
