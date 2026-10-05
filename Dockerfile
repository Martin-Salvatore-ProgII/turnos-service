# Etapa 1: compila el jar con el JDK. No hace falta tener Java ni Gradle en la máquina.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Primero solo los archivos de build: mientras no cambien, Docker reutiliza la capa
# con las dependencias ya descargadas.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

# Etapa 2: la imagen final solo lleva el JRE y el jar.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
