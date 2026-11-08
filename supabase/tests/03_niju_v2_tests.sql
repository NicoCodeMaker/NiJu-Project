-- =============================================================================
-- NiJu · Pruebas de la v2 (migraciones 5 y 6 + seed_v2_contexts.sql)
-- Requiere: stub, migraciones 1–6, seed.sql, seed_home_transport.sql, seed_v2_contexts.sql.
-- Todo ocurre en una transacción que se revierte. Imprime "ok - ..." o se detiene con "FAIL".
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
  begin
    execute p_sql;
  exception when others then
    if sqlerrm like p_like then return 'ok - ' || p_msg; end if;
    raise exception 'FAIL: % (error inesperado: %)', p_msg, sqlerrm;
  end;
  raise exception 'FAIL: % (no lanzó error)', p_msg;
end $$;

create function t.answer_for(p_item bigint) returns text
language sql stable security definer as $$
  select coalesce(i.answer_key, case when i.exercise_type = 'en_to_es' then w.spanish else w.english end)
    from public.session_items i join public.words w on w.id = i.word_id where i.id = p_item
$$;

create function t.as_user(p_uid text) returns void
language sql as $$ select set_config('request.jwt.claim.sub', p_uid, true) $$;

-- Responde todos los ejercicios de una sesión (bien, o mal en las posiciones indicadas).
create function t.answer_all(p_sid uuid, p_wrong integer[] default '{}', p_ms integer default 2000)
returns integer language plpgsql as $$
declare r record; n integer := 0;
begin
  for r in select id, position from public.session_items where session_id = p_sid order by position loop
    perform public.submit_answer(r.id,
      case when r.position = any(p_wrong) then 'respuesta equivocada' else t.answer_for(r.id) end, p_ms);
    n := n + 1;
  end loop;
  return n;
end $$;

grant execute on all functions in schema t to anon, authenticated;
grant select on public.words to authenticated;

-- Los retos se controlan en la sección 8: aquí se apagan para que el XP sea predecible.
update public.challenge_templates set is_active = false;

\set A '00000000-0000-0000-0000-0000000000a1'
\set B '00000000-0000-0000-0000-0000000000b1'
insert into auth.users (id, email, raw_user_meta_data) values
  (:'A', 'ana@niju.test', '{"full_name": "Ana Gómez"}'),
  (:'B', 'beto@niju.test', '{}');

\echo '--- 1. Contenido: 12 contextos'
select t.ok(count(*) = 12, '12 contextos publicados') from contexts where is_published;
select t.ok(array_agg(slug order by sort_order) = array['home','school','beach','restaurant','airport',
            'supermarket','shopping','health','transport','technology','work','travel'],
            'orden del catálogo según el plan') from contexts;
select t.ok(count(*) = 24 and bool_and(level in ('A1', 'A2')), '24 lecciones (A1 y A2)') from lessons;
select t.ok(count(*) = 144, '144 palabras') from words;
select t.ok(min(c) = 2, 'cada contexto tiene 2 lecciones')
  from (select count(*) c from lessons group by context_id) x;
select t.ok(count(*) = 0, 'cada palabra de lección tiene un ejemplo que la contiene')
  from lesson_words lw join words w on w.id = lw.word_id
 where not exists (select 1 from word_examples e where e.word_id = w.id
                    and e.sentence_en ~* niju_private.word_pattern(w.english));
select t.ok(count(*) = count(distinct emoji) and count(*) >= 80, 'emojis únicos (relacionar sin ambigüedad)')
  from words where emoji is not null;
select t.ok(count(*) = 84 and bool_and(d.line_b_en ~* niju_private.word_pattern(w.english)),
            '84 diálogos y cada línea B contiene su palabra')
  from word_dialogues d join words w on w.id = d.word_id;
select t.ok(threshold = 24, '"Ruta completa" = 24 lecciones') from achievements where code = 'all_lessons';
select t.ok(count(*) = 20, '20 logros') from achievements;

