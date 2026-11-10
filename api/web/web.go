package web

import (
	"net/http"
	"strconv"
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
