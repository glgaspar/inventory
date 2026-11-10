package lookup

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/glgaspar/inventory/api/images"
	"github.com/glgaspar/inventory/api/web"
	"github.com/labstack/echo/v4"
)

type Suggestion struct {
	Barcode     string  `json:"barcode"`
	Name        string  `json:"name"`
	Brand       string  `json:"brand"`
	Category    string  `json:"category"`
	Unit        string  `json:"unit"`
	PackageSize float64 `json:"packageSize"`
	ImageURL    *string `json:"imageUrl"`
	Source      string  `json:"source"`
}

type source struct {
	name            string
	url             string
	defaultCategory string
}

var OpenFacts = []source{
	{"openfoodfacts", "https://world.openfoodfacts.org", "kitchen"},
	{"openproductsfacts", "https://world.openproductsfacts.org", "other"},
	{"openbeautyfacts", "https://world.openbeautyfacts.org", "bathroom"},
}

const missTTL = "7 days"

var client = &http.Client{Timeout: 8 * time.Second}
var barcodePattern = regexp.MustCompile(`^[0-9]{8,14}$`)

var errNotFound = errors.New("not found")

type Handler struct {
	DB *sql.DB
}

func (h Handler) Get(c echo.Context) error {
	barcode := c.Param("barcode")
	if !barcodePattern.MatchString(barcode) {
		return echo.NewHTTPError(http.StatusNotFound, "not found")
	}

	var cached []byte
	err := h.DB.QueryRow("select result from lookups where barcode = $1 and (result is not null or fetched_at > now() - $2::interval)",
		barcode, missTTL).Scan(&cached)
	if err == nil {
		if cached == nil {
			return echo.NewHTTPError(http.StatusNotFound, "not found")
		}
		var s Suggestion
		if err := json.Unmarshal(cached, &s); err != nil {
			return err
		}
		return respond(c, s)
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return err
	}

	s, err := Find(barcode)
	if errors.Is(err, errNotFound) {
		if _, err := h.DB.Exec(`insert into lookups (barcode, result) values ($1, null)
			on conflict (barcode) do update set result = null, fetched_at = now()`, barcode); err != nil {
			return err
		}
		return echo.NewHTTPError(http.StatusNotFound, "not found")
	}
	if err != nil {
		slog.Warn("lookup failed", "barcode", barcode, "err", err)
		return echo.NewHTTPError(http.StatusBadGateway, "product databases unavailable, try again later")
	}

	if s.ImageURL != nil {
		path, err := images.Download(h.DB, *s.ImageURL)
		if err != nil {
			slog.Warn("lookup image", "barcode", barcode, "err", err)
			s.ImageURL = nil
		} else {
			s.ImageURL = &path
		}
	}
	data, err := json.Marshal(s)
	if err != nil {
		return err
	}
	if _, err := h.DB.Exec(`insert into lookups (barcode, result) values ($1, $2)
		on conflict (barcode) do update set result = $2, fetched_at = now()`, barcode, data); err != nil {
		return err
	}
	return respond(c, s)
}

func respond(c echo.Context, s Suggestion) error {
	s.ImageURL = web.AbsURL(c, s.ImageURL)
	return c.JSON(http.StatusOK, s)
}

func Find(barcode string) (Suggestion, error) {
	results := make([]Suggestion, len(OpenFacts))
	errs := make([]error, len(OpenFacts))
	var wg sync.WaitGroup
	for i, src := range OpenFacts {
		wg.Add(1)
		go func() {
			defer wg.Done()
			results[i], errs[i] = openFacts(src, barcode)
		}()
	}
	wg.Wait()

	var partial *Suggestion
	failed := false
	for i, s := range results {
		switch {
		case errs[i] == nil && s.Name != "":
			return s, nil
		case errs[i] == nil && partial == nil:
			partial = &results[i]
		case errs[i] != nil && !errors.Is(errs[i], errNotFound):
			failed = true
		}
	}

	if partial != nil {
		return *partial, nil
	}
	if failed {
		return Suggestion{}, errors.New("a product database did not answer")
	}
	return Suggestion{}, errNotFound
}

func getJSON(req *http.Request, out any) error {
	res, err := client.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode == http.StatusNotFound {
		return errNotFound
	}
	if res.StatusCode != http.StatusOK {
		return fmt.Errorf("%s: status %d", req.URL.Host, res.StatusCode)
	}
	return json.NewDecoder(io.LimitReader(res.Body, 2<<20)).Decode(out)
}

