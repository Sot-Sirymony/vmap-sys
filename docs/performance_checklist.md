# Production performance checklist

Why the deployed app felt slow, what was changed, and what the running system
actually measures. The original audit was a judgement from reading the code.
The baseline below replaced that judgement with numbers, and overturned two of
its conclusions — both corrections are kept rather than quietly edited out.

Measurements were taken on 2026-09-24 from a laptop in the app's usual
location, so the figures include real client network conditions. They are a
single-client sample at one moment, not a load test.

## Measured baseline

**Where things run.** The services are hosted in Oregon (`gcp-us-west1`).
Cloudflare answers from Singapore, which is close to the user. Static files are
served from that nearby edge; API calls pass through it to Oregon and back.

**That crossing dominates every API call.** Three endpoints doing very
different amounts of work all answer in the same time, which is what a
distance-bound request looks like:

| Endpoint | Work done | Time to first byte |
|---|---|---|
| `/api/dreams` | none — rejected as 401 before any handler | 0.41s |
| `/api/nope` | none — no such route | 0.37s |
| `/api/health` | opens a Redis connection and pings it | 0.40s |

For comparison, a direct PostgreSQL query from the same laptop takes about
0.22s of pure round trip. Server-side processing is a small part of what a user
waits for.

**Concurrency is where the instance size shows.** Ten simultaneous health
requests, against 0.44s when issued one at a time:

| | Time to first byte |
|---|---|
| Fastest | 0.48s |
| Median | 0.81s |
| Slowest | 1.09s |

**Frontend.** Assets are Cloudflare cache hits compressed with brotli, about
0.20s to first byte. `index.html` is 974 bytes. The critical path is roughly
240 KB on the wire: 197 KB vendor, 31 KB entry, 12 KB CSS. The 111 KB chart
bundle is no longer part of it and arrives after paint.

**Cache.** Valkey 8.1.4, up 38 days. Peak use 7.17 MB against a 25 MB ceiling,
with zero evictions — the free plan is comfortably sized. It held zero keys when
sampled, which is correct: entries expire after ten minutes and traffic was
idle. Hit rate is 41% (2,726 hits against 3,880 misses), held down by clearing
a user's entries on every write.

**Database.** PostgreSQL 18.4, 10 MB in total, 100% buffer cache hit rate — the
whole thing sits in memory. The largest table has 188 rows. Of 103 available
connections, 13 were open.

| Table | Rows |
|---|---|
| `vision_steps` | 188 |
| `goals` | 131 |
| `task_items` | 80 |
| `dreams` | 57 |
| `app_users` | 5 |

## What to do next, in order

1. **Host closer to the users.** Removing the Oregon round trip is worth more
   than every code change made so far put together. It means recreating the web
   service, database and cache in a nearer region, so it needs a data migration
   and a maintenance window. Nothing else on this list comes close.
2. **Cut the number of API round trips per screen.** At roughly 0.3s each this
   is now the largest cost the application itself controls. The request cache
   below already removes repeat calls; combining the four to seven list calls
   the hierarchy pages each make would remove more.
3. **Stop reloading the signed-in user on every request.** `app_users` has
   served about 250,000 lookups against five rows. Individually trivial, but it
   happens on every authenticated call.
4. **Raise the instance only when concurrency starts to matter.** The
   measurements say this will not improve what a single user feels. Revisit once
   several people use the app at the same time.
5. **Leave the indexes alone and recheck at scale.** Re-read the index scan
   counts once any table passes a few thousand rows.

## What was done, and how it held up

**1. Instance size and JVM tuning — OPEN, and downgraded in priority.**
Half a CPU, 512MB, 20 request threads, 5 database connections, and a JIT pinned
to C1. This was the original top recommendation. The measurements only half
support it: single-request latency is distance, not CPU, so a larger plan will
not change the 0.4s a lone user sees. The concurrency figures above do show the
headroom is thin, so this becomes worthwhile with simultaneous users. These
values are a ceiling for a 512MB box and move up together with the instance.

**2. Batch fetching for lazy relations — DONE.**
`default_batch_fetch_size: 50` on all three profiles. It turned out to be a
safety net, not a fix: the dashboard measured zero lazy fetches, because the
mappers only read parent ids and parents load before children in the same
session. It bounds the Excel export, the archive cascades, and any future mapper
that walks a parent.

