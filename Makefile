SHELL := bash

DOCKER_COMPOSE ?= docker compose
JAVAC ?= javac
JAVA ?= java
SQLCMD ?= sqlcmd
CURL ?= curl

DB_PROFILE_FILE ?= .db-profile.mk
-include $(DB_PROFILE_FILE)

DB_HOST ?= localhost
DB_PORT ?= 1433
DB_NAME ?= cs338015
DB_USER ?= sa
DB_PASSWORD ?= LocalSqlServerPassw0rd!
DB_ENCRYPT ?= true
DB_TRUST_SERVER_CERTIFICATE ?= true

REMOTE_DB_HOST ?= uranium.cs.umanitoba.ca
REMOTE_DB_PORT ?= 1433
REMOTE_DB_NAME ?= cs338015
REMOTE_DB_USER ?= 
REMOTE_DB_PASSWORD ?= 
REMOTE_DB_ENCRYPT ?= true
REMOTE_DB_TRUST_SERVER_CERTIFICATE ?= true

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

define LOCAL_DB_PROFILE_CONTENT
DB_HOST := localhost
DB_PORT := 1433
DB_NAME := cs338015
DB_USER := sa
DB_PASSWORD := LocalSqlServerPassw0rd!
DB_ENCRYPT := true
DB_TRUST_SERVER_CERTIFICATE := true
endef

define REMOTE_DB_PROFILE_CONTENT
DB_HOST := $(REMOTE_DB_HOST)
DB_PORT := $(REMOTE_DB_PORT)
DB_NAME := $(REMOTE_DB_NAME)
DB_USER := $(REMOTE_DB_USER)
DB_PASSWORD := $(REMOTE_DB_PASSWORD)
DB_ENCRYPT := $(REMOTE_DB_ENCRYPT)
DB_TRUST_SERVER_CERTIFICATE := $(REMOTE_DB_TRUST_SERVER_CERTIFICATE)
endef

.PHONY: help deps db-up db-down db-reset db-load db-databases db-tables db-show-config db-use-local db-use-remote db-clear-profile cli-build cli-run cli-run-remote run-remote clean deps-clean

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
	  "  make db-show-config  Show the active DB connection settings" \
	  "  make db-use-local    Persist the local SQL Server settings as defaults" \
	  "  make db-use-remote   Persist REMOTE_DB_* settings as the active defaults" \
	  "  make db-clear-profile Remove any saved DB profile overrides" \
	  "  make cli-build     Compile the Java CLI classes" \
	  "  make cli-run       Build and run the Java CLI" \
	  "  make run-remote    Prompt for the remote DB username and password and run the Java CLI" \
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

db-show-config:
	@printf "%s\n" \
	  "DB_HOST=$(DB_HOST)" \
	  "DB_PORT=$(DB_PORT)" \
	  "DB_NAME=$(DB_NAME)" \
	  "DB_USER=$(DB_USER)" \
	  "DB_ENCRYPT=$(DB_ENCRYPT)" \
	  "DB_TRUST_SERVER_CERTIFICATE=$(DB_TRUST_SERVER_CERTIFICATE)" \
	  "DB_PROFILE_FILE=$(DB_PROFILE_FILE)"

db-use-local:
	$(file >$(DB_PROFILE_FILE),$(LOCAL_DB_PROFILE_CONTENT))
	@printf "Saved local DB defaults to %s\n" "$(DB_PROFILE_FILE)"

db-use-remote:
	@:$(if $(strip $(REMOTE_DB_HOST)),,$(error REMOTE_DB_HOST is required))
	@:$(if $(strip $(REMOTE_DB_USER)),,$(error REMOTE_DB_USER is required))
	@:$(if $(strip $(REMOTE_DB_PASSWORD)),,$(error REMOTE_DB_PASSWORD is required))
	$(file >$(DB_PROFILE_FILE),$(REMOTE_DB_PROFILE_CONTENT))
	@printf "Saved remote DB defaults to %s\n" "$(DB_PROFILE_FILE)"

db-clear-profile:
	rm -f $(DB_PROFILE_FILE)
	@printf "Removed %s\n" "$(DB_PROFILE_FILE)"

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

cli-run-remote: cli-build
	@:$(if $(strip $(REMOTE_DB_HOST)),,$(error REMOTE_DB_HOST is required))
	@read -rp "Remote DB username$(if $(strip $(REMOTE_DB_USER)), [$(REMOTE_DB_USER)],): " remote_db_user; \
	  if [[ -z "$$remote_db_user" ]]; then remote_db_user="$(REMOTE_DB_USER)"; fi; \
	  if [[ -z "$$remote_db_user" ]]; then echo "Remote DB username is required" >&2; exit 1; fi; \
	  read -rsp "Remote DB password for $$remote_db_user@$(REMOTE_DB_HOST): " remote_db_password; \
	  echo; \
	  export F1_DB_PASSWORD="$$remote_db_password"; \
	  $(JAVA) -cp "$(BUILD_DIR):$(LIB_DIR)/*" $(MAIN_CLASS) \
	    --host $(REMOTE_DB_HOST) \
	    --port $(REMOTE_DB_PORT) \
	    --database $(REMOTE_DB_NAME) \
	    --username "$$remote_db_user" \
	    --encrypt $(REMOTE_DB_ENCRYPT) \
	    --trust-server-certificate $(REMOTE_DB_TRUST_SERVER_CERTIFICATE) \
	    --project-root $(CURDIR) \
	    --sqlite-replica $(CURDIR)/$(SQLITE_REPLICA) \
	    --schema $(CURDIR)/$(SCHEMA_PATH) \
	    --batch-size $(BATCH_SIZE) \
	    $(CLI_ARGS)

run-remote: cli-run-remote

clean:
	rm -rf build target

deps-clean:
	rm -f $(LIB_DIR)/*.jar
