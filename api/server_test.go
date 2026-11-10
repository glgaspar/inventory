package main

import (
	"database/sql"
	"encoding/json"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/labstack/echo/v4"
)

var testDb *sql.DB

func TestMain(m *testing.M) {
	if os.Getenv("PG_HOST") == "" {
		println("PG_HOST not set, skipping api tests")
		os.Exit(0)
	}
	db, err := openDb()
	if err != nil {
		panic(err)
	}
	testDb = db
	os.Exit(m.Run())
}

func newTestServer(t *testing.T) *echo.Echo {
	if _, err := testDb.Exec("drop table if exists movements, batches, products"); err != nil {
		t.Fatal(err)
	}
	if _, err := testDb.Exec(schema); err != nil {
		t.Fatal(err)
	}
	return newServer(testDb)
}

func call(t *testing.T, e *echo.Echo, method, path, body string, wantStatus int, out any) {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != wantStatus {
		t.Fatalf("%s %s: status %d, want %d: %s", method, path, rec.Code, wantStatus, rec.Body)
	}
	if out != nil {
		if err := json.Unmarshal(rec.Body.Bytes(), out); err != nil {
			t.Fatalf("%s %s: %v: %s", method, path, err, rec.Body)
		}
	}
}

const rice = `{"barcode":"789","name":"Arroz 5kg","category":"kitchen","unit":"kg","packageSize":5,"imageUrl":null}`
const soap = `{"barcode":"790","name":"Sabonete","category":"bathroom","unit":"un","packageSize":1}`

func TestProducts(t *testing.T) {
	e := newTestServer(t)

	var created Product
	call(t, e, "POST", "/products", rice, 201, &created)
	if created.ID == 0 || created.Name != "Arroz 5kg" || created.PackageSize != 5 || created.ImageURL != nil {
		t.Fatalf("created %+v", created)
	}

	var found Product
	call(t, e, "GET", "/products/by-barcode/789", "", 200, &found)
	if found != created {
		t.Fatalf("by barcode %+v, want %+v", found, created)
	}
	call(t, e, "GET", "/products/1", "", 200, &found)
	if found != created {
		t.Fatalf("by id %+v, want %+v", found, created)
	}

	call(t, e, "GET", "/products/by-barcode/000", "", 404, nil)
	call(t, e, "GET", "/products/99", "", 404, nil)
	call(t, e, "GET", "/products/abc", "", 404, nil)
	call(t, e, "POST", "/products", rice, 409, nil)
	call(t, e, "POST", "/products", strings.Replace(soap, "bathroom", "garage", 1), 400, nil)
	call(t, e, "POST", "/products", strings.Replace(soap, `"un"`, `"lb"`, 1), 400, nil)
	call(t, e, "POST", "/products", strings.Replace(soap, `"packageSize":1`, `"packageSize":0`, 1), 400, nil)
	call(t, e, "POST", "/products", strings.Replace(soap, `"Sabonete"`, `""`, 1), 400, nil)
}

func TestMovementsAndInventory(t *testing.T) {
	e := newTestServer(t)
	call(t, e, "POST", "/products", rice, 201, nil)
	call(t, e, "POST", "/products", soap, 201, nil)
	call(t, e, "POST", "/products", `{"barcode":"791","name":"Café","category":"kitchen","unit":"g","packageSize":500}`, 201, nil)

	var inventory []InventoryItem
	call(t, e, "GET", "/inventory", "", 200, &inventory)
	if len(inventory) != 0 {
		t.Fatalf("inventory before movements: %+v", inventory)
	}

	var batch struct{ ID int64 }
	call(t, e, "POST", "/movements", `{"type":"in","at":"2026-09-01T10:00:00Z","items":[
		{"productId":1,"packages":3,"amount":15},{"productId":2,"packages":2,"amount":2}]}`, 201, &batch)
	if batch.ID == 0 {
		t.Fatal("no batch id")
	}
	call(t, e, "POST", "/movements", `{"type":"out","at":"2026-09-10T19:30:00-03:00","items":[
		{"productId":1,"packages":0,"amount":2.5}]}`, 201, nil)

	call(t, e, "POST", "/movements", `{"type":"in","at":"2026-09-11T10:00:00Z","items":[
		{"productId":2,"packages":1,"amount":1},{"productId":99,"packages":1,"amount":1}]}`, 400, nil)
	call(t, e, "POST", "/movements", `{"type":"lost","at":"2026-09-11T10:00:00Z","items":[{"productId":1,"packages":1,"amount":5}]}`, 400, nil)
	call(t, e, "POST", "/movements", `{"type":"in","items":[{"productId":1,"packages":1,"amount":5}]}`, 400, nil)
	call(t, e, "POST", "/movements", `{"type":"in","at":"2026-09-11T10:00:00Z","items":[]}`, 400, nil)

	call(t, e, "GET", "/inventory", "", 200, &inventory)
	if len(inventory) != 2 {
		t.Fatalf("inventory: %+v", inventory)
	}
	r, s := inventory[0], inventory[1]
	if r.Product.Barcode != "789" || r.Amount != 12.5 || r.Packages != 2 || r.UpdatedAt != "2026-09-10T22:30:00Z" {
		t.Fatalf("rice %+v", r)
	}
	if s.Product.Barcode != "790" || s.Amount != 2 || s.Packages != 2 || s.UpdatedAt != "2026-09-01T10:00:00Z" {
		t.Fatalf("soap %+v (the failed batch must not count)", s)
	}

	var history []Movement
	call(t, e, "GET", "/products/1/movements", "", 200, &history)
	if len(history) != 2 || history[0].Type != "out" || history[0].Amount != 2.5 || history[0].At != "2026-09-10T22:30:00Z" ||
		history[1].Type != "in" || history[1].Packages != 3 || history[1].ProductID != 1 {
		t.Fatalf("history %+v", history)
	}

	call(t, e, "GET", "/products/3/movements", "", 200, &history)
	if history == nil || len(history) != 0 {
		t.Fatalf("empty history %+v", history)
	}
}
