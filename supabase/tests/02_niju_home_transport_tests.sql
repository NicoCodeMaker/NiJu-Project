-- =============================================================================
-- NiJu · Pruebas del contenido Hogar/Transporte y de get_next_lesson
-- Requiere: schema + migraciones 2, 3 y 4 + seed.sql + seed_home_transport.sql
-- Uso local:  psql -d niju -v ON_ERROR_STOP=1 -f tests/02_niju_home_transport_tests.sql
-- Se ejecuta en una transacción que se revierte al final.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1
\pset tuples_only on
\pset format unaligned

begin;

create schema t;
grant usage on schema t to anon, authenticated;

create function t.ok(p_cond boolean, p_msg text) returns text
language plpgsql as $$
begin
  if not coalesce(p_cond, false) then raise exception 'FAIL: %', p_msg; end if;
  return 'ok - ' || p_msg;
end $$;

create function t.throws(p_sql text, p_like text, p_msg text) returns text
language plpgsql as $$
begin
  begin execute p_sql;
  exception when others then
    if sqlerrm like p_like then return 'ok - ' || p_msg; end if;
    raise exception 'FAIL: % (error inesperado: %)', p_msg, sqlerrm;
  end;
  raise exception 'FAIL: % (no lanzó error)', p_msg;
end $$;

create function t.answer_for(p_item bigint) returns text
language sql stable as $$
  select case when i.exercise_type = 'en_to_es' then w.spanish else w.english end
    from public.session_items i join public.words w on w.id = i.word_id
   where i.id = p_item
$$;

create function t.as_user(p_uid text) returns void
language sql as $$ select set_config('request.jwt.claim.sub', p_uid, true) $$;

-- Completa una lección respondiendo todo bien (como lo haría un estudiante).
create function t.complete_lesson(p_lesson_id integer) returns void
language plpgsql as $$
declare v_sid uuid; r record;
begin
  v_sid := (public.start_session('lesson', p_lesson_id) ->> 'session_id')::uuid;
  for r in select id from public.session_items where session_id = v_sid order by position loop
    perform public.submit_answer(r.id, t.answer_for(r.id));
  end loop;
  perform public.finish_session(v_sid);
end $$;

grant execute on all functions in schema t to anon, authenticated;
grant select on public.session_items, public.words to authenticated;

\set A '00000000-0000-0000-0000-00000000000a'
\set B '00000000-0000-0000-0000-00000000000b'
\set C '00000000-0000-0000-0000-00000000000c'

insert into auth.users (id, email, raw_user_meta_data) values
  (:'A', 'ana@example.com', '{"name": "Ana"}'),
  (:'B', 'beto@example.com', '{}'),
  (:'C', 'carla@example.com', '{}');

\echo '--- 1. Integridad del contenido nuevo'
select t.ok((select count(*) from contexts) = 5, 'hay 5 contextos (3 base + Hogar + Transporte)');
select t.ok((select count(*) from lessons) = 10, 'hay 10 lecciones');
select t.ok((select count(*) from lessons where slug in
              ('home_rooms','home_objects','transport_city','transport_directions')) = 4,
            'existen las 4 lecciones nuevas');
select t.ok(bool_and(c = 6), 'cada lección nueva tiene 6 palabras')
  from (select count(*) c from lesson_words lw join lessons l on l.id = lw.lesson_id
         where l.slug in ('home_rooms','home_objects','transport_city','transport_directions')
         group by lw.lesson_id) x;
select t.ok(count(*) = 0, 'cada palabra nueva tiene un ejemplo que la contiene')
  from lesson_words lw
  join lessons l on l.id = lw.lesson_id and l.slug in
       ('home_rooms','home_objects','transport_city','transport_directions')
  join words w on w.id = lw.word_id
 where not exists (select 1 from word_examples e where e.word_id = w.id
                    and e.sentence_en ~* ('\m' || w.english || '\M'));
select t.ok(count(*) = 0, 'ninguna palabra nueva repite a otra en inglés dentro de su categoría gramatical')
  from (select english, part_of_speech from words group by 1, 2 having count(*) > 1) d;
select t.ok((select threshold from achievements where code = 'all_lessons') = 10,
            'el logro "Ruta completa" pide las 10 lecciones');
select t.ok((select sort_order from contexts where slug = 'home') < (select sort_order from contexts where slug = 'restaurant'),
            'Hogar se muestra antes que Restaurante');

