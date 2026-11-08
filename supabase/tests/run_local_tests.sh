#!/usr/bin/env bash
# Ejecuta TODAS las pruebas de la base de datos en PostgreSQL local (15+).
# Crea tres bases temporales, aplica las migraciones y los contenidos, y las borra al terminar.
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

echo "== Base 3: contenido completo (12 contextos) → pruebas 03 (v2)"
prepare niju_t3 seed.sql seed_home_transport.sql seed_v2_contexts.sql

echo "== Base 3: reinicio desde cero (se vuelven a ejecutar todas las migraciones y seeds)"
psql -X -q -d niju_t3 -v ON_ERROR_STOP=1 -c \
  "insert into auth.users (id, email, raw_user_meta_data) values ('00000000-0000-0000-0000-00000000beef', 'previo@niju.test', '{\"full_name\": \"Usuario previo\"}')"
for f in migrations/*.sql seed.sql seed_home_transport.sql seed_v2_contexts.sql; do run niju_t3 "$f"; done
psql -X -q -t -d niju_t3 -v ON_ERROR_STOP=1 -c "do \$\$ begin
  if (select count(*) from public.contexts) <> 12 or (select count(*) from public.words) <> 144
     or (select count(*) from public.achievements) <> 20 then raise exception 'reinicio: contenido incorrecto'; end if;
  if not exists (select 1 from public.profiles p join public.user_stats s on s.user_id = p.id
                  where p.id = '00000000-0000-0000-0000-00000000beef' and p.display_name = 'Usuario previo')
    then raise exception 'reinicio: el usuario previo no recuperó su perfil'; end if;
  delete from auth.users where id = '00000000-0000-0000-0000-00000000beef';
end \$\$;"
echo "ok - reinicio desde cero: contenido completo y perfil recreado"
psql -X -d niju_t3 -v ON_ERROR_STOP=1 -f tests/03_niju_v2_tests.sql | tail -3

dropdb niju_t1; dropdb niju_t2; dropdb niju_t3
echo "== Todas las pruebas de la base de datos pasaron"
