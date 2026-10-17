-- =============================================================================
-- NiJu · Migración 1: esquema relacional
-- Base de datos: Supabase (PostgreSQL 15+)
--
-- Separación clave:
--   * CONTENIDO COMPARTIDO (contexts, words, word_examples, lessons,
--     lesson_words, achievements): igual para todos, solo lectura para la app.
--   * DATOS DE CADA ESTUDIANTE (profiles, user_stats, study_sessions,
--     session_items, attempts, user_word_progress, user_lesson_progress,
--     xp_ledger, user_achievements): cada fila pertenece a un usuario y solo
--     él puede leerla. Nadie escribe en ellas directamente: solo las funciones
--     del servidor (migración 2), para que puntos y progreso no se falsifiquen.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Tipos enumerados
-- ----------------------------------------------------------------------------
create type public.cefr_level as enum ('A1', 'A2', 'B1', 'B2', 'C1', 'C2');

create type public.part_of_speech as enum (
  'noun', 'verb', 'adjective', 'adverb', 'pronoun',
  'preposition', 'conjunction', 'interjection', 'phrase'
);

-- Tipos de ejercicio de la versión 1:
--   en_to_es   : se muestra la palabra en inglés, se elige el significado.
--   es_to_en   : se muestra el significado, se elige la palabra en inglés.
--   fill_blank : se muestra una oración con un hueco, se elige la palabra.
create type public.exercise_type as enum ('en_to_es', 'es_to_en', 'fill_blank');

-- Modos de sesión:
--   lesson   : estudiar una lección (palabras nuevas, en orden).
--   review   : repasar las palabras cuya fecha de repaso ya llegó (SM-2).
--   practice : práctica libre de un contexto (no adelanta el calendario SM-2).
create type public.session_mode as enum ('lesson', 'review', 'practice');

create type public.achievement_metric as enum (
  'lessons_completed', 'streak_days', 'words_mastered', 'correct_reviews'
);

-- Esquema privado para funciones internas (PostgREST no lo expone).
create schema if not exists niju_private;

-- ============================================================================
-- CONTENIDO COMPARTIDO
-- ============================================================================

create table public.contexts (
  id           smallint generated always as identity primary key,
  slug         text     not null unique check (slug ~ '^[a-z0-9_]+$'),
  name         text     not null check (length(name) between 1 and 60),
  description  text     not null default '',
  icon         text,                         -- nombre del drawable en la app
  sort_order   smallint not null default 0,
  is_published boolean  not null default true
);

create table public.words (
  id             integer generated always as identity primary key,
  english        text not null check (length(trim(english)) > 0),
  spanish        text not null check (length(trim(spanish)) > 0),
  pronunciation  text,                       -- IPA, opcional
  part_of_speech public.part_of_speech,
  level          public.cefr_level not null default 'A1',
  created_at     timestamptz not null default now(),
  constraint words_english_pos_unique unique nulls not distinct (english, part_of_speech)
);

create table public.word_examples (
  id          integer generated always as identity primary key,
  word_id     integer  not null references public.words (id) on delete cascade,
  context_id  smallint references public.contexts (id) on delete set null,
  sentence_en text     not null,
  sentence_es text     not null,
  sort_order  smallint not null default 0
);
create index word_examples_word_idx on public.word_examples (word_id);

create table public.lessons (
  id           integer generated always as identity primary key,
  context_id   smallint not null references public.contexts (id) on delete restrict,
  slug         text     not null unique check (slug ~ '^[a-z0-9_]+$'),
  title        text     not null,
  description  text     not null default '',
  level        public.cefr_level not null default 'A1',
  sort_order   smallint not null default 0,
  is_published boolean  not null default true,
  constraint lessons_context_order_unique unique (context_id, sort_order)
);

-- Una palabra puede aparecer en varias lecciones sin duplicarse.
create table public.lesson_words (
  lesson_id integer  not null references public.lessons (id) on delete cascade,
  word_id   integer  not null references public.words (id) on delete restrict,
  position  smallint not null check (position > 0),
  primary key (lesson_id, word_id),
  constraint lesson_words_position_unique unique (lesson_id, position)
);
create index lesson_words_word_idx on public.lesson_words (word_id);

create table public.achievements (
  code        text primary key check (code ~ '^[a-z0-9_]+$'),
  title       text    not null,
  description text    not null,
  metric      public.achievement_metric not null,
  threshold   integer not null check (threshold > 0),
  xp_reward   integer not null default 0 check (xp_reward >= 0),
  sort_order  smallint not null default 0
);

-- ============================================================================
-- DATOS DE CADA ESTUDIANTE
-- ============================================================================

