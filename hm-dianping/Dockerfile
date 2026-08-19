FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY hm-dianping/pom.xml hm-dianping/pom.xml
RUN mvn -B -f hm-dianping/pom.xml dependency:go-offline

# application.yaml is intentionally excluded by .dockerignore.
COPY hm-dianping/src hm-dianping/src
RUN mvn -B -f hm-dianping/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN mkdir -p /app/uploads /app/rag-documents /app/logs
COPY --from=build /workspace/hm-dianping/target/*.jar /app/app.jar

EXPOSE 8081 9999
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
