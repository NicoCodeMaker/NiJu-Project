-- =============================================================================
-- NiJu · Pruebas de la base de datos
-- Se ejecutan dentro de una transacción que se revierte al final: no dejan datos.
-- Uso local:  psql -d niju -v ON_ERROR_STOP=1 -f tests/01_niju_tests.sql
-- Cada comprobación imprime "ok - ..." o detiene el script con "FAIL: ...".
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
  if not coalesce(p_cond, false) then
    raise exception 'FAIL: %', p_msg;
  end if;
  return 'ok - ' || p_msg;
end $$;

create function t.throws(p_sql text, p_like text, p_msg text) returns text
language plpgsql as $$
begin
  begin
    execute p_sql;
  exception when others then
    if sqlerrm like p_like then
      return 'ok - ' || p_msg;
    end if;
    raise exception 'FAIL: % (error inesperado: %)', p_msg, sqlerrm;
  end;
  raise exception 'FAIL: % (no lanzó error)', p_msg;
end $$;

-- Respuesta correcta de un ejercicio (lo usa la prueba, no la app).
create function t.answer_for(p_item bigint) returns text
language sql stable as $$
  select case when i.exercise_type = 'en_to_es' then w.spanish else w.english end
    from public.session_items i join public.words w on w.id = i.word_id
   where i.id = p_item
$$;

create function t.as_user(p_uid text) returns void
language sql as $$ select set_config('request.jwt.claim.sub', p_uid, true) $$;

grant execute on all functions in schema t to anon, authenticated;
grant select on public.session_items, public.words to authenticated;

\set A '00000000-0000-0000-0000-00000000000a'
\set B '00000000-0000-0000-0000-00000000000b'

\echo '--- 1. Funciones puras: normalización, SM-2, niveles, dominio'
select t.ok(niju_private.normalize_answer('  ¡Menú! ') = 'menu', 'normaliza mayúsculas, tildes y signos');
select t.ok(niju_private.normalize_answer('check   in') = 'check in', 'colapsa espacios');
select t.ok(s.interval_days = 1 and s.repetitions = 1 and s.ease_factor = 2.50,
            'SM-2: primer acierto -> 1 día') from niju_private.sm2_next(2.50, 0, 0, true) s;
select t.ok(s.interval_days = 6 and s.repetitions = 2, 'SM-2: segundo acierto -> 6 días')
  from niju_private.sm2_next(2.50, 1, 1, true) s;
select t.ok(s.interval_days = 15 and s.repetitions = 3, 'SM-2: tercer acierto -> 15 días')
  from niju_private.sm2_next(2.50, 6, 2, true) s;
select t.ok(s.interval_days = 38, 'SM-2: cuarto acierto -> 38 días (dominada)')
  from niju_private.sm2_next(2.50, 15, 3, true) s;
select t.ok(s.interval_days = 1 and s.repetitions = 0 and s.ease_factor = 2.18,
            'SM-2: fallo reinicia a 1 día y baja el factor 0.32')
  from niju_private.sm2_next(2.50, 15, 3, false) s;
select t.ok(s.ease_factor = 1.30, 'SM-2: el factor nunca baja de 1.30')
  from niju_private.sm2_next(1.40, 1, 0, false) s;
select t.ok(level_for_xp(0) = 1 and level_for_xp(99) = 1 and level_for_xp(100) = 2
            and level_for_xp(399) = 2 and level_for_xp(400) = 3 and xp_for_level(4) = 900,
            'niveles: 100*(n-1)^2 XP');
select t.ok(mastery_status(null, 0) = 'new' and mastery_status(1, 1) = 'learning'
            and mastery_status(6, 2) = 'review' and mastery_status(21, 4) = 'mastered',
            'estados de dominio');

\echo '--- 2. Registro: perfil y estadísticas automáticos'
insert into auth.users (id, email, raw_user_meta_data) values
  (:'A', 'ana@example.com', '{"name": "Ana"}'),
  (:'B', 'beto@example.com', '{}');
select t.ok((select display_name from profiles where id = :'A') = 'Ana', 'perfil creado con el nombre');
select t.ok((select count(*) from user_stats where user_id in (:'A', :'B')) = 2, 'estadísticas creadas');

