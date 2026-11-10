package users

import (
	"database/sql"
	"net/http"
	"time"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/db"
	"github.com/glgaspar/inventory/api/web"
	"github.com/labstack/echo/v4"
)

type User struct {
	ID        int64  `json:"id"`
	Username  string `json:"username"`
	Admin     bool   `json:"admin"`
	Active    bool   `json:"active"`
	CreatedAt string `json:"createdAt"`
}

type Handler struct {
	DB *sql.DB
}

func (h Handler) List(c echo.Context) error {
	rows, err := h.DB.Query("select id, username, admin, active, created_at from users order by id")
	if err != nil {
		return err
	}
	defer rows.Close()

	list := []User{}
	for rows.Next() {
		var u User
		var createdAt time.Time
		if err := rows.Scan(&u.ID, &u.Username, &u.Admin, &u.Active, &createdAt); err != nil {
			return err
		}
		u.CreatedAt = web.ISO(createdAt)
		list = append(list, u)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return c.JSON(http.StatusOK, list)
}

func (h Handler) Create(c echo.Context) error {
	var body struct {
		Username string `json:"username"`
		Password string `json:"password"`
		Admin    bool   `json:"admin"`
	}
	if err := c.Bind(&body); err != nil {
		return err
	}
	id, err := auth.CreateUser(h.DB, body.Username, body.Password, body.Admin)
	if err != nil {
		return err
	}
	username := auth.NormalizeUsername(body.Username)
	activity.Log(h.DB, c, auth.CurrentID(c), "user.create", map[string]any{"id": id, "username": username, "admin": body.Admin})
	return c.JSON(http.StatusCreated, User{ID: id, Username: username, Admin: body.Admin, Active: true, CreatedAt: web.ISO(time.Now())})
}

func (h Handler) Update(c echo.Context) error {
	id, err := web.ParamID(c)
	if err != nil {
		return err
	}
	var body struct {
		Admin    *bool   `json:"admin"`
		Active   *bool   `json:"active"`
		Password *string `json:"password"`
	}
	if err := c.Bind(&body); err != nil {
		return err
	}
	if id == auth.Current(c).ID && ((body.Admin != nil && !*body.Admin) || (body.Active != nil && !*body.Active)) {
		return echo.NewHTTPError(http.StatusBadRequest, "you can't remove your own admin or disable yourself")
	}
	var passwordHash *string
	if body.Password != nil {
		h, err := auth.HashPassword(*body.Password)
		if err != nil {
			return err
		}
		passwordHash = &h
	}

	tx, err := h.DB.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var u User
	var createdAt time.Time
	err = tx.QueryRow(`update users set
			admin = coalesce($2, admin),
			active = coalesce($3, active),
			password_hash = coalesce($4, password_hash)
		where id = $1
		returning id, username, admin, active, created_at`, id, body.Admin, body.Active, passwordHash).
		Scan(&u.ID, &u.Username, &u.Admin, &u.Active, &createdAt)
	if err != nil {
		return db.HTTPError(err)
	}
	if !u.Active || passwordHash != nil {
		if _, err := tx.Exec("delete from tokens where user_id = $1", id); err != nil {
			return err
		}
	}
	if err := tx.Commit(); err != nil {
		return err
	}
	u.CreatedAt = web.ISO(createdAt)
	activity.Log(h.DB, c, auth.CurrentID(c), "user.update",
		map[string]any{"id": id, "admin": body.Admin, "active": body.Active, "passwordChanged": passwordHash != nil})
	return c.JSON(http.StatusOK, u)
}