\echo '--- 2. Reglas puras'
select t.ok(niju_private.answer_quality('en_to_es', true, 1500) = 5
        and niju_private.answer_quality('en_to_es', true, 9000) = 4
        and niju_private.answer_quality('en_to_es', true, 20000) = 3
        and niju_private.answer_quality('en_to_es', true, null) = 4
        and niju_private.answer_quality('en_to_es', false, 1000) = 2
        and niju_private.answer_quality('type_answer', true, 9000) = 5
        and niju_private.answer_quality('type_answer', false, 9000) = 1,
            'calidad SM-2 por acierto, tipo y tiempo');
select t.ok(s.ease_factor = 2.60 and s.interval_days = 1, 'SM-2 q=5: factor +0.10')
  from niju_private.sm2_next_q(2.50, 0, 0, 5) s;
select t.ok(s.ease_factor = 2.36 and s.interval_days = 1 and s.repetitions = 1, 'SM-2 q=3: acierto lento, factor −0.14')
  from niju_private.sm2_next_q(2.50, 0, 0, 3) s;
select t.ok(s.ease_factor = 1.96 and s.repetitions = 0 and s.interval_days = 1, 'SM-2 q=1: fallo en producción, factor −0.54')
  from niju_private.sm2_next_q(2.50, 15, 3, 1) s;
select t.ok(s.interval_days = 16, 'SM-2: el intervalo usa el nuevo factor (6 × 2.6 = 16)')
  from niju_private.sm2_next_q(2.50, 6, 2, 5) s;
select t.ok(s.ease_factor = 2.18 and s.interval_days = 1, 'la versión binaria v1 se conserva')
  from niju_private.sm2_next(2.50, 15, 3, false) s;
select t.ok(word_bucket(null, 0, 0, 0, 0) = 'new' and word_bucket(1, 1, 0, 1, 0) = 'learning'
        and word_bucket(6, 2, 0, 2, 0) = 'review' and word_bucket(21, 4, 3, 4, 3) = 'mastered'
        and word_bucket(1, 0, 3, 2, 3) = 'reinforce' and word_bucket(1, 0, 2, 1, 2) = 'reinforce',
            'estados: nueva, aprendiendo, en repaso, dominada, refuerzo');
select t.ok(level_title(1) = 'Principiante' and level_title(3) = 'Explorador' and level_title(20) = 'Leyenda',
            'títulos de nivel');
select t.ok(streak_bonus_for(3) = 15 and streak_bonus_for(7) = 40 and streak_bonus_for(4) = 0, 'bonos de racha');
select t.ok(niju_private.tokens('Excuse me, this is my seat.') = array['Excuse','me','this','is','my','seat']
        and niju_private.tokens('The arrival time is six o''clock.') = array['The','arrival','time','is','six','o''clock'],
            'división en palabras para ordenar');

\echo '--- 3. Generador: cada tipo es válido y su respuesta es comprobable'
do $$
declare
  v_word integer := (select id from public.words where english = 'backpack');
  v_ctx  smallint := (select id from public.contexts where slug = 'school');
  v_type text;
  e record;
begin
  foreach v_type in array array['en_to_es','es_to_en','fill_blank','image_match','listen_choose',
                                'type_answer','word_order','spot_error','dialogue'] loop
    for i in 1..5 loop   -- varias veces: hay azar
      select * into e from niju_private.build_exercise(v_word, v_type::public.exercise_type, v_ctx, true);
      if e.exercise_type::text <> v_type then
        raise exception 'FAIL: % cambió a % sin necesidad', v_type, e.exercise_type; end if;
      if v_type in ('en_to_es','es_to_en','fill_blank','image_match','listen_choose','dialogue','spot_error')
         and not (e.answer_key = any(e.options)) then
        raise exception 'FAIL: % no incluye la respuesta en las opciones', v_type; end if;
      if v_type = 'type_answer' and (cardinality(e.options) <> 0 or e.answer_key <> 'backpack') then
        raise exception 'FAIL: escribir mal formado'; end if;
      if v_type = 'word_order' and (niju_private.normalize_answer(array_to_string(
           (select array_agg(x order by x) from unnest(e.options) x), ' '))
           <> niju_private.normalize_answer(array_to_string(
           (select array_agg(x order by x) from unnest(niju_private.tokens(e.answer_key)) x), ' '))
           or array_to_string(e.options, ' ') = array_to_string(niju_private.tokens(e.answer_key), ' ')) then
        raise exception 'FAIL: ordenar no es una permutación mezclada de la oración'; end if;
      if v_type = 'spot_error' and (e.prompt = e.solution or position(e.answer_key in e.prompt) = 0
           or e.solution ~* niju_private.word_pattern(e.answer_key)) then
        raise exception 'FAIL: identificar el error mal formado: % / %', e.prompt, e.answer_key; end if;
      if v_type = 'dialogue' and (e.prompt not like 'A: %' or e.prompt not like '%_____%') then
        raise exception 'FAIL: diálogo mal formado'; end if;
      if v_type = 'image_match' and e.prompt <> '🎒' then
        raise exception 'FAIL: la imagen no es la de la palabra'; end if;
    end loop;
  end loop;