\echo '--- 3. Integridad del contenido'
select t.ok(count(*) = 0, 'cada palabra de lección tiene un ejemplo que la contiene')
  from lesson_words lw join words w on w.id = lw.word_id
 where not exists (select 1 from word_examples e where e.word_id = w.id
                    and e.sentence_en ~* ('\m' || w.english || '\M'));
select t.ok(min(c) >= 3, 'cada lección tiene al menos 3 palabras')
  from (select count(*) c from lesson_words group by lesson_id) x;
insert into words (english, spanish) values ('zzzword', 'palabra de prueba');
select t.ok(e.exercise_type = 'es_to_en', 'sin ejemplo, "completar" cambia a es_to_en')
  from words w, niju_private.build_exercise(w.id, 'fill_blank', null) e where w.english = 'zzzword';
delete from words where english = 'zzzword';

\echo '--- 4. Seguridad (RLS y permisos) como usuario A'
set role authenticated;
select t.as_user(:'A');
select t.ok((select count(*) from contexts) = 3, 'el contenido es legible');
select t.ok((select count(*) from profiles) = 1, 'solo ve su propio perfil');
select t.throws('insert into public.user_word_progress (user_id, word_id) values (auth.uid(), 1)',
                'permission denied%', 'no puede escribir progreso directamente');
select t.throws('update public.user_stats set xp_total = 9999',
                'permission denied%', 'no puede regalarse XP');
select t.throws('update public.profiles set id = gen_random_uuid()',
                'permission denied%', 'no puede cambiar el id del perfil');
select t.throws('update public.profiles set timezone = ''Marte/Base''',
                'invalid_timezone%', 'rechaza zonas horarias inválidas');
select t.throws('select niju_private.normalize_answer(''x'')',
                'permission denied%', 'las funciones internas no son accesibles');
update profiles set display_name = 'Ana María';
select t.ok((select display_name from profiles) = 'Ana María', 'puede editar su nombre');

\echo '--- 5. Sesión de lección'
select (start_session('lesson', (select id from lessons where slug = 'restaurant_table')))
       ->> 'session_id' as sid \gset
select set_config('t.sid', :'sid', true);
select t.ok(count(*) = 6, 'la lección genera 6 ejercicios') from session_items where session_id = :'sid';
select t.ok(bool_and(cardinality(options) = 3 and t.answer_for(id) = any(options)),
            'cada ejercicio tiene 3 opciones e incluye la correcta')
  from session_items where session_id = :'sid';
select t.ok(array_agg(exercise_type::text order by position)
            = array['en_to_es','es_to_en','fill_blank','en_to_es','es_to_en','fill_blank'],
            'los tipos rotan en la lección')
  from session_items where session_id = :'sid';
select t.ok(bool_and(prompt like '%\_\_\_\_\_%' and hint is not null),
            '"completar" muestra el hueco y la traducción')
  from session_items where session_id = :'sid' and exercise_type = 'fill_blank';

-- Responde 5 bien y 1 mal (la posición 2).
do $$
declare r record; res jsonb;
begin
  for r in select id, position from public.session_items
            where session_id = current_setting('t.sid')::uuid order by position loop
    res := public.submit_answer(r.id, case when r.position = 2 then 'respuesta mala'
                                           else t.answer_for(r.id) end, 1500);
    if (res ->> 'is_correct')::boolean <> (r.position <> 2) then
      raise exception 'FAIL: corrección inesperada en la posición %', r.position;
    end if;
  end loop;
end $$;
select 'ok - submit_answer corrige en el servidor';

select t.ok((select xp_total from user_stats) = 50, '5 aciertos en palabras nuevas = 50 XP');
select t.ok(count(*) = 6 and count(*) filter (where interval_days = 1) = 6,
            'las 6 palabras quedan programadas a 1 día')
  from user_word_progress;
select t.ok(lapses = 1 and incorrect_count = 1 and repetitions = 0, 'el fallo queda registrado')
  from user_word_progress p join session_items i on i.word_id = p.word_id
 where i.session_id = :'sid' and i.position = 2;
