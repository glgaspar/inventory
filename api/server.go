package main

import (
	"database/sql"
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
	"github.com/lib/pq"
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

type InventoryItem struct {
	Product   Product `json:"product"`
	Amount    float64 `json:"amount"`
	Packages  int     `json:"packages"`
	UpdatedAt string  `json:"updatedAt"`
}

type Movement struct {
	ID        int64   `json:"id"`
	ProductID int64   `json:"productId"`
	Type      string  `json:"type"`
	Amount    float64 `json:"amount"`
	Packages  int     `json:"packages"`
	At        string  `json:"at"`
}

type MovementBatch struct {
	Type  string    `json:"type"`
	At    time.Time `json:"at"`
	Items []struct {
		ProductID int64   `json:"productId"`
		Packages  int     `json:"packages"`
		Amount    float64 `json:"amount"`
	} `json:"items"`
}

const productColumns = "p.id, p.barcode, p.name, p.category, p.unit, p.package_size, p.image_url"

type server struct {
	db *sql.DB
}

func newServer(db *sql.DB) *echo.Echo {
	s := &server{db}
	e := echo.New()
	e.HideBanner = true
	e.Use(middleware.Logger(), middleware.Recover())

	e.GET("/", func(c echo.Context) error { return c.NoContent(http.StatusOK) })
	e.GET("/products/by-barcode/:barcode", s.productByBarcode)
	e.POST("/products", s.createProduct)
	e.GET("/products/:id", s.product)
	e.GET("/products/:id/movements", s.movements)
	e.POST("/movements", s.createMovements)
	e.GET("/inventory", s.inventory)
	return e
}

func (s *server) productByBarcode(c echo.Context) error {
	return s.oneProduct(c, "p.barcode = $1", c.Param("barcode"))
}

func (s *server) product(c echo.Context) error {
	id, err := idParam(c)
	if err != nil {
		return err
	}
	return s.oneProduct(c, "p.id = $1", id)
}

func (s *server) oneProduct(c echo.Context, where string, arg any) error {
	var p Product
	err := s.db.QueryRow("select "+productColumns+" from products p where "+where, arg).
		Scan(&p.ID, &p.Barcode, &p.Name, &p.Category, &p.Unit, &p.PackageSize, &p.ImageURL)
	if err != nil {
		return dbError(err)
	}
	return c.JSON(http.StatusOK, p)
}

func (s *server) createProduct(c echo.Context) error {
	var p Product
	if err := c.Bind(&p); err != nil {
		return err
	}
	err := s.db.QueryRow(`insert into products (barcode, name, category, unit, package_size, image_url)
		values ($1, $2, $3, $4, $5, $6) returning id`,
		p.Barcode, p.Name, p.Category, p.Unit, p.PackageSize, p.ImageURL).Scan(&p.ID)
	if err != nil {
		return dbError(err)
	}
	return c.JSON(http.StatusCreated, p)
}

func (s *server) createMovements(c echo.Context) error {
	var b MovementBatch
	if err := c.Bind(&b); err != nil {
		return err
	}
	if b.At.IsZero() || len(b.Items) == 0 {
		return echo.NewHTTPError(http.StatusBadRequest, "at and items are required")
	}

	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	var id int64
	if err := tx.QueryRow("insert into batches (type, at) values ($1, $2) returning id", b.Type, b.At).Scan(&id); err != nil {
		return dbError(err)
	}
	for _, item := range b.Items {
		_, err := tx.Exec("insert into movements (batch_id, product_id, amount, packages) values ($1, $2, $3, $4)",
			id, item.ProductID, item.Amount, item.Packages)
		if err != nil {
			return dbError(err)
		}
	}
	if err := tx.Commit(); err != nil {
		return err
	}
	return c.JSON(http.StatusCreated, map[string]int64{"id": id})
}

func (s *server) inventory(c echo.Context) error {
	rows, err := s.db.Query(`select ` + productColumns + `,
			sum(case when b.type = 'in' then m.amount else -m.amount end),
			floor(sum(case when b.type = 'in' then m.amount else -m.amount end) / p.package_size)::int,
			max(b.at)
		from products p
		join movements m on m.product_id = p.id
		join batches b on b.id = m.batch_id
		group by p.id
		order by p.id`)
	if err != nil {
		return err
	}
	defer rows.Close()

	items := []InventoryItem{}
	for rows.Next() {
		var i InventoryItem
		var updatedAt time.Time
		p := &i.Product
		if err := rows.Scan(&p.ID, &p.Barcode, &p.Name, &p.Category, &p.Unit, &p.PackageSize, &p.ImageURL,
			&i.Amount, &i.Packages, &updatedAt); err != nil {
			return err
		}
		i.UpdatedAt = iso(updatedAt)
		items = append(items, i)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return c.JSON(http.StatusOK, items)
}

func (s *server) movements(c echo.Context) error {
	id, err := idParam(c)
	if err != nil {
		return err
	}
	rows, err := s.db.Query(`select m.id, m.product_id, b.type, m.amount, m.packages, b.at
		from movements m
		join batches b on b.id = m.batch_id
		where m.product_id = $1
		order by b.at desc, m.id desc`, id)
	if err != nil {
		return err
	}
	defer rows.Close()

	movements := []Movement{}
	for rows.Next() {
		var m Movement
		var at time.Time
		if err := rows.Scan(&m.ID, &m.ProductID, &m.Type, &m.Amount, &m.Packages, &at); err != nil {
			return err
		}
		m.At = iso(at)
		movements = append(movements, m)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return c.JSON(http.StatusOK, movements)
}

func idParam(c echo.Context) (int64, error) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		return 0, echo.NewHTTPError(http.StatusNotFound, "not found")
	}
	return id, nil
}

func iso(t time.Time) string {
	return t.UTC().Format(time.RFC3339)
}

func dbError(err error) error {
	if errors.Is(err, sql.ErrNoRows) {
		return echo.NewHTTPError(http.StatusNotFound, "not found")
	}
	var pqErr *pq.Error
	if errors.As(err, &pqErr) {
		switch pqErr.Code {
		case "23505":
			return echo.NewHTTPError(http.StatusConflict, "already registered")
		case "23503":
			return echo.NewHTTPError(http.StatusBadRequest, "unknown product")
		case "23502", "23514", "22P02":
			return echo.NewHTTPError(http.StatusBadRequest, "invalid "+pqErr.Constraint)
		}
	}
	return err
}
