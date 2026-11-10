package lookup

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestParseQuantity(t *testing.T) {
	cases := map[string]struct {
		unit string
		n    float64
	}{
		"395 g":                 {"g", 395},
		"500ml":                 {"mL", 500},
		"ARROZ TIO JOAO T1 5KG": {"kg", 5},
		"Óleo de soja 900 mL":   {"mL", 900},
		"Refrigerante 2L":       {"L", 2},
		"Café 0,5 kg":           {"kg", 0.5},
		"Papel higiênico 12 un": {"un", 12},
		"Sabonete":              {"", 0},
		"395 g (2 x 197,5 g)":   {"g", 395},
		"CERVEJA LATA 350ML":    {"mL", 350},
	}
	for text, want := range cases {
		unit, n := parseQuantity(text)
		if unit != want.unit || n != want.n {
			t.Errorf("%q: %s %v, want %s %v", text, unit, n, want.unit, want.n)
		}
	}
}

func TestGuessCategory(t *testing.T) {
	cases := map[string]string{
		"pt:Detergente Lava louças":     "cleaning",
		"Papel Higiênico Neve":          "bathroom",
		"en:dairies en:condensed-milks": "kitchen",
		"Pilhas AA":                     "other",
	}
	for text, want := range cases {
		fallback := "kitchen"
		if want == "other" {
			fallback = "other"
		}
		if got := guessCategory(text, fallback); got != want {
			t.Errorf("%q: %s, want %s", text, got, want)
		}
	}
}

func fakeOpenFacts(t *testing.T, food, products, beauty string) {
	serve := func(body string) *httptest.Server {
		s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if body == "down" {
				w.WriteHeader(502)
				return
			}
			w.Write([]byte(body))
		}))
		t.Cleanup(s.Close)
		return s
	}
	old := OpenFacts
	t.Cleanup(func() { OpenFacts = old })
	OpenFacts = []source{
		{"openfoodfacts", serve(food).URL, "kitchen"},
		{"openproductsfacts", serve(products).URL, "other"},
		{"openbeautyfacts", serve(beauty).URL, "bathroom"},
	}
}

const missing = `{"status":0,"status_verbose":"product not found"}`

func TestFind(t *testing.T) {
	t.Setenv("COSMOS_TOKEN", "")

	fakeOpenFacts(t, `{"status":1,"product":{"product_name_pt":"Leite Condensado","brands":"Nestlé, Moça","quantity":"395 g","categories_tags":["en:dairies"],"image_front_url":"https://images.openfoodfacts.org/x.jpg"}}`,
		`{"status":1,"product":{"product_name":"Something else"}}`, missing)
	s, err := Find("7891000100103")
	if err != nil || s.Name != "Leite Condensado Nestlé" || s.Brand != "Nestlé" || s.Unit != "g" || s.PackageSize != 395 ||
		s.Category != "kitchen" || s.Source != "openfoodfacts" || s.ImageURL == nil {
		t.Fatalf("food: %+v %v", s, err)
	}

	fakeOpenFacts(t, missing, `{"status":1,"product":{"product_name_pt":"Lava louças (maçã)","brands":"Ypê","quantity":"500ml","categories_tags":["pt:Detergente"]}}`, missing)
	s, err = Find("7896098900215")
	if err != nil || s.Name != "Lava louças (maçã) Ypê" || s.Category != "cleaning" || s.Unit != "mL" || s.PackageSize != 500 {
		t.Fatalf("cleaning: %+v %v", s, err)
	}

	fakeOpenFacts(t, missing, missing, `{"status":1,"product":{"image_front_url":"https://images.openbeautyfacts.org/y.jpg"}}`)
	s, err = Find("7891024134702")
	if err != nil || s.Name != "" || s.ImageURL == nil || s.Unit != "un" || s.PackageSize != 1 {
		t.Fatalf("image only: %+v %v", s, err)
	}

	fakeOpenFacts(t, missing, missing, missing)
	if _, err = Find("1234567890123"); err != errNotFound {
		t.Fatalf("missing everywhere: %v", err)
	}

	fakeOpenFacts(t, "down", missing, missing)
	if _, err = Find("1234567890123"); err == nil || err == errNotFound {
		t.Fatalf("food db down must be an error, not a miss: %v", err)
	}
}

func TestCosmos(t *testing.T) {
	var gotToken, gotPath string
	cosmosServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotToken, gotPath = r.Header.Get("X-Cosmos-Token"), r.URL.Path
		if strings.Contains(r.URL.Path, "000") {
			w.WriteHeader(404)
			return
		}
		w.Write([]byte(`{"description":"PAPEL HIGIENICO NEVE FOLHA DUPLA 12 UN","thumbnail":"https://cdn-cosmos.bluesoft.com.br/p.png","brand":{"name":"NEVE"},"gpc":{"description":"Papel Higiênico"},"ncm":{"description":"Papel higiênico"}}`))
	}))
	defer cosmosServer.Close()
	old := CosmosURL
	CosmosURL = cosmosServer.URL
	defer func() { CosmosURL = old }()

	fakeOpenFacts(t, missing, missing, missing)
	t.Setenv("COSMOS_TOKEN", "secret")
	s, err := Find("7891172422102")
	if err != nil || gotToken != "secret" || gotPath != "/gtins/7891172422102.json" || s.Source != "cosmos" ||
		s.Name != "PAPEL HIGIENICO NEVE FOLHA DUPLA 12 UN" || s.Category != "bathroom" || s.Unit != "un" || s.PackageSize != 12 {
		t.Fatalf("cosmos: %+v %v (token %q path %q)", s, err, gotToken, gotPath)
	}
	if _, err := Find("7891000000000"); err != errNotFound {
		t.Fatalf("cosmos 404: %v", err)
	}

	t.Setenv("COSMOS_TOKEN", "")
	gotPath = ""
	if _, err := Find("7891172422102"); err != errNotFound || gotPath != "" {
		t.Fatalf("no token must skip cosmos: %v %q", err, gotPath)
	}
}
