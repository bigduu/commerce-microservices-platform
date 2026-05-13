#!/bin/bash
set -e

IFS=',' read -ra DBS <<< "$POSTGRES_MULTIPLE_DATABASES"
for DB in "${DBS[@]}"; do
    echo "Creating database: $DB"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE DATABASE $DB;
EOSQL
done
