# api

Go (echo + Postgres) backend for the app. The contract is [docs/API.md](../docs/API.md). Tables are created on startup from `db/schema.sql`.

| Folder | What |
|---|---|
| `frontend/` | the web UI at `/web/`: plain HTML/CSS/JS in `static/`, embedded in the binary, no build step |
| `server/` | every route and the middleware it needs (the access rules live here), request logging, integration tests |
| `auth/` | login, logout, tokens, passwords, first admin |
| `users/` | admin user management |
| `activity/` | audit log: write and list |
| `lookup/` | identifies unknown barcodes: Open Food/Products/Beauty Facts; results cached in `lookups` |
| `images/` | downloads product photos from those sources and serves them over plain HTTP at `/images/{id}` (the CN51 can't do HTTPS) |
| `recipes/` | recipe suggestions from the kitchen stock through any OpenAI-compatible chat API (e.g. Open WebUI or Ollama) |
| `products/`, `movements/` | the app's endpoints; `movements/` also builds the inventory |
| `db/` | connection, schema, Postgres error → HTTP status |
| `web/` | small shared helpers |

On the first start with no users, set `ADMIN_USERNAME` and `ADMIN_PASSWORD` to create the first admin; they are ignored once any user exists.

The compose file runs only the API; the database is yours. Put its connection in `.env` (`PG_HOST`, `PG_PORT`, `PG_USER`, `PG_PASSWORD`, `PG_DBNAME`; the database must exist, the tables are created on start):

```sh
cp .env.example .env          # fill in PG_* and, for the first start, ADMIN_*
docker compose up -d --build  # API on :8080, or set HOST_PORT in .env
```

Or without Docker: `go run .` (it reads `.env` too).

Tests need a Postgres they can wipe (they drop and recreate the tables), so never point them at a real database:

```sh
docker run -d --rm --name inventario-test-pg -e POSTGRES_USER=inventory -e POSTGRES_PASSWORD=inventory -e POSTGRES_DB=inventory -p 55432:5432 postgres:17-alpine
PG_HOST=localhost PG_PORT=55432 PG_USER=inventory PG_PASSWORD=inventory PG_DBNAME=inventory go test ./...
```

Without `PG_HOST` the tests are skipped.

Web UI: open `http://<host>:8080/web/` and log in with the same users as the app. It has the inventory, product history (with who sent each movement), entry/usage sessions by barcode (a USB barcode scanner works in the input), and, for admins, users and the activity log. Text follows the browser language (pt-BR or English). It keeps the token and an unsent session in `localStorage`.

Unknown barcodes: `GET /lookup/{barcode}` asks Open Food Facts, Open Products Facts and Open Beauty Facts at the same time (free, no key). Found products are cached forever, misses for 7 days, failures not at all. The result is only a suggestion: the user confirms or fixes it in the new-product form.

Recipes: `POST /recipes/suggest` sends the kitchen items in stock (name and amount, nothing else) and an optional hint to the model set in `RECIPES_*`, asks for 3 recipes as JSON and returns them. Answers are cached per stock + hint + language + model, so asking again without changes is free; `refresh: true` asks for new ones. `RECIPES_API_URL` is the API base (the part before `/chat/completions`), `RECIPES_API_KEY` its bearer key if it needs one, `RECIPES_MODEL` a model id it serves. Empty = the feature is off (`503`).

Security notes:
- It speaks plain HTTP because Android 4.2 can't do modern TLS, so passwords and tokens cross the network in the clear. Keep it on a private network and don't expose it to the internet.
- Client IPs come from the TCP connection, not `X-Forwarded-For`. If it ever runs behind a proxy, the login rate limit (10 tries, then 1 every 10 s per IP) would be shared by everyone, so change `IPExtractor` in `server/server.go`.
- The UI sets all text with `textContent` and the CSP only allows scripts and styles from the server itself, so a product name can't inject script.
- Product photos are only downloaded from the lookup sources' hosts (`images.AllowedHosts`), at most 3 MB, and must really be an image.
- Recipe calls cost money on paid models, so they are rate-limited per IP (10, then 1 every 10 s) and logged in `activity` as `recipes.suggest`.
- Logs are JSON on stdout, one line per request (user and request id included). Who did what is in the `activity` table (`GET /activity`, admins only).
