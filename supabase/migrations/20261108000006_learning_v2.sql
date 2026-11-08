-- =============================================================================
-- NiJu · Migración 6: aprendizaje v2 y gamificación completa
-- Requiere las migraciones 1–5 (la 5 en una ejecución anterior y separada).
--
-- Qué agrega (todo se calcula y se valida en el servidor):
--   1. Contenido: imagen ilustrativa (emoji) por palabra y por contexto, y
--      diálogos cortos para "completar conversaciones".
--   2. Nueve tipos de ejercicio + evaluación por contexto (modo exam).
--      La respuesta correcta se guarda en una columna que la app NO puede leer.
--   3. SM-2 con calidad 0–5 (antes binaria): depende del acierto, del tipo de
--      ejercicio y del tiempo de respuesta.
--   4. Estados de cada palabra: nueva, aprendiendo, refuerzo, en repaso,
--      dominada; y "urgente" (repaso muy atrasado o palabra difícil vencida).
--   5. Retos diarios, metas semanales y bonos de racha, sin premios duplicados.
--   6. Más logros, perfil completo, tiempo de estudio e historial de sesiones.
--
-- No borra ni reescribe datos de estudiantes. Las funciones que cambian de
-- forma (parámetros o columnas) se eliminan y se crean de nuevo.
-- =============================================================================

-- ============================================================================
-- 1. ESQUEMA
-- ============================================================================

alter table public.words    add column if not exists emoji text check (emoji is null or length(emoji) <= 16);
alter table public.contexts add column if not exists emoji text check (emoji is null or length(emoji) <= 16);

-- Diálogo corto: la línea B contiene la palabra exacta (se oculta en el ejercicio).
drop table if exists public.word_dialogues cascade;
create table public.word_dialogues (
  id        integer generated always as identity primary key,
  word_id   integer not null references public.words (id) on delete cascade,
  line_a_en text not null,
  line_a_es text not null,
  line_b_en text not null,
  line_b_es text not null
);
create index if not exists word_dialogues_word_idx on public.word_dialogues (word_id);
alter table public.word_dialogues enable row level security;
drop policy if exists word_dialogues_read on public.word_dialogues;
create policy word_dialogues_read on public.word_dialogues
  for select to anon, authenticated using (true);
revoke insert, update, delete, truncate on public.word_dialogues from anon, authenticated;
grant select on public.word_dialogues to anon, authenticated;

-- Ejercicios v2: hasta 12 opciones (ordenar palabras) o ninguna (escribir).
alter table public.session_items drop constraint if exists session_items_options_check;
alter table public.session_items add constraint session_items_options_check
  check (cardinality(options) between 0 and 12);
-- Respuesta correcta y solución completa. La app no puede leer estas columnas
-- (ver privilegios por columna al final); solo las conoce submit_answer.
alter table public.session_items add column if not exists answer_key text;
alter table public.session_items add column if not exists solution text;

-- Calidad SM-2 (0–5) de cada intento: historial de desempeño por palabra.
alter table public.attempts add column if not exists quality smallint check (quality between 0 and 5);
alter table public.user_word_progress add column if not exists last_quality smallint
  check (last_quality between 0 and 5);

alter table public.user_stats add column if not exists perfect_sessions     integer not null default 0;
alter table public.user_stats add column if not exists exams_passed         integer not null default 0;
alter table public.user_stats add column if not exists challenges_completed integer not null default 0;

-- Nuevos motivos de XP.
alter table public.xp_ledger drop constraint if exists xp_ledger_reason_check;
alter table public.xp_ledger add constraint xp_ledger_reason_check check (reason in
  ('answer_due', 'answer_practice', 'lesson_complete', 'achievement',
   'daily_challenge', 'weekly_goal', 'streak_bonus', 'exam_pass'));

-- Evaluación de cada contexto por estudiante.
drop table if exists public.user_context_exams cascade;
create table public.user_context_exams (
  user_id        uuid     not null references public.profiles (id) on delete cascade,
  context_id     smallint not null references public.contexts (id) on delete cascade,
  attempts_count integer  not null default 0,
  best_score     numeric(5, 2) not null default 0 check (best_score between 0 and 100),
  passed_at      timestamptz,
  primary key (user_id, context_id)
);

-- Retos diarios y metas semanales: plantillas (reglas del sistema) y premios cobrados.
drop table if exists public.challenge_templates cascade;
create table public.challenge_templates (
  code        text primary key check (code ~ '^[a-z0-9_]+$'),
  period      text not null check (period in ('daily', 'weekly')),
  title       text not null,
  description text not null,
  metric      text not null check (metric in
                ('answers', 'correct', 'review_correct', 'lessons', 'xp', 'typed_correct',
                 'listen_correct', 'perfect', 'days', 'new_words')),
  target      integer not null check (target >= 0),   -- 0 en 'xp' semanal = 5 × meta diaria
  xp_reward   integer not null check (xp_reward > 0),
  sort_order  smallint not null default 0,
  is_active   boolean not null default true
);

drop table if exists public.user_challenge_claims cascade;
create table public.user_challenge_claims (
  user_id      uuid not null references public.profiles (id) on delete cascade,
  period_start date not null,
  code         text not null references public.challenge_templates (code) on delete cascade,
  xp_awarded   integer not null default 0,
  claimed_at   timestamptz not null default now(),
  primary key (user_id, period_start, code)
);

alter table public.user_context_exams    enable row level security;
alter table public.challenge_templates   enable row level security;
alter table public.user_challenge_claims enable row level security;
drop policy if exists user_context_exams_select_own on public.user_context_exams;
create policy user_context_exams_select_own on public.user_context_exams
  for select to authenticated using (user_id = (select auth.uid()));
drop policy if exists challenge_templates_read on public.challenge_templates;
create policy challenge_templates_read on public.challenge_templates
  for select to anon, authenticated using (true);
drop policy if exists user_challenge_claims_select_own on public.user_challenge_claims;
create policy user_challenge_claims_select_own on public.user_challenge_claims
  for select to authenticated using (user_id = (select auth.uid()));
revoke insert, update, delete, truncate on
  public.user_context_exams, public.challenge_templates, public.user_challenge_claims
  from anon, authenticated;
revoke select on public.user_context_exams, public.user_challenge_claims from anon;
grant select on public.challenge_templates to anon, authenticated;
grant select on public.user_context_exams, public.user_challenge_claims to authenticated;

-- ============================================================================
-- 2. REGLAS PURAS (sin tablas)
-- ============================================================================

-- Calidad SM-2 (0–5) a partir del acierto, el tipo de ejercicio y el tiempo.
--   Acierto rápido 5 · acierto normal 4 · acierto muy lento 3.
--   Fallo en reconocimiento (elegir) 2 · fallo en producción (escribir/ordenar) 1.
--   "Rápido" depende del esfuerzo: elegir ≤ 4 s, oración ≤ 8 s, escribir ≤ 10 s.
--   Sin tiempo medido se asume acierto normal (4), como en la v1.
create or replace function niju_private.answer_quality(
  p_type public.exercise_type, p_correct boolean, p_ms integer)
returns smallint
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_fast integer := case
    when p_type::text in ('type_answer', 'word_order') then 10000
    when p_type::text in ('fill_blank', 'dialogue', 'spot_error') then 8000
    else 4000 end;
begin
  if not p_correct then
    return case when p_type::text in ('type_answer', 'word_order', 'spot_error') then 1 else 2 end;
  end if;
  if p_ms is null then return 4; end if;
  if p_ms <= v_fast then return 5; end if;
  if p_ms <= v_fast * 3 then return 4; end if;
  return 3;
end;
$$;

-- Paso SM-2 clásico (SuperMemo 2, Wozniak 1990) con calidad q = 0..5:
--   EF' = EF + (0.1 − (5−q)(0.08 + (5−q)·0.02)), mínimo 1.30
--   q ≥ 3: repeticiones + 1; intervalo 1, 6 y luego round(I · EF')
--   q < 3: repeticiones a 0; repaso al día siguiente.
create or replace function niju_private.sm2_next_q(
  p_ease numeric, p_interval integer, p_repetitions integer, p_quality integer,
  out ease_factor numeric, out interval_days integer, out repetitions integer)
language plpgsql
immutable
set search_path = ''
as $$
declare
  q integer := least(greatest(coalesce(p_quality, 0), 0), 5);
begin
  ease_factor := greatest(1.30, round(p_ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)), 2));
  if q >= 3 then
    repetitions := p_repetitions + 1;
    interval_days := case repetitions
                       when 1 then 1
                       when 2 then 6
                       else greatest(1, round(p_interval * ease_factor))::integer
                     end;
  else
    repetitions := 0;
    interval_days := 1;
  end if;
end;
$$;

-- La versión binaria de la v1 se conserva como caso particular (acierto = 4, fallo = 2).
create or replace function niju_private.sm2_next(
  p_ease numeric, p_interval integer, p_repetitions integer, p_correct boolean,
  out ease_factor numeric, out interval_days integer, out repetitions integer)
language sql
immutable
set search_path = ''
as $$
  select s.ease_factor, s.interval_days, s.repetitions
    from niju_private.sm2_next_q(p_ease, p_interval, p_repetitions,
                                 case when p_correct then 4 else 2 end) s;
$$;

