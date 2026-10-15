# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-alpine AS derleme
WORKDIR /kaynak
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/abone-provisioning-*.jar extract --layers --launcher --destination /katmanlar

FROM eclipse-temurin:17-jre-alpine
# OpenShift rastgele UID ile ama root grubunda (GID 0) calistirir: yazilabilir dizinler grup 0'a acik
RUN adduser -S -u 10001 -G root uygulama \
    && mkdir -p /var/lib/abone/atomikos \
    && chgrp -R 0 /var/lib/abone && chmod -R g=u /var/lib/abone
WORKDIR /uygulama
COPY --from=derleme /katmanlar/dependencies/ ./
COPY --from=derleme /katmanlar/spring-boot-loader/ ./
COPY --from=derleme /katmanlar/snapshot-dependencies/ ./
COPY --from=derleme /katmanlar/application/ ./
USER 10001
ENV TZ=Europe/Istanbul
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
