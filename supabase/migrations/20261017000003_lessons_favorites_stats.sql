-- =============================================================================
-- NiJu · Migración 3: contenido de lecciones, favoritos y logros
--
--   get_lesson_content(lesson_id)  -> tarjetas de presentación de una lección
--   toggle_favorite(word_id)       -> marca o desmarca una palabra favorita
--   get_favorites()                -> palabras favoritas con su estado
--   get_achievements()             -> todos los logros con avance y fecha
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Favoritos (dato personal; se escribe solo con toggle_favorite)
-- ----------------------------------------------------------------------------
create table public.user_favorite_words (
  user_id    uuid    not null references public.profiles (id) on delete cascade,
  word_id    integer not null references public.words (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, word_id)
);

alter table public.user_favorite_words enable row level security;
create policy user_favorite_words_select_own on public.user_favorite_words
  for select to authenticated using (user_id = (select auth.uid()));
revoke insert, update, delete, truncate on public.user_favorite_words from anon, authenticated;
revoke select on public.user_favorite_words from anon;
grant select on public.user_favorite_words to authenticated;

-- ----------------------------------------------------------------------------
-- Tarjetas de una lección: palabra, pronunciación, significado y un ejemplo
-- (preferentemente del mismo contexto). Respeta el bloqueo de lecciones.
-- ----------------------------------------------------------------------------
create or replace function public.get_lesson_content(p_lesson_id integer)
returns table (
  word_id integer, "position" smallint, english text, spanish text,
  pronunciation text, part_of_speech public.part_of_speech,
  sentence_en text, sentence_es text, is_favorite boolean, status text)
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
         public.mastery_status(p.interval_days, coalesce(p.repetitions, 0))
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

-- Marca o desmarca una favorita. Devuelve el estado final (true = favorita).
create or replace function public.toggle_favorite(p_word_id integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := niju_private.require_user();
begin
  if not exists (select 1 from public.words where id = p_word_id) then
    raise exception 'word_not_found' using errcode = 'P0002';
  end if;
  delete from public.user_favorite_words where user_id = v_uid and word_id = p_word_id;
  if found then
    return false;
  end if;
  insert into public.user_favorite_words (user_id, word_id) values (v_uid, p_word_id);
  return true;
end;
$$;

create or replace function public.get_favorites()
returns table (
  word_id integer, english text, spanish text, pronunciation text,
  sentence_en text, sentence_es text, status text, added_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  select w.id, w.english, w.spanish, w.pronunciation,
         ex.sentence_en, ex.sentence_es,
         public.mastery_status(p.interval_days, coalesce(p.repetitions, 0)),
         f.created_at
    from public.user_favorite_words f
    join public.words w on w.id = f.word_id
    left join public.user_word_progress p on p.user_id = f.user_id and p.word_id = w.id
    left join lateral (
      select e.sentence_en, e.sentence_es from public.word_examples e
       where e.word_id = w.id order by e.sort_order, e.id limit 1) ex on true
   where f.user_id = niju_private.require_user()
   order by f.created_at desc;
$$;

-- Todos los logros con el avance actual del usuario.
create or replace function public.get_achievements()
returns table (
  code text, title text, description text, metric public.achievement_metric,
  threshold integer, xp_reward integer, current_value integer, unlocked_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  with me as (select niju_private.require_user() as uid),
  st as (select s.* from public.user_stats s, me where s.user_id = me.uid),
  mastered as (select count(*)::integer as n from public.user_word_progress p, me
                where p.user_id = me.uid and p.interval_days >= 21)
  select a.code, a.title, a.description, a.metric, a.threshold, a.xp_reward,
         least(a.threshold, case a.metric
           when 'lessons_completed' then coalesce((select lessons_completed from st), 0)
           when 'streak_days'       then coalesce((select longest_streak from st), 0)
           when 'words_mastered'    then (select n from mastered)
           when 'correct_reviews'   then coalesce((select correct_reviews from st), 0)
         end),
         ua.unlocked_at
    from public.achievements a
    cross join me
    left join public.user_achievements ua
      on ua.achievement_code = a.code and ua.user_id = me.uid
   order by (ua.unlocked_at is null), a.sort_order;
$$;

-- ----------------------------------------------------------------------------
-- Logros (reglas de gamificación; forman parte del sistema, no del contenido)
-- "Ruta completa" exige 6 lecciones: ajústalo si cambia el número de lecciones.
-- ----------------------------------------------------------------------------
insert into public.achievements (code, title, description, metric, threshold, xp_reward, sort_order) values
  ('first_lesson', 'Primer paso',      'Completa tu primera lección.',              'lessons_completed', 1,  20, 1),
  ('all_lessons',  'Ruta completa',    'Completa 6 lecciones.',                 'lessons_completed', 6, 100, 2),
  ('streak_3',     'Constancia',       'Estudia 3 días seguidos.',                  'streak_days',       3,  30, 3),
  ('streak_7',     'Una semana',       'Estudia 7 días seguidos.',                  'streak_days',       7,  70, 4),
  ('reviews_25',   'Buena memoria',    'Acierta 25 repasos programados.',           'correct_reviews',  25,  50, 5),
  ('mastered_10',  'Vocabulario firme','Domina 10 palabras (repaso a 21+ días).',   'words_mastered',   10, 100, 6)
on conflict (code) do nothing;

-- ----------------------------------------------------------------------------
-- Permisos
-- ----------------------------------------------------------------------------
revoke execute on function
  public.get_lesson_content(integer), public.toggle_favorite(integer),
  public.get_favorites(), public.get_achievements()
  from public, anon;
grant execute on function
  public.get_lesson_content(integer), public.toggle_favorite(integer),
  public.get_favorites(), public.get_achievements()
  to authenticated;
revoke all on all functions in schema niju_private from public, anon, authenticated;