-- Estado de una palabra para el estudiante.
--   new       : nunca respondida.
--   mastered  : intervalo ≥ 21 días (recordada en repasos espaciados ≥ 3 semanas).
--   reinforce : difícil — 3+ olvidos, o 2+ fallos y más fallos que aciertos.
--   learning  : menos de 2 repasos acertados seguidos.
--   review    : en el calendario de repasos.
create or replace function public.word_bucket(
  p_interval integer, p_repetitions integer, p_lapses integer,
  p_correct integer, p_incorrect integer)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_interval is null then 'new'
    when p_interval >= 21 then 'mastered'
    when coalesce(p_lapses, 0) >= 3
      or (coalesce(p_incorrect, 0) >= 2 and coalesce(p_incorrect, 0) > coalesce(p_correct, 0)) then 'reinforce'
    when coalesce(p_repetitions, 0) < 2 then 'learning'
    else 'review'
  end;
$$;

-- Urgente: repaso atrasado 2+ días, o palabra en refuerzo con repaso vencido.
create or replace function niju_private.is_urgent(p_due timestamptz, p_bucket text)
returns boolean
language sql
stable
set search_path = ''
as $$
  select p_due is not null
     and (p_due <= now() - interval '2 days' or (p_bucket = 'reinforce' and p_due <= now()));
$$;

-- Títulos de nivel (motivación; no cambian las reglas de XP).
create or replace function public.level_title(p_level integer)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_level >= 20 then 'Leyenda'
    when p_level >= 16 then 'Maestro'
    when p_level >= 12 then 'Experto'
    when p_level >= 8  then 'Conversador'
    when p_level >= 5  then 'Viajero'
    when p_level >= 3  then 'Explorador'
    else 'Principiante'
  end;
$$;

-- Bonos de racha: días alcanzados → XP. Se premia una vez por racha.
create or replace function public.streak_bonus_for(p_days integer)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case p_days
    when 3 then 15 when 7 then 40 when 14 then 80
    when 30 then 150 when 60 then 300 when 100 then 500
    else 0 end;
$$;

-- Patrón "palabra completa" (\m…\M) con los caracteres especiales escapados.
create or replace function niju_private.word_pattern(p_text text)
returns text
language sql
immutable
set search_path = ''
as $$
  select '\m' || pg_catalog.regexp_replace(p_text, '([.^$*+?()\[\]{}|\\-])', '\\\1', 'g') || '\M';
$$;

-- Divide una oración en palabras para "ordenar" e "identificar el error".
-- Quita la puntuación de los extremos de cada palabra (conserva apóstrofos internos).
create or replace function niju_private.tokens(p_sentence text)
returns text[]
language sql
immutable
set search_path = ''
as $$
  select coalesce(array_agg(t order by n), '{}')
    from (select pg_catalog.regexp_replace(x, '^[[:punct:]¿¡]+|[[:punct:]]+$', '', 'g') as t, n
            from unnest(pg_catalog.regexp_split_to_array(pg_catalog.btrim(p_sentence), '\s+'))
                 with ordinality as u(x, n)) s
   where t <> '';
$$;

-- ============================================================================
-- 3. GENERACIÓN DE EJERCICIOS
-- ============================================================================

-- n distractores distintos de la respuesta, del mismo contexto y categoría gramatical
-- cuando es posible. p_spanish = true devuelve significados; false, palabras en inglés.
create or replace function niju_private.distractors(
  p_word_id integer, p_spanish boolean, p_context_id smallint, p_n integer)
returns text[]
language sql
volatile
set search_path = ''
as $$
  with w as (select * from public.words where id = p_word_id),
  c as (
    select distinct on (niju_private.normalize_answer(case when p_spanish then o.spanish else o.english end))
           case when p_spanish then o.spanish else o.english end as ans,
           exists (select 1 from public.lesson_words lw
                     join public.lessons l on l.id = lw.lesson_id
                    where lw.word_id = o.id and l.context_id = p_context_id) as same_ctx,
           (o.part_of_speech is not distinct from w.part_of_speech) as same_pos
      from public.words o, w
     where o.id <> w.id
       and niju_private.normalize_answer(case when p_spanish then o.spanish else o.english end)
           <> niju_private.normalize_answer(case when p_spanish then w.spanish else w.english end))
  select coalesce(array_agg(ans), '{}') from (
    select ans from c order by same_ctx desc, same_pos desc, random() limit greatest(p_n, 0)) x;
$$;

create or replace function niju_private.shuffle(p_items text[])
returns text[]
language sql
volatile
set search_path = ''
as $$
  select coalesce(array_agg(x order by random()), '{}') from unnest(p_items) as x;
$$;

-- Construye un ejercicio. Si el tipo pedido no es posible con el contenido
-- disponible (sin emoji, sin oración, sin diálogo, oración muy corta o larga),
-- usa una alternativa equivalente. Devuelve también la respuesta y la solución,
-- que se guardan en columnas ocultas a la app.
drop function if exists niju_private.build_exercise(integer, public.exercise_type, smallint);
create or replace function niju_private.build_exercise(
  p_word_id integer, p_type public.exercise_type, p_context_id smallint,
  p_with_hints boolean default true,
  out exercise_type public.exercise_type, out prompt text, out hint text,
  out options text[], out answer_key text, out solution text)
language plpgsql
volatile
set search_path = ''
as $$
declare
  w       public.words;
  v_type  text := p_type::text;
  v_pat   text;
  v_en    text;
  v_es    text;
  v_dlg   public.word_dialogues;
  v_tok   text[];
  v_wrong text;
  v_shuf  text[];
  v_i     integer;
begin
  select * into strict w from public.words where id = p_word_id;
  v_pat := niju_private.word_pattern(w.english);

  -- Oración de ejemplo que contiene la palabra (del mismo contexto, si hay).
  select e.sentence_en, e.sentence_es into v_en, v_es
    from public.word_examples e
   where e.word_id = w.id and e.sentence_en ~* v_pat
   order by (e.context_id is not distinct from p_context_id) desc, e.sort_order, e.id
   limit 1;

  -- Alternativas cuando falta contenido.
  if v_type = 'image_match' and w.emoji is null then v_type := 'es_to_en'; end if;
  if v_type = 'dialogue' then
    select * into v_dlg from public.word_dialogues d
     where d.word_id = w.id and d.line_b_en ~* v_pat order by random() limit 1;
    if v_dlg.id is null then v_type := 'fill_blank'; end if;
  end if;
  if v_type in ('word_order', 'spot_error') then
    v_tok := niju_private.tokens(v_en);
    if v_en is null or cardinality(v_tok) < 3 or cardinality(v_tok) > 12 then v_type := 'fill_blank'; end if;
  end if;
  if v_type = 'spot_error' and cardinality(v_tok) < 4 then v_type := 'fill_blank'; end if;
  if v_type = 'fill_blank' and v_en is null then v_type := 'es_to_en'; end if;

  exercise_type := v_type::public.exercise_type;
  answer_key := w.english;

  if v_type = 'en_to_es' then
    prompt := w.english; hint := w.pronunciation; answer_key := w.spanish;
    options := niju_private.shuffle(niju_private.distractors(w.id, true, p_context_id, 2) || w.spanish);

  elsif v_type = 'es_to_en' then
    prompt := w.spanish;
    options := niju_private.shuffle(niju_private.distractors(w.id, false, p_context_id, 2) || w.english);

  elsif v_type = 'image_match' then
    prompt := w.emoji; hint := null;
    options := niju_private.shuffle(niju_private.distractors(w.id, false, p_context_id, 2) || w.english);

  elsif v_type = 'listen_choose' then
    -- La app reproduce el audio de `prompt` y no muestra el texto.
    prompt := w.english; hint := null;
    options := niju_private.shuffle(niju_private.distractors(w.id, false, p_context_id, 3) || w.english);

  elsif v_type = 'type_answer' then
    prompt := w.spanish; options := '{}';
    hint := format('Empieza por «%s» · %s letras', left(w.english, 1),
                   length(pg_catalog.regexp_replace(w.english, '[^A-Za-z]', '', 'g')));

  elsif v_type = 'fill_blank' then
    prompt := pg_catalog.regexp_replace(v_en, v_pat, '_____', 'i'); hint := v_es; solution := v_en;
    options := niju_private.shuffle(niju_private.distractors(w.id, false, p_context_id, 2) || w.english);

  elsif v_type = 'dialogue' then
    prompt := 'A: ' || v_dlg.line_a_en || E'\nB: ' || pg_catalog.regexp_replace(v_dlg.line_b_en, v_pat, '_____', 'i');
    hint := v_dlg.line_a_es || ' — ' || v_dlg.line_b_es;
    solution := 'A: ' || v_dlg.line_a_en || E'\nB: ' || v_dlg.line_b_en;
    options := niju_private.shuffle(niju_private.distractors(w.id, false, p_context_id, 2) || w.english);

  elsif v_type = 'word_order' then
    prompt := v_es; hint := null; answer_key := v_en; solution := v_en;
    -- Mezcla garantizando que no quede en el orden original (si hay más de un orden posible).
    for v_i in 1..6 loop
      v_shuf := niju_private.shuffle(v_tok);
      exit when v_shuf <> v_tok;
    end loop;
    options := v_shuf;

  else -- spot_error: se cambia la palabra por otra; hay que tocar la que sobra.
    v_wrong := (niju_private.distractors(w.id, false, p_context_id, 1))[1];
    if v_wrong is null or v_wrong ~ '\s' then
      -- Sin distractor de una sola palabra: completar la oración.
      exercise_type := 'fill_blank';
      prompt := pg_catalog.regexp_replace(v_en, v_pat, '_____', 'i'); hint := v_es; solution := v_en;
      options := niju_private.shuffle(niju_private.distractors(w.id, false, p_context_id, 2) || w.english);
    else
      prompt := pg_catalog.regexp_replace(v_en, v_pat, v_wrong, 'i');
      hint := v_es; answer_key := v_wrong; solution := v_en;
      -- Opciones: la palabra equivocada y otras 3 de la oración, en el orden en que aparecen.
      select coalesce(array_agg(t order by n), '{}') into options from (
        select t, n from unnest(niju_private.tokens(prompt)) with ordinality as u(t, n)
         where lower(t) = lower(v_wrong)
            or n in (select n2 from unnest(niju_private.tokens(prompt)) with ordinality as u2(t2, n2)
                      where lower(t2) <> lower(v_wrong) and length(t2) > 1
                      order by random() limit 3)
      ) s;
      -- Si la palabra equivocada aparece repetida, se deja una sola vez.
      select coalesce(array_agg(distinct_t order by first_n), '{}') into options from (
        select t as distinct_t, min(n) as first_n
          from unnest(options) with ordinality as u(t, n) group by t) d;
    end if;
  end if;

  if not p_with_hints then hint := null; end if;
