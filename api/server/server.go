package server

import (
	"database/sql"
	"log/slog"
	"net/http"
	"time"

	"github.com/glgaspar/inventory/api/activity"
	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/frontend"
	"github.com/glgaspar/inventory/api/images"
	"github.com/glgaspar/inventory/api/lookup"
	"github.com/glgaspar/inventory/api/movements"
	"github.com/glgaspar/inventory/api/products"
	"github.com/glgaspar/inventory/api/recipes"
	"github.com/glgaspar/inventory/api/users"
	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
)

func New(db *sql.DB) *echo.Echo {
	e := echo.New()
	e.HideBanner = true
	e.HidePort = true
	e.IPExtractor = echo.ExtractIPDirect()
	secure := middleware.DefaultSecureConfig
	secure.XFrameOptions = "DENY"
	secure.ReferrerPolicy = "same-origin"
	secure.ContentSecurityPolicy = "default-src 'self'; img-src 'self' http: https: data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"
	e.Use(requestLog(), middleware.Recover(), middleware.RequestID(), middleware.SecureWithConfig(secure), middleware.BodyLimit("1M"))

	user := auth.Require(db)
	admin := []echo.MiddlewareFunc{user, auth.AdminOnly}
	loginLimit := middleware.RateLimiter(middleware.NewRateLimiterMemoryStoreWithConfig(
		middleware.RateLimiterMemoryStoreConfig{Rate: 0.1, Burst: 10, ExpiresIn: 10 * time.Minute}))

	a := auth.Handler{DB: db}
	p := products.Handler{DB: db}
	m := movements.Handler{DB: db}
	u := users.Handler{DB: db}
	log := activity.Handler{DB: db}
	l := lookup.Handler{DB: db}
	img := images.Handler{DB: db}
	r := recipes.Handler{DB: db}
	recipeLimit := middleware.RateLimiter(middleware.NewRateLimiterMemoryStoreWithConfig(
		middleware.RateLimiterMemoryStoreConfig{Rate: 0.1, Burst: 10, ExpiresIn: 10 * time.Minute}))

	e.GET("/", func(c echo.Context) error { return c.NoContent(http.StatusOK) })
	frontend.Register(e)
	e.GET("/images/:id", img.Get)

	e.POST("/login", a.Login, loginLimit)
	e.POST("/logout", a.Logout, user)
	e.GET("/me", a.Me, user)
	e.PUT("/me/password", a.ChangePassword, user)

	e.GET("/products/by-barcode/:barcode", p.ByBarcode, user)
	e.GET("/products", p.List, user)
	e.POST("/products", p.Create, user)
	e.PATCH("/products/:id", p.Update, user)
	e.DELETE("/products/:id", p.Delete, user)
	e.PUT("/products/:id/image", p.SetImage, user)
	e.DELETE("/products/:id/image", p.RemoveImage, user)
	e.DELETE("/batches/:id", m.Undo, user)
	e.GET("/lookup/:barcode", l.Get, user)
	e.GET("/products/:id", p.Get, user)
	e.GET("/products/:id/movements", m.ByProduct, user)
	e.POST("/movements", m.Create, user)
	e.GET("/inventory", m.Inventory, user)
	e.POST("/recipes/suggest", r.Suggest, user, recipeLimit)

	e.GET("/users", u.List, admin...)
	e.POST("/users", u.Create, admin...)
	e.PATCH("/users/:id", u.Update, admin...)
	e.GET("/activity", log.List, admin...)

	return e
}

func requestLog() echo.MiddlewareFunc {
	return middleware.RequestLoggerWithConfig(middleware.RequestLoggerConfig{
		LogMethod:    true,
		LogURI:       true,
		LogStatus:    true,
		LogLatency:   true,
		LogRemoteIP:  true,
		LogRequestID: true,
		LogError:     true,
		HandleError:  true,
		LogValuesFunc: func(c echo.Context, v middleware.RequestLoggerValues) error {
			attrs := []any{"method", v.Method, "uri", v.URI, "status", v.Status, "latency", v.Latency.String(),
				"ip", v.RemoteIP, "requestId", v.RequestID}
			if u := auth.Current(c); u.ID != 0 {
				attrs = append(attrs, "user", u.Username)
			}
			if v.Status >= 500 && v.Error != nil {
				slog.Error("request", append(attrs, "err", v.Error.Error())...)
			} else {
				slog.Info("request", attrs...)
			}
			return nil
		},
	})
}
