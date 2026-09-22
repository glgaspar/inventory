# Inventario backend API (as consumed by the Android app)

The app talks to a CRUD API over Postgres that lives in a separate repository.
Until it exists, the app uses `MockInventoryApi`, which implements this same contract in memory.

- Base URL: configurable in the app (plain `http://` — Android 4.2 has TLS 1.2 disabled by default).
- All bodies are JSON with `Content-Type: application/json`.
- Timestamps are ISO-8601 strings in UTC, e.g. `2026-09-22T13:05:00Z`.
- Categories: `kitchen`, `cleaning`, `bathroom`, `other`.
- Units: `un`, `kg`, `g`, `L`, `mL`. `packageSize` is how much of the unit one scanned package holds.

## Endpoints

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
  "at": "2026-09-22T13:05:00Z"
}

// MovementBatch  (what the app POSTs when a scanning session is sent)
{
  "type": "in",
  "at": "2026-09-22T13:05:00Z",
  "items": [
    { "productId": 12, "packages": 2, "amount": 10 },
    { "productId": 7,  "packages": 1, "amount": 1 }
  ]
}
```

## Notes for the backend

- Endpoint 1 must return `404` (not an empty body) when the barcode is unknown; the app opens the
  "register product" dialog on 404.
- Endpoint 3 is atomic: either every item is recorded or none. The app deletes its local session only
  after a `201`.
- The app currently only produces `type: "in"`. `"out"` movements come from other services and are
  only displayed.