end;
$$;

-- ============================================================================
-- 4. RACHA CON BONOS
-- ============================================================================

-- Devuelve el XP de bono de racha otorgado (0 si no tocaba).
drop function if exists niju_private.touch_streak(uuid);
create or replace function niju_private.touch_streak(p_user uuid)
returns integer
language plpgsql
set search_path = ''
as $$
declare
  v_today  date := niju_private.local_today(p_user);
  v_before public.user_stats;
  v_new    integer;
  v_bonus  integer;
begin
  select * into v_before from public.user_stats where user_id = p_user for update;
  v_new := case
    when v_before.last_study_date = v_today     then v_before.current_streak
    when v_before.last_study_date = v_today - 1 then v_before.current_streak + 1
    else 1 end;

  update public.user_stats s
     set current_streak  = v_new,
         longest_streak  = greatest(s.longest_streak, v_new),
         last_study_date = v_today,
         updated_at      = now()
   where s.user_id = p_user;

  -- Bono solo el día en que la racha sube a un hito. La referencia incluye el día
  -- en que empezó la racha: la misma racha no se premia dos veces, una nueva sí.
  v_bonus := public.streak_bonus_for(v_new);
  if v_bonus > 0 and v_before.last_study_date is distinct from v_today then
    return niju_private.grant_xp(p_user, v_bonus, 'streak_bonus',
                                 v_new::text || ':' || (v_today - (v_new - 1))::text);
  end if;
  return 0;
end;
$$;

-- ============================================================================
-- 5. RETOS DIARIOS Y METAS SEMANALES
-- ============================================================================

insert into public.challenge_templates (code, period, title, description, metric, target, xp_reward, sort_order) values
  ('d_answers_20',   'daily',  'Calentamiento',       'Responde 20 ejercicios hoy.',                    'answers',        20, 20, 1),
  ('d_correct_15',   'daily',  'Puntería',            'Acierta 15 ejercicios hoy.',                     'correct',        15, 25, 2),
  ('d_review_5',     'daily',  'Memoria fresca',      'Acierta 5 ejercicios en un repaso programado.',  'review_correct',  5, 25, 3),
  ('d_lesson_1',     'daily',  'Paso adelante',       'Supera una lección hoy.',                        'lessons',         1, 30, 4),
  ('d_typed_5',      'daily',  'Escritor',            'Acierta 5 ejercicios de escribir u ordenar.',    'typed_correct',   5, 30, 5),
  ('d_listen_4',     'daily',  'Buen oído',           'Acierta 4 ejercicios de escuchar.',              'listen_correct',  4, 20, 6),
  ('d_perfect_1',    'daily',  'Sesión perfecta',     'Termina una sesión de 5+ ejercicios sin fallar.','perfect',         1, 35, 7),
  ('d_new_words_5',  'daily',  'Palabras nuevas',     'Estudia 5 palabras nuevas hoy.',                 'new_words',       5, 25, 8),
  ('w_xp',           'weekly', 'Meta semanal de XP',  'Gana 5 veces tu meta diaria de XP esta semana.', 'xp',              0, 100, 1),
  ('w_days_5',       'weekly', 'Cinco días',          'Estudia 5 días distintos esta semana.',          'days',            5, 120, 2),
  ('w_lessons_3',    'weekly', 'Tres lecciones',      'Supera 3 lecciones esta semana.',                'lessons',         3, 80, 3)
on conflict (code) do nothing;

-- Inicio del periodo (día local o lunes de la semana local).
create or replace function niju_private.period_start(p_user uuid, p_period text)
returns date
language sql
stable
set search_path = ''
as $$
  select case when p_period = 'weekly'
              then (date_trunc('week', niju_private.local_today(p_user)::timestamp))::date
              else niju_private.local_today(p_user) end;
$$;

-- Meta efectiva (la meta semanal de XP depende de la meta diaria del perfil).
create or replace function niju_private.challenge_target(p_user uuid, t public.challenge_templates)
returns integer
language sql
stable
set search_path = ''
as $$
  select case when t.metric = 'xp' and t.target = 0
              then 5 * coalesce((select p.daily_goal_xp from public.profiles p where p.id = p_user), 50)
              else t.target end;
$$;

-- Avance de una métrica entre dos días locales (incluidos).
create or replace function niju_private.challenge_progress(
  p_user uuid, p_metric text, p_from date, p_to date)
returns integer
language plpgsql
stable
set search_path = ''
as $$
declare
  v_tz text := coalesce((select p.timezone from public.profiles p where p.id = p_user), 'America/Bogota');
  v_n  integer;
begin
  if p_metric in ('answers', 'correct', 'review_correct', 'typed_correct', 'listen_correct') then
    select count(*) into v_n
      from public.attempts a
      left join public.session_items i on i.id = a.session_item_id
      left join public.study_sessions s on s.id = i.session_id
     where a.user_id = p_user
       and (a.created_at at time zone v_tz)::date between p_from and p_to
       and case p_metric
             when 'answers'        then true
             when 'correct'        then a.is_correct
             when 'review_correct' then a.is_correct and s.mode = 'review'
             when 'typed_correct'  then a.is_correct and a.exercise_type::text in ('type_answer', 'word_order')
             when 'listen_correct' then a.is_correct and a.exercise_type::text = 'listen_choose'
           end;
  elsif p_metric = 'lessons' then
    select count(*) into v_n from public.study_sessions s
     where s.user_id = p_user and s.mode = 'lesson' and s.finished_at is not null
       and s.total_items > 0 and s.correct_items * 100 >= 70 * s.total_items
       and (s.finished_at at time zone v_tz)::date between p_from and p_to;
  elsif p_metric = 'perfect' then
    select count(*) into v_n from public.study_sessions s
     where s.user_id = p_user and s.finished_at is not null
       and s.total_items >= 5 and s.correct_items = s.total_items
       and (s.finished_at at time zone v_tz)::date between p_from and p_to;
  elsif p_metric = 'xp' then
    -- Los premios de retos y metas no cuentan (evita que se alimenten a sí mismos).
    select coalesce(sum(x.amount), 0) into v_n from public.xp_ledger x
     where x.user_id = p_user and x.reason not in ('daily_challenge', 'weekly_goal')
       and (x.created_at at time zone v_tz)::date between p_from and p_to;
  elsif p_metric = 'days' then
    select count(distinct (a.created_at at time zone v_tz)::date) into v_n from public.attempts a
     where a.user_id = p_user and (a.created_at at time zone v_tz)::date between p_from and p_to;
  elsif p_metric = 'new_words' then
    select count(*) into v_n from public.user_word_progress p
     where p.user_id = p_user and (p.first_seen_at at time zone v_tz)::date between p_from and p_to;
  end if;
  return coalesce(v_n, 0);
end;
$$;

-- Retos del periodo actual para el usuario: 3 diarios (elegidos de forma estable
-- para ese día y ese usuario) y todas las metas semanales.
create or replace function niju_private.current_challenges(p_user uuid)
returns table (code text, period text, title text, description text, metric text,
               target integer, xp_reward integer, period_start date, progress integer, claimed boolean)
language sql
stable
set search_path = ''
as $$
  with picked as (
    (select t.*, niju_private.period_start(p_user, 'daily') as ps
       from public.challenge_templates t
      where t.is_active and t.period = 'daily'
      order by md5(t.code || niju_private.local_today(p_user)::text || p_user::text)
      limit 3)
    union all
    (select t.*, niju_private.period_start(p_user, 'weekly')
       from public.challenge_templates t
      where t.is_active and t.period = 'weekly'))
  select p.code, p.period, p.title, p.description, p.metric,
         niju_private.challenge_target(p_user, row(p.code, p.period, p.title, p.description, p.metric,
                                       p.target, p.xp_reward, p.sort_order, p.is_active)::public.challenge_templates),
         p.xp_reward, p.ps,
         niju_private.challenge_progress(p_user, p.metric, p.ps,
           case when p.period = 'weekly' then p.ps + 6 else p.ps end),
         exists (select 1 from public.user_challenge_claims c
                  where c.user_id = p_user and c.period_start = p.ps and c.code = p.code)
    from picked p
   order by (p.period = 'weekly'), p.sort_order;
