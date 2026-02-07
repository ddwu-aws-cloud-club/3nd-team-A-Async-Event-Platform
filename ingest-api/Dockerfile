FROM eclipse-temurin:21-jre
WORKDIR /app

# 이미 만들어진 jar만 복사하도록 수정함
COPY build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
