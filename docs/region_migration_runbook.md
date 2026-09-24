# Moving production from Oregon to Singapore

The services run in Oregon while the users are in Southeast Asia. Cloudflare
already answers from Singapore, so the handshake is fast, but every API call
then crosses the Pacific to the origin and back. That crossing is about 0.27 of
the 0.40 seconds a request takes, and no amount of tuning removes it.

| | Time to first byte per API call |
|---|---|
| Today, origin in Oregon | ~0.40s |
| Expected, origin in Singapore | ~0.15–0.20s |

## Read this first

**Render cannot move a service between regions.** Region is fixed when a
service is created. "Moving" means creating new services in Singapore, copying
the data across, pointing the frontend at them, and deleting the old ones.

**The web service cannot move on its own.** Private networking only works
within one region. A backend in Singapore talking to a database in Oregon would
fall back to the public internet and pay the Pacific crossing *per query*
instead of per request — many times worse than today. The database, the cache
and the backend move together, or not at all.

The frontend is a static site served from the CDN, so it does not need to move.
Only the API base URL it points at changes.

## Before you start

1. **Confirm Singapore offers what you use.** The Docker web service, the
   `basic-256mb` Postgres plan, and the free Key Value plan. Also confirm the
   pricing matches Oregon. Region pricing has historically been uniform, but
   check before committing.

2. **Install a Postgres 18 client.** The server is PostgreSQL 18.4 and this
   machine has 14.15, which refuses to dump a newer server.

   ```sh
   brew install postgresql@18
   /opt/homebrew/opt/postgresql@18/bin/pg_dump --version   # must report 18.x
   ```

3. **Decide about a custom domain.** New services get new `onrender.com`
   subdomains, so this move changes the URL your users type. A custom domain in
   front of the frontend makes this cutover — and every future one — invisible
   to them. If you are ever going to add one, add it before this move, not after.

4. **Pick a quiet window.** With five users, thirty minutes is generous. Writes
   made to Oregon after the dump is taken will not reach Singapore.

## Step 1 — Take a backup you can go back to

Run this while the old system is still live. Keep the file until the new
deployment has been trusted for a few days.

```sh
cd /Users/sotsirymony/Desktop/vmap-sys/vmap-sys
OLD_DB=$(grep -A1 -i 'external_Url' docs/production_env.md | grep -oE 'postgresql://[^ ]+')
/opt/homebrew/opt/postgresql@18/bin/pg_dump "$OLD_DB" \
  --no-owner --no-privileges --format=custom \
  -f "vmap-oregon-$(date +%Y%m%d-%H%M).dump"
ls -lh vmap-oregon-*.dump
```

Record what you expect to find on the other side:

```sh
psql "$OLD_DB" -c "select relname, n_live_tup from pg_stat_user_tables order by n_live_tup desc limit 10;"
```

## Step 2 — Create the Singapore services

Edit `render.yaml`. Give every service a **new name** and set the region. New
names matter: Render treats a renamed service as a new one, which is exactly
what you want — the Oregon services keep running untouched as your rollback.

```yaml
databases:
  - name: vision-mapping-db-sg          # was vision-mapping-db
    databaseName: vision_mapping
    user: vision_mapping
    plan: basic-256mb
    region: singapore

services:
  - type: keyvalue
    name: vision-mapping-cache-sg       # was vision-mapping-cache
    plan: free
    region: singapore
    maxmemoryPolicy: allkeys-lru
    ipAllowList: []

  - type: web
    name: vision-mapping-backend-sg     # was vision-mapping-backend
    runtime: docker
    region: singapore
    rootDir: backend
    dockerfilePath: ./Dockerfile
    plan: starter
    healthCheckPath: /api/health
    # every fromDatabase/fromService reference must now name the -sg services
```

Update the three `fromDatabase` and `fromService` blocks to point at
`vision-mapping-db-sg` and `vision-mapping-cache-sg`, or the new backend will
connect back to Oregon and you will have built the worst of both worlds.

Push, and let Render create the new services. Expect the backend to start,
run Flyway against an empty database, and come up healthy but with no data.

## Step 3 — Copy the data across

Take the new database's external URL from the Render dashboard.

```sh
NEW_DB='postgresql://...singapore-postgres.render.com/...'   # from the dashboard

# Flyway has already created the schema on the new database, so drop it and
# let the dump recreate everything, history table included.
psql "$NEW_DB" -c 'drop schema public cascade; create schema public;'

/opt/homebrew/opt/postgresql@18/bin/pg_restore \
  --no-owner --no-privileges -d "$NEW_DB" vmap-oregon-*.dump
```

Verify the row counts match what you recorded in step 1:

```sh
psql "$NEW_DB" -c "select relname, n_live_tup from pg_stat_user_tables order by n_live_tup desc limit 10;"
psql "$NEW_DB" -c "select max(version::int) as last_migration from flyway_schema_history;"
```

The last migration should read 38. Restart the new backend so Hibernate sees
the restored schema, and confirm it reports healthy:

```sh
curl -s https://vision-mapping-backend-sg.onrender.com/api/health
```

Expect `"status":"UP"` and `"cache":"redis:up"`.

The cache needs no migration. It holds nothing durable, expires in ten minutes,
and rebuilds from Postgres on a miss.

## Step 4 — Point the frontend at Singapore

Three settings name the old hosts. Update all three:

| Setting | Service | New value |
|---|---|---|
| `VITE_API_BASE_URL` | frontend | the `-sg` backend, plus `/api` |
| `CORS_ALLOWED_ORIGINS` | backend | the frontend origin |
| `PASSWORD_RESET_URL` | backend | the frontend's `/reset-password` |

`VITE_API_BASE_URL` is compiled into the bundle, so the frontend needs a
rebuild, not just a restart.

## Step 5 — Verify before you delete anything

```sh
# Latency: this is the whole point of the exercise.
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "ttfb=%{time_starttransfer}s\n" \
    https://vision-mapping-backend-sg.onrender.com/api/health
done
```

Expect roughly 0.15–0.20s against the 0.40s you measured in Oregon. If it has
not improved, stop and find out why before going further — something is still
pointing at Oregon.

Then, in the browser: sign in, open the dashboard, create and edit a record,
check the Vision Map renders, and confirm an existing account's data is all
present.

## Step 6 — Clean up, but not too quickly

Leave the Oregon services running for a few days. They cost little and they are
your rollback: if something is wrong, point `VITE_API_BASE_URL` back and you are
live again in one rebuild.

Once you are confident:

1. Delete the Oregon web service, database and cache in the dashboard.
2. Remove their blocks from `render.yaml`.
3. Update `docs/production_env.md` with the new connection strings. It is
   gitignored and must stay that way — the external URLs embed passwords.
4. Rotate the old credentials if they were ever pasted anywhere shared.

## If it goes wrong

Nothing here is destructive until step 6. Oregon keeps serving throughout, the
dump file is a second copy, and the cutover is one environment variable. The
only irreversible moment is deleting the Oregon database, which is why it is
last and why the dump is kept.

## Afterwards

Re-measure and update `docs/performance_checklist.md`. The baseline there was
taken against Oregon, and every latency figure in it becomes wrong the moment
this succeeds. Item 1 of the checklist — the instance size — should also be
re-judged against the new numbers, since the concurrency test was run with the
Pacific crossing included in every sample.
