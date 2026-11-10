package server

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/db"
	"github.com/glgaspar/inventory/api/images"
	"github.com/glgaspar/inventory/api/lookup"
	"github.com/glgaspar/inventory/api/movements"
	"github.com/glgaspar/inventory/api/products"
	"github.com/labstack/echo/v4"
)

var conn *sql.DB

func TestMain(m *testing.M) {
	if os.Getenv("PG_HOST") == "" {
		println("PG_HOST not set, skipping api tests")
		os.Exit(0)
	}
	slog.SetDefault(slog.New(slog.NewTextHandler(io.Discard, nil)))
	var err error
	if conn, err = db.Open(); err != nil {
		panic(err)
	}
	os.Exit(m.Run())
}

type client struct {
	t     *testing.T
	e     *echo.Echo
	token string
}

func setup(t *testing.T) *client {
	if _, err := conn.Exec("drop table if exists activity, movements, batches, products, tokens, users, lookups, images, recipe_suggestions"); err != nil {
		t.Fatal(err)
	}
	if err := db.Migrate(conn); err != nil {
		t.Fatal(err)
	}
	if _, err := auth.CreateUser(conn, "Admin", "admin-password", true); err != nil {
		t.Fatal(err)
	}
	c := &client{t: t, e: New(conn)}
	return c.login("admin", "admin-password")
}

func (c *client) login(username, password string) *client {
	c.t.Helper()
	var res struct{ Token string }
	(&client{t: c.t, e: c.e}).call("POST", "/login", `{"username":"`+username+`","password":"`+password+`"}`, 200, &res)
	return &client{t: c.t, e: c.e, token: res.Token}
}

func (c *client) call(method, path, body string, wantStatus int, out any) {
	c.t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if c.token != "" {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}
	rec := httptest.NewRecorder()
	c.e.ServeHTTP(rec, req)
	if rec.Code != wantStatus {
		c.t.Fatalf("%s %s: status %d, want %d: %s", method, path, rec.Code, wantStatus, rec.Body)
	}
	if out != nil {
		if err := json.Unmarshal(rec.Body.Bytes(), out); err != nil {
			c.t.Fatalf("%s %s: %v: %s", method, path, err, rec.Body)
		}
	}
}

const rice = `{"barcode":"789","name":"Arroz 5kg","category":"kitchen","unit":"kg","packageSize":5,"imageUrl":null}`
const soap = `{"barcode":"790","name":"Sabonete","category":"bathroom","unit":"un","packageSize":1}`

