# syntax=docker/dockerfile:1
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
ENV MAVEN_OPTS="-Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dsun.net.client.defaultReadTimeout=600000 -Dsun.net.client.defaultConnectTimeout=600000"
COPY backend/pom.xml ./pom.xml
RUN mvn -B -DskipTests -U dependency:go-offline
COPY backend/src ./src
RUN mvn -B -DskipTests -o package

FROM nvidia/cuda:12.1.1-cudnn8-runtime-ubuntu22.04
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
COPY ai /app/ai

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    openjdk-21-jre-headless \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

RUN python3 -m pip install --no-cache-dir --upgrade pip && \
    python3 -m pip install --no-cache-dir torch==2.5.1 --index-url https://download.pytorch.org/whl/cu121 && \
    python3 -m pip install --no-cache-dir transformers==4.57.1 tokenizers safetensors huggingface-hub requests PyYAML tqdm accelerate packaging

ENV HF_HOME=/models/hub
ENV DISABLE_TRITON=0
ENV AI_VERIFICATION_VERBOSE=false

EXPOSE 8080

# Модель уже предзагружена сервисом model-preloader в volume /models
# Просто запускаем Java приложение
ENTRYPOINT ["java","-jar","/app/app.jar"]