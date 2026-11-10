package main

import (
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/glgaspar/inventory/api/auth"
	"github.com/glgaspar/inventory/api/db"
	"github.com/glgaspar/inventory/api/server"
	"github.com/joho/godotenv"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))
	godotenv.Load()

	conn, err := db.Open()
	if err != nil {
		fail(err)
	}
	if err := db.Migrate(conn); err != nil {
		fail(err)
	}
	if err := auth.Bootstrap(conn); err != nil {
		fail(err)
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	s := &http.Server{
		Addr:              ":" + port,
		Handler:           server.New(conn),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      2 * time.Minute,
		IdleTimeout:       2 * time.Minute,
	}
	slog.Info("listening", "port", port)
	fail(s.ListenAndServe())
}

func fail(err error) {
	slog.Error("fatal", "err", err)
	os.Exit(1)
}