\echo '--- 2. Ejercicios de las lecciones nuevas (respuesta verificable)'
set role authenticated;
select t.as_user(:'C');
do $$
declare v_slug text; v_sid uuid; r record; n integer;
begin
  foreach v_slug in array array['home_rooms','home_objects','transport_city','transport_directions'] loop
    v_sid := (public.start_session('lesson', (select id from public.lessons where slug = v_slug))
              ->> 'session_id')::uuid;
    select count(*) into n from public.session_items where session_id = v_sid;
    if n <> 6 then raise exception 'FAIL: % genera % ejercicios (esperado 6)', v_slug, n; end if;
    for r in select * from public.session_items where session_id = v_sid loop
      if cardinality(r.options) <> 3 then
        raise exception 'FAIL: % ejercicio % no tiene 3 opciones', v_slug, r.position; end if;
      if (select count(distinct o) from unnest(r.options) o) <> 3 then
        raise exception 'FAIL: % ejercicio % tiene opciones repetidas', v_slug, r.position; end if;
      if not (t.answer_for(r.id) = any(r.options)) then
        raise exception 'FAIL: % ejercicio % no incluye la respuesta correcta', v_slug, r.position; end if;
      if r.exercise_type = 'fill_blank' and r.prompt not like '%\_\_\_\_\_%' then
        raise exception 'FAIL: % ejercicio % de completar sin hueco', v_slug, r.position; end if;
    end loop;
    -- Responde todo bien: la respuesta "verificable" aprueba la lección y abre la siguiente.
    for r in select * from public.session_items where session_id = v_sid order by position loop
      if not (public.submit_answer(r.id, t.answer_for(r.id)) ->> 'is_correct')::boolean then
        raise exception 'FAIL: % ejercicio % la respuesta correcta fue rechazada', v_slug, r.position; end if;
    end loop;
    if not (public.finish_session(v_sid) ->> 'lesson_completed')::boolean then
      raise exception 'FAIL: % no se superó con todo correcto', v_slug; end if;
  end loop;
end $$;
select t.ok(true, '4 lecciones × 6 ejercicios: 3 opciones distintas, la correcta se acepta y se supera cada lección');

-- Una respuesta incorrecta se corrige en el servidor (usuario A, lección 1 de Hogar).
select t.as_user(:'A');
select (start_session('lesson', (select id from lessons where slug = 'home_rooms'))) ->> 'session_id' as hs \gset
select i.id as hitem from session_items i where i.session_id = :'hs' and i.position = 1 \gset
select t.ok(not (submit_answer(:hitem, 'respuesta equivocada') ->> 'is_correct')::boolean,
            'una respuesta incorrecta se marca como incorrecta');

\echo '--- 3. get_next_lesson'
reset role;
set role authenticated;
select t.as_user(:'B');
select t.ok(lesson_slug = 'home_rooms' and context_slug = 'home' and level = 'A1'
            and word_count = 6 and context_lessons_completed = 0 and context_lessons_total = 2,
            'estudiante nuevo: empieza por la primera lección del primer contexto (Hogar)')
  from get_next_lesson();
select t.ok(count(*) = 1, 'devuelve una sola lección') from get_next_lesson();
select t.complete_lesson((select id from lessons where slug = 'home_rooms'));
select t.ok(lesson_slug = 'home_objects' and context_lessons_completed = 1,
            'tras superar la 1 sigue con la 2 del mismo contexto')
  from get_next_lesson();
select t.complete_lesson((select id from lessons where slug = 'home_objects'));
select t.ok(lesson_slug = 'restaurant_table',
            'con Hogar terminado pasa al siguiente contexto del catálogo')
  from get_next_lesson();
-- Estudia primero la lección 1 de Transporte (es la primera de su contexto: está abierta).
select t.complete_lesson((select id from lessons where slug = 'transport_city'));
-- Dentro de una transacción de prueba now() es el mismo para todas las sesiones; en la app cada
-- llamada es su propia transacción. Se fija la cronología a mano: Transporte fue lo último.
reset role;
update study_sessions set started_at = now() - interval '3 hours'
 where user_id = :'B' and lesson_id in (select id from lessons where slug in ('home_rooms', 'home_objects'));
update study_sessions set started_at = now() - interval '1 hour'
 where user_id = :'B' and lesson_id = (select id from lessons where slug = 'transport_city');
set role authenticated;
select t.as_user(:'B');
select t.ok(lesson_slug = 'transport_directions',
            'prefiere el contexto de la última lección estudiada (Transporte)')
  from get_next_lesson();
select t.ok(lesson_slug not in ('restaurant_pay', 'supermarket_pay', 'airport_gate'),
            'nunca devuelve una lección bloqueada')
  from get_next_lesson();

-- Completa todo lo que queda usando la propia función.
do $$
declare n integer := 0; v_id integer;
begin
  loop
    select lesson_id into v_id from public.get_next_lesson();
    exit when v_id is null;
    perform t.complete_lesson(v_id);
    n := n + 1;
    if n > 20 then raise exception 'FAIL: get_next_lesson no avanza'; end if;
  end loop;
end $$;
select t.ok(count(*) = 0, 'con las 10 lecciones superadas no quedan pendientes') from get_next_lesson();
select t.ok(count(*) = 10, 'las 10 lecciones quedaron superadas')
  from user_lesson_progress where completed_at is not null and user_id = :'B';
select t.ok(unlocked_at is not null, 'se desbloquea "Ruta completa" al terminar las 10')
  from get_achievements() where code = 'all_lessons';

\echo '--- 4. Seguridad'
select t.as_user(:'A');
select t.ok(lesson_slug = 'home_rooms', 'cada estudiante ve su propio avance (A sigue en Hogar)')
  from get_next_lesson();
reset role;
set role anon;
select t.as_user('');
select t.throws('select * from public.get_next_lesson()', 'permission denied%', 'anónimo no puede llamar get_next_lesson');
reset role;

\echo '=== TODAS LAS PRUEBAS (HOGAR/TRANSPORTE) PASARON ==='
rollback;