end $$;
select 'ok - los 9 tipos se generan con respuesta verificable';
select t.ok(e.exercise_type = 'es_to_en', 'sin emoji, "imagen" usa es_to_en')
  from niju_private.build_exercise((select id from words where english = 'menu'), 'image_match', null) e;
select t.ok(e.exercise_type = 'fill_blank', 'sin diálogo, "conversación" usa completar')
  from niju_private.build_exercise((select id from words where english = 'menu'), 'dialogue', null) e;
select t.ok(e.hint is null, 'en evaluación no hay pistas')
  from niju_private.build_exercise((select id from words where english = 'kitchen'), 'en_to_es', null, false) e;

\echo '--- 4. Lección v2 como usuario A'
set role authenticated;
select t.as_user(:'A');
select (start_session('lesson', (select id from lessons where slug = 'school_classroom'))) as s1 \gset
select t.ok(jsonb_array_length(:'s1'::jsonb -> 'items') = 6, 'la lección trae 6 ejercicios');
select t.ok(not (:'s1'::jsonb::text like '%answer_key%') and not (:'s1'::jsonb::text like '%solution%'),
            'la respuesta no viaja a la app');
select :'s1'::jsonb ->> 'session_id' as sid1 \gset
select t.ok(array_agg(exercise_type::text order by position)
            = array['en_to_es','image_match','listen_choose','fill_blank','type_answer','word_order'],
            'primer intento: de reconocer a producir (A)') from session_items where session_id = :'sid1';
select t.throws('select answer_key from public.session_items limit 1', 'permission denied%',
                'la app no puede leer la respuesta guardada');
select t.ok(t.answer_all(:'sid1', '{}', 1500) = 6, 'responde los 6 tipos');
select t.ok(count(*) = 6 and bool_and(is_correct) and bool_and(quality = 5), 'todas correctas y rápidas: calidad 5')
  from attempts where user_id = :'A';
select t.ok(bool_and(ease_factor = 2.60) and bool_and(last_quality = 5), 'factor 2.60 tras respuesta segura')
  from user_word_progress where user_id = :'A';
select finish_session(:'sid1') as f1 \gset
select t.ok((:'f1'::jsonb ->> 'lesson_completed')::boolean and (:'f1'::jsonb ->> 'perfect')::boolean,
            'lección superada y sesión perfecta');
select t.ok(:'f1'::jsonb -> 'new_achievements' @> '[{"code": "first_lesson"}, {"code": "perfect_1"}]',
            'logros "Primer paso" y "Sin errores"');
select t.ok((:'f1'::jsonb ->> 'previous_level')::int = 1 and (:'f1'::jsonb ->> 'level')::int = 2
            and :'f1'::jsonb ->> 'level_title' = 'Principiante', 'sube del nivel 1 al 2 (para la animación)');
select t.ok((:'f1'::jsonb ->> 'study_ms')::int = 9000, 'tiempo de estudio de la sesión (6 × 1,5 s)');

-- Segundo intento de la misma lección: otra secuencia (B).
select (start_session('lesson', (select id from lessons where slug = 'school_classroom'))) ->> 'session_id' as sid2 \gset
select t.ok(array_agg(exercise_type::text order by position)
            = array['es_to_en','listen_choose','dialogue','spot_error','image_match','type_answer'],
            'segundo intento: otra secuencia (B) para no repetir') from session_items where session_id = :'sid2';
