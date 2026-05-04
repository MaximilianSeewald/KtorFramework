FROM openjdk:21-jre-slim

WORKDIR /app

COPY deploy/ktor.jar ktor.jar
COPY deploy/app app

EXPOSE 8080

CMD ["java", "-jar", "ktor.jar"]
