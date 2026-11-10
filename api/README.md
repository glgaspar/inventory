# api

Go (echo + Postgres) backend for the app. The contract is [docs/API.md](../docs/API.md). Tables are created on startup from `db.sql`.

```sh
cp .env.example .env          # set PG_PASSWORD
docker compose up -d --build  # api on :8080, postgres on :5433
```

Run locally against the compose database: `docker compose up -d db && go run .`

Tests need a Postgres they can wipe (they drop and recreate the tables):

```sh
docker compose up -d db
PG_HOST=localhost PG_PORT=5433 PG_USER=inventory PG_PASSWORD=... PG_DBNAME=inventory go test ./...
```

Without `PG_HOST` the tests are skipped. There is no auth, so keep it on the LAN and don't publish it through Cloudflare.