-- Falla la de escribir (posición 6, producción → calidad 1).
select t.ok(t.answer_all(:'sid2', '{6}', 1500) = 6, 'responde el segundo intento');
select t.ok(a.quality = 1 and not a.is_correct, 'fallo al escribir: calidad 1')
  from attempts a join session_items i on i.id = a.session_item_id
 where i.session_id = :'sid2' and i.position = 6;
select finish_session(:'sid2') \gset

\echo '--- 5. Repaso: estados, urgentes y refuerzo'
reset role;
update user_word_progress set due_at = now() - interval '3 days' where user_id = :'A'
   and word_id in (select id from words where english in ('teacher', 'student'));
update user_word_progress set lapses = 3, due_at = now() + interval '2 days' where user_id = :'A'
   and word_id = (select id from words where english = 'pencil');
set role authenticated;
select t.as_user(:'A');
select get_review_overview() as ov \gset
select t.ok((:'ov'::jsonb ->> 'total')::int = 6 and (:'ov'::jsonb ->> 'due_now')::int = 2
        and (:'ov'::jsonb ->> 'urgent')::int = 2 and (:'ov'::jsonb ->> 'reinforce')::int >= 1,
            'resumen: 6 palabras, 2 vencidas y urgentes, refuerzo');
select t.ok(jsonb_array_length(:'ov'::jsonb -> 'forecast') = 7
        and (:'ov'::jsonb -> 'forecast' -> 0 ->> 'count')::int = 2, 'pronóstico de 7 días (hoy incluye atrasadas)');
select t.ok(count(*) = 2 and bool_and(urgent), 'lista de urgentes') from get_review_words('urgent');
select t.ok(count(*) >= 1 and bool_and(bucket = 'reinforce'), 'lista de refuerzo') from get_review_words('reinforce');
select (start_session('review', null, null, 10, 'reinforce')) ->> 'session_id' as rf \gset
select t.ok(count(*) >= 1, 'repaso enfocado en palabras difíciles') from session_items where session_id = :'rf';
select finish_session(:'rf') \gset
select (start_session('review')) ->> 'session_id' as rv \gset
select t.ok(count(*) = 2, 'el repaso trae las 2 vencidas') from session_items where session_id = :'rv';
select t.ok(t.answer_all(:'rv', '{}', null) = 2, 'responde el repaso');
select t.ok(bool_and(p.interval_days = 6 and p.last_quality = 4), 'repaso acertado sin tiempo: calidad 4, 1 → 6 días')
  from user_word_progress p join words w on w.id = p.word_id where w.english in ('teacher', 'student');
select finish_session(:'rv') \gset
select get_word_detail((select id from words where english = 'teacher')) as wd \gset
select t.ok(jsonb_array_length(:'wd'::jsonb -> 'history') >= 2 and :'wd'::jsonb ->> 'bucket' = 'review'
        and (:'wd'::jsonb ->> 'interval_days')::int = 6 and :'wd'::jsonb ->> 'emoji' = '🧑‍🏫',
            'detalle de palabra con historial');

\echo '--- 6. Práctica de favoritas'
select t.throws('select public.start_session(''practice'', null, null, 10, ''favorites'')', 'no_favorites%',
                'sin favoritas: mensaje claro');
select toggle_favorite((select id from words where english = 'pencil')) \gset
select (start_session('practice', null, null, 10, 'favorites')) ->> 'session_id' as fv \gset
select t.ok(count(*) = 1 and min(word_id) = (select id from words where english = 'pencil'),
            'práctica solo con favoritas') from session_items where session_id = :'fv';
select finish_session(:'fv') \gset

\echo '--- 7. Bono de racha (una vez por racha)'
reset role;
update user_stats set last_study_date = (now() at time zone 'America/Bogota')::date - 1,
                      current_streak = 2, longest_streak = 2 where user_id = :'A';
