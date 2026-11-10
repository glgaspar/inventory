package activity

import (
	"database/sql"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/glgaspar/inventory/api/web"
	"github.com/labstack/echo/v4"
)

type Entry struct {
	ID       int64           `json:"id"`
	UserID   *int64          `json:"userId"`
	Username *string         `json:"username"`
	Action   string          `json:"action"`
	Details  json.RawMessage `json:"details"`
	IP       string          `json:"ip"`
	At       string          `json:"at"`
}

func Log(db *sql.DB, c echo.Context, userID *int64, action string, details any) {
	data, err := json.Marshal(details)
	if err == nil {
		_, err = db.Exec("insert into activity (user_id, action, details, ip) values ($1, $2, $3, $4)",
			userID, action, data, c.RealIP())
	}
	if err != nil {
		slog.Error("activity log failed", "action", action, "err", err)
	}
}

type Handler struct {
	DB *sql.DB
}

func (h Handler) List(c echo.Context) error {
	before, _ := strconv.ParseInt(c.QueryParam("before"), 10, 64)
	limit, _ := strconv.Atoi(c.QueryParam("limit"))
	if limit <= 0 || limit > 500 {
		limit = 100
	}
	rows, err := h.DB.Query(`select a.id, a.user_id, u.username, a.action, a.details, a.ip, a.at
		from activity a
		left join users u on u.id = a.user_id
		where $1 = 0 or a.id < $1
		order by a.id desc
		limit $2`, before, limit)
	if err != nil {
		return err
	}
	defer rows.Close()

	entries := []Entry{}
	for rows.Next() {
		var e Entry
		var at time.Time
		if err := rows.Scan(&e.ID, &e.UserID, &e.Username, &e.Action, &e.Details, &e.IP, &at); err != nil {
			return err
		}
		e.At = web.ISO(at)
		entries = append(entries, e)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return c.JSON(http.StatusOK, entries)
}
