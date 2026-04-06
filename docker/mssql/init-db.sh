#!/usr/bin/env bash
set -euo pipefail

readonly init_marker="/var/opt/mssql/.comp3380-db-created"

# SQL Server expects a writable HOME for its internal .system directory.
# The base image exports HOME=/home/mssql, but that path is not created.
# Fall back to /tmp when HOME is missing or unusable.
if [[ -z "${HOME:-}" || ! -d "${HOME:-}" || ! -w "${HOME:-}" ]]; then
  export HOME="/tmp/mssql-home"
fi
mkdir -p "$HOME"

sqlcmd_bin=""
for candidate in /opt/mssql-tools18/bin/sqlcmd /opt/mssql-tools/bin/sqlcmd; do
  if [[ -x "$candidate" ]]; then
    sqlcmd_bin="$candidate"
    break
  fi
done

if [[ -z "$sqlcmd_bin" ]]; then
  echo "sqlcmd not found in the SQL Server image" >&2
  exit 1
fi

if [[ "${ACCEPT_EULA:-}" != "Y" ]]; then
  echo "ACCEPT_EULA must be set to Y" >&2
  exit 1
fi

if [[ -z "${MSSQL_SA_PASSWORD:-}" ]]; then
  echo "MSSQL_SA_PASSWORD is required" >&2
  exit 1
fi

if [[ -z "${MSSQL_DB_NAME:-}" ]]; then
  echo "MSSQL_DB_NAME is required" >&2
  exit 1
fi

if [[ ! "${MSSQL_DB_NAME}" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "MSSQL_DB_NAME must use only letters, numbers, underscores, or dashes" >&2
  exit 1
fi

/opt/mssql/bin/sqlservr &
sqlservr_pid=$!

if [[ ! -f "$init_marker" ]]; then
  echo "Waiting for SQL Server to accept connections..."
  for _ in $(seq 1 60); do
    if "$sqlcmd_bin" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "SELECT 1" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  if ! "$sqlcmd_bin" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "SELECT 1" >/dev/null 2>&1; then
    echo "SQL Server did not become ready in time." >&2
    exit 1
  fi

  echo "Creating database ${MSSQL_DB_NAME} if it does not exist..."
  "$sqlcmd_bin" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "IF DB_ID(N'${MSSQL_DB_NAME}') IS NULL CREATE DATABASE [${MSSQL_DB_NAME}];"

  touch "$init_marker"
fi

wait "$sqlservr_pid"