set role authenticated;
select t.as_user(:'A');
select (start_session('practice', null, null, 2)) ->> 'session_id' as st \gset
select (submit_answer((select min(id) from session_items where session_id = :'st'), 'x')) as sa \gset
select t.ok((:'sa'::jsonb ->> 'streak_bonus')::int = 15, 'al llegar a 3 días: +15 XP');
select (submit_answer((select max(id) from session_items where session_id = :'st'), 'x')) as sb \gset
select t.ok((:'sb'::jsonb ->> 'streak_bonus')::int = 0, 'el mismo día no se repite');
select finish_session(:'st') \gset
reset role;
select t.ok(count(*) = 1, 'un solo bono en el libro de puntos') from xp_ledger where user_id = :'A' and reason = 'streak_bonus';
-- Racha nueva (otro inicio) que vuelve a llegar a 3: se premia de nuevo.
update user_stats set last_study_date = (now() at time zone 'America/Bogota')::date - 1,
                      current_streak = 2 where user_id = :'A';
update xp_ledger set ref = '3:2000-01-01' where user_id = :'A' and reason = 'streak_bonus';
set role authenticated;
select t.as_user(:'A');
select (start_session('practice', null, null, 1)) ->> 'session_id' as st2 \gset
select t.ok((submit_answer((select id from session_items where session_id = :'st2'), 'x') ->> 'streak_bonus')::int = 15,
            'una racha distinta se premia otra vez');
select finish_session(:'st2') \gset

\echo '--- 8. Retos diarios y metas semanales'
reset role;
update challenge_templates set is_active = (code in ('d_answers_20', 'w_lessons_3'));
set role authenticated;
select t.as_user(:'B');
select t.ok(jsonb_array_length(get_rewards() -> 'challenges') = 2, 'B ve 1 reto diario y 1 meta semanal');
select (start_session('practice', null, (select id from contexts where slug = 'school'), 12)) ->> 'session_id' as c1 \gset
select t.ok(t.answer_all(:'c1') = 12, '12 respuestas');
select finish_session(:'c1') as fc1 \gset
select t.ok(jsonb_array_length(:'fc1'::jsonb -> 'challenges_completed') = 0, 'con 12 de 20 aún no se cumple');
select t.ok((get_rewards() -> 'challenges' -> 0 ->> 'progress')::int = 12, 'avance visible: 12 / 20');
select (start_session('practice', null, (select id from contexts where slug = 'school'), 12)) ->> 'session_id' as c2 \gset
select t.ok(t.answer_all(:'c2') = 12, '12 respuestas más');
select finish_session(:'c2') as fc2 \gset
select t.ok(:'fc2'::jsonb -> 'challenges_completed' @> '[{"code": "d_answers_20", "xp_reward": 20}]',
            'reto cumplido y cobrado al cerrar la sesión');
select t.ok(jsonb_array_length(claim_rewards() -> 'challenges_completed') = 0, 'no se cobra dos veces');
select finish_session(:'c2') as fc3 \gset
select t.ok(jsonb_array_length(:'fc3'::jsonb -> 'challenges_completed') = 0, 'cerrar otra vez no lo duplica');
reset role;
select t.ok(count(*) = 1 and sum(amount) = 20, 'un solo premio en el libro de puntos')
  from xp_ledger where user_id = :'B' and reason = 'daily_challenge';
select t.ok(challenges_completed = 1, 'contador de retos para el logro "Retador"') from user_stats where user_id = :'B';
set role authenticated;
select t.as_user(:'B');

\echo '--- 9. Evaluación de contexto'
select t.throws(format('select public.start_session(''exam'', null, %s::smallint)', (select id from contexts where slug = 'school')),
                'exam_locked%', 'bloqueada hasta superar las lecciones');
select (start_session('lesson', (select id from lessons where slug = 'school_classroom'))) ->> 'session_id' as l1 \gset
select t.answer_all(:'l1') \gset
select finish_session(:'l1') \gset
select (start_session('lesson', (select id from lessons where slug = 'school_campus'))) ->> 'session_id' as l2 \gset
select t.answer_all(:'l2') \gset
select finish_session(:'l2') \gset
select t.ok(exam_unlocked and not exam_passed, 'Escuela: evaluación abierta') from get_context_progress() where slug = 'school';
select (start_session('exam', null, (select id from contexts where slug = 'school'))) ->> 'session_id' as ex1 \gset
select t.ok(count(*) = 10 and bool_and(hint is null) and count(distinct exercise_type) >= 5,
            'evaluación: 10 ejercicios variados sin pistas') from session_items where session_id = :'ex1';