**3. Unbounded reads when creating a record — DONE.**
Generating the next display code (`T-001`) loaded every record the user owned to
find the highest suffix; sort-order placement loaded every sibling to count it.
Both are now single queries. Measured with Hibernate statistics, creating one
task in a 49-task account:

| | Entities loaded |
|---|---|
| Before | 53 |
| After | 8 |

**4. The dashboard read whole tables — DONE.**
`progress_logs` grows on every task edit and all of it was being read to draw a
twelve-week trend. The query now returns the window plus, per task, the last row
before it — the value each series carries forward from. Gratitude read the
user's whole history twice; that is now a count and a three-row page.

The KPI counts still aggregate in memory from rows already loaded. Pushing five
levels of hierarchy into grouped SQL risks moving the numbers, and with no N+1
present the gain would be small.

**5. Thin indexes — DONE, and premature.**
`V38__query_shaped_indexes.sql` added composites matching how the app reads:
`(user_id, archived)` for list endpoints, `(parent_id, user_id, archived)` for
the tree, and time-trailing ones for the trend, gratitude and reviews.

Eighteen of the nineteen have recorded zero scans since the migration ran. At
188 rows PostgreSQL correctly prefers a sequential scan, and the buffer cache
hit rate is already 100%. They cost 8–16 KB each and a little write time, and
they will start earning their place somewhere around ten thousand rows per
table. This was the right change made several years early.

Additive only. The older single-column indexes are redundant on PostgreSQL but
are not dropped, because H2 — which the test profile runs — backs a foreign key
constraint with them and refuses. A test database that no longer runs
production's migrations would cost more than a few duplicate b-trees.

**6. The progress roll-up loaded its children — DONE.**
Every task write reloaded the step's tasks and the goal's steps to average them.
Both are now one aggregate returning a count, a sum and a completed tally, with
the division left in Java so the rounding is unchanged. It is only correct
because JPQL flushes pending changes before querying — the roll-up runs
immediately after the write it reflects — and mocks cannot demonstrate that, so
`ProgressRollupFlowTests` checks it against a real database.

**7. The dashboard bundle was mostly charts — DONE.**
Recharts and its d3 dependencies now form a separate `charts` chunk reached only
through lazy imports, with same-size placeholders so nothing shifts when it
lands. The login form starts fetching the dashboard chunk while the sign-in
request is still in flight.

| | Dashboard page chunk |
|---|---|
| Before | 411 kB |
| After | 27 kB, plus a 381 kB chart chunk fetched after paint |

Not done: dropping MUI. That is a rewrite, not a tuning change.

**8. No compression or cache headers — DONE, with one gap.**
Responses over 2KB are compressed. Content-hashed assets are immutable for a
year and `index.html` is always revalidated.

The gap: the header rule matches on request path, so it applies to
`/index.html` but not to `/` or to deep routes like `/dashboard`, which reach the
same file through the single-page-app rewrite. Those fall back to Render's
default, which still revalidates in the browser but lets the edge hold the page
for five minutes. The effect is that a deploy can take up to five minutes to
reach someone already browsing.

**9. Pages fired four to seven list calls each — DONE.**
The hierarchy pages each need the same lists (areas, dreams, goals, steps,
tasks) and refetched all of them on every navigation. `apiClient` now holds a
GET for 30 seconds, keyed by token as well as path, and collapses concurrent
identical GETs into one request. Any write clears it, as does signing in or out.

The window is far shorter than the backend's ten-minute cache because nothing
but its own TTL can evict this one. `apiClient.test.ts` pins what keeps it safe:
no reuse across accounts, nothing surviving a write including a failed one, and
nothing cached from a failed request.

## How to re-measure

The connection strings live in `docs/production_env.md`, which is gitignored and
must stay that way — the external URLs embed passwords.

- **Is the cache alive:** `GET /api/health` reports `redis:up` or `redis:down`.
  It opens a real connection and pings, so it is a live check rather than a
  config echo. Cache failures degrade to a miss and never fail a request, so
  this field and the logs are the only signal that Redis has died.
- **Is latency distance or work:** compare time to first byte for `/api/health`
  against a 401 or a 404. If they match, the time is not being spent on work.
- **Are the indexes being used yet:** read `idx_scan` from
  `pg_stat_user_indexes` for the `%archived%` index names.
- **Is the cache earning its keep:** `keyspace_hits` and `keyspace_misses` from
  Redis `INFO stats`, and `DBSIZE` for how much it is currently holding.