create table public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  display_name  text not null default '' check (length(display_name) <= 60),
  avatar_url    text check (avatar_url is null or avatar_url ~ '^https://'),
  daily_goal_xp integer not null default 50 check (daily_goal_xp between 10 and 500),
  timezone      text not null default 'America/Bogota',
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- Contadores agregados. Solo los modifican las funciones del servidor.
create table public.user_stats (
  user_id           uuid primary key references public.profiles (id) on delete cascade,
  xp_total          integer not null default 0 check (xp_total >= 0),
  current_streak    integer not null default 0 check (current_streak >= 0),
  longest_streak    integer not null default 0 check (longest_streak >= 0),
  last_study_date   date,                    -- en la zona horaria del usuario
  lessons_completed integer not null default 0 check (lessons_completed >= 0),
  correct_reviews   integer not null default 0 check (correct_reviews >= 0),
  updated_at        timestamptz not null default now()
);

create table public.study_sessions (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles (id) on delete cascade,
  mode          public.session_mode not null,
  lesson_id     integer  references public.lessons (id) on delete set null,
  context_id    smallint references public.contexts (id) on delete set null,
  started_at    timestamptz not null default now(),
  finished_at   timestamptz,
  total_items   smallint not null default 0,
  correct_items smallint not null default 0,
  xp_earned     integer  not null default 0,
  constraint study_sessions_lesson_required check (mode <> 'lesson' or lesson_id is not null)
);
create index study_sessions_user_idx on public.study_sessions (user_id, started_at desc);

-- Ejercicios generados para una sesión. Fijarlos en el servidor permite
-- validar cada respuesta y que cada ejercicio se responda una sola vez.
create table public.session_items (
  id            bigint generated always as identity primary key,
  session_id    uuid     not null references public.study_sessions (id) on delete cascade,
  position      smallint not null check (position > 0),
  word_id       integer  not null references public.words (id) on delete cascade,
  exercise_type public.exercise_type not null,
  prompt        text     not null,
  hint          text,
  options       text[]   not null check (cardinality(options) between 2 and 4),
  answered_at   timestamptz,
  constraint session_items_position_unique unique (session_id, position)
);

-- Historial completo: un intento por ejercicio.
create table public.attempts (
  id              bigint generated always as identity primary key,
  user_id         uuid    not null references public.profiles (id) on delete cascade,
  session_item_id bigint  not null unique references public.session_items (id) on delete cascade,
  word_id         integer not null references public.words (id) on delete cascade,
  exercise_type   public.exercise_type not null,
  given_answer    text    not null,
  is_correct      boolean not null,
  was_due         boolean not null,   -- ¿la palabra era nueva o tocaba repasarla?
  xp_awarded      integer not null default 0,
  response_ms     integer check (response_ms is null or response_ms >= 0),
  created_at      timestamptz not null default now()
);
create index attempts_user_time_idx on public.attempts (user_id, created_at desc);
create index attempts_user_word_idx on public.attempts (user_id, word_id);

-- Estado de repetición espaciada (SM-2) de cada palabra para cada usuario.
create table public.user_word_progress (
  user_id          uuid    not null references public.profiles (id) on delete cascade,
  word_id          integer not null references public.words (id) on delete cascade,
  ease_factor      numeric(4, 2) not null default 2.50 check (ease_factor >= 1.30),
  interval_days    integer not null default 0 check (interval_days >= 0),
  repetitions      integer not null default 0 check (repetitions >= 0),
  lapses           integer not null default 0 check (lapses >= 0),
  correct_count    integer not null default 0 check (correct_count >= 0),
  incorrect_count  integer not null default 0 check (incorrect_count >= 0),
  first_seen_at    timestamptz not null default now(),
  last_reviewed_at timestamptz,
  due_at           timestamptz not null default now(),
  primary key (user_id, word_id)
);
create index user_word_progress_due_idx on public.user_word_progress (user_id, due_at);

create table public.user_lesson_progress (
  user_id        uuid    not null references public.profiles (id) on delete cascade,
  lesson_id      integer not null references public.lessons (id) on delete cascade,
  attempts_count integer not null default 0,
  best_score     numeric(5, 2) not null default 0 check (best_score between 0 and 100),
  completed_at   timestamptz,
  primary key (user_id, lesson_id)
);

-- Libro de puntos. La restricción única (usuario, motivo, referencia) impide
-- que la misma acción otorgue puntos dos veces.
create table public.xp_ledger (
  id         bigint generated always as identity primary key,
  user_id    uuid    not null references public.profiles (id) on delete cascade,
  amount     integer not null check (amount > 0),
  reason     text    not null check (reason in
               ('answer_due', 'answer_practice', 'lesson_complete', 'achievement')),
  ref        text    not null,
  created_at timestamptz not null default now(),
  constraint xp_ledger_once unique (user_id, reason, ref)
);
create index xp_ledger_user_time_idx on public.xp_ledger (user_id, created_at desc);

