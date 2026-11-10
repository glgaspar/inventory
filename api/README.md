# api

Go (echo + Postgres) backend for the app. The contract is [docs/API.md](../docs/API.md). Tables are created on startup from `db/schema.sql`.

| Folder | What |
|---|---|
| `server/` | every route and the middleware it needs (the access rules live here), request logging, integration tests |
| `auth/` | login, logout, tokens, passwords, first admin |
| `users/` | admin user management |
| `activity/` | audit log: write and list |
| `products/`, `movements/` | the app's endpoints; `movements/` also builds the inventory |
| `db/` | connection, schema, Postgres error → HTTP status |
| `web/` | small shared helpers |

On the first start with no users, set `ADMIN_USERNAME` and `ADMIN_PASSWORD` to create the first admin; they are ignored once any user exists.

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

Without `PG_HOST` the tests are skipped.

Security notes:
- It speaks plain HTTP because Android 4.2 can't do modern TLS, so passwords and tokens cross the network in the clear. Keep it on the LAN and don't publish it through Cloudflare.
- Client IPs come from the TCP connection, not `X-Forwarded-For`. If it ever runs behind a proxy, the login rate limit (10 tries, then 1 every 10 s per IP) would be shared by everyone, so change `IPExtractor` in `server/server.go`.
- Logs are JSON on stdout, one line per request (user and request id included). Who did what is in the `activity` table (`GET /activity`, admins only).