select t.answer_all(:'ex1', '{1,2,3}') \gset
select finish_session(:'ex1') as fe1 \gset
select t.ok(not (:'fe1'::jsonb ->> 'exam_passed')::boolean, '70 % no aprueba (se exige 80 %)');
select (start_session('exam', null, (select id from contexts where slug = 'school'))) ->> 'session_id' as ex2 \gset
select t.answer_all(:'ex2', '{1}') \gset
select finish_session(:'ex2') as fe2 \gset
select t.ok((:'fe2'::jsonb ->> 'exam_passed')::boolean
        and :'fe2'::jsonb -> 'new_achievements' @> '[{"code": "exam_1"}]', '90 %: aprobada y logro "Evaluado"');
select t.ok(exam_passed and exam_best_score = 90, 'la ruta muestra la evaluación aprobada')
  from get_context_progress() where slug = 'school';
select (start_session('exam', null, (select id from contexts where slug = 'school'))) ->> 'session_id' as ex3 \gset
select t.answer_all(:'ex3') \gset
select finish_session(:'ex3') as fe3 \gset
select t.ok(not (:'fe3'::jsonb ->> 'exam_passed')::boolean, 'aprobar otra vez no repite el premio');
reset role;
select t.ok(count(*) = 1 and sum(amount) = 100, 'premio de evaluación una sola vez')
  from xp_ledger where user_id = :'B' and reason = 'exam_pass';
set role authenticated;
select t.as_user(:'B');

\echo '--- 10. Recompensas, perfil e historial'
select get_rewards() as rw \gset
select t.ok((:'rw'::jsonb ->> 'level')::int >= 2 and :'rw'::jsonb ->> 'level_title' is not null
        and jsonb_array_length(:'rw'::jsonb -> 'streak_milestones') = 6
        and jsonb_array_length(:'rw'::jsonb -> 'recent_rewards') >= 3
        and (:'rw'::jsonb ->> 'achievements_total')::int = 20, 'pantalla de recompensas');
select get_profile_overview() as po \gset
select t.ok(:'po'::jsonb ->> 'email' = 'beto@niju.test' and (:'po'::jsonb ->> 'lessons_completed')::int = 2
        and (:'po'::jsonb ->> 'exams_passed')::int = 1 and (:'po'::jsonb ->> 'study_ms_total')::bigint > 0
        and (:'po'::jsonb ->> 'words_learned')::int = 12 and (:'po'::jsonb ->> 'lessons_total')::int = 24,
            'perfil con datos reales');
select t.ok(count(*) = 7 and bool_and(finished_at is not null), 'historial de 7 sesiones') from get_session_history(50);
select t.ok((get_dashboard() ->> 'challenges_total')::int = 3 and get_dashboard() ? 'level_title'
            and get_dashboard() ? 'study_ms_today', 'el inicio trae los campos nuevos');

\echo '--- 11. Seguridad'
select t.throws('insert into public.user_challenge_claims (user_id, period_start, code) values (auth.uid(), current_date, ''d_answers_20'')',
                'permission denied%', 'no se pueden cobrar retos directamente');
select t.throws('update public.user_context_exams set best_score = 100', 'permission denied%',
                'no se puede falsificar una evaluación');
select t.throws('select niju_private.settle_challenges(auth.uid())', 'permission denied%',
                'funciones internas inaccesibles');
reset role;
set role anon;
select t.as_user('');
select t.throws('select public.get_rewards()', 'permission denied%', 'anónimo no ve recompensas');
select t.throws('select public.get_review_overview()', 'permission denied%', 'anónimo no ve repasos');
select t.throws('select public.claim_rewards()', 'permission denied%', 'anónimo no cobra');
select t.ok((select count(*) from word_dialogues) = 84, 'anónimo ve el contenido (diálogos)');
reset role;

\echo '=== TODAS LAS PRUEBAS V2 PASARON ==='
rollback;
