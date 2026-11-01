# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-noble AS source
WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts may contain CRLF; normalize only the copy inside the image.
RUN sed -i 's/\r$//' mvnw
COPY src/ src/

FROM source AS test
CMD ["sh", "./mvnw", "--batch-mode", "--no-transfer-progress", "clean", "test"]

FROM source AS build
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
