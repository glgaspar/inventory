package recipes

import (
	"bytes"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/web"
	"github.com/labstack/echo/v4"
)

type Ingredient struct {
	Name      string `json:"name"`
	Amount    string `json:"amount"`
	FromStock bool   `json:"fromStock"`
}

type Recipe struct {
	Title       string       `json:"title"`
	Summary     string       `json:"summary"`
	Minutes     int          `json:"minutes"`
	Servings    int          `json:"servings"`
	Ingredients []Ingredient `json:"ingredients"`
	Missing     []string     `json:"missing"`
	Steps       []string     `json:"steps"`
}

type Result struct {
	Recipes     []Recipe `json:"recipes"`
	Model       string   `json:"model"`
	GeneratedAt string   `json:"generatedAt"`
	Cached      bool     `json:"cached"`
}

var client = &http.Client{Timeout: 100 * time.Second}

type Handler struct {
	DB *sql.DB
}

func (h Handler) Suggest(c echo.Context) error {
	baseURL, model := strings.TrimRight(os.Getenv("RECIPES_API_URL"), "/"), os.Getenv("RECIPES_MODEL")
	if baseURL == "" || model == "" {
		return echo.NewHTTPError(http.StatusServiceUnavailable, "recipe suggestions are not configured")
	}
	var body struct {
		Hint    string `json:"hint"`
		Lang    string `json:"lang"`
		Refresh bool   `json:"refresh"`
	}
	if err := c.Bind(&body); err != nil {
		return err
	}
	if len(body.Hint) > 200 {
		return echo.NewHTTPError(http.StatusBadRequest, "hint is too long")
	}
	if body.Lang != "en" {
		body.Lang = "pt"
	}

	stock, err := h.stock()
	if err != nil {
		return err
	}
	if len(stock) == 0 {
		return echo.NewHTTPError(http.StatusConflict, "no kitchen items in stock")
	}

	sum := sha256.Sum256([]byte(model + "\n" + body.Lang + "\n" + strings.TrimSpace(body.Hint) + "\n" + strings.Join(stock, "\n")))
	key := hex.EncodeToString(sum[:])
	if !body.Refresh {
		var cached []byte
		err := h.DB.QueryRow("select result from recipe_suggestions where key = $1", key).Scan(&cached)
		if err == nil {
			var r Result
			if err := json.Unmarshal(cached, &r); err != nil {
				return err
			}
			r.Cached = true
			return c.JSON(http.StatusOK, r)
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return err
		}
	}

	recipes, err := ask(baseURL, model, prompt(body.Lang), userMessage(body.Lang, stock, body.Hint))
	if err != nil {
		slog.Warn("recipe suggestion failed", "model", model, "err", err)
		return echo.NewHTTPError(http.StatusBadGateway, "could not get suggestions, try again")
	}
	r := Result{Recipes: recipes, Model: model, GeneratedAt: web.ISO(time.Now())}
	data, err := json.Marshal(r)
	if err != nil {
		return err
	}
	if _, err := h.DB.Exec(`insert into recipe_suggestions (key, result) values ($1, $2)
		on conflict (key) do update set result = $2, created_at = now()`, key, data); err != nil {
		return err
	}
	activity.Log(h.DB, c, auth.CurrentID(c), "recipes.suggest", map[string]any{"model": model, "items": len(stock), "hint": body.Hint})
	return c.JSON(http.StatusOK, r)
}

func (h Handler) stock() ([]string, error) {
	rows, err := h.DB.Query(`select p.name, p.unit,
			sum(case when b.type = 'in' then m.amount else -m.amount end) as amount
		from products p
		join movements m on m.product_id = p.id
		join batches b on b.id = m.batch_id
		where p.category = 'kitchen'
		group by p.id
		having sum(case when b.type = 'in' then m.amount else -m.amount end) > 0
		order by p.name`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var stock []string
	for rows.Next() {
		var name, unit string
		var amount float64
		if err := rows.Scan(&name, &unit, &amount); err != nil {
			return nil, err
		}
		stock = append(stock, fmt.Sprintf("%s: %g %s", name, amount, unit))
	}
	return stock, rows.Err()
}

const schema = `{"recipes":[{"title":"","summary":"","minutes":0,"servings":0,"ingredients":[{"name":"","amount":"","fromStock":true}],"missing":[""],"steps":[""]}]}`

func prompt(lang string) string {
	if lang == "en" {
		return `You suggest home-cooking recipes from what is in a Brazilian household's pantry. Suggest 3 different, realistic recipes that use mostly the stock items. Never use more of an item than the stock has. Assume salt, pepper, water and cooking oil are always available. Anything else that is not in the stock goes in "missing" (keep it short, at most 3 items). "fromStock" is true for ingredients taken from the stock. Steps are short and practical. Write in English. Answer with JSON only, no markdown, exactly in this shape: ` + schema
	}
	return `Você sugere receitas caseiras a partir da despensa de uma casa brasileira. Sugira 3 receitas diferentes e realistas, de preferência do dia a dia brasileiro, usando principalmente os itens do estoque. Nunca use mais de um item do que o estoque tem. Considere que sal, pimenta, água e óleo sempre existem. Qualquer outra coisa fora do estoque vai em "missing" (no máximo 3 itens). "fromStock" é true para ingredientes que vêm do estoque. Passos curtos e práticos. Escreva em português do Brasil. Responda só com JSON, sem markdown, exatamente neste formato: ` + schema
}

func userMessage(lang string, stock []string, hint string) string {
	head, hintLabel := "Estoque:", "Pedido: "
	if lang == "en" {
		head, hintLabel = "Stock:", "Request: "
	}
	msg := head + "\n- " + strings.Join(stock, "\n- ")
	if hint = strings.TrimSpace(hint); hint != "" {
		msg += "\n\n" + hintLabel + hint
	}
	return msg
}

func ask(baseURL, model, system, user string) ([]Recipe, error) {
	payload, err := json.Marshal(map[string]any{
		"model":  model,
		"stream": false,
		"messages": []map[string]string{
			{"role": "system", "content": system},
			{"role": "user", "content": user},
		},
	})
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequest("POST", baseURL+"/chat/completions", bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	if key := os.Getenv("RECIPES_API_KEY"); key != "" {
		req.Header.Set("Authorization", "Bearer "+key)
	}
	res, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(res.Body, 1<<20))
	if err != nil {
		return nil, err
	}
	if res.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("status %d: %.300s", res.StatusCode, raw)
	}
	var out struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}
	if err := json.Unmarshal(raw, &out); err != nil {
		return nil, err
	}
	if len(out.Choices) == 0 {
		return nil, errors.New("no answer")
	}
	return parse(out.Choices[0].Message.Content)
}

func parse(content string) ([]Recipe, error) {
	start, end := strings.Index(content, "{"), strings.LastIndex(content, "}")
	if start < 0 || end < start {
		return nil, fmt.Errorf("no JSON in answer: %.200s", content)
	}
	var r struct {
		Recipes []Recipe `json:"recipes"`
	}
	if err := json.Unmarshal([]byte(content[start:end+1]), &r); err != nil {
		return nil, err
	}
	var valid []Recipe
	for _, recipe := range r.Recipes {
		if recipe.Title != "" && len(recipe.Ingredients) > 0 && len(recipe.Steps) > 0 {
			if recipe.Missing == nil {
				recipe.Missing = []string{}
			}
			valid = append(valid, recipe)
		}
	}
	if len(valid) == 0 {
		return nil, errors.New("answer had no usable recipes")
	}
	return valid, nil
}
