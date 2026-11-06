# V10 (SEC-03-05): base images are pinned to an exact version and digest, so a
# build always starts from the same bytes. Dependabot proposes updates weekly,
# and the CI image scan fails when a fixable High/Critical issue appears.

# Build stage
FROM maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build
WORKDIR /app
# V10 (SEC-03-02): dependencies from Maven Central only (see maven-settings.xml).
COPY maven-settings.xml pom.xml ./
RUN mvn -B -ntp -s maven-settings.xml dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -s maven-settings.xml clean package -DskipTests

# Run stage
FROM eclipse-temurin:24.0.2_12-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f
# The plan PDF lays out Khmer text through Java's font engine, which needs these.
RUN apk add --no-cache fontconfig freetype
# V10 (SEC-02-06): the app runs as an ordinary user, not root. /app is its own
# so the Excel import can write its backup snapshot under /app/data/backup.
RUN addgroup -S app && adduser -S -G app -h /home/app app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
RUN chown app:app /app
USER app

# Render sets $PORT at runtime; application.yml reads it via server.port: ${PORT:8080}
EXPOSE 8080

# JVM sizing for the 512MB / 0.5-CPU Render starter instance.
#
# Without these the container was SIGKILLed (exit 137) partway through startup:
# the context took 37s to initialise and the port was still unbound after ~115s,
# which is what memory pressure looks like before the kill. An untuned JVM sizes
# itself for the box, and the defaults are wrong in both directions here — the
# default heap is only 25% of RAM while G1 and 1MB thread stacks spend far more
# native memory than this instance can spare.
#
#   MaxRAMPercentage=40   ~205MB of heap. The 25% default (128MB) was what the
#                         GC thrashed against; 50% was the opposite mistake — it
#                         let the app boot, but 256MB of heap plus the metaspace
#                         cap left under 100MB for code cache, stacks, direct
#                         buffers and the JVM itself, and Render killed it with
#                         "used over 512Mi" on the first request. Every cap here
#                         has to sum to less than the instance, not just look
#                         reasonable on its own.
#   UseSerialGC           G1 reserves region tables and per-thread structures
#                         that are pure overhead below ~1GB with one CPU. Serial
#                         is the usual recommendation at this size and frees
#                         both RSS and startup CPU.
#   MaxMetaspaceSize      the classpath is large (JPA + Hibernate + Security +
#                         Redis + Flyway + Apache POI), and metaspace is
#                         unbounded by default, so it is capped rather than
#                         allowed to grow into the kill threshold.
#   ReservedCodeCacheSize with C1 only the JIT needs a fraction of the 240MB it
#                         reserves by default. Reserved address space is not
#                         resident memory, but the cap keeps what it commits
#                         predictable.
#   MaxDirectMemorySize   Lettuce talks to Redis over Netty, which allocates
#                         off-heap ByteBuffers. Unbounded, that is invisible to
#                         every heap setting above and lands straight in the
#                         container's total.
#   Xss512k               stacks are per thread, so this pairs with the Tomcat
#                         thread cap in application.yml: 20 request threads at
#                         512k instead of the default 200 at 1MB.
#   TieredStopAtLevel=1   C1 only. Cuts JIT compilation work and memory during
#                         the cold start we were losing to. This trades some
#                         peak throughput — drop this flag first if the API ever
#                         feels slow under sustained load.
#
# If the instance is ever resized, revisit these: they are tuned for 512MB.
# Kept in an env var so the values can be changed from the Render dashboard (or
# render.yaml) to retune a struggling instance without waiting on an image
# rebuild — which matters when the thing you are debugging is a boot failure.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC -XX:MaxMetaspaceSize=144m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -Xss512k -XX:TieredStopAtLevel=1"

# `exec` so the JVM replaces the shell and stays PID 1: without it the shell
# holds PID 1 and the JVM never receives Render's SIGTERM, turning every deploy
# into a hard kill after the grace period.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