select t.throws(format('select public.submit_answer(%s, ''x'')',
                       (select id from session_items where session_id = :'sid' and position = 1)),
                'item_already_answered%', 'no se puede responder dos veces');

select finish_session(:'sid') as fin \gset
select t.ok((:'fin'::jsonb ->> 'lesson_completed')::boolean, 'lección superada con 83 %');
select t.ok(:'fin'::jsonb -> 'new_achievements' @> '[{"code": "first_lesson"}]', 'logro "Primer paso"');
select t.ok((:'fin'::jsonb ->> 'xp_earned')::int = 120, 'XP de la sesión: 50 + 50 bono + 20 logro');
select t.ok((select xp_total from user_stats) = 120 and (:'fin'::jsonb ->> 'level')::int = 2, 'nivel 2 con 120 XP');
select finish_session(:'sid') as fin2 \gset
select t.ok(not (:'fin2'::jsonb ->> 'lesson_completed')::boolean
            and (select xp_total from user_stats) = 120, 'cerrar dos veces no duplica el bono');
select t.ok(current_streak = 1 and last_study_date = (now() at time zone 'America/Bogota')::date,
            'racha = 1 el primer día') from user_stats;

\echo '--- 6. Aislamiento entre usuarios'
select t.as_user(:'B');
select t.ok((select count(*) from session_items) = 0 and (select count(*) from attempts) = 0,
            'B no ve las sesiones ni intentos de A');
select t.throws(format('select public.submit_answer(%s, ''x'')',
                       (select min(id) from public.words)),   -- un id cualquiera
                'item_not_found%', 'B no puede responder ejercicios ajenos');
select t.throws(format('select public.finish_session(%L)', :'sid'),
                'session_not_found%', 'B no puede cerrar sesiones ajenas');
select t.as_user(:'A');

\echo '--- 7. Práctica libre: no infla el calendario ni regala XP repetido'
select (start_session('practice', null, (select id from contexts where slug = 'restaurant'), 20))
       ->> 'session_id' as p1 \gset
select t.ok(count(*) = 12, 'práctica del contexto: 12 palabras') from session_items where session_id = :'p1';
select set_config('t.sid', :'p1', true);
do $$ declare r record; begin
  for r in select id from public.session_items where session_id = current_setting('t.sid')::uuid loop
    perform public.submit_answer(r.id, t.answer_for(r.id));
  end loop; end $$;
select t.ok((select xp_total from user_stats) = 120 + 6*2 + 6*10,
            '6 palabras ya vistas = 2 XP c/u; 6 nuevas = 10 XP c/u');
select t.ok(count(*) filter (where interval_days = 1) = 12,
            'acertar antes de tiempo no adelanta el repaso') from user_word_progress;
select finish_session(:'p1') \gset
select (start_session('practice', null, (select id from contexts where slug = 'restaurant'), 20))
       ->> 'session_id' as p2 \gset
select set_config('t.sid', :'p2', true);
do $$ declare r record; begin
  for r in select id from public.session_items where session_id = current_setting('t.sid')::uuid loop
    perform public.submit_answer(r.id, t.answer_for(r.id));
  end loop; end $$;
select t.ok((select xp_total from user_stats) = 192 + 6*2,
            'repetir la práctica el mismo día solo da XP por palabras aún no premiadas hoy');
select finish_session(:'p2') \gset

\echo '--- 8. Repaso programado (SM-2)'
reset role;
update user_word_progress set due_at = now() - interval '1 hour'
 where user_id = :'A' and word_id in (select word_id from lesson_words lw join lessons l on l.id = lw.lesson_id
                                       where l.slug = 'restaurant_table');
set role authenticated;
select t.as_user(:'A');
select t.ok((get_dashboard() ->> 'due_reviews')::int = 6, 'el inicio muestra 6 repasos pendientes');
select (start_session('review')) ->> 'session_id' as r1 \gset
select t.ok(count(*) = 6, 'el repaso incluye solo las vencidas') from session_items where session_id = :'r1';
select set_config('t.sid', :'r1', true);
do $$ declare r record; begin
  for r in select id from public.session_items where session_id = current_setting('t.sid')::uuid loop
    perform public.submit_answer(r.id, t.answer_for(r.id));
  end loop; end $$;
