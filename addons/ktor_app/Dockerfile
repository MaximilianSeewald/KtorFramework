FROM eclipse-temurin:21-jre

WORKDIR /app

COPY ktor.jar ktor.jar
COPY app app

EXPOSE 8080

CMD ["java", "-jar", "ktor.jar"]