func TestAccessControl(t *testing.T) {
	admin := setup(t)
	anon := &client{t: t, e: admin.e}
	bogus := &client{t: t, e: admin.e, token: "nope"}

	anon.call("GET", "/", "", 200, nil)
	for _, path := range []string{"/inventory", "/products/1", "/products/by-barcode/1", "/products/1/movements", "/me", "/users", "/activity"} {
		anon.call("GET", path, "", 401, nil)
		bogus.call("GET", path, "", 401, nil)
	}
	anon.call("POST", "/movements", `{}`, 401, nil)
	anon.call("POST", "/products", rice, 401, nil)

	anon.call("POST", "/login", `{"username":"admin","password":"wrong-password"}`, 401, nil)
	anon.call("POST", "/login", `{"username":"ghost","password":"whatever-pass"}`, 401, nil)

	admin.call("POST", "/users", `{"username":"Maria","password":"short"}`, 400, nil)
	admin.call("POST", "/users", `{"username":"bad name","password":"long-enough"}`, 400, nil)
	var maria struct{ ID int64 }
	admin.call("POST", "/users", `{"username":"Maria","password":"maria-password"}`, 201, &maria)
	admin.call("POST", "/users", `{"username":"maria","password":"maria-password"}`, 409, nil)

	user := admin.login("MARIA ", "maria-password")
	var me auth.User
	user.call("GET", "/me", "", 200, &me)
	if me.Username != "maria" || me.Admin {
		t.Fatalf("me %+v", me)
	}
	user.call("GET", "/inventory", "", 200, nil)
	user.call("GET", "/users", "", 403, nil)
	user.call("POST", "/users", `{"username":"x","password":"long-enough"}`, 403, nil)
	user.call("PATCH", "/users/1", `{"admin":false}`, 403, nil)
	user.call("GET", "/activity", "", 403, nil)

	admin.call("PATCH", "/users/1", `{"admin":false}`, 400, nil)
	admin.call("PATCH", "/users/1", `{"active":false}`, 400, nil)

	user.call("PUT", "/me/password", `{"current":"wrong","new":"new-password"}`, 403, nil)
	other := admin.login("maria", "maria-password")
	user.call("PUT", "/me/password", `{"current":"maria-password","new":"new-password"}`, 204, nil)
	user.call("GET", "/me", "", 200, nil)
	other.call("GET", "/me", "", 401, nil)
	anon.call("POST", "/login", `{"username":"maria","password":"maria-password"}`, 401, nil)
	user = admin.login("maria", "new-password")

	admin.call("PATCH", "/users/"+jsonNumber(maria.ID), `{"active":false}`, 200, nil)
	user.call("GET", "/me", "", 401, nil)
	anon.call("POST", "/login", `{"username":"maria","password":"new-password"}`, 401, nil)
	admin.call("PATCH", "/users/"+jsonNumber(maria.ID), `{"active":true,"password":"reset-password"}`, 200, nil)
	user = admin.login("maria", "reset-password")

	user.call("POST", "/logout", "", 204, nil)
	user.call("GET", "/me", "", 401, nil)

	var log []activity.Entry
	admin.call("GET", "/activity?limit=500", "", 200, &log)
	actions := map[string]int{}
	for _, e := range log {
		actions[e.Action]++
	}
	if actions["login.failed"] != 4 || actions["user.create"] != 1 || actions["user.update"] != 2 ||
		actions["password.change"] != 1 || actions["logout"] != 1 {
		t.Fatalf("activity %v", actions)
	}
	admin.call("GET", "/activity?limit=2", "", 200, &log)
	if len(log) != 2 || log[0].ID <= log[1].ID {
		t.Fatalf("activity paging %+v", log)
	}
	var older []activity.Entry
	admin.call("GET", "/activity?limit=2&before="+jsonNumber(log[1].ID), "", 200, &older)
	if len(older) != 2 || older[0].ID >= log[1].ID {
		t.Fatalf("activity before %+v", older)
	}
}

func TestLoginRateLimit(t *testing.T) {
	admin := setup(t)
	anon := &client{t: t, e: admin.e}
	for i := 0; i < 9; i++ {
		anon.call("POST", "/login", `{"username":"admin","password":"wrong-password"}`, 401, nil)
	}
	anon.call("POST", "/login", `{"username":"admin","password":"wrong-password"}`, 429, nil)
}

func TestProducts(t *testing.T) {
	c := setup(t)

	var created products.Product
	c.call("POST", "/products", rice, 201, &created)
	if created.ID == 0 || created.Name != "Arroz 5kg" || created.PackageSize != 5 || created.ImageURL != nil {
		t.Fatalf("created %+v", created)
	}

	var found products.Product
	c.call("GET", "/products/by-barcode/789", "", 200, &found)
	if found != created {
		t.Fatalf("by barcode %+v, want %+v", found, created)
	}
	c.call("GET", "/products/1", "", 200, &found)
	if found != created {
		t.Fatalf("by id %+v, want %+v", found, created)
	}

	c.call("GET", "/products/by-barcode/000", "", 404, nil)
	c.call("GET", "/products/99", "", 404, nil)
	c.call("GET", "/products/abc", "", 404, nil)
	c.call("POST", "/products", rice, 409, nil)
	c.call("POST", "/products", strings.Replace(soap, "bathroom", "garage", 1), 400, nil)
	c.call("POST", "/products", strings.Replace(soap, `"un"`, `"lb"`, 1), 400, nil)
	c.call("POST", "/products", strings.Replace(soap, `"packageSize":1`, `"packageSize":0`, 1), 400, nil)
	c.call("POST", "/products", strings.Replace(soap, `"Sabonete"`, `""`, 1), 400, nil)
	c.call("POST", "/products", strings.Replace(soap, `"Sabonete"`, `"`+strings.Repeat("a", 201)+`"`, 1), 400, nil)
	c.call("POST", "/products", `{"barcode":"1","name":"`+strings.Repeat("a", 2<<20)+`"}`, 413, nil)
}

