FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system triagem && useradd --system --gid triagem --home-dir /app triagem
WORKDIR /app
COPY --from=build --chown=triagem:triagem /build/target/triagem-chamados-0.0.1-SNAPSHOT.jar app.jar
USER triagem
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
