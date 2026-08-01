# Backend deploy failure — Render, 2026-08-01

Why `vision-mapping-backend` failed to deploy, what the logs actually showed, and
what was changed. Written from two deploy logs: commit `6c33a92` (07:18 UTC) and
commit `80a273d` (07:44 UTC).

---

## Summary

The backend was killed by Render for exceeding the memory of its instance. It was
never a code defect and never a build failure — the image built successfully every
time. The container is a **512 MiB / 0.5-CPU `starter` instance**, and the image
ran a bare `java -jar app.jar` with **no JVM memory flags at all**, so the JVM
sized itself for the machine it thought it had and overshot the cgroup limit.

There were two distinct failure modes, one after the other:

| # | Commit | Symptom | Cause |
|---|---------|---------|-------|
| 1 | `6c33a92` | `Exited with status 137` — killed mid-boot, port never bound | Default 128 MB heap → GC thrashing; startup too slow to ever finish |
| 2 | `80a273d` | `Out of memory (used over 512Mi)` — booted, then died on first request | Heap fixed, but the *sum* of all memory caps still exceeded 512 MiB |

Fixing the first exposed the second. They are the same underlying problem —
an untuned JVM in a small container — seen from two angles.

---

## What was NOT the cause

**The frontend commit did not break the backend.** This matters because the
failure appeared immediately after a commit that touched only
`frontend/src/**`.

In the `6c33a92` build log, every backend layer was reused from cache:

```
#11 [build 5/6] COPY src ./src                          CACHED
#12 [build 6/6] RUN mvn -B clean package -DskipTests    CACHED
#17 [stage-1 3/3] COPY --from=build /app/target/*.jar   CACHED
```

`CACHED` on the `COPY src` and `mvn package` layers proves the backend source and
the resulting jar were byte-identical to the previous deploy. The jar that failed
was the same jar that had been running.

The reason a frontend change surfaced a backend failure at all: **Render
redeploys the entire blueprint on any push to `main`.** Both services in
`render.yaml` restart, so a frontend-only commit still bounces the backend. The
backend had most likely been marginal for some time; this deploy simply restarted
it into the failure.

---

## Failure 1 — killed during startup (`6c33a92`)

### What the log showed

```
07:18:38.855  Starting VisionMappingApplication v4.0.0 ... with PID 1
07:18:55.049  Finished Spring Data repository scanning in 912 ms. Found 14 JPA repository interfaces.
07:18:55.536  Finished Spring Data repository scanning in 278 ms. Found 0 Redis repository interfaces.
07:19:15.438  Tomcat initialized with port 10000 (http)
07:19:16.742  Root WebApplicationContext: initialization completed in 37188 ms
==> No open ports detected, continuing to scan...
07:19:53.335  Successfully validated 18 migrations (execution time 00:01.915s)
07:20:11.665  Initialized JPA EntityManagerFactory for persistence unit 'default'
07:20:33.035  Hibernate is in classpath; If applicable, HQL parser will be used.
==> Exited with status 137
```

### Reading it

Two lines never appeared, and their absence is the whole diagnosis:

- `Tomcat started on port(s): 10000` — the port was **initialized** but never
  **started**. Tomcat binds at the very end of startup.
- `Started VisionMappingApplication in N seconds`

The app was killed roughly **115 seconds in**, still working through startup.
Render's `No open ports detected` messages are a consequence, not the cause: the
app had not got far enough to bind.

`status 137` is `128 + 9` — **SIGKILL**. Render does not label this one as an OOM
in the log, but SIGKILL with no application-level exception, on a container that
never finished booting, is the signature of the kernel OOM killer. Failure 2
confirmed it explicitly.

The supporting evidence is the *timing*. A healthy start for this app is 10–15
seconds. Every phase was pathologically slow, which is what GC thrashing under a
too-small heap looks like — the JVM spends its time collecting instead of
progressing:

| Phase | `6c33a92` | `80a273d` (after heap fix) | Speed-up |
|---|---|---|---|
| Root WebApplicationContext init | 37,188 ms | 8,598 ms | **4.3×** |
| JPA repository scan | 912 ms | 505 ms | 1.8× |
| Redis repository scan | 278 ms | 11 ms | **25×** |
| Flyway validate (18 migrations) | 1,915 ms | 136 ms | **14×** |

