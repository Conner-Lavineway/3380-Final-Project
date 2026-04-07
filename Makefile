SHELL := bash

DOCKER_COMPOSE ?= docker compose
JAVAC ?= javac
JAVA ?= java
SQLCMD ?= sqlcmd
CURL ?= curl

DB_HOST ?= localhost
DB_PORT ?= 1433
DB_NAME ?= cs338015
DB_USER ?= sa
DB_PASSWORD ?= LocalSqlServerPassw0rd!
DB_ENCRYPT ?= true
DB_TRUST_SERVER_CERTIFICATE ?= true
SQLITE_REPLICA ?= dbs/F1_refactored.db
SCHEMA_PATH ?= schema/refactored_race_entry_schema.sql
BATCH_SIZE ?= 500
CLI_ARGS ?=

LIB_DIR := lib
BUILD_DIR := build/classes
MSSQL_JDBC_VERSION := 12.8.1.jre11
SQLITE_JDBC_VERSION := 3.46.1.3
MSSQL_JAR := $(LIB_DIR)/mssql-jdbc-$(MSSQL_JDBC_VERSION).jar
SQLITE_JAR := $(LIB_DIR)/sqlite-jdbc-$(SQLITE_JDBC_VERSION).jar
MSSQL_JDBC_URL := https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/$(MSSQL_JDBC_VERSION)/mssql-jdbc-$(MSSQL_JDBC_VERSION).jar
SQLITE_JDBC_URL := https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$(SQLITE_JDBC_VERSION)/sqlite-jdbc-$(SQLITE_JDBC_VERSION).jar
JAVA_SOURCES := $(shell find src -name '*.java' | sort)
MAIN_CLASS := Main
LOADER_CLASS := LoaderMain

.PHONY: help deps db-up db-down db-reset db-load db-databases db-tables cli-build cli-run clean deps-clean

help:
	@printf "%s\n" \
	  "Available targets:" \
	  "  make deps          Download the JDBC jars into lib/" \
	  "  make db-up         Start the local SQL Server container" \
	  "  make db-down       Stop the local SQL Server container" \
	  "  make db-reset      Remove the local SQL Server container and volume" \
	  "  make db-load       Rebuild and load $(DB_NAME) from $(SQLITE_REPLICA)" \
	  "  make db-databases  List SQL Server databases" \
	  "  make db-tables     List base tables in $(DB_NAME)" \
	  "  make cli-build     Compile the Java CLI classes" \
	  "  make cli-run       Build and run the Java CLI" \
	  "  make clean         Remove compiled Java output" \
	  "  make deps-clean    Remove downloaded JDBC jars"

deps: $(MSSQL_JAR) $(SQLITE_JAR)

$(LIB_DIR):
	mkdir -p $(LIB_DIR)

$(MSSQL_JAR): | $(LIB_DIR)
	$(CURL) -L "$(MSSQL_JDBC_URL)" -o "$@"

$(SQLITE_JAR): | $(LIB_DIR)
	$(CURL) -L "$(SQLITE_JDBC_URL)" -o "$@"

db-up:
	$(DOCKER_COMPOSE) up -d

db-down:
	$(DOCKER_COMPOSE) down

db-reset:
	$(DOCKER_COMPOSE) down -v

db-load: cli-build
	$(JAVA) -cp "$(BUILD_DIR):$(LIB_DIR)/*" $(LOADER_CLASS) \
	  --host $(DB_HOST) \
	  --port $(DB_PORT) \
	  --database $(DB_NAME) \
	  --username $(DB_USER) \
	  --password $(DB_PASSWORD) \
	  --encrypt $(DB_ENCRYPT) \
	  --trust-server-certificate $(DB_TRUST_SERVER_CERTIFICATE) \
	  --project-root $(CURDIR) \
	  --sqlite-replica $(CURDIR)/$(SQLITE_REPLICA) \
	  --schema $(CURDIR)/$(SCHEMA_PATH) \
	  --batch-size $(BATCH_SIZE)

db-databases:
	$(SQLCMD) -S $(DB_HOST),$(DB_PORT) -U $(DB_USER) -P '$(DB_PASSWORD)' -Q "SET NOCOUNT ON; SELECT name FROM sys.databases ORDER BY name;"

db-tables:
	$(SQLCMD) -S $(DB_HOST),$(DB_PORT) -U $(DB_USER) -P '$(DB_PASSWORD)' -d $(DB_NAME) -Q "SET NOCOUNT ON; SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME;"

cli-build: deps
	mkdir -p $(BUILD_DIR)
	$(JAVAC) -cp "$(LIB_DIR)/*" -d $(BUILD_DIR) $(JAVA_SOURCES)

cli-run: cli-build
	$(JAVA) -cp "$(BUILD_DIR):$(LIB_DIR)/*" $(MAIN_CLASS) \
	  --host $(DB_HOST) \
	  --port $(DB_PORT) \
	  --database $(DB_NAME) \
	  --username $(DB_USER) \
	  --password $(DB_PASSWORD) \
	  --encrypt $(DB_ENCRYPT) \
	  --trust-server-certificate $(DB_TRUST_SERVER_CERTIFICATE) \
	  --project-root $(CURDIR) \
	  --sqlite-replica $(CURDIR)/$(SQLITE_REPLICA) \
	  --schema $(CURDIR)/$(SCHEMA_PATH) \
	  --batch-size $(BATCH_SIZE) \
	  $(CLI_ARGS)

clean:
	rm -rf build target

deps-clean:
	rm -f $(LIB_DIR)/*.jar