create table public.user_achievements (
  user_id          uuid not null references public.profiles (id) on delete cascade,
  achievement_code text not null references public.achievements (code) on delete cascade,
  unlocked_at      timestamptz not null default now(),
  primary key (user_id, achievement_code)
);

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Al registrarse en Supabase Auth se crean el perfil y las estadísticas.
create or replace function niju_private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Microsoft (Azure) y Google envían el nombre en "full_name" o "name".
  -- Google también envía "avatar_url"; Microsoft normalmente no.
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    left(coalesce(new.raw_user_meta_data ->> 'full_name',
                  new.raw_user_meta_data ->> 'name', ''), 60),
    case when new.raw_user_meta_data ->> 'avatar_url' like 'https://%'
         then new.raw_user_meta_data ->> 'avatar_url' end
  );
  insert into public.user_stats (user_id) values (new.id);
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function niju_private.handle_new_user();

-- Valida la zona horaria y actualiza updated_at.
create or replace function niju_private.profiles_before_write()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if not exists (select 1 from pg_catalog.pg_timezone_names where name = new.timezone) then
    raise exception 'invalid_timezone: %', new.timezone using errcode = '22023';
  end if;
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_before_write
  before insert or update on public.profiles
  for each row execute function niju_private.profiles_before_write();

-- ============================================================================
-- SEGURIDAD: Row Level Security
-- ============================================================================

alter table public.contexts             enable row level security;
alter table public.words                enable row level security;
alter table public.word_examples        enable row level security;
alter table public.lessons              enable row level security;
alter table public.lesson_words         enable row level security;
alter table public.achievements         enable row level security;
alter table public.profiles             enable row level security;
alter table public.user_stats           enable row level security;
alter table public.study_sessions       enable row level security;
alter table public.session_items        enable row level security;
alter table public.attempts             enable row level security;
alter table public.user_word_progress   enable row level security;
alter table public.user_lesson_progress enable row level security;
alter table public.xp_ledger            enable row level security;
alter table public.user_achievements    enable row level security;

-- Contenido: lectura pública (solo lo publicado). Sin políticas de escritura:
-- el contenido se administra desde el panel de Supabase o con service_role.
create policy contexts_read on public.contexts
  for select to anon, authenticated using (is_published);
create policy words_read on public.words
  for select to anon, authenticated using (true);
create policy word_examples_read on public.word_examples
  for select to anon, authenticated using (true);
create policy lessons_read on public.lessons
  for select to anon, authenticated using (is_published);
create policy lesson_words_read on public.lesson_words
  for select to anon, authenticated using (true);
create policy achievements_read on public.achievements
  for select to anon, authenticated using (true);

-- Perfil: el usuario lee y edita solo el suyo.
create policy profiles_select_own on public.profiles
  for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- Datos de aprendizaje: solo lectura del propietario.
create policy user_stats_select_own on public.user_stats
  for select to authenticated using (user_id = (select auth.uid()));
create policy study_sessions_select_own on public.study_sessions
  for select to authenticated using (user_id = (select auth.uid()));
create policy session_items_select_own on public.session_items
  for select to authenticated using (exists (
    select 1 from public.study_sessions s
    where s.id = session_id and s.user_id = (select auth.uid())));
create policy attempts_select_own on public.attempts
  for select to authenticated using (user_id = (select auth.uid()));
create policy user_word_progress_select_own on public.user_word_progress
  for select to authenticated using (user_id = (select auth.uid()));
create policy user_lesson_progress_select_own on public.user_lesson_progress
  for select to authenticated using (user_id = (select auth.uid()));
create policy xp_ledger_select_own on public.xp_ledger
  for select to authenticated using (user_id = (select auth.uid()));
create policy user_achievements_select_own on public.user_achievements
  for select to authenticated using (user_id = (select auth.uid()));

-- Privilegios por columna: el usuario solo puede cambiar estos campos del perfil.
revoke insert, update, delete on public.profiles from anon, authenticated;
grant update (display_name, avatar_url, daily_goal_xp, timezone)
  on public.profiles to authenticated;

-- Ninguna tabla de progreso se escribe desde la app.
revoke insert, update, delete, truncate on
  public.user_stats, public.study_sessions, public.session_items, public.attempts,
  public.user_word_progress, public.user_lesson_progress, public.xp_ledger,
  public.user_achievements,
  public.contexts, public.words, public.word_examples, public.lessons,
  public.lesson_words, public.achievements
  from anon, authenticated;

-- Los anónimos no ven datos de usuarios.
revoke select on
  public.profiles, public.user_stats, public.study_sessions, public.session_items,
  public.attempts, public.user_word_progress, public.user_lesson_progress,
  public.xp_ledger, public.user_achievements
  from anon;

-- Lectura explícita (por si el proyecto no concede privilegios por defecto).
grant usage on schema public to anon, authenticated;
grant select on
  public.contexts, public.words, public.word_examples, public.lessons,
  public.lesson_words, public.achievements
  to anon, authenticated;
grant select on
  public.profiles, public.user_stats, public.study_sessions, public.session_items,
  public.attempts, public.user_word_progress, public.user_lesson_progress,
  public.xp_ledger, public.user_achievements
  to authenticated;

revoke all on schema niju_private from public, anon, authenticated;
