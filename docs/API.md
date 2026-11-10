# Inventario backend API (as consumed by the Android app)

The app talks to a CRUD API over Postgres in `api/` (Go + echo). The app can still run without it: an empty
Server setting uses `MockInventoryApi`, which implements this same contract in memory.

- Base URL: set in the app, home screen → **Server** (e.g. `192.168.0.10:8080`; `http://` is added if missing, paths like
  `http://host/api` work). Empty = the built-in demo data (`MockInventoryApi`). Use plain `http://`: Android 4.2 has
  TLS 1.2 disabled by default. Client: `HttpInventoryApi` (OkHttp 3.12), tested against `MockWebServer`.
- All bodies are JSON with `Content-Type: application/json`.
- Timestamps are ISO-8601 strings in UTC, e.g. `2026-09-22T13:05:00Z`.
- Categories: `kitchen`, `cleaning`, `bathroom`, `other`.
- Units: `un`, `kg`, `g`, `L`, `mL`. `packageSize` is how much of the unit one scanned package holds.

## Auth

The web UI is served at `GET /web/` (static, no login needed to load it). Every other endpoint except `GET /` and `POST /login` needs `Authorization: Bearer <token>`. A missing, unknown or
expired token gets `401`; the app should then show its login screen. Tokens expire after 30 days without use, on
logout, when the password changes (other tokens of that user) and when an admin disables the user or resets
their password. Login is rate-limited per IP (`429`).

| Method & path | Who | Request body | Response |
|---|---|---|---|
| `POST /login` | anyone | `{ "username", "password" }` | `200 { "token", "user": User }` / `401` |
| `POST /logout` | user | — | `204` |
| `GET /me` | user | — | `200 User` |
| `PUT /me/password` | user | `{ "current", "new" }` | `204` / `403` wrong current |
| `GET /users` | admin | — | `200 [User + "active", "createdAt"]` |
| `POST /users` | admin | `{ "username", "password", "admin" }` | `201` |
| `PATCH /users/{id}` | admin | any of `{ "admin", "active", "password" }` | `200` |
| `GET /activity?limit=100&before={id}` | admin | — | `200 [ActivityEntry]` newest first |

`User` is `{ "id", "username", "admin" }`. Usernames are lowercased, `a-z 0-9 . _ -`, up to 50 chars.
Passwords are 8 to 72 characters. A non-admin calling an admin endpoint gets `403`. An admin can't remove their
own admin flag or disable themselves.

## Endpoints

All of these need a logged-in user (any role).

| # | Method & path | Used by | Request body | Response |
|---|---|---|---|---|
| 1 | `GET /products/by-barcode/{barcode}` | scan | — | `200 Product` / `404` |
| 2 | `POST /products` | register unknown code | `Product` without `id` | `201 Product` |
| 3 | `POST /movements` | "Send" session | `MovementBatch` | `201 { "id": 42 }` |
| 4 | `GET /inventory` | inventory list | — | `200 [InventoryItem]` |
| 5 | `GET /products/{id}` | product detail | — | `200 Product` |
| 6 | `GET /products/{id}/movements` | product history | — | `200 [Movement]` newest first |

## Types

```jsonc
// Product
{
  "id": 12,
  "barcode": "7891000100103",
  "name": "Arroz Tio João 5kg",
  "category": "kitchen",
  "unit": "kg",
  "packageSize": 5,
  "imageUrl": "http://host/images/12.jpg"   // may be null
}

// InventoryItem  (one per product that has stock or history)
{
  "product": { /* Product */ },
  "amount": 15,          // total in `unit`
  "packages": 3,         // amount / packageSize, rounded down
  "updatedAt": "2026-09-22T13:05:00Z"
}

// Movement  (one row of a product's history)
{
  "id": 301,
  "productId": 12,
  "type": "in",          // "in" = entry, "out" = usage
  "amount": 5,
  "packages": 1,
  "at": "2026-09-22T13:05:00Z",
  "userId": 3,           // who sent the batch this movement came from
  "username": "maria"
}

// MovementBatch  (what the app POSTs when a scanning session is sent; the user comes from the token)
{
  "type": "in",
  "at": "2026-09-22T13:05:00Z",
  "items": [
    { "productId": 12, "packages": 2, "amount": 10 },
    { "productId": 7,  "packages": 1, "amount": 1 }
  ]
}

// ActivityEntry  (audit log)
{
  "id": 88,
  "userId": 3,           // null for a failed login with an unknown username
  "username": "maria",
  "action": "movements.create",   // login, login.failed, logout, password.change, user.create,
                                  // user.update, product.create, movements.create
  "details": { "batchId": 42, "type": "in", "items": 2 },
  "ip": "192.168.0.23",
  "at": "2026-09-22T13:05:00Z"
}
```

## Notes for the backend

- Any non-2xx status is shown to the user as an error; a connection failure appears as status 0.
- Endpoint 1 must return `404` (not an empty body) when the barcode is unknown; the app opens the
  "register product" dialog on 404.
- Endpoint 3 is atomic: either every item is recorded or none. The app deletes its local session only
  after a `201`.
- The app currently only produces `type: "in"`. `"out"` movements come from other services and are
  only displayed.
