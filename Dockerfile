# syntax=docker/dockerfile:1

# REGISTRY lets you pull base images through a mirror when Docker Hub is blocked, e.g.
#   docker build --build-arg REGISTRY=<your-mirror-host> .
ARG REGISTRY=docker.io

FROM ${REGISTRY}/library/maven:3.9-eclipse-temurin-25 AS build
# Optional Maven Central mirror for networks where repo.maven.apache.org is unreachable.
ARG MAVEN_MIRROR_URL=
WORKDIR /src
RUN printf '<settings><mirrors>%s</mirrors></settings>\n' \
    "${MAVEN_MIRROR_URL:+<mirror><id>mirror</id><mirrorOf>*</mirrorOf><url>${MAVEN_MIRROR_URL}</url></mirror>}" \
    > /tmp/settings.xml
COPY pom.xml .
COPY src ./src
# Tests run on the developer machine / CI (mvn verify), not inside the image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -s /tmp/settings.xml -DskipTests package

FROM ${REGISTRY}/library/eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home hamgam
WORKDIR /app
COPY --from=build /src/target/hamgam-landing.jar /app/app.jar
USER hamgam
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
