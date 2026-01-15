FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/worker.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
