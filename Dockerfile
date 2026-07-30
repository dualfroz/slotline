FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system slotline && useradd --system --gid slotline --home /app slotline

COPY --from=build /workspace/target/slotline-0.0.1-SNAPSHOT.jar app.jar
USER slotline

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
