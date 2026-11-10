package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"strings"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/db"
	"github.com/labstack/echo/v4"
	"golang.org/x/crypto/bcrypt"
)

const idleLimit = "30 days"

type User struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
	Admin    bool   `json:"admin"`
}

var dummyHash, _ = bcrypt.GenerateFromPassword([]byte("dummy password"), bcrypt.DefaultCost)

func Current(c echo.Context) User {
	u, _ := c.Get("user").(User)
	return u
}

func CurrentID(c echo.Context) *int64 {
	u, ok := c.Get("user").(User)
	if !ok {
		return nil
	}
	return &u.ID
}

func Require(conn *sql.DB) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			token, ok := strings.CutPrefix(c.Request().Header.Get("Authorization"), "Bearer ")
			if !ok || token == "" {
				return echo.NewHTTPError(http.StatusUnauthorized, "login required")
			}
			var u User
			err := conn.QueryRow(`update tokens t set last_used_at = now()
				from users u
				where t.hash = $1 and t.last_used_at > now() - $2::interval and u.id = t.user_id and u.active
				returning u.id, u.username, u.admin`, hash(token), idleLimit).Scan(&u.ID, &u.Username, &u.Admin)
			if errors.Is(err, sql.ErrNoRows) {
				return echo.NewHTTPError(http.StatusUnauthorized, "login required")
			}
			if err != nil {
				return err
			}
			c.Set("user", u)
			c.Set("tokenHash", hash(token))
			return next(c)
		}
	}
}

func AdminOnly(next echo.HandlerFunc) echo.HandlerFunc {
	return func(c echo.Context) error {
		if !Current(c).Admin {
			return echo.NewHTTPError(http.StatusForbidden, "admins only")
		}
		return next(c)
	}
}

func HashPassword(password string) (string, error) {
	if len(password) < 8 || len(password) > 72 {
		return "", echo.NewHTTPError(http.StatusBadRequest, "password must have 8 to 72 characters")
	}
	h, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	return string(h), err
}

func NormalizeUsername(username string) string {
	return strings.ToLower(strings.TrimSpace(username))
}

func CreateUser(conn *sql.DB, username, password string, admin bool) (int64, error) {
	h, err := HashPassword(password)
	if err != nil {
		return 0, err
	}
	var id int64
	err = conn.QueryRow("insert into users (username, password_hash, admin) values ($1, $2, $3) returning id",
		NormalizeUsername(username), h, admin).Scan(&id)
	return id, db.HTTPError(err)
}

func Bootstrap(conn *sql.DB) error {
	var any bool
	if err := conn.QueryRow("select exists (select 1 from users)").Scan(&any); err != nil || any {
		return err
	}
	username, password := os.Getenv("ADMIN_USERNAME"), os.Getenv("ADMIN_PASSWORD")
	if username == "" || password == "" {
		slog.Warn("no users yet: set ADMIN_USERNAME and ADMIN_PASSWORD to create the first admin")
		return nil
	}
	if _, err := CreateUser(conn, username, password, true); err != nil {
		return err
	}
	slog.Info("first admin created", "username", NormalizeUsername(username))
	return nil
}

func hash(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func newToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

type Handler struct {
	DB *sql.DB
}

type credentials struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func (h Handler) Login(c echo.Context) error {
	var body credentials
	if err := c.Bind(&body); err != nil {
		return err
	}
	username := NormalizeUsername(body.Username)

	var u User
	var passwordHash string
	var active bool
	err := h.DB.QueryRow("select id, username, admin, password_hash, active from users where username = $1", username).
		Scan(&u.ID, &u.Username, &u.Admin, &passwordHash, &active)
	if errors.Is(err, sql.ErrNoRows) {
		bcrypt.CompareHashAndPassword(dummyHash, []byte(body.Password))
		return h.loginFailed(c, nil, username)
	}
	if err != nil {
		return err
	}
	if bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(body.Password)) != nil || !active {
		return h.loginFailed(c, &u.ID, username)
	}

	token, err := newToken()
	if err != nil {
		return err
	}
	if _, err := h.DB.Exec("insert into tokens (hash, user_id) values ($1, $2)", hash(token), u.ID); err != nil {
		return err
	}
	h.DB.Exec("delete from tokens where last_used_at < now() - $1::interval", idleLimit)
	activity.Log(h.DB, c, &u.ID, "login", nil)
	return c.JSON(http.StatusOK, map[string]any{"token": token, "user": u})
}

func (h Handler) loginFailed(c echo.Context, userID *int64, username string) error {
	if len(username) > 50 {
		username = username[:50]
	}
	activity.Log(h.DB, c, userID, "login.failed", map[string]string{"username": username})
	return echo.NewHTTPError(http.StatusUnauthorized, "wrong username or password")
}

func (h Handler) Logout(c echo.Context) error {
	if _, err := h.DB.Exec("delete from tokens where hash = $1", c.Get("tokenHash")); err != nil {
		return err
	}
	activity.Log(h.DB, c, CurrentID(c), "logout", nil)
	return c.NoContent(http.StatusNoContent)
}

func (h Handler) Me(c echo.Context) error {
	return c.JSON(http.StatusOK, Current(c))
}

func (h Handler) ChangePassword(c echo.Context) error {
	var body struct {
		Current string `json:"current"`
		New     string `json:"new"`
	}
	if err := c.Bind(&body); err != nil {
		return err
	}
	u := Current(c)
	var passwordHash string
	if err := h.DB.QueryRow("select password_hash from users where id = $1", u.ID).Scan(&passwordHash); err != nil {
		return err
	}
	if bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(body.Current)) != nil {
		return echo.NewHTTPError(http.StatusForbidden, "wrong current password")
	}
	newHash, err := HashPassword(body.New)
	if err != nil {
		return err
	}
	tx, err := h.DB.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.Exec("update users set password_hash = $1 where id = $2", newHash, u.ID); err != nil {
		return err
	}
	if _, err := tx.Exec("delete from tokens where user_id = $1 and hash <> $2", u.ID, c.Get("tokenHash")); err != nil {
		return err
	}
	if err := tx.Commit(); err != nil {
		return err
	}
	activity.Log(h.DB, c, &u.ID, "password.change", nil)
	return c.NoContent(http.StatusNoContent)
}