func TestMovementsAndInventory(t *testing.T) {
	admin := setup(t)
	admin.call("POST", "/users", `{"username":"maria","password":"maria-password"}`, 201, nil)
	c := admin.login("maria", "maria-password")
	c.call("POST", "/products", rice, 201, nil)
	c.call("POST", "/products", soap, 201, nil)
	c.call("POST", "/products", `{"barcode":"791","name":"Café","category":"kitchen","unit":"g","packageSize":500}`, 201, nil)

	var inventory []movements.InventoryItem
	c.call("GET", "/inventory", "", 200, &inventory)
	if len(inventory) != 0 {
		t.Fatalf("inventory before movements: %+v", inventory)
	}

	var batch struct{ ID int64 }
	c.call("POST", "/movements", `{"type":"in","at":"2026-09-01T10:00:00Z","items":[
		{"productId":1,"packages":3,"amount":15},{"productId":2,"packages":2,"amount":2}]}`, 201, &batch)
	if batch.ID == 0 {
		t.Fatal("no batch id")
	}
	admin.call("POST", "/movements", `{"type":"out","at":"2026-09-10T19:30:00-03:00","items":[
		{"productId":1,"packages":0,"amount":2.5}]}`, 201, nil)

	c.call("POST", "/movements", `{"type":"in","at":"2026-09-11T10:00:00Z","items":[
		{"productId":2,"packages":1,"amount":1},{"productId":99,"packages":1,"amount":1}]}`, 400, nil)
	c.call("POST", "/movements", `{"type":"lost","at":"2026-09-11T10:00:00Z","items":[{"productId":1,"packages":1,"amount":5}]}`, 400, nil)
	c.call("POST", "/movements", `{"type":"in","items":[{"productId":1,"packages":1,"amount":5}]}`, 400, nil)
	c.call("POST", "/movements", `{"type":"in","at":"2026-09-11T10:00:00Z","items":[]}`, 400, nil)
	c.call("POST", "/movements", `{"type":"in","at":"2026-09-11T10:00:00Z","items":[{"productId":1,"packages":-1,"amount":5}]}`, 400, nil)

	c.call("GET", "/inventory", "", 200, &inventory)
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

	var history []movements.Movement
	c.call("GET", "/products/1/movements", "", 200, &history)
	if len(history) != 2 ||
		history[0].Type != "out" || history[0].Amount != 2.5 || history[0].At != "2026-09-10T22:30:00Z" || history[0].Username != "admin" ||
		history[1].Type != "in" || history[1].Packages != 3 || history[1].ProductID != 1 || history[1].Username != "maria" || history[1].UserID != 2 {
		t.Fatalf("history %+v", history)
	}

	c.call("GET", "/products/3/movements", "", 200, &history)
	if history == nil || len(history) != 0 {
		t.Fatalf("empty history %+v", history)
	}

	var log []activity.Entry
	admin.call("GET", "/activity", "", 200, &log)
	found := false
	for _, e := range log {
		if e.Action == "movements.create" && e.Username != nil && *e.Username == "maria" && strings.Contains(string(e.Details), `"items":2`) {
			found = true
		}
	}
	if !found {
		t.Fatalf("no movements.create by maria in %+v", log)
	}
}

func jsonNumber(n int64) string {
	b, _ := json.Marshal(n)
	return string(b)
}

func TestFrontend(t *testing.T) {
	e := New(nil)
	for path, want := range map[string]int{"/web": 301, "/web/": 200, "/web/app.js": 200, "/web/style.css": 200, "/web/nope.js": 404} {
		rec := httptest.NewRecorder()
		e.ServeHTTP(rec, httptest.NewRequest("GET", path, nil))
		if rec.Code != want {
			t.Fatalf("GET %s: %d, want %d", path, rec.Code, want)
		}
		if !strings.Contains(rec.Header().Get("Content-Security-Policy"), "default-src 'self'") {
			t.Fatalf("GET %s: no CSP", path)
		}
	}
}