select t.ok(count(*) filter (where interval_days = 6) = 5 and count(*) filter (where interval_days = 1) = 6 + 1,
            'acierto en repaso: 1 -> 6 días (la fallada vuelve a 1 día)')
  from user_word_progress;
select t.ok((select correct_reviews from user_stats) = 6, 'repasos acertados contados');
select finish_session(:'r1') \gset
select t.throws('select public.start_session(''review'')', 'no_due_reviews%',
                'sin repasos vencidos la app recibe "no_due_reviews"');

\echo '--- 9. Racha por días calendario'
reset role;
update user_stats set last_study_date = (now() at time zone 'America/Bogota')::date - 1,
                      current_streak = 4, longest_streak = 4 where user_id = :'A';
set role authenticated;
select t.as_user(:'A');
select (start_session('practice', null, null, 1)) ->> 'session_id' as s9 \gset
select submit_answer((select id from session_items where session_id = :'s9'), 'x') \gset
select t.ok(current_streak = 5 and longest_streak = 5, 'estudiar al día siguiente suma 1') from user_stats;
select finish_session(:'s9') as f9 \gset
select t.ok(:'f9'::jsonb -> 'new_achievements' @> '[{"code": "streak_3"}]', 'logro "Constancia" (3 días)');
reset role;
update user_stats set last_study_date = (now() at time zone 'America/Bogota')::date - 3 where user_id = :'A';
set role authenticated;
select t.as_user(:'A');
select t.ok((get_dashboard() ->> 'current_streak')::int = 0, 'tras 2 días sin estudiar se muestra 0');
select (start_session('practice', null, null, 1)) ->> 'session_id' as s10 \gset
select submit_answer((select id from session_items where session_id = :'s10'), 'x') \gset
select t.ok(current_streak = 1 and longest_streak = 5, 'la racha se reinicia; el récord se conserva') from user_stats;
select finish_session(:'s10') \gset

\echo '--- 10. Estadísticas y progreso'
reset role;
update user_word_progress set interval_days = 21
 where user_id = :'A' and word_id in (select word_id from user_word_progress where user_id = :'A' limit 10);
update user_word_progress set lapses = 3
 where user_id = :'A' and word_id = (select min(word_id) from user_word_progress where user_id = :'A');
set role authenticated;
select t.as_user(:'A');
select get_dashboard() as d \gset
select t.ok((:'d'::jsonb ->> 'words_mastered')::int = 10, 'dashboard: 10 dominadas');
select t.ok((:'d'::jsonb ->> 'words_practiced')::int >= 12, 'dashboard: practicadas');
select t.ok((:'d'::jsonb ->> 'words_seen')::int >= (:'d'::jsonb ->> 'words_practiced')::int,
            'vistas >= practicadas >= dominadas');
select t.ok((:'d'::jsonb ->> 'xp_today')::int = (select xp_total from user_stats), 'XP de hoy = suma del libro de puntos');
select t.ok(lessons_completed = 1 and lessons_total = 2 and percent_complete = 50,
            'Ruta: Restaurante al 50 %') from get_context_progress() where slug = 'restaurant';
select t.ok(array_agg(unlocked order by sort_order) = array[true, true],
            'Restaurante: lección 2 desbloqueada') from get_context_lessons((select id from contexts where slug = 'restaurant'));
select t.ok(array_agg(unlocked order by sort_order) = array[true, false],
            'Supermercado: lección 2 bloqueada') from get_context_lessons((select id from contexts where slug = 'supermarket'));
select t.ok(count(*) = 7 and max(answers) > 0, 'actividad de 7 días con datos de hoy') from get_daily_activity(7);
select t.ok(count(*) >= 1, 'palabras que requieren refuerzo') from get_struggling_words();
select (start_session('practice', null, null, 1)) ->> 'session_id' as s11 \gset
select finish_session(:'s11') as f11 \gset
select t.ok(:'f11'::jsonb -> 'new_achievements' @> '[{"code": "mastered_10"}]', 'logro "Vocabulario firme"');
select t.ok(reset_progress((select min(word_id) from user_word_progress)) = 1, 'reiniciar una palabra');

