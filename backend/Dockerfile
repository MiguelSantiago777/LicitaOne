# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && java -Djarmode=tools -jar target/shemacrm-*.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:21-jre-alpine
# Correções de segurança do Alpine publicadas depois da imagem base (Trivy no CI)
RUN apk upgrade --no-cache && addgroup -S shema && adduser -S shema -G shema
WORKDIR /app
ENV TZ=America/Sao_Paulo \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
# Pasta do FileStorage local: existe na imagem já com o dono certo, para o volume herdar a permissão
RUN mkdir -p /app/data/files && chown -R shema:shema /app/data
USER shema
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
