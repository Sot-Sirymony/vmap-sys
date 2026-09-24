# Production performance checklist

A code-based audit of why the deployed app felt slow, and what was done about
it. The ranking was a judgement from reading the code and the deploy config,
not from profiling production. Where a change was measured, the measurement is
recorded next to it.

Items 2 to 9 are done. Item 1 is open and is the operator's call.

## 1. The instance is small, and the JVM is tuned for survival — OPEN

The service runs on half a CPU and 512MB, with 20 request threads and 5
database connections. The Dockerfile pins the JIT to C1 only, and its own
comment says to drop that flag first if the API ever feels slow under load.

Raising the Render plan, and then the heap, thread and pool ceilings together,
is likely to beat any remaining code change. These values are a resource
ceiling for a 512MB box, so they move up together with the instance, never on
their own. Deliberately not changed here: it costs money and is a decision
about the deployment, not the code.

## 2. Lazy relations had no batch fetching — DONE

`default_batch_fetch_size: 50` on all three profiles. This turned out to be a
safety net rather than a fix: the dashboard measured zero lazy fetches, because
the mappers only read parent ids and parents are loaded before children in the
same session. It bounds the Excel export, the archive cascades, and any future
mapper that walks a parent.

## 3. Creating a record read every record of its type — DONE

Generating the next display code (`T-001`) loaded every record the user owned
to find the highest suffix. `CodedRepository` now reads just the code column.
Sort-order placement did the same thing to count siblings, and is now a count
query.

Measured with Hibernate statistics, creating one task in a 49-task account:

| | Entities loaded |
|---|---|
| Before | 53 |
| After | 8 |

## 4. The dashboard read whole tables — DONE

`progress_logs` grows on every task edit, and all of it was being read to draw
a twelve-week trend. The query now returns the window plus, per task, the last
row before it — the value each series carries forward from. Gratitude read the
user's whole history twice; that is now a count and a three-row page.

The KPI counts still aggregate in memory from rows already loaded. Pushing five
levels of hierarchy into grouped SQL risks moving the numbers, and with no N+1
present the remaining win is small at realistic sizes.

## 5. Indexes were thin — DONE

`V38__query_shaped_indexes.sql` adds composites matching how the app actually
reads: `(user_id, archived)` for the list endpoints, `(parent_id, user_id,
archived)` for the tree, and time-trailing ones for the progress-log trend,
gratitude and reviews.

Additive only. The older single-column indexes are redundant on PostgreSQL but
are not dropped, because H2 — which the test profile runs — backs a foreign key
constraint with them and refuses. Diverging the test schema from production
would cost more than a few duplicate b-trees.

## 6. The progress roll-up loaded its children — DONE

Every task write reloaded the step's tasks and the goal's steps to average
them. Both are now one aggregate query returning a count, a sum and a completed
tally, with the division left in Java so the rounding is unchanged.

The roll-up runs immediately after the write it reflects, so it is only correct
because JPQL flushes pending changes before querying. Mocks cannot demonstrate
that, so `ProgressRollupFlowTests` checks it against a real database.

## 7. The dashboard bundle was mostly charts — DONE

Recharts and its d3 dependencies are now a separate `charts` chunk, reached
only through lazy imports, with same-size placeholders so nothing shifts when
it lands. The dashboard's numbers, attention panel and task table no longer
wait on chart code. The login form also starts fetching the dashboard chunk
while the sign-in request is in flight.

| | Dashboard page chunk |
|---|---|
| Before | 411 kB |
| After | 27 kB, plus a 381 kB chart chunk loaded after paint |

Not done: dropping MUI. That is a rewrite, not a tuning change.

## 8. No compression or cache headers — DONE

Response compression on the API above 2KB, so the large JSON payloads stop
travelling uncompressed. In `render.yaml`, the hashed assets under `/assets`
are now immutable for a year, while `index.html` is always revalidated — it is
the one file whose name never changes and it points at everything else.

## 9. Pages fired four to seven list calls each — DONE

The hierarchy pages each need the same lists (areas, dreams, goals, steps,
tasks) to label and filter their rows, and refetched all of them on every
navigation. `apiClient` now caches GETs for 30 seconds, keyed by token as well
as path, and collapses concurrent identical GETs into one request. Any write
clears it, as does signing in or out.

The window is deliberately far shorter than the backend's ten-minute Redis
cache, because nothing but its own TTL can evict this one. `apiClient.test.ts`
pins the rules that keep it safe: no cross-account reuse, nothing surviving a
write, and nothing cached from a failed request.