\echo '--- 11. Usuario anónimo'
reset role;
set role anon;
select t.as_user('');
select t.ok((select count(*) from contexts) = 3, 'anónimo ve el catálogo');
select t.throws('select public.start_session(''practice'')', 'permission denied%', 'anónimo no puede iniciar sesiones');
select t.throws('select * from public.profiles', 'permission denied%', 'anónimo no ve perfiles');

\echo '--- 12. Eliminar cuenta'
reset role;
set role authenticated;
select t.as_user(:'A');
select delete_my_account() \gset
reset role;
select t.ok((select count(*) from profiles where id = :'A') = 0
        and (select count(*) from attempts where user_id = :'A') = 0
        and (select count(*) from xp_ledger where user_id = :'A') = 0
        and (select count(*) from auth.users) = 1, 'la cuenta y todos sus datos se eliminan');

\echo '--- 13. Lecciones: contenido y bloqueo (usuario B)'
set role authenticated;
select t.as_user(:'B');
select t.ok(count(*) = 6 and bool_and(sentence_en ~* ('\m' || english || '\M')) and bool_and(status = 'new')
            and bool_and(not is_favorite), 'tarjetas de la lección 1 con ejemplo y estado "new"')
  from get_lesson_content((select id from lessons where slug = 'restaurant_table'));
select t.throws(format('select public.start_session(''lesson'', %s)', (select id from lessons where slug = 'restaurant_pay')),
                'lesson_locked%', 'no se puede saltar a la lección 2');
select t.throws(format('select * from public.get_lesson_content(%s)', (select id from lessons where slug = 'restaurant_pay')),
                'lesson_locked%', 'tampoco se ven sus tarjetas');
select t.throws('select * from public.get_lesson_content(999999)', 'lesson_not_found%', 'lección inexistente');

\echo '--- 14. Favoritos'
select t.ok(toggle_favorite((select id from words where english = 'menu')), 'marcar favorita');
select t.ok(not toggle_favorite((select id from words where english = 'menu')), 'desmarcar favorita');
select t.ok(toggle_favorite((select id from words where english = 'menu')), 'marcar de nuevo');
select t.ok(count(*) = 1 and min(english) = 'menu' and min(sentence_en) is not null, 'lista de favoritas') from get_favorites();
select t.ok(bool_or(is_favorite), 'la tarjeta muestra la estrella')
  from get_lesson_content((select id from lessons where slug = 'restaurant_table'));
select t.throws('insert into public.user_favorite_words (user_id, word_id) values (auth.uid(), 2)',
                'permission denied%', 'no se escriben favoritas directamente');
select t.throws('select public.toggle_favorite(999999)', 'word_not_found%', 'palabra inexistente');

\echo '--- 15. Logros con avance'
select t.ok(count(*) = 6 and bool_and(unlocked_at is null) and max(current_value) = 0, 'B sin logros todavía') from get_achievements();
select (start_session('lesson', (select id from lessons where slug = 'restaurant_table'))) ->> 'session_id' as b1 \gset
select set_config('t.sid', :'b1', true);
do $$ declare r record; begin
  for r in select id from public.session_items where session_id = current_setting('t.sid')::uuid loop
    perform public.submit_answer(r.id, t.answer_for(r.id));
  end loop; end $$;
select finish_session(:'b1') \gset
select t.ok(code = 'first_lesson' and unlocked_at is not null and current_value = 1,
            'el logro desbloqueado aparece primero') from get_achievements() limit 1;
select t.ok(current_value = 1 and threshold = 6, 'avance parcial de "Ruta completa"') from get_achievements() where code = 'all_lessons';
select t.ok((start_session('lesson', (select id from lessons where slug = 'restaurant_pay'))) ? 'session_id',
            'al superar la lección 1 se abre la 2');

\echo '--- 16. Anónimo y nuevas funciones'
reset role;
set role anon;
select t.as_user('');
select t.throws('select * from public.get_favorites()', 'permission denied%', 'anónimo no ve favoritas');
select t.throws('select * from public.get_achievements()', 'permission denied%', 'anónimo no ve logros');
reset role;

\echo '=== TODAS LAS PRUEBAS PASARON ==='
rollback;
