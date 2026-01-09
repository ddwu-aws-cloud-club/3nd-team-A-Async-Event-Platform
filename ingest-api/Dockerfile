FROM eclipse-temurin:21-jre
WORKDIR /app

# 이미 만들어진 jar만 복사
COPY build/libs/ingest-api.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
