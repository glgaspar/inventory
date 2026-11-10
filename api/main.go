package main

import (
	"database/sql"
	_ "embed"
	"fmt"
	"log"
	"os"

	"github.com/joho/godotenv"
	_ "github.com/lib/pq"
)

//go:embed db.sql
var schema string

func main() {
	godotenv.Load()

	db, err := openDb()
	if err != nil {
		log.Fatal(err)
	}
	if _, err := db.Exec(schema); err != nil {
		log.Fatal(err)
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Fatal(newServer(db).Start(":" + port))
}

func openDb() (*sql.DB, error) {
	db, err := sql.Open("postgres", fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		os.Getenv("PG_HOST"), os.Getenv("PG_PORT"), os.Getenv("PG_USER"), os.Getenv("PG_PASSWORD"), os.Getenv("PG_DBNAME")))
	if err != nil {
		return nil, err
	}
	return db, db.Ping()
}
