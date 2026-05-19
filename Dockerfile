FROM gradle:8.7-jdk24 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
RUN mkdir -p /app/db
VOLUME ["/app/db"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