func TestLookup(t *testing.T) {
	c := setup(t)
	png := []byte("\x89PNG\r\n\x1a\n" + strings.Repeat("\x00", 64))
	hits := 0
	fake := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasPrefix(r.URL.Path, "/img"):
			w.Write(png)
		case strings.Contains(r.URL.Path, "7891000100103"):
			hits++
			w.Write([]byte(`{"status":1,"product":{"product_name_pt":"Leite Condensado","brands":"Moça","quantity":"395 g","image_front_url":"` +
				"http://" + r.Host + `/img.png"}}`))
		case strings.Contains(r.URL.Path, "5555555555555"):
			w.WriteHeader(502)
		default:
			hits++
			w.Write([]byte(`{"status":0}`))
		}
	}))
	defer fake.Close()
	lookup.SetSources(fake.URL)
	images.AllowedHosts = append(images.AllowedHosts, "127.0.0.1")

	var s lookup.Suggestion
	c.call("GET", "/lookup/7891000100103", "", 200, &s)
	if s.Name != "Leite Condensado Moça" || s.Unit != "g" || s.PackageSize != 395 || s.ImageURL == nil ||
		!strings.HasPrefix(*s.ImageURL, "http://example.com/images/") {
		t.Fatalf("lookup %+v", s)
	}
	before := hits
	c.call("GET", "/lookup/7891000100103", "", 200, nil)
	if hits != before {
		t.Fatal("second lookup was not cached")
	}

	rec := httptest.NewRecorder()
	c.e.ServeHTTP(rec, httptest.NewRequest("GET", strings.TrimPrefix(*s.ImageURL, "http://example.com"), nil))
	if rec.Code != 200 || rec.Header().Get("Content-Type") != "image/png" || rec.Body.String() != string(png) {
		t.Fatalf("image: %d %s", rec.Code, rec.Header().Get("Content-Type"))
	}
	rec = httptest.NewRecorder()
	c.e.ServeHTTP(rec, httptest.NewRequest("GET", "/images/nope", nil))
	if rec.Code != 404 {
		t.Fatalf("missing image: %d", rec.Code)
	}

	c.call("GET", "/lookup/1111111111111", "", 404, nil)
	before = hits
	c.call("GET", "/lookup/1111111111111", "", 404, nil)
	if hits != before {
		t.Fatal("miss was not cached")
	}
	c.call("GET", "/lookup/5555555555555", "", 502, nil)
	var cached int
	conn.QueryRow("select count(*) from lookups where barcode = '5555555555555'").Scan(&cached)
	if cached != 0 {
		t.Fatal("a failed lookup must not be cached")
	}
	c.call("GET", "/lookup/abc", "", 404, nil)
	(&client{t: t, e: c.e}).call("GET", "/lookup/7891000100103", "", 401, nil)

	var p products.Product
	c.call("POST", "/products", `{"barcode":"7891000100103","name":"Leite","category":"kitchen","unit":"g","packageSize":395,"imageUrl":"`+*s.ImageURL+`"}`, 201, &p)
	var stored string
	conn.QueryRow("select image_url from products where id = $1", p.ID).Scan(&stored)
	if !strings.HasPrefix(stored, "/images/") || p.ImageURL == nil || *p.ImageURL != *s.ImageURL {
		t.Fatalf("stored %q, returned %v", stored, p.ImageURL)
	}
}

