package movements

import (
	"database/sql"
	"net/http"
	"time"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/db"
	"github.com/glgaspar/inventory/api/products"
	"github.com/glgaspar/inventory/api/web"
	"github.com/labstack/echo/v4"
)

type InventoryItem struct {
	Product   products.Product `json:"product"`
	Amount    float64          `json:"amount"`
	Packages  int              `json:"packages"`
	UpdatedAt string           `json:"updatedAt"`
}

type Movement struct {
	ID        int64   `json:"id"`
	ProductID int64   `json:"productId"`
	Type      string  `json:"type"`
	Amount    float64 `json:"amount"`
	Packages  int     `json:"packages"`
	At        string  `json:"at"`
	UserID    int64   `json:"userId"`
	Username  string  `json:"username"`
}

type Batch struct {
	Type  string    `json:"type"`
	At    time.Time `json:"at"`
	Items []struct {
		ProductID int64   `json:"productId"`
		Packages  int     `json:"packages"`
		Amount    float64 `json:"amount"`
	} `json:"items"`
}

type Handler struct {
	DB *sql.DB
}

func (h Handler) Create(c echo.Context) error {
	var b Batch
	if err := c.Bind(&b); err != nil {
		return err
	}
	if b.At.IsZero() || len(b.Items) == 0 || len(b.Items) > 500 {
		return echo.NewHTTPError(http.StatusBadRequest, "at and 1 to 500 items are required")
	}
	userID := auth.Current(c).ID

	tx, err := h.DB.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	var id int64
	err = tx.QueryRow("insert into batches (type, at, user_id) values ($1, $2, $3) returning id", b.Type, b.At, userID).Scan(&id)
	if err != nil {
		return db.HTTPError(err)
	}
	for _, item := range b.Items {
		_, err := tx.Exec("insert into movements (batch_id, product_id, amount, packages) values ($1, $2, $3, $4)",
			id, item.ProductID, item.Amount, item.Packages)
		if err != nil {
			return db.HTTPError(err)
		}
	}
	if err := tx.Commit(); err != nil {
		return err
	}
	activity.Log(h.DB, c, &userID, "movements.create", map[string]any{"batchId": id, "type": b.Type, "items": len(b.Items)})
	return c.JSON(http.StatusCreated, map[string]int64{"id": id})
}

func (h Handler) Inventory(c echo.Context) error {
	rows, err := h.DB.Query(`select ` + products.Columns + `,
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
		if err := rows.Scan(append(i.Product.Fields(), &i.Amount, &i.Packages, &updatedAt)...); err != nil {
			return err
		}
		i.UpdatedAt = web.ISO(updatedAt)
		i.Product.ImageURL = web.AbsURL(c, i.Product.ImageURL)
		items = append(items, i)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return c.JSON(http.StatusOK, items)
}

func (h Handler) ByProduct(c echo.Context) error {
	id, err := web.ParamID(c)
	if err != nil {
		return err
	}
	rows, err := h.DB.Query(`select m.id, m.product_id, b.type, m.amount, m.packages, b.at, u.id, u.username
		from movements m
		join batches b on b.id = m.batch_id
		join users u on u.id = b.user_id
		where m.product_id = $1
		order by b.at desc, m.id desc`, id)
	if err != nil {
		return err
	}
	defer rows.Close()

	list := []Movement{}
	for rows.Next() {
		var m Movement
		var at time.Time
		if err := rows.Scan(&m.ID, &m.ProductID, &m.Type, &m.Amount, &m.Packages, &at, &m.UserID, &m.Username); err != nil {
			return err
		}
		m.At = web.ISO(at)
		list = append(list, m)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return c.JSON(http.StatusOK, list)
}
