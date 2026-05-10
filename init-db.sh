#!/bin/bash
set -e

for DB in "$@"; do
    echo "Creating database: $DB"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE DATABASE $DB;
EOSQL
done
