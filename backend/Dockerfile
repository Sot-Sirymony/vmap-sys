# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

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