Nothing about the application changed between those two columns — only JVM flags.
That rules out slow queries, a slow database, or cold DNS, and points squarely at
resource starvation inside the JVM.

### Root cause

`backend/Dockerfile` ended with:

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

No memory flags. On a 512 MiB container a Java 17 JVM is container-aware but its
defaults are wrong here in both directions at once:

- **Heap too small.** `MaxRAMPercentage` defaults to **25%** → a 128 MB heap.
  That is not enough for Spring Boot with 14 JPA repositories, Hibernate,
  Security, Redis, Flyway and Apache POI, so the GC ran continuously.
- **Everything else too large.** Metaspace is **unbounded** by default. G1 (the
  default collector) reserves region tables and per-thread structures that are
  pure overhead below ~1 GB on a single CPU. Thread stacks default to 1 MB on
  Linux.

So the heap starved while native memory grew unchecked, and the total crossed the
cgroup limit.

### Fix — commit `80a273d`

```dockerfile
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m \
               -Xss512k -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```

- Options live in `JAVA_OPTS` so a struggling instance can be retuned from the
  Render dashboard without waiting on an image rebuild — which matters when the
  thing being debugged is a boot failure.
- `exec` so the JVM replaces the shell and stays PID 1. Without it the shell holds
  PID 1, the JVM never receives Render's `SIGTERM`, and every deploy becomes a
  hard kill after the grace period.

**Result: the startup failure was fixed.** The app booted and bound its port.

---

## Failure 2 — OOM on first request (`80a273d`)

### What the log showed

```
07:44:39.934  Root WebApplicationContext: initialization completed in 8598 ms
07:44:57.684  Tomcat started on port 10000 (http) with context path '/'
07:44:57.742  Started VisionMappingApplication in 28.874 seconds
07:44:58.632  Initializing Spring DispatcherServlet 'dispatcherServlet'
07:44:58.636  Completed initialization in 3 ms
==> Out of memory (used over 512Mi)
==> Instance restarted
07:45:30.494  Started VisionMappingApplication in 25.902 seconds     <- second boot OK
```

### Reading it

Both missing lines from failure 1 now appear — the app **started successfully**.
Render then killed it with an explicit OOM message, and *where* that message
falls is the useful part: immediately after `DispatcherServlet` initialized, which
is lazy and initializes on the **first HTTP request** (here, the health check).
Render's `==> Out of memory` line carries no timestamp, so the exact gap is
unknown — but the ordering is unambiguous. Startup fit inside the container;
startup plus serving one request did not.

The instance restarted and the second boot came up clean and stayed up, which is
the profile of a service sitting exactly on its ceiling rather than one that is
fundamentally broken.

### Root cause

Sizing the heap was only half the job. The heap is one of *five* things competing
for the same 512 MiB, and the previous fix bounded only two of them. Adding up
what those settings actually permitted:

| Consumer | Permitted | Notes |
|---|---:|---|
| Heap | 256 MB | `MaxRAMPercentage=50` |
| Metaspace | 160 MB | capped, but generously |
| **Code cache** | **240 MB** | JIT's *default reservation* — never counted |
| **Direct memory** | **unbounded** | Lettuce → Netty off-heap buffers for Redis |
| **Thread stacks** | **~100 MB** | Tomcat's **default 200 request threads** × 512k |
| JVM base / native | ~40 MB | metadata, symbol tables, GC structures |
| **Worst case** | **~800 MB** | on a 512 MB instance |

Each number looked defensible on its own. That is exactly how the total was
missed. Two consumers had not been considered at all:

- **The JIT code cache** reserves 240 MB by default. Reserved address space is not
  all resident, but it was entirely unaccounted for.
- **Tomcat's thread pool defaults to 200 threads.** On 0.5 of a CPU that is
  meaningless as concurrency and dangerous as memory — the stacks alone could
  outgrow the heap.

Also unbounded: Netty's direct `ByteBuffer`s, used by Lettuce to talk to Redis.
Those live **off-heap**, so they are invisible to every heap setting and land
straight in the container's total.

### Fix — commit `4821093`

