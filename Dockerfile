FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/xml_parser-1.0-SNAPSHOT.jar app.jar

RUN chmod +r app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]