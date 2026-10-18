#!/usr/bin/env bash
# Ejecuta TODAS las pruebas de la base de datos en PostgreSQL local (15+).
# Crea dos bases temporales, aplica las migraciones y los contenidos, y las borra al terminar.
#   Uso:  PGUSER=postgres ./tests/run_local_tests.sh
# (Si necesitas otro usuario/host exporta PGUSER, PGHOST, PGPASSWORD como de costumbre.)
set -euo pipefail
cd "$(dirname "$0")/.."

run() { psql -X -q -v ON_ERROR_STOP=1 -d "$1" -f "$2"; }

prepare() {                     # $1 = base, resto = archivos extra de contenido
  local db="$1"; shift
  dropdb --if-exists "$db"; createdb "$db"
  run "$db" tests/00_local_supabase_stub.sql
  for f in migrations/*.sql; do run "$db" "$f"; done
  for f in "$@"; do run "$db" "$f"; done
}

echo "== Base 1: contenido v1 (seed.sql) → pruebas 01"
prepare niju_t1 seed.sql
psql -X -d niju_t1 -v ON_ERROR_STOP=1 -f tests/01_niju_tests.sql | tail -3

echo "== Base 2: contenido v1 + Hogar/Transporte → pruebas 02"
prepare niju_t2 seed.sql seed_home_transport.sql
psql -X -d niju_t2 -v ON_ERROR_STOP=1 -f tests/02_niju_home_transport_tests.sql | tail -3

dropdb niju_t1; dropdb niju_t2
echo "== Todas las pruebas de la base de datos pasaron"