func TestRecipes(t *testing.T) {
	c := setup(t)
	t.Setenv("RECIPES_API_URL", "")
	c.call("POST", "/recipes/suggest", `{}`, 503, nil)

	var calls int
	var lastBody map[string]any
	var lastAuth string
	answer := `{"recipes":[{"title":"Arroz com feijão","summary":"Clássico","minutes":40,"servings":4,"ingredients":[{"name":"arroz","amount":"1 xícara","fromStock":true}],"missing":["alho"],"steps":["Cozinhe o arroz"]}]}`
	fake := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		lastAuth = r.Header.Get("Authorization")
		json.NewDecoder(r.Body).Decode(&lastBody)
		if r.URL.Path != "/api/chat/completions" {
			w.WriteHeader(404)
			return
		}
		content := answer
		if strings.Contains(fmt.Sprint(lastBody), "broken") {
			content = "desculpe, não consigo"
		}
		json.NewEncoder(w).Encode(map[string]any{"choices": []any{map[string]any{"message": map[string]string{"content": "```json\n" + content + "\n```"}}}})
	}))
	defer fake.Close()
	t.Setenv("RECIPES_API_URL", fake.URL+"/api/")
	t.Setenv("RECIPES_API_KEY", "webui-key")
	t.Setenv("RECIPES_MODEL", "claude-test")

	c.call("POST", "/recipes/suggest", `{}`, 409, nil)

	c.call("POST", "/products", rice, 201, nil)
	c.call("POST", "/products", soap, 201, nil)
	c.call("POST", "/products", `{"barcode":"791","name":"Feijão 1kg","category":"kitchen","unit":"kg","packageSize":1}`, 201, nil)
	c.call("POST", "/movements", `{"type":"in","at":"2026-09-01T10:00:00Z","items":[
		{"productId":1,"packages":1,"amount":5},{"productId":2,"packages":3,"amount":3},{"productId":3,"packages":1,"amount":1}]}`, 201, nil)
	c.call("POST", "/movements", `{"type":"out","at":"2026-09-02T10:00:00Z","items":[{"productId":3,"packages":1,"amount":1}]}`, 201, nil)

	var r struct {
		Recipes []struct {
			Title   string
			Missing []string
		}
		Model  string
		Cached bool
	}
	c.call("POST", "/recipes/suggest", `{"hint":"almoço"}`, 200, &r)
	if len(r.Recipes) != 1 || r.Recipes[0].Title != "Arroz com feijão" || r.Model != "claude-test" || r.Cached || calls != 1 {
		t.Fatalf("suggest %+v calls %d", r, calls)
	}
	if lastAuth != "Bearer webui-key" || lastBody["model"] != "claude-test" {
		t.Fatalf("request auth %q body %v", lastAuth, lastBody)
	}
	sent := fmt.Sprint(lastBody["messages"])
	if !strings.Contains(sent, "Arroz 5kg: 5 kg") || strings.Contains(sent, "Sabonete") || strings.Contains(sent, "Feijão") ||
		!strings.Contains(sent, "almoço") || !strings.Contains(sent, "português") {
		t.Fatalf("prompt should list kitchen stock only: %s", sent)
	}

	c.call("POST", "/recipes/suggest", `{"hint":"almoço"}`, 200, &r)
	if !r.Cached || calls != 1 {
		t.Fatalf("second call should be cached: %+v calls %d", r, calls)
	}
	c.call("POST", "/recipes/suggest", `{"hint":"almoço","refresh":true}`, 200, &r)
	if r.Cached || calls != 2 {
		t.Fatalf("refresh should ask again: calls %d", calls)
	}
	c.call("POST", "/recipes/suggest", `{"hint":"almoço","lang":"en"}`, 200, nil)
	if calls != 3 || !strings.Contains(fmt.Sprint(lastBody["messages"]), "Write in English") {
		t.Fatalf("english prompt: %v", lastBody["messages"])
	}

	c.call("POST", "/recipes/suggest", `{"hint":"broken"}`, 502, nil)
	c.call("POST", "/recipes/suggest", `{"hint":"`+strings.Repeat("a", 201)+`"}`, 400, nil)
	(&client{t: t, e: c.e}).call("POST", "/recipes/suggest", `{}`, 401, nil)
}

func TestProductEditing(t *testing.T) {
	admin := setup(t)
	admin.call("POST", "/users", `{"username":"maria","password":"maria-password"}`, 201, nil)
	c := admin.login("maria", "maria-password")
	c.call("POST", "/products", rice, 201, nil)
	c.call("POST", "/products", soap, 201, nil)

	var all []products.Product
	c.call("GET", "/products", "", 200, &all)
	if len(all) != 2 || all[0].Name != "Arroz 5kg" || all[1].Name != "Sabonete" {
		t.Fatalf("list %+v", all)
	}

	var p products.Product
	c.call("PATCH", "/products/1", `{"name":"Arroz Tio João 5kg","packageSize":2.5}`, 200, &p)
	if p.Name != "Arroz Tio João 5kg" || p.PackageSize != 2.5 || p.Barcode != "789" || p.Category != "kitchen" {
		t.Fatalf("patched %+v", p)
	}
	c.call("PATCH", "/products/1", `{"barcode":"790"}`, 409, nil)
	c.call("PATCH", "/products/1", `{"category":"garage"}`, 400, nil)
	c.call("PATCH", "/products/1", `{"packageSize":0}`, 400, nil)
	c.call("PATCH", "/products/99", `{"name":"x"}`, 404, nil)

	c.call("POST", "/movements", `{"type":"in","at":"2026-09-01T10:00:00Z","items":[{"productId":1,"packages":1,"amount":2.5}]}`, 201, nil)
	c.call("DELETE", "/products/1", "", 409, nil)
	c.call("DELETE", "/products/2", "", 204, nil)
	c.call("GET", "/products/2", "", 404, nil)
	c.call("DELETE", "/products/2", "", 404, nil)

	png := "\x89PNG\r\n\x1a\n" + strings.Repeat("\x00", 64)
	req := httptest.NewRequest("PUT", "/products/1/image", strings.NewReader(png))
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "image/png")
	rec := httptest.NewRecorder()
	c.e.ServeHTTP(rec, req)
	if rec.Code != 200 {
		t.Fatalf("upload: %d %s", rec.Code, rec.Body)
	}
	json.Unmarshal(rec.Body.Bytes(), &p)
	if p.ImageURL == nil || !strings.HasPrefix(*p.ImageURL, "http://example.com/images/") {
		t.Fatalf("image url %v", p.ImageURL)
	}
	img := httptest.NewRecorder()
	c.e.ServeHTTP(img, httptest.NewRequest("GET", strings.TrimPrefix(*p.ImageURL, "http://example.com"), nil))
	if img.Code != 200 || img.Body.String() != png {
		t.Fatalf("serve uploaded image: %d", img.Code)
	}
	c.call("PUT", "/products/1/image", `not an image`, 400, nil)
	c.call("DELETE", "/products/1/image", "", 200, &p)
	if p.ImageURL != nil {
		t.Fatalf("image not removed %v", p.ImageURL)
	}
	(&client{t: t, e: c.e}).call("PATCH", "/products/1", `{"name":"x"}`, 401, nil)

	var log []activity.Entry
	admin.call("GET", "/activity", "", 200, &log)
	seen := map[string]bool{}
	for _, e := range log {
		seen[e.Action] = true
	}
	for _, a := range []string{"product.update", "product.delete", "product.image", "product.image.remove"} {
		if !seen[a] {
			t.Fatalf("no %s in activity", a)
		}
	}
}

