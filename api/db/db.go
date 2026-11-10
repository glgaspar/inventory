package db

import (
	"database/sql"
	_ "embed"
	"errors"
	"fmt"
	"net/http"
	"os"
	"time"

	"github.com/labstack/echo/v4"
	"github.com/lib/pq"
)

//go:embed schema.sql
var schema string

func Open() (*sql.DB, error) {
	conn, err := sql.Open("postgres", fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		os.Getenv("PG_HOST"), os.Getenv("PG_PORT"), os.Getenv("PG_USER"), os.Getenv("PG_PASSWORD"), os.Getenv("PG_DBNAME")))
	if err != nil {
		return nil, err
	}
	conn.SetMaxOpenConns(10)
	conn.SetConnMaxIdleTime(5 * time.Minute)
	return conn, conn.Ping()
}

func Migrate(conn *sql.DB) error {
	_, err := conn.Exec(schema)
	return err
}

func HTTPError(err error) error {
	if errors.Is(err, sql.ErrNoRows) {
		return echo.NewHTTPError(http.StatusNotFound, "not found")
	}
	var pqErr *pq.Error
	if errors.As(err, &pqErr) {
		switch pqErr.Code {
		case "23505":
			return echo.NewHTTPError(http.StatusConflict, "already exists")
		case "23503", "23502", "23514", "22P02", "22001", "22003":
			return echo.NewHTTPError(http.StatusBadRequest, "invalid "+pqErr.Constraint)
		}
	}
	return err
}
