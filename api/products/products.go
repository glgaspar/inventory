package products

import (
	"database/sql"
	"errors"
	"io"
	"net/http"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/db"
	"github.com/glgaspar/inventory/api/images"
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

func (h Handler) List(c echo.Context) error {
	rows, err := h.DB.Query("select " + Columns + " from products p order by p.name, p.id")
	if err != nil {
		return err
	}
	defer rows.Close()
	list := []Product{}
	for rows.Next() {
		var p Product
		if err := rows.Scan(p.Fields()...); err != nil {
			return err
		}
		p.ImageURL = web.AbsURL(c, p.ImageURL)
		list = append(list, p)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return c.JSON(http.StatusOK, list)
}

func (h Handler) Update(c echo.Context) error {
	id, err := web.ParamID(c)
	if err != nil {
		return err
	}
	var body struct {
		Barcode     *string  `json:"barcode"`
		Name        *string  `json:"name"`
		Category    *string  `json:"category"`
		Unit        *string  `json:"unit"`
		PackageSize *float64 `json:"packageSize"`
	}
	if err := c.Bind(&body); err != nil {
		return err
	}
	var p Product
	err = h.DB.QueryRow(`update products p set
			barcode = coalesce($2, barcode),
			name = coalesce($3, name),
			category = coalesce($4, category),
			unit = coalesce($5, unit),
			package_size = coalesce($6, package_size)
		where id = $1
		returning `+Columns, id, body.Barcode, body.Name, body.Category, body.Unit, body.PackageSize).Scan(p.Fields()...)
	if err != nil {
		return db.HTTPError(err)
	}
	activity.Log(h.DB, c, auth.CurrentID(c), "product.update", map[string]any{"id": id, "barcode": body.Barcode,
		"name": body.Name, "category": body.Category, "unit": body.Unit, "packageSize": body.PackageSize})
	p.ImageURL = web.AbsURL(c, p.ImageURL)
	return c.JSON(http.StatusOK, p)
}

func (h Handler) Delete(c echo.Context) error {
	id, err := web.ParamID(c)
	if err != nil {
		return err
	}
	var hasHistory bool
	if err := h.DB.QueryRow("select exists (select 1 from movements where product_id = $1)", id).Scan(&hasHistory); err != nil {
		return err
	}
	if hasHistory {
		return echo.NewHTTPError(http.StatusConflict, "product has movements, it can only be edited")
	}
	var p Product
	if err := h.DB.QueryRow("delete from products p where id = $1 returning "+Columns, id).Scan(p.Fields()...); err != nil {
		return db.HTTPError(err)
	}
	activity.Log(h.DB, c, auth.CurrentID(c), "product.delete", map[string]any{"id": id, "barcode": p.Barcode, "name": p.Name})
	return c.NoContent(http.StatusNoContent)
}

func (h Handler) SetImage(c echo.Context) error {
	id, err := web.ParamID(c)
	if err != nil {
		return err
	}
	data, err := io.ReadAll(c.Request().Body)
	if err != nil {
		return err
	}
	path, err := images.Save(h.DB, data)
	if errors.Is(err, images.ErrNotImage) {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}
	if err != nil {
		return err
	}
	return h.setImageURL(c, id, &path, "product.image")
}

func (h Handler) RemoveImage(c echo.Context) error {
	id, err := web.ParamID(c)
	if err != nil {
		return err
	}
	return h.setImageURL(c, id, nil, "product.image.remove")
}

func (h Handler) setImageURL(c echo.Context, id int64, path *string, action string) error {
	var p Product
	err := h.DB.QueryRow("update products p set image_url = $2 where id = $1 returning "+Columns, id, path).Scan(p.Fields()...)
	if err != nil {
		return db.HTTPError(err)
	}
	activity.Log(h.DB, c, auth.CurrentID(c), action, map[string]any{"id": id})
	p.ImageURL = web.AbsURL(c, p.ImageURL)
	return c.JSON(http.StatusOK, p)
}