func TestUndo(t *testing.T) {
	admin := setup(t)
	admin.call("POST", "/users", `{"username":"maria","password":"maria-password"}`, 201, nil)
	admin.call("POST", "/users", `{"username":"joao","password":"joao-password"}`, 201, nil)
	maria := admin.login("maria", "maria-password")
	joao := admin.login("joao", "joao-password")
	maria.call("POST", "/products", rice, 201, nil)
	maria.call("POST", "/products", `{"barcode":"791","name":"Feijão","category":"kitchen","unit":"kg","packageSize":1}`, 201, nil)

	var b1, b2 struct{ ID int64 }
	maria.call("POST", "/movements", `{"type":"in","at":"2026-09-01T10:00:00Z","items":[{"productId":1,"packages":2,"amount":10},{"productId":2,"packages":3,"amount":3}]}`, 201, &b1)
	maria.call("POST", "/movements", `{"type":"out","at":"2026-09-02T10:00:00Z","items":[{"productId":1,"packages":1,"amount":5}]}`, 201, &b2)

	var history []movements.Movement
	maria.call("GET", "/products/1/movements", "", 200, &history)
	if len(history) != 2 || history[0].BatchID != b2.ID || history[1].BatchID != b1.ID {
		t.Fatalf("batch ids in history %+v", history)
	}

	joao.call("DELETE", "/batches/"+jsonNumber(b2.ID), "", 403, nil)
	maria.call("DELETE", "/batches/"+jsonNumber(b2.ID), "", 204, nil)
	maria.call("DELETE", "/batches/"+jsonNumber(b2.ID), "", 409, nil)
	maria.call("DELETE", "/batches/999", "", 404, nil)

	var inventory []movements.InventoryItem
	maria.call("GET", "/inventory", "", 200, &inventory)
	if len(inventory) != 2 || inventory[0].Amount != 10 || inventory[0].UpdatedAt != "2026-09-01T10:00:00Z" {
		t.Fatalf("undone usage must not count: %+v", inventory)
	}
	maria.call("GET", "/products/1/movements", "", 200, &history)
	if len(history) != 1 || history[0].Type != "in" {
		t.Fatalf("undone batch must leave history: %+v", history)
	}

	admin.call("DELETE", "/batches/"+jsonNumber(b1.ID), "", 204, nil)
	maria.call("GET", "/inventory", "", 200, &inventory)
	if len(inventory) != 0 {
		t.Fatalf("all undone: %+v", inventory)
	}
	maria.call("DELETE", "/products/1", "", 409, nil)

	var log []activity.Entry
	admin.call("GET", "/activity", "", 200, &log)
	undos := 0
	for _, e := range log {
		if e.Action == "movements.undo" {
			undos++
		}
	}
	if undos != 2 {
		t.Fatalf("undo activity %d", undos)
	}
}