$$;

-- Cobra los retos cumplidos y aún no cobrados. Devuelve los cobrados ahora.
create or replace function niju_private.settle_challenges(p_user uuid)
returns table (code text, title text, period text, xp_reward integer)
language plpgsql
set search_path = ''
as $$
declare
  c record;
begin
  for c in select * from niju_private.current_challenges(p_user) x
            where not x.claimed and x.target > 0 and x.progress >= x.target loop
    insert into public.user_challenge_claims (user_id, period_start, code, xp_awarded)
    values (p_user, c.period_start, c.code, c.xp_reward)
    on conflict do nothing;
    if found then
      perform niju_private.grant_xp(p_user, c.xp_reward,
        case when c.period = 'weekly' then 'weekly_goal' else 'daily_challenge' end,
        c.code || ':' || c.period_start::text);
      if c.period = 'daily' then
        update public.user_stats set challenges_completed = challenges_completed + 1
         where user_id = p_user;
      end if;
      code := c.code; title := c.title; period := c.period; xp_reward := c.xp_reward;
      return next;
    end if;
  end loop;
end;
$$;

-- ============================================================================
-- 6. LOGROS v2
-- ============================================================================

create or replace function niju_private.metric_value(p_user uuid, p_metric public.achievement_metric)
returns integer
language sql
stable
set search_path = ''
as $$
  select coalesce(case p_metric::text
    when 'lessons_completed'    then (select s.lessons_completed from public.user_stats s where s.user_id = p_user)
    when 'streak_days'          then (select s.longest_streak from public.user_stats s where s.user_id = p_user)
    when 'words_mastered'       then (select count(*)::integer from public.user_word_progress p
                                       where p.user_id = p_user and p.interval_days >= 21)
    when 'correct_reviews'      then (select s.correct_reviews from public.user_stats s where s.user_id = p_user)
    when 'words_practiced'      then (select count(*)::integer from public.user_word_progress p where p.user_id = p_user)
    when 'perfect_sessions'     then (select s.perfect_sessions from public.user_stats s where s.user_id = p_user)
    when 'exams_passed'         then (select s.exams_passed from public.user_stats s where s.user_id = p_user)
    when 'xp_total'             then (select s.xp_total from public.user_stats s where s.user_id = p_user)
    when 'challenges_completed' then (select s.challenges_completed from public.user_stats s where s.user_id = p_user)
  end, 0);
$$;

create or replace function niju_private.check_achievements(p_user uuid)
returns table (code text, title text, xp_reward integer)
language plpgsql
set search_path = ''
as $$
declare
  a public.achievements;
begin
  for a in
    select ach.* from public.achievements ach
     where not exists (select 1 from public.user_achievements ua
                        where ua.user_id = p_user and ua.achievement_code = ach.code)
     order by ach.sort_order
  loop
    if niju_private.metric_value(p_user, a.metric) >= a.threshold then
      insert into public.user_achievements (user_id, achievement_code)
      values (p_user, a.code) on conflict do nothing;
      if found then
        perform niju_private.grant_xp(p_user, a.xp_reward, 'achievement', a.code);
        code := a.code; title := a.title; xp_reward := a.xp_reward;
        return next;
      end if;
    end if;
  end loop;
end;
$$;

