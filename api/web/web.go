package web

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/labstack/echo/v4"
)

func ParamID(c echo.Context) (int64, error) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		return 0, echo.NewHTTPError(http.StatusNotFound, "not found")
	}
	return id, nil
}

func ISO(t time.Time) string {
	return t.UTC().Format(time.RFC3339)
}

func AbsURL(c echo.Context, path *string) *string {
	if path == nil || !strings.HasPrefix(*path, "/") {
		return path
	}
	abs := c.Scheme() + "://" + c.Request().Host + *path
	return &abs
}

func LocalPath(c echo.Context, url *string) *string {
	if url == nil {
		return nil
	}
	prefix := c.Scheme() + "://" + c.Request().Host + "/images/"
	if strings.HasPrefix(*url, prefix) {
		path := "/images/" + strings.TrimPrefix(*url, prefix)
		return &path
	}
	return url
}
