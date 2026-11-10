package images

import (
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/labstack/echo/v4"
)

const maxSize = 3 << 20

var AllowedHosts = []string{"openfoodfacts.org", "openbeautyfacts.org", "openproductsfacts.org"}

var client = &http.Client{Timeout: 10 * time.Second}

func allowed(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || (u.Scheme != "https" && u.Scheme != "http") {
		return false
	}
	host := u.Hostname()
	for _, h := range AllowedHosts {
		if host == h || strings.HasSuffix(host, "."+h) {
			return true
		}
	}
	return false
}

func Download(db *sql.DB, raw string) (string, error) {
	if !allowed(raw) {
		return "", fmt.Errorf("image host not allowed: %s", raw)
	}
	res, err := client.Get(raw)
	if err != nil {
		return "", err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return "", fmt.Errorf("image download: status %d", res.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(res.Body, maxSize+1))
	if err != nil {
		return "", err
	}
	return Save(db, data)
}

var ErrNotImage = errors.New("not a JPEG, PNG, WebP or GIF image of at most 3 MB")

func Save(db *sql.DB, data []byte) (string, error) {
	if len(data) == 0 || len(data) > maxSize {
		return "", ErrNotImage
	}
	contentType := http.DetectContentType(data)
	if contentType != "image/jpeg" && contentType != "image/png" && contentType != "image/webp" && contentType != "image/gif" {
		return "", ErrNotImage
	}
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	id := hex.EncodeToString(b)
	if _, err := db.Exec("insert into images (id, content_type, data) values ($1, $2, $3)", id, contentType, data); err != nil {
		return "", err
	}
	return "/images/" + id, nil
}

type Handler struct {
	DB *sql.DB
}

func (h Handler) Get(c echo.Context) error {
	var contentType string
	var data []byte
	err := h.DB.QueryRow("select content_type, data from images where id = $1", c.Param("id")).Scan(&contentType, &data)
	if err == sql.ErrNoRows {
		return echo.NewHTTPError(http.StatusNotFound, "not found")
	}
	if err != nil {
		return err
	}
	c.Response().Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	return c.Blob(http.StatusOK, contentType, data)
}
