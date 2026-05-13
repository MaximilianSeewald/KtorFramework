FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY ktor.jar ktor.jar
COPY app app
COPY start.sh start.sh

RUN chmod +x start.sh

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/health/ready || exit 1

CMD ["./start.sh"]
