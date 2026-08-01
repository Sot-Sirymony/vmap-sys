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
#   MaxRAMPercentage=50   256MB of heap — double the 128MB the 25% default gave
#                         it, which is what the GC was thrashing against, while
#                         still leaving ~256MB for everything the heap does not
#                         cover. Going higher is tempting and wrong: at 70% the
#                         heap alone is 360MB and the metaspace cap below would
#                         put the total over the instance before stacks and code
#                         cache are counted.
#   UseSerialGC           G1 reserves region tables and per-thread structures
#                         that are pure overhead below ~1GB with one CPU. Serial
#                         is the usual recommendation at this size and frees
#                         both RSS and startup CPU.
#   MaxMetaspaceSize      the classpath is large (JPA + Hibernate + Security +
#                         Redis + Flyway + Apache POI), and metaspace is
#                         unbounded by default, so it is capped rather than
#                         allowed to grow into the kill threshold.
#   Xss512k               ~50 Tomcat/Hikari threads at the 1MB default is ~50MB
#                         of stacks; half that is still ample for this app.
#   TieredStopAtLevel=1   C1 only. Cuts JIT compilation work and memory during
#                         the cold start we were losing to. This trades some
#                         peak throughput — drop this flag first if the API ever
#                         feels slow under sustained load.
#
# If the instance is ever resized, revisit these: they are tuned for 512MB.
# Kept in an env var so the values can be changed from the Render dashboard (or
# render.yaml) to retune a struggling instance without waiting on an image
# rebuild — which matters when the thing you are debugging is a boot failure.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -Xss512k -XX:TieredStopAtLevel=1"

# `exec` so the JVM replaces the shell and stays PID 1: without it the shell
# holds PID 1 and the JVM never receives Render's SIGTERM, turning every deploy
# into a hard kill after the grace period.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