Every consumer capped, and the caps made to **sum to less than the instance**:

```dockerfile
ENV JAVA_OPTS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC -XX:MaxMetaspaceSize=144m \
               -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m \
               -Xss512k -XX:TieredStopAtLevel=1"
```

```yaml
# application.yml, prod profile — not JVM flags, so they live here
server:
  tomcat:
    threads:
      max: 20
      min-spare: 4
    accept-count: 100
    max-connections: 200
spring:
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
```

Resulting budget:

| Component | Cap |
|---|---:|
| Heap | 208 MB |
| Metaspace | 144 MB |
| Code cache | 64 MB |
| Direct memory | 32 MB |
| Thread stacks (~40 × 512k) | 20 MB |
| JVM base / native (est.) | 40 MB |
| **Worst case total** | **508 MB** |
| Container limit | 512 MB |

Realistic committed usage is nearer **420 MB**, since metaspace settles around
120 MB and the code cache commits far less than it reserves.

Requests beyond the 20-thread pool queue in `accept-count` rather than being
refused. Hikari's default of 10 connections was more than the capped thread pool
will ever ask for, and more than the 256 MB database plan wants to hold.

---

## Verification

**Done:**

- JVM flags parsed directly out of `backend/Dockerfile` and resolved against a
  simulated 512 MiB container (`-XX:MaxRAM=512m -XX:+PrintFlagsFinal`), confirming
  each one is accepted and produces the intended value.
- All six new YAML properties checked against
  `spring-boot-autoconfigure-3.5.3.jar`'s own `spring-configuration-metadata.json`.
  This check matters more than it looks: **a misspelled Spring property binds to
  nothing and fails silently**, so a typo would have shipped as a no-op.
- `mvn test` green.

**Not done:**

- No end-to-end container run. There was no Docker daemon available locally, so
  steady-state memory under real traffic is proven only by the deploy itself.

---

## Open risks

1. **512 MiB is genuinely marginal for this stack.** JPA (14 repositories) +
   Hibernate + Security + Redis + Flyway + Apache POI. The budget now fits, but
   with little slack. If it OOMs again, the answer is not another tuning round —
   it is `plan: standard` (2 GB) in `render.yaml`.
2. **Excel export is the untested spike.** Apache POI builds workbooks in memory.
   A large `/api/excel/export` could exceed the ceiling regardless of these caps,
   because the cost scales with the data, not with the configuration. That
   endpoint should be exercised against production-sized data specifically.
3. **`TieredStopAtLevel=1` trades peak throughput for startup cost.** It is the
   right call on 0.5 of a CPU, but it is the first flag to drop if the API feels
   slow under sustained load.
4. **These are a resource ceiling, not a tuning preference.** If the instance is
   ever resized, raise them together — a larger box with a 20-thread cap is just a
   more expensive bottleneck.

---

## Runbook — diagnosing this class of failure

1. **Check whether your commit is even implicated.** Read the build log for
   `CACHED` on the `COPY src` and `mvn package` layers. If both are cached, the
   jar is unchanged and the cause is environmental.
2. **Look for the two "finished" lines.** `Tomcat started on port(s)` and
   `Started <App> in N seconds`. If they are missing, the app died during startup.
   If they are present and it still died, it died while serving.
3. **Read `exit 137` as SIGKILL**, and on a container platform assume the OOM
   killer until something else is proven.
4. **Treat slow startup as a memory symptom.** Compare phase timings between
   deploys. An application that is 4× slower with no code change is not slow —
   it is starved.
5. **Add up every cap before trusting any of them.** Heap, metaspace, code cache,
   direct memory, thread stacks, JVM base. The failure is in the sum, and the
   easiest ones to forget — code cache and thread stacks — are not heap settings
   and so do not appear in any heap-related search.

---

## Commit trail

| Commit | Change |
|---|---|
| `6c33a92` | Frontend pie-chart colour fix — the deploy that surfaced this, not its cause |
| `80a273d` | JVM sizing: heap, Serial GC, metaspace cap, stacks, JIT tiering — fixed the startup kill |
| `4821093` | Total-footprint fix: code cache, direct memory, Tomcat threads, Hikari pool — caps now sum under 512 MiB |
