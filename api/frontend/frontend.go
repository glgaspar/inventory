package frontend

import (
	"embed"
	"net/http"

	"github.com/labstack/echo/v4"
)

//go:embed static
var files embed.FS

func Register(e *echo.Echo) {
	e.StaticFS("/web", echo.MustSubFS(files, "static"))
	e.GET("/web", func(c echo.Context) error { return c.Redirect(http.StatusMovedPermanently, "/web/") })
}