create or replace function public.get_achievements()
returns table (
  code text, title text, description text, metric public.achievement_metric,
  threshold integer, xp_reward integer, current_value integer, unlocked_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  with me as (select niju_private.require_user() as uid)
  select a.code, a.title, a.description, a.metric, a.threshold, a.xp_reward,
         least(a.threshold, niju_private.metric_value(me.uid, a.metric)),
         ua.unlocked_at
    from public.achievements a
    cross join me
    left join public.user_achievements ua
      on ua.achievement_code = a.code and ua.user_id = me.uid
   order by (ua.unlocked_at is null), a.sort_order;
$$;

insert into public.achievements (code, title, description, metric, threshold, xp_reward, sort_order) values
  ('lessons_5',      'Aprendiz constante',   'Completa 5 lecciones.',                         'lessons_completed',     5,  50,  7),
  ('lessons_15',     'Explorador de contextos','Completa 15 lecciones.',                      'lessons_completed',    15, 150,  8),
  ('streak_14',      'Dos semanas',          'Estudia 14 días seguidos.',                     'streak_days',          14, 140,  9),
  ('streak_30',      'Hábito de hierro',     'Estudia 30 días seguidos.',                     'streak_days',          30, 300, 10),
  ('reviews_100',    'Memoria de elefante',  'Acierta 100 repasos programados.',              'correct_reviews',     100, 150, 11),
  ('mastered_50',    'Vocabulario sólido',   'Domina 50 palabras.',                           'words_mastered',       50, 250, 12),
  ('practiced_50',   'Coleccionista',        'Practica 50 palabras distintas.',               'words_practiced',      50,  50, 13),
  ('practiced_150',  'Diccionario andante',  'Practica 150 palabras distintas.',              'words_practiced',     150, 200, 14),
  ('perfect_1',      'Sin errores',          'Termina una sesión de 5+ ejercicios sin fallar.','perfect_sessions',     1,  30, 15),
  ('perfect_10',     'Precisión',            'Termina 10 sesiones perfectas.',                'perfect_sessions',     10, 120, 16),
  ('exam_1',         'Evaluado',             'Aprueba la evaluación de un contexto.',         'exams_passed',          1,  80, 17),
  ('exam_6',         'Políglota en marcha',  'Aprueba 6 evaluaciones de contexto.',           'exams_passed',          6, 300, 18),
  ('xp_1000',        'Mil puntos',           'Acumula 1000 XP.',                              'xp_total',           1000,  50, 19),
  ('challenges_10',  'Retador',              'Cumple 10 retos diarios.',                      'challenges_completed', 10, 100, 20)
on conflict (code) do nothing;

-- ============================================================================
-- 7. SESIONES v2
-- ============================================================================

-- ¿Está abierta la evaluación del contexto? Cuando todas sus lecciones están superadas.
create or replace function niju_private.exam_unlocked(p_user uuid, p_context_id smallint)
returns boolean
language sql
stable
set search_path = ''
as $$
  select exists (select 1 from public.lessons l where l.context_id = p_context_id and l.is_published)
     and not exists (
       select 1 from public.lessons l
        where l.context_id = p_context_id and l.is_published
          and not exists (select 1 from public.user_lesson_progress u
                           where u.user_id = p_user and u.lesson_id = l.id and u.completed_at is not null));
$$;

drop function if exists public.start_session(public.session_mode, integer, smallint, integer);

-- Inicia una sesión y devuelve sus ejercicios (sin respuestas).
--   lesson   : palabras de la lección en orden. Los tipos cambian en cada intento.
--   review   : repasos vencidos; primero los urgentes y los más atrasados.
--              p_focus = 'reinforce' → palabras difíciles; 'urgent' → solo urgentes.
--   practice : palabras al azar del contexto (o de todo). p_focus = 'favorites' → favoritas.
--   exam     : evaluación de un contexto: 10 palabras, tipos variados, sin pistas.
create or replace function public.start_session(
  p_mode       public.session_mode,
  p_lesson_id  integer  default null,
  p_context_id smallint default null,
  p_limit      integer  default 10,
  p_focus      text     default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid      uuid := niju_private.require_user();
  v_limit    integer := least(greatest(coalesce(p_limit, 10), 1), 20);
  v_ctx      smallint := p_context_id;
  v_session  uuid;
  v_words    integer[];
  v_pos      integer := 0;
  v_word     integer;
  v_type     public.exercise_type;
  v_ex       record;
  v_attempts integer := 0;
  v_all      text[] := array['en_to_es', 'es_to_en', 'fill_blank', 'image_match', 'listen_choose',
                             'type_answer', 'word_order', 'spot_error', 'dialogue'];
  -- Lección: de reconocer a producir. El primer intento usa A; los siguientes alternan con B.
  v_lesson_a text[] := array['en_to_es', 'image_match', 'listen_choose', 'fill_blank', 'type_answer', 'word_order'];
  v_lesson_b text[] := array['es_to_en', 'listen_choose', 'dialogue', 'spot_error', 'image_match', 'type_answer'];
  v_seq      text[];
begin
  if p_mode = 'lesson' then
    select l.context_id into v_ctx
      from public.lessons l where l.id = p_lesson_id and l.is_published;
    if not found then
      raise exception 'lesson_not_found' using errcode = 'P0002';
    end if;
    if not niju_private.lesson_unlocked(v_uid, p_lesson_id) then
      raise exception 'lesson_locked' using errcode = 'P0001';
    end if;
    select array_agg(lw.word_id order by lw.position) into v_words
      from public.lesson_words lw where lw.lesson_id = p_lesson_id;
    select coalesce(u.attempts_count, 0) into v_attempts
      from public.user_lesson_progress u where u.user_id = v_uid and u.lesson_id = p_lesson_id;
    v_seq := case when coalesce(v_attempts, 0) % 2 = 0 then v_lesson_a else v_lesson_b end;

  elsif p_mode = 'review' then
    select array_agg(x.word_id order by x.urgent desc, x.due_at) into v_words
      from (select p.word_id, p.due_at,
                   niju_private.is_urgent(p.due_at, public.word_bucket(p.interval_days, p.repetitions,
                     p.lapses, p.correct_count, p.incorrect_count)) as urgent,
                   public.word_bucket(p.interval_days, p.repetitions, p.lapses,
                                      p.correct_count, p.incorrect_count) as bucket
              from public.user_word_progress p
             where p.user_id = v_uid
               and case coalesce(p_focus, 'due')
                     when 'reinforce' then public.word_bucket(p.interval_days, p.repetitions, p.lapses,
                                                              p.correct_count, p.incorrect_count) = 'reinforce'
                     when 'urgent' then p.due_at <= now()
                     else p.due_at <= now()
                   end
             order by 3 desc, p.due_at
             limit v_limit) x
     where p_focus is distinct from 'urgent' or x.urgent;

  elsif p_mode = 'exam' then
    if v_ctx is null or not exists (select 1 from public.contexts c where c.id = v_ctx and c.is_published) then
      raise exception 'context_not_found' using errcode = 'P0002';
    end if;
    if not niju_private.exam_unlocked(v_uid, v_ctx) then
      raise exception 'exam_locked' using errcode = 'P0001';
    end if;
    select array_agg(x.id) into v_words
      from (select distinct w.id, random() as r
              from public.words w
              join public.lesson_words lw on lw.word_id = w.id
              join public.lessons l on l.id = lw.lesson_id and l.is_published and l.context_id = v_ctx
             order by r limit 10) x;
    v_seq := niju_private.shuffle(v_all);

  else -- practice
    select array_agg(x.id) into v_words
      from (select distinct w.id, random() as r
              from public.words w
              join public.lesson_words lw on lw.word_id = w.id
              join public.lessons l on l.id = lw.lesson_id and l.is_published
             where (v_ctx is null or l.context_id = v_ctx)
               and (p_focus is distinct from 'favorites' or exists (
                     select 1 from public.user_favorite_words f
                      where f.user_id = v_uid and f.word_id = w.id))
             order by r
             limit v_limit) x;
  end if;

  if v_words is null or cardinality(v_words) = 0 then
    raise exception '%', case
        when p_mode = 'review' then 'no_due_reviews'
        when p_focus = 'favorites' then 'no_favorites'
        else 'no_words' end
      using errcode = 'P0002';
  end if;

  insert into public.study_sessions (user_id, mode, lesson_id, context_id, total_items)
  values (v_uid, p_mode, case when p_mode = 'lesson' then p_lesson_id end,
          v_ctx, cardinality(v_words))
  returning id into v_session;

  foreach v_word in array v_words loop
    v_pos := v_pos + 1;
    v_type := (case
      when v_seq is not null then v_seq[1 + (v_pos - 1) % cardinality(v_seq)]
      else v_all[1 + floor(random() * cardinality(v_all))::integer]
    end)::public.exercise_type;
    select * into v_ex from niju_private.build_exercise(v_word, v_type, v_ctx, p_mode <> 'exam');
    insert into public.session_items
      (session_id, position, word_id, exercise_type, prompt, hint, options, answer_key, solution)
    values (v_session, v_pos, v_word, v_ex.exercise_type, v_ex.prompt, v_ex.hint, v_ex.options,
            v_ex.answer_key, v_ex.solution);
  end loop;

  return jsonb_build_object(
    'session_id', v_session,
    'mode', p_mode,
    'lesson_id', case when p_mode = 'lesson' then p_lesson_id end,
    'context_id', v_ctx,
    'items', (select jsonb_agg(jsonb_build_object(
                       'item_id', i.id, 'position', i.position, 'word_id', i.word_id,
                       'exercise_type', i.exercise_type, 'prompt', i.prompt,
                       'hint', i.hint, 'options', to_jsonb(i.options))
                     order by i.position)
                from public.session_items i where i.session_id = v_session));
end;
$$;

-- Corrige una respuesta; actualiza SM-2 (calidad 0–5), XP, racha y bono de racha.
create or replace function public.submit_answer(
  p_item_id     bigint,
  p_answer      text,
  p_response_ms integer default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid      uuid := niju_private.require_user();
  v_item     public.session_items;
  v_session  public.study_sessions;
  v_word     public.words;
  v_prog     public.user_word_progress;
  v_correct_answer text;
  v_is_correct boolean;
  v_was_due  boolean;
  v_quality  smallint;
  v_sm2      record;
  v_xp       integer := 0;
  v_bonus    integer := 0;
  v_today    date;
  v_ms       integer := case when p_response_ms is null then null
                             else least(greatest(p_response_ms, 0), 3600000) end;
begin
  select * into v_item from public.session_items where id = p_item_id for update;
  if not found then
    raise exception 'item_not_found' using errcode = 'P0002';
  end if;
  select * into v_session from public.study_sessions where id = v_item.session_id;
  if v_session.user_id <> v_uid then
    raise exception 'item_not_found' using errcode = 'P0002';   -- no revelar ajenos
  end if;
  if v_session.finished_at is not null then
    raise exception 'session_finished' using errcode = 'P0001';
  end if;
  if v_item.answered_at is not null then
    raise exception 'item_already_answered' using errcode = 'P0001';
  end if;
  if p_answer is null or length(p_answer) > 300 then
    raise exception 'invalid_answer' using errcode = '22023';
  end if;

  select * into strict v_word from public.words where id = v_item.word_id;
  v_correct_answer := coalesce(v_item.answer_key,
    case when v_item.exercise_type = 'en_to_es' then v_word.spanish else v_word.english end);
  v_is_correct := niju_private.normalize_answer(p_answer) = niju_private.normalize_answer(v_correct_answer);
  v_quality := niju_private.answer_quality(v_item.exercise_type, v_is_correct, v_ms);

  select * into v_prog from public.user_word_progress
   where user_id = v_uid and word_id = v_word.id for update;
  v_was_due := (v_prog.user_id is null) or v_prog.due_at <= now();

  if v_prog.user_id is null then
    select * into v_sm2 from niju_private.sm2_next_q(2.50, 0, 0, v_quality);
    insert into public.user_word_progress
      (user_id, word_id, ease_factor, interval_days, repetitions, lapses,
       correct_count, incorrect_count, last_reviewed_at, due_at, last_quality)
    values (v_uid, v_word.id, v_sm2.ease_factor, v_sm2.interval_days, v_sm2.repetitions,
            case when v_is_correct then 0 else 1 end,
            case when v_is_correct then 1 else 0 end,
            case when v_is_correct then 0 else 1 end,
            now(), now() + make_interval(days => v_sm2.interval_days), v_quality)
    returning * into v_prog;

  elsif v_was_due or not v_is_correct then
    -- Repaso que tocaba, o fallo (un fallo siempre cuenta como olvido).
    select * into v_sm2
      from niju_private.sm2_next_q(v_prog.ease_factor, v_prog.interval_days,
                                   v_prog.repetitions, v_quality);
    update public.user_word_progress
       set ease_factor      = v_sm2.ease_factor,
           interval_days    = v_sm2.interval_days,
           repetitions      = v_sm2.repetitions,
           lapses           = lapses + case when v_is_correct then 0 else 1 end,
           correct_count    = correct_count + case when v_is_correct then 1 else 0 end,
           incorrect_count  = incorrect_count + case when v_is_correct then 0 else 1 end,
           last_reviewed_at = now(),
           due_at           = now() + make_interval(days => v_sm2.interval_days),
           last_quality     = v_quality
     where user_id = v_uid and word_id = v_word.id
     returning * into v_prog;

    if v_is_correct then
      update public.user_stats set correct_reviews = correct_reviews + 1
       where user_id = v_uid;
    end if;

  else
    -- Acierto en una palabra que aún no tocaba: se registra, sin mover el calendario.
    update public.user_word_progress
       set correct_count = correct_count + 1, last_quality = v_quality
     where user_id = v_uid and word_id = v_word.id
     returning * into v_prog;
  end if;

  if v_is_correct then
    if v_was_due then
      v_xp := niju_private.grant_xp(v_uid, 10, 'answer_due', p_item_id::text);
    else
      v_today := niju_private.local_today(v_uid);
      v_xp := niju_private.grant_xp(v_uid, 2, 'answer_practice',
                                    v_word.id::text || ':' || v_today::text);
    end if;
  end if;

  insert into public.attempts
    (user_id, session_item_id, word_id, exercise_type, given_answer,
     is_correct, was_due, xp_awarded, response_ms, quality)
  values (v_uid, p_item_id, v_word.id, v_item.exercise_type, p_answer,
          v_is_correct, v_was_due, v_xp, v_ms, v_quality);

  v_bonus := niju_private.touch_streak(v_uid);

  update public.session_items set answered_at = now() where id = p_item_id;
  update public.study_sessions
     set correct_items = correct_items + case when v_is_correct then 1 else 0 end,
         xp_earned     = xp_earned + v_xp + v_bonus
   where id = v_session.id;

  return jsonb_build_object(
    'is_correct',     v_is_correct,
    'correct_answer', v_correct_answer,
    'solution',       v_item.solution,
    'xp_awarded',     v_xp,
    'streak_bonus',   v_bonus,
    'was_due',        v_was_due,
    'quality',        v_quality,
    'status',         public.mastery_status(v_prog.interval_days, v_prog.repetitions),
    'bucket',         public.word_bucket(v_prog.interval_days, v_prog.repetitions, v_prog.lapses,
                                         v_prog.correct_count, v_prog.incorrect_count),
    'ease_factor',    v_prog.ease_factor,
    'interval_days',  v_prog.interval_days,
    'next_due_at',    v_prog.due_at);
end;
$$;

-- Cierra la sesión (idempotente): bono de lección, evaluación, sesión perfecta,
-- retos cumplidos y logros. Devuelve el resumen.
create or replace function public.finish_session(p_session_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid       uuid := niju_private.require_user();
  v_session   public.study_sessions;
  v_answered  integer;
  v_score     numeric;
  v_bonus     integer := 0;
  v_completed boolean := false;
  v_exam_pass boolean := false;
  v_perfect   boolean := false;
  v_new_ach   jsonb := '[]'::jsonb;
  v_ach_xp    integer := 0;
  v_chal      jsonb := '[]'::jsonb;
  v_chal_xp   integer := 0;
  v_stats     public.user_stats;
  v_ms        bigint;
begin
  select * into v_session from public.study_sessions
   where id = p_session_id and user_id = v_uid for update;
  if not found then
    raise exception 'session_not_found' using errcode = 'P0002';
  end if;

  select count(*) into v_answered
    from public.session_items where session_id = p_session_id and answered_at is not null;
  v_score := case when v_session.total_items > 0
                  then round(100.0 * v_session.correct_items / v_session.total_items, 2)
                  else 0 end;
  v_perfect := v_session.total_items >= 5 and v_session.correct_items = v_session.total_items;

  if v_session.finished_at is null then
    if v_session.mode = 'lesson' and v_session.lesson_id is not null then
      insert into public.user_lesson_progress (user_id, lesson_id, attempts_count, best_score)
      values (v_uid, v_session.lesson_id, 1, v_score)
      on conflict (user_id, lesson_id) do update
        set attempts_count = public.user_lesson_progress.attempts_count + 1,
            best_score     = greatest(public.user_lesson_progress.best_score, excluded.best_score);

      if v_score >= 70 then
        update public.user_lesson_progress
           set completed_at = now()
         where user_id = v_uid and lesson_id = v_session.lesson_id and completed_at is null;
        if found then
          v_completed := true;
          update public.user_stats set lessons_completed = lessons_completed + 1
           where user_id = v_uid;
          v_bonus := niju_private.grant_xp(v_uid, 50, 'lesson_complete',
                                           v_session.lesson_id::text);
        end if;
      end if;
    end if;

    if v_session.mode::text = 'exam' and v_session.context_id is not null then
      insert into public.user_context_exams (user_id, context_id, attempts_count, best_score)
      values (v_uid, v_session.context_id, 1, v_score)
      on conflict (user_id, context_id) do update
        set attempts_count = public.user_context_exams.attempts_count + 1,
            best_score     = greatest(public.user_context_exams.best_score, excluded.best_score);
      if v_score >= 80 then
        update public.user_context_exams set passed_at = now()
         where user_id = v_uid and context_id = v_session.context_id and passed_at is null;
        if found then
          v_exam_pass := true;
          update public.user_stats set exams_passed = exams_passed + 1 where user_id = v_uid;
          v_bonus := v_bonus + niju_private.grant_xp(v_uid, 100, 'exam_pass', v_session.context_id::text);
        end if;
      end if;
    end if;

    if v_perfect then
      update public.user_stats set perfect_sessions = perfect_sessions + 1 where user_id = v_uid;
    end if;

    -- Cerrar la sesión ANTES de contar retos: "sesión perfecta" y "lecciones" la incluyen.
    update public.study_sessions set finished_at = now() where id = p_session_id;

    select coalesce(jsonb_agg(jsonb_build_object('code', c.code, 'title', c.title,
                                                 'period', c.period, 'xp_reward', c.xp_reward)), '[]'::jsonb),
           coalesce(sum(c.xp_reward), 0)
      into v_chal, v_chal_xp
      from niju_private.settle_challenges(v_uid) c;

    select coalesce(jsonb_agg(jsonb_build_object('code', c.code, 'title', c.title,
                                                 'xp_reward', c.xp_reward)), '[]'::jsonb),
           coalesce(sum(c.xp_reward), 0)
      into v_new_ach, v_ach_xp
      from niju_private.check_achievements(v_uid) c;

    update public.study_sessions
       set xp_earned = xp_earned + v_bonus + v_ach_xp + v_chal_xp
     where id = p_session_id
     returning * into v_session;
  end if;

  select * into v_stats from public.user_stats where user_id = v_uid;
  select coalesce(sum(least(coalesce(a.response_ms, 0), 60000)), 0) into v_ms
    from public.attempts a join public.session_items i on i.id = a.session_item_id
   where i.session_id = p_session_id;

  return jsonb_build_object(
    'session_id',           v_session.id,
    'mode',                 v_session.mode,
    'total',                v_session.total_items,
    'answered',             v_answered,
    'correct',              v_session.correct_items,
    'score',                v_score,
    'xp_earned',            v_session.xp_earned,
    'lesson_completed',     v_completed,
    'exam_passed',          v_exam_pass,
    'perfect',              v_perfect,
    'new_achievements',     v_new_ach,
    'challenges_completed', v_chal,
    'study_ms',             v_ms,
    'xp_total',             v_stats.xp_total,
    'level',                public.level_for_xp(v_stats.xp_total),
    'previous_level',       public.level_for_xp(greatest(v_stats.xp_total - v_session.xp_earned, 0)),
    'level_title',          public.level_title(public.level_for_xp(v_stats.xp_total)),
    'current_streak',       v_stats.current_streak);
end;
$$;

-- Cobra los retos cumplidos (lo llama la pantalla de recompensas al abrirse).
create or replace function public.claim_rewards()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := niju_private.require_user();
  v_chal jsonb;
  v_ach  jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object('code', c.code, 'title', c.title,
                                               'period', c.period, 'xp_reward', c.xp_reward)), '[]'::jsonb)
    into v_chal from niju_private.settle_challenges(v_uid) c;
  select coalesce(jsonb_agg(jsonb_build_object('code', c.code, 'title', c.title,
                                               'xp_reward', c.xp_reward)), '[]'::jsonb)
    into v_ach from niju_private.check_achievements(v_uid) c;
  return jsonb_build_object('challenges_completed', v_chal, 'new_achievements', v_ach);
end;
$$;

-- ============================================================================
-- 8. CONSULTAS v2
-- ============================================================================

-- Inicio y Perfil (misma forma que la v1 + campos nuevos).
create or replace function public.get_dashboard()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := niju_private.require_user();
  v_today date := niju_private.local_today(v_uid);
  v_prof  public.profiles;
  v_stats public.user_stats;
  v_tz    text;
  v_level integer;
begin
  select * into v_prof  from public.profiles   where id = v_uid;
  select * into v_stats from public.user_stats where user_id = v_uid;
  v_tz := coalesce(v_prof.timezone, 'America/Bogota');
  v_level := public.level_for_xp(v_stats.xp_total);

  return jsonb_build_object(
    'display_name',    v_prof.display_name,
    'avatar_url',      v_prof.avatar_url,
    'xp_total',        v_stats.xp_total,
    'level',           v_level,
    'level_title',     public.level_title(v_level),
    'level_start_xp',  public.xp_for_level(v_level),
    'next_level_xp',   public.xp_for_level(v_level + 1),
    'xp_today',        (select coalesce(sum(x.amount), 0) from public.xp_ledger x
                         where x.user_id = v_uid
                           and (x.created_at at time zone v_tz)::date = v_today),
    'daily_goal_xp',   v_prof.daily_goal_xp,
    'current_streak',  case when v_stats.last_study_date >= v_today - 1
                            then v_stats.current_streak else 0 end,
    'studied_today',   coalesce(v_stats.last_study_date = v_today, false),
    'longest_streak',  v_stats.longest_streak,
    'lessons_completed', v_stats.lessons_completed,
    'due_reviews',     (select count(*) from public.user_word_progress p
                         where p.user_id = v_uid and p.due_at <= now()),
    'urgent_reviews',  (select count(*) from public.user_word_progress p
                         where p.user_id = v_uid
                           and niju_private.is_urgent(p.due_at, public.word_bucket(p.interval_days,
                                 p.repetitions, p.lapses, p.correct_count, p.incorrect_count))),
    'words_seen',      (select count(distinct i.word_id) from public.session_items i
                          join public.study_sessions s on s.id = i.session_id
                         where s.user_id = v_uid),
    'words_practiced', (select count(*) from public.user_word_progress p
                         where p.user_id = v_uid),
    'words_mastered',  (select count(*) from public.user_word_progress p
                         where p.user_id = v_uid and p.interval_days >= 21),
    'accuracy',        (select case when count(*) = 0 then null
                                    else round(100.0 * count(*) filter (where a.is_correct) / count(*), 1) end
                          from public.attempts a where a.user_id = v_uid),
    'study_ms_today',  (select coalesce(sum(least(coalesce(a.response_ms, 0), 60000)), 0)
                          from public.attempts a
                         where a.user_id = v_uid and (a.created_at at time zone v_tz)::date = v_today),
    'challenges_done', (select count(*) from niju_private.current_challenges(v_uid) c
                         where c.period = 'daily' and (c.claimed or c.progress >= c.target)),
    'challenges_total', 3);
end;
$$;

-- Ruta: avance por contexto + evaluación + emoji del contexto.
drop function if exists public.get_context_progress();
create or replace function public.get_context_progress()
returns table (
  context_id smallint, slug text, name text, description text, icon text, emoji text,
  lessons_total integer, lessons_completed integer,
  words_total integer, words_practiced integer, words_mastered integer,
  percent_complete numeric,
  exam_unlocked boolean, exam_best_score numeric, exam_passed boolean)
language sql
stable
security definer
set search_path = ''
as $$
  with me as (select niju_private.require_user() as uid),
  ctx_words as (
    select distinct l.context_id, lw.word_id
      from public.lessons l join public.lesson_words lw on lw.lesson_id = l.id
     where l.is_published)
  select c.id, c.slug, c.name, c.description, c.icon, c.emoji,
         (select count(*) from public.lessons l
           where l.context_id = c.id and l.is_published)::integer,
         (select count(*) from public.user_lesson_progress ulp
            join public.lessons l on l.id = ulp.lesson_id
           where l.context_id = c.id and ulp.user_id = me.uid
             and ulp.completed_at is not null)::integer,
         (select count(*) from ctx_words cw where cw.context_id = c.id)::integer,
         (select count(*) from ctx_words cw join public.user_word_progress p
              on p.word_id = cw.word_id and p.user_id = me.uid
           where cw.context_id = c.id)::integer,
         (select count(*) from ctx_words cw join public.user_word_progress p
              on p.word_id = cw.word_id and p.user_id = me.uid and p.interval_days >= 21
           where cw.context_id = c.id)::integer,
         coalesce(round(100.0 *
           (select count(*) from public.user_lesson_progress ulp
              join public.lessons l on l.id = ulp.lesson_id
             where l.context_id = c.id and ulp.user_id = me.uid and ulp.completed_at is not null)
           / nullif((select count(*) from public.lessons l
                      where l.context_id = c.id and l.is_published), 0), 0), 0),
         niju_private.exam_unlocked(me.uid, c.id),
         coalesce((select e.best_score from public.user_context_exams e
                    where e.user_id = me.uid and e.context_id = c.id), 0),
         exists (select 1 from public.user_context_exams e
                  where e.user_id = me.uid and e.context_id = c.id and e.passed_at is not null)
    from public.contexts c, me
   where c.is_published
   order by c.sort_order, c.id;
$$;

-- Tarjetas de una lección (+ emoji).
drop function if exists public.get_lesson_content(integer);
create or replace function public.get_lesson_content(p_lesson_id integer)
returns table (
  word_id integer, "position" smallint, english text, spanish text,
  pronunciation text, part_of_speech public.part_of_speech,
  sentence_en text, sentence_es text, is_favorite boolean, status text, emoji text)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := niju_private.require_user();
  v_ctx smallint;
begin
  select l.context_id into v_ctx from public.lessons l
   where l.id = p_lesson_id and l.is_published;
  if not found then
    raise exception 'lesson_not_found' using errcode = 'P0002';
  end if;
  if not niju_private.lesson_unlocked(v_uid, p_lesson_id) then
    raise exception 'lesson_locked' using errcode = 'P0001';
  end if;

  return query
  select w.id, lw.position, w.english, w.spanish, w.pronunciation, w.part_of_speech,
         ex.sentence_en, ex.sentence_es,
         exists (select 1 from public.user_favorite_words f
                  where f.user_id = v_uid and f.word_id = w.id),
         public.mastery_status(p.interval_days, coalesce(p.repetitions, 0)),
         w.emoji
    from public.lesson_words lw
    join public.words w on w.id = lw.word_id
    left join public.user_word_progress p on p.user_id = v_uid and p.word_id = w.id
    left join lateral (
      select e.sentence_en, e.sentence_es from public.word_examples e
       where e.word_id = w.id
       order by (e.context_id is not distinct from v_ctx) desc, e.sort_order, e.id
       limit 1) ex on true
   where lw.lesson_id = p_lesson_id
   order by lw.position;
end;
$$;

drop function if exists public.get_favorites();
create or replace function public.get_favorites()
returns table (
  word_id integer, english text, spanish text, pronunciation text,
  sentence_en text, sentence_es text, status text, added_at timestamptz, emoji text)
language sql
stable
security definer
set search_path = ''
as $$
  select w.id, w.english, w.spanish, w.pronunciation,
         ex.sentence_en, ex.sentence_es,
         public.mastery_status(p.interval_days, coalesce(p.repetitions, 0)),
         f.created_at, w.emoji
    from public.user_favorite_words f
    join public.words w on w.id = f.word_id
    left join public.user_word_progress p on p.user_id = f.user_id and p.word_id = w.id
    left join lateral (
      select e.sentence_en, e.sentence_es from public.word_examples e
       where e.word_id = w.id order by e.sort_order, e.id limit 1) ex on true
   where f.user_id = niju_private.require_user()
   order by f.created_at desc;
$$;

-- Resumen de repetición espaciada para la pantalla de Repaso.
create or replace function public.get_review_overview()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := niju_private.require_user();
  v_tz    text := coalesce((select p.timezone from public.profiles p where p.id = v_uid), 'America/Bogota');
  v_today date := niju_private.local_today(v_uid);
begin
  return (
    with p as (
      select x.*, public.word_bucket(x.interval_days, x.repetitions, x.lapses,
                                     x.correct_count, x.incorrect_count) as bucket
        from public.user_word_progress x where x.user_id = v_uid)
    select jsonb_build_object(
      'total',       (select count(*) from p),
      'due_now',     (select count(*) from p where p.due_at <= now()),
      'urgent',      (select count(*) from p where niju_private.is_urgent(p.due_at, p.bucket)),
      'learning',    (select count(*) from p where p.bucket = 'learning'),
      'reinforce',   (select count(*) from p where p.bucket = 'reinforce'),
      'review',      (select count(*) from p where p.bucket = 'review'),
      'mastered',    (select count(*) from p where p.bucket = 'mastered'),
      'average_ease',(select round(avg(p.ease_factor), 2) from p),
      'next_due_at', (select min(p.due_at) from p where p.due_at > now()),
      -- Próximos 7 días; el día 0 incluye los atrasados.
      'forecast',    (select jsonb_agg(jsonb_build_object('day', d::date, 'count',
                        (select count(*) from p
                          where case when d::date = v_today then (p.due_at at time zone v_tz)::date <= v_today
                                     else (p.due_at at time zone v_tz)::date = d::date end))
                        order by d)
                        from generate_series(v_today, v_today + 6, interval '1 day') d))
  );
end;
$$;

-- Palabras del estudiante con su estado SM-2. p_bucket: null (todas), 'due', 'urgent',
-- 'learning', 'reinforce', 'review' o 'mastered'.
create or replace function public.get_review_words(p_bucket text default null, p_limit integer default 50)
returns table (
  word_id integer, english text, spanish text, emoji text, pronunciation text,
  bucket text, urgent boolean, ease_factor numeric, interval_days integer, repetitions integer,
  lapses integer, correct_count integer, incorrect_count integer, last_quality smallint,
  first_seen_at timestamptz, last_reviewed_at timestamptz, due_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  with p as (
    select x.*, public.word_bucket(x.interval_days, x.repetitions, x.lapses,
                                   x.correct_count, x.incorrect_count) as b
      from public.user_word_progress x where x.user_id = niju_private.require_user())
  select w.id, w.english, w.spanish, w.emoji, w.pronunciation,
         p.b, niju_private.is_urgent(p.due_at, p.b), p.ease_factor, p.interval_days, p.repetitions,
         p.lapses, p.correct_count, p.incorrect_count, p.last_quality,
         p.first_seen_at, p.last_reviewed_at, p.due_at
    from p join public.words w on w.id = p.word_id
   where case coalesce(p_bucket, 'all')
           when 'all'    then true
           when 'due'    then p.due_at <= now()
           when 'urgent' then niju_private.is_urgent(p.due_at, p.b)
           else p.b = p_bucket
         end
   order by niju_private.is_urgent(p.due_at, p.b) desc, p.due_at
   limit least(greatest(coalesce(p_limit, 50), 1), 200);
$$;

-- Detalle de una palabra: estado SM-2 + últimas 20 respuestas.
create or replace function public.get_word_detail(p_word_id integer)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := niju_private.require_user();
  w     public.words;
  p     public.user_word_progress;
  v_b   text;
begin
  select * into w from public.words where id = p_word_id;
  if not found then
    raise exception 'word_not_found' using errcode = 'P0002';
  end if;
  select * into p from public.user_word_progress where user_id = v_uid and word_id = p_word_id;
  v_b := case when p.user_id is null then 'new'
              else public.word_bucket(p.interval_days, p.repetitions, p.lapses, p.correct_count, p.incorrect_count) end;
  return jsonb_build_object(
    'word_id', w.id, 'english', w.english, 'spanish', w.spanish, 'emoji', w.emoji,
    'pronunciation', w.pronunciation, 'part_of_speech', w.part_of_speech, 'level', w.level,
    'example_en', (select e.sentence_en from public.word_examples e where e.word_id = w.id order by e.sort_order, e.id limit 1),
    'example_es', (select e.sentence_es from public.word_examples e where e.word_id = w.id order by e.sort_order, e.id limit 1),
    'bucket', v_b,
    'urgent', coalesce(niju_private.is_urgent(p.due_at, v_b), false),
    'ease_factor', p.ease_factor, 'interval_days', p.interval_days, 'repetitions', p.repetitions,
    'lapses', p.lapses, 'correct_count', p.correct_count, 'incorrect_count', p.incorrect_count,
    'last_quality', p.last_quality, 'first_seen_at', p.first_seen_at,
    'last_reviewed_at', p.last_reviewed_at, 'due_at', p.due_at,
    'is_favorite', exists (select 1 from public.user_favorite_words f where f.user_id = v_uid and f.word_id = w.id),
    'history', (select coalesce(jsonb_agg(h order by h.created_at desc), '[]'::jsonb) from (
                  select a.created_at, a.exercise_type, a.is_correct, a.quality, a.response_ms
                    from public.attempts a where a.user_id = v_uid and a.word_id = w.id
                   order by a.created_at desc limit 20) h));
end;
$$;

-- Recompensas: nivel, racha e hitos, retos del día, metas de la semana y últimos premios.
create or replace function public.get_rewards()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := niju_private.require_user();
  v_today date := niju_private.local_today(v_uid);
  s       public.user_stats;
  v_level integer;
  v_cur   integer;
begin
  select * into s from public.user_stats where user_id = v_uid;
  v_level := public.level_for_xp(s.xp_total);
  v_cur := case when s.last_study_date >= v_today - 1 then s.current_streak else 0 end;
  return jsonb_build_object(
    'xp_total', s.xp_total, 'level', v_level,
    'level_title', public.level_title(v_level),
    'next_level_title', (select public.level_title(n) from generate_series(v_level + 1, 25) n
                          where public.level_title(n) <> public.level_title(v_level) limit 1),
    'level_start_xp', public.xp_for_level(v_level), 'next_level_xp', public.xp_for_level(v_level + 1),
    'current_streak', v_cur, 'longest_streak', s.longest_streak,
    'studied_today', coalesce(s.last_study_date = v_today, false),
    'streak_milestones', (select jsonb_agg(jsonb_build_object('days', d, 'xp', public.streak_bonus_for(d),
                                                              'reached', s.longest_streak >= d) order by d)
                            from unnest(array[3, 7, 14, 30, 60, 100]) d),
    'challenges', (select coalesce(jsonb_agg(jsonb_build_object(
                      'code', c.code, 'period', c.period, 'title', c.title, 'description', c.description,
                      'progress', least(c.progress, c.target), 'target', c.target,
                      'xp_reward', c.xp_reward, 'claimed', c.claimed,
                      'completed', c.claimed or c.progress >= c.target)), '[]'::jsonb)
                     from niju_private.current_challenges(v_uid) c),
    'week_start', niju_private.period_start(v_uid, 'weekly'),
    'achievements_unlocked', (select count(*) from public.user_achievements u where u.user_id = v_uid),
    'achievements_total', (select count(*) from public.achievements),
    'recent_rewards', (select coalesce(jsonb_agg(r order by r.created_at desc), '[]'::jsonb) from (
                         select x.reason, x.amount, x.ref, x.created_at
                           from public.xp_ledger x
                          where x.user_id = v_uid
                            and x.reason in ('lesson_complete', 'achievement', 'daily_challenge',
                                             'weekly_goal', 'streak_bonus', 'exam_pass')
                          order by x.created_at desc limit 15) r));
end;
$$;

-- Perfil completo del estudiante.
create or replace function public.get_profile_overview()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := niju_private.require_user();
  v_today date := niju_private.local_today(v_uid);
  v_prof  public.profiles;
  s       public.user_stats;
  v_level integer;
  v_tz    text;
begin
  select * into v_prof from public.profiles where id = v_uid;
  select * into s from public.user_stats where user_id = v_uid;
  v_level := public.level_for_xp(s.xp_total);
  v_tz := coalesce(v_prof.timezone, 'America/Bogota');
  return jsonb_build_object(
    'display_name', v_prof.display_name,
    'email', (select u.email from auth.users u where u.id = v_uid),
    'avatar_url', v_prof.avatar_url,
    'member_since', v_prof.created_at,
    'daily_goal_xp', v_prof.daily_goal_xp,
    'level', v_level, 'level_title', public.level_title(v_level), 'xp_total', s.xp_total,
    'level_start_xp', public.xp_for_level(v_level), 'next_level_xp', public.xp_for_level(v_level + 1),
    'lessons_completed', s.lessons_completed,
    'lessons_total', (select count(*) from public.lessons l where l.is_published),
    'words_learned', (select count(*) from public.user_word_progress p where p.user_id = v_uid),
    'words_mastered', (select count(*) from public.user_word_progress p where p.user_id = v_uid and p.interval_days >= 21),
    'current_streak', case when s.last_study_date >= v_today - 1 then s.current_streak else 0 end,
    'longest_streak', s.longest_streak,
    'study_ms_total', (select coalesce(sum(least(coalesce(a.response_ms, 0), 60000)), 0)
                         from public.attempts a where a.user_id = v_uid),
    'study_ms_week', (select coalesce(sum(least(coalesce(a.response_ms, 0), 60000)), 0)
                        from public.attempts a
                       where a.user_id = v_uid
                         and (a.created_at at time zone v_tz)::date >= niju_private.period_start(v_uid, 'weekly')),
    'sessions_completed', (select count(*) from public.study_sessions x
                            where x.user_id = v_uid and x.finished_at is not null),
    'answers_total', (select count(*) from public.attempts a where a.user_id = v_uid),
    'accuracy', (select case when count(*) = 0 then null
                             else round(100.0 * count(*) filter (where a.is_correct) / count(*), 1) end
                   from public.attempts a where a.user_id = v_uid),
    'perfect_sessions', s.perfect_sessions,
    'exams_passed', s.exams_passed,
    'challenges_completed', s.challenges_completed,
    'contexts_completed', (select count(*) from public.contexts c
                            where c.is_published and niju_private.exam_unlocked(v_uid, c.id)),
    'achievements_unlocked', (select count(*) from public.user_achievements u where u.user_id = v_uid),
    'achievements_total', (select count(*) from public.achievements));
end;
$$;

-- Historial de sesiones terminadas (más recientes primero).
create or replace function public.get_session_history(p_limit integer default 20)
returns table (
  session_id uuid, mode public.session_mode, lesson_title text, context_name text,
  started_at timestamptz, finished_at timestamptz, total integer, correct integer,
  score numeric, xp_earned integer, study_ms bigint)
language sql
stable
security definer
set search_path = ''
as $$
  select s.id, s.mode, l.title, c.name, s.started_at, s.finished_at,
         s.total_items::integer, s.correct_items::integer,
         case when s.total_items > 0 then round(100.0 * s.correct_items / s.total_items, 1) else 0 end,
         s.xp_earned,
         (select coalesce(sum(least(coalesce(a.response_ms, 0), 60000)), 0)::bigint
            from public.attempts a join public.session_items i on i.id = a.session_item_id
           where i.session_id = s.id)
    from public.study_sessions s
    left join public.lessons l on l.id = s.lesson_id
    left join public.contexts c on c.id = s.context_id
   where s.user_id = niju_private.require_user() and s.finished_at is not null
   order by s.finished_at desc
   limit least(greatest(coalesce(p_limit, 20), 1), 100);
$$;

-- ============================================================================
-- 9. PERMISOS
-- ============================================================================

-- La app puede leer sus ejercicios, pero NO la respuesta ni la solución.
revoke select on public.session_items from authenticated;
grant select (id, session_id, position, word_id, exercise_type, prompt, hint, options, answered_at)
  on public.session_items to authenticated;

revoke all on all functions in schema niju_private from public, anon, authenticated;

revoke execute on function
  public.start_session(public.session_mode, integer, smallint, integer, text),
  public.submit_answer(bigint, text, integer),
  public.finish_session(uuid),
  public.claim_rewards(),
  public.get_dashboard(),
  public.get_context_progress(),
  public.get_lesson_content(integer),
  public.get_favorites(),
  public.get_achievements(),
  public.get_review_overview(),
  public.get_review_words(text, integer),
  public.get_word_detail(integer),
  public.get_rewards(),
  public.get_profile_overview(),
  public.get_session_history(integer)
  from public, anon;

grant execute on function
  public.start_session(public.session_mode, integer, smallint, integer, text),
  public.submit_answer(bigint, text, integer),
  public.finish_session(uuid),
  public.claim_rewards(),
  public.get_dashboard(),
  public.get_context_progress(),
  public.get_lesson_content(integer),
  public.get_favorites(),
  public.get_achievements(),
  public.get_review_overview(),
  public.get_review_words(text, integer),
  public.get_word_detail(integer),
  public.get_rewards(),
  public.get_profile_overview(),
  public.get_session_history(integer)
  to authenticated;