func openFacts(src source, barcode string) (Suggestion, error) {
	req, err := http.NewRequest("GET", src.url+"/api/v2/product/"+barcode+
		"?fields=product_name,product_name_pt,brands,quantity,product_quantity,product_quantity_unit,image_front_url,categories_tags", nil)
	if err != nil {
		return Suggestion{}, err
	}
	req.Header.Set("User-Agent", "inventario-home/1.0")
	var body struct {
		Status  int `json:"status"`
		Product struct {
			Name         string   `json:"product_name"`
			NamePT       string   `json:"product_name_pt"`
			Brands       string   `json:"brands"`
			Quantity     string   `json:"quantity"`
			ProductQty   any      `json:"product_quantity"`
			ProductQtyU  string   `json:"product_quantity_unit"`
			Image        string   `json:"image_front_url"`
			CategoryTags []string `json:"categories_tags"`
		} `json:"product"`
	}
	if err := getJSON(req, &body); err != nil {
		return Suggestion{}, err
	}
	if body.Status != 1 {
		return Suggestion{}, errNotFound
	}
	p := body.Product
	name := strings.TrimSpace(p.NamePT)
	if name == "" {
		name = strings.TrimSpace(p.Name)
	}
	brand := strings.TrimSpace(strings.Split(p.Brands, ",")[0])
	s := Suggestion{
		Barcode:  barcode,
		Name:     withBrand(name, brand),
		Brand:    brand,
		Category: guessCategory(strings.Join(p.CategoryTags, " ")+" "+name, src.defaultCategory),
		Source:   src.name,
	}
	s.Unit, s.PackageSize = parseQuantity(p.Quantity)
	if s.Unit == "" {
		s.Unit, s.PackageSize = parseQuantity(fmt.Sprint(p.ProductQty) + " " + p.ProductQtyU)
	}
	if p.Image != "" {
		s.ImageURL = &p.Image
	}
	if name == "" && s.ImageURL == nil {
		return Suggestion{}, errNotFound
	}
	return defaults(s), nil
}

func withBrand(name, brand string) string {
	if brand == "" || name == "" || strings.Contains(strings.ToLower(name), strings.ToLower(brand)) {
		return name
	}
	return name + " " + brand
}

func defaults(s Suggestion) Suggestion {
	if s.Unit == "" {
		s.Unit, s.PackageSize = "un", 1
	}
	return s
}

var quantityPattern = regexp.MustCompile(`(?i)(\d+(?:[.,]\d+)?)\s*(kg|g|gr|mg|l|lt|ml|un|und|unid)\b`)

func parseQuantity(text string) (string, float64) {
	m := quantityPattern.FindStringSubmatch(text)
	if m == nil {
		return "", 0
	}
	n, err := strconv.ParseFloat(strings.Replace(m[1], ",", ".", 1), 64)
	if err != nil || n <= 0 {
		return "", 0
	}
	switch strings.ToLower(m[2]) {
	case "kg":
		return "kg", n
	case "g", "gr":
		return "g", n
	case "mg":
		return "g", n / 1000
	case "l", "lt":
		return "L", n
	case "ml":
		return "mL", n
	default:
		return "un", n
	}
}

var cleaningWords = []string{"limpeza", "cleaning", "detergent", "sabão em pó", "sabao em po", "lava-roupa", "lava roupa",
	"lava louça", "lava-louça", "lava loucas", "desinfetante", "disinfectant", "água sanitária", "agua sanitaria", "alvejante",
	"amaciante", "fabric-softener", "multiuso", "household", "esponja", "limpador"}
var bathroomWords = []string{"higiene", "hygiene", "papel higiênico", "papel higienico", "toilet", "sabonete", "soap",
	"shampoo", "xampu", "condicionador", "conditioner", "creme dental", "toothpaste", "pasta de dente", "escova de dente",
	"toothbrush", "desodorante", "deodorant", "absorvente", "fio dental", "enxaguante", "mouthwash", "barbear", "shaving"}
var foodWords = []string{"alimento", "bebida", "food", "beverage", "comida"}

func guessCategory(text, fallback string) string {
	t := strings.ToLower(text)
	has := func(words []string) bool {
		for _, w := range words {
			if strings.Contains(t, w) {
				return true
			}
		}
		return false
	}
	switch {
	case has(cleaningWords):
		return "cleaning"
	case has(bathroomWords):
		return "bathroom"
	case fallback == "other" && has(foodWords):
		return "kitchen"
	}
	return fallback
}

func SetSources(baseURL string) {
	for i := range OpenFacts {
		OpenFacts[i].url = baseURL
	}
}
