package products

import (
	"database/sql"
	"net/http"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/db"
	"github.com/glgaspar/inventory/api/web"
	"github.com/labstack/echo/v4"
)

type Product struct {
	ID          int64   `json:"id"`
	Barcode     string  `json:"barcode"`
	Name        string  `json:"name"`
	Category    string  `json:"category"`
	Unit        string  `json:"unit"`
	PackageSize float64 `json:"packageSize"`
	ImageURL    *string `json:"imageUrl"`
}

const Columns = "p.id, p.barcode, p.name, p.category, p.unit, p.package_size, p.image_url"

func (p *Product) Fields() []any {
	return []any{&p.ID, &p.Barcode, &p.Name, &p.Category, &p.Unit, &p.PackageSize, &p.ImageURL}
}

type Handler struct {
	DB *sql.DB
}

func (h Handler) ByBarcode(c echo.Context) error {
	return h.one(c, "p.barcode = $1", c.Param("barcode"))
}

func (h Handler) Get(c echo.Context) error {
	id, err := web.ParamID(c)
	if err != nil {
		return err
	}
	return h.one(c, "p.id = $1", id)
}

func (h Handler) one(c echo.Context, where string, arg any) error {
	var p Product
	if err := h.DB.QueryRow("select "+Columns+" from products p where "+where, arg).Scan(p.Fields()...); err != nil {
		return db.HTTPError(err)
	}
	p.ImageURL = web.AbsURL(c, p.ImageURL)
	return c.JSON(http.StatusOK, p)
}

func (h Handler) Create(c echo.Context) error {
	var p Product
	if err := c.Bind(&p); err != nil {
		return err
	}
	p.ImageURL = web.LocalPath(c, p.ImageURL)
	err := h.DB.QueryRow(`insert into products (barcode, name, category, unit, package_size, image_url)
		values ($1, $2, $3, $4, $5, $6) returning id`,
		p.Barcode, p.Name, p.Category, p.Unit, p.PackageSize, p.ImageURL).Scan(&p.ID)
	if err != nil {
		return db.HTTPError(err)
	}
	activity.Log(h.DB, c, auth.CurrentID(c), "product.create", map[string]any{"id": p.ID, "barcode": p.Barcode, "name": p.Name})
	p.ImageURL = web.AbsURL(c, p.ImageURL)
	return c.JSON(http.StatusCreated, p)
}
