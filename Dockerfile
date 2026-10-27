# Builds CampusReserve's HTTP server and runs it on port 8080.
# The build stage compiles every module. The run stage keeps only the
# compiled classes the server needs.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -q -DskipTests compile

FROM eclipse-temurin:21.0.12.1_1-jre
WORKDIR /app
COPY --from=build /src/http-api/target/classes http-api
COPY --from=build /src/core-domain/target/classes core-domain
COPY --from=build /src/storage/target/classes storage
COPY --from=build /src/notifications/target/classes notifications
COPY --from=build /src/accounts/target/classes accounts
EXPOSE 8080
CMD ["java", "-cp", "http-api:core-domain:storage:notifications:accounts", \
     "edu.cmu.cs214.campusreserve.http.CampusReserveServer", "8080"]
