# Build stage: Maven build with the embedded Angular frontend
# (the with-frontend profile downloads its own Node, so the JDK image suffices)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# warm the dependency cache before copying sources
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
COPY frontend frontend
RUN ./mvnw -B package -Pwith-frontend -DskipTests

# Runtime stage: JRE only
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home minibank
USER minibank
COPY --from=build /app/target/minibank-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
