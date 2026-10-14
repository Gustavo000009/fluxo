FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system fluxo && useradd --system --gid fluxo fluxo && mkdir /app && chown fluxo:fluxo /app
WORKDIR /app
COPY --from=build --chown=fluxo:fluxo /build/target/fluxo-1.0.0.jar app.jar
USER fluxo
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
