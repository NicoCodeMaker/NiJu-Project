-- =============================================================================
-- NiJu · Migración 2: lógica de negocio en el servidor
--
-- Toda la lógica que afecta a puntos, progreso y repasos vive aquí, no en la
-- app. Así una app modificada no puede regalarse XP ni marcar palabras como
-- dominadas. La app solo llama a estas funciones (RPC de Supabase):
--
--   start_session(mode, lesson_id, context_id, limit)  -> sesión + ejercicios
--   submit_answer(item_id, answer, response_ms)        -> corrección + SM-2 + XP
--   finish_session(session_id)                         -> resumen + logros
--   get_dashboard()                                    -> datos de Inicio/Perfil
--   get_context_progress()                             -> pantalla Ruta
--   get_context_lessons(context_id)                    -> lecciones con estado
--   get_daily_activity(days)                           -> evolución diaria
--   get_struggling_words(limit)                        -> palabras con refuerzo
--   reset_progress(word_id)                            -> reiniciar progreso
--   delete_my_account()                                -> borrar cuenta y datos
-- =============================================================================

-- ============================================================================
-- REGLAS (constantes documentadas)
-- ============================================================================
--  SM-2 simplificado con respuesta binaria:
--    acierto -> calidad 4 (el factor de facilidad no cambia)
--    fallo   -> calidad 2 (factor -0.32, mínimo 1.30; repeticiones a 0;
--                          repaso al día siguiente; lapses + 1)
--    intervalos tras aciertos seguidos: 1, 6, round(intervalo * factor)...
--  Una palabra está DOMINADA cuando su intervalo es >= 21 días, es decir,
--  se ha recordado en repasos espaciados durante al menos tres semanas.
--  Practicar una palabra que aún no toca repasar NO adelanta su calendario
--  (evita inflar el progreso estudiando todo el mismo día), pero un fallo sí
--  cuenta como olvido.
--
--  XP:
--    acierto en palabra nueva o con repaso vencido ............ 10
--    acierto en práctica de palabra no vencida (1 vez/palabra/día)  2
--    lección superada por primera vez (>= 70 % de aciertos) ..... 50
--    logro desbloqueado ........................ según tabla achievements
--  Nivel n requiere 100 * (n - 1)^2 XP  (2: 100, 3: 400, 4: 900, 5: 1600...)
--  Racha: días calendario seguidos (zona horaria del usuario) con al menos
--  una respuesta enviada.

-- ----------------------------------------------------------------------------
-- Utilidades puras (sin acceso a tablas): fáciles de probar
-- ----------------------------------------------------------------------------

create or replace function niju_private.normalize_answer(p text)
returns text
language sql
immutable
set search_path = ''
as $$
  select pg_catalog.btrim(pg_catalog.regexp_replace(
           pg_catalog.regexp_replace(
             pg_catalog.translate(pg_catalog.lower(coalesce(p, '')),
                                  'áéíóúüàèìòùâêîôû', 'aeiouuaeiouaeiou'),
             '[[:punct:]¿¡]', '', 'g'),
           '\s+', ' ', 'g'));
$$;

create or replace function public.level_for_xp(p_xp integer)
returns integer
language sql
immutable
set search_path = ''
as $$
  select (pg_catalog.floor(pg_catalog.sqrt(greatest(p_xp, 0) / 100.0)))::integer + 1;
$$;

create or replace function public.xp_for_level(p_level integer)
returns integer
language sql
immutable
set search_path = ''
as $$
  select 100 * (greatest(p_level, 1) - 1) * (greatest(p_level, 1) - 1);
$$;

create or replace function public.mastery_status(p_interval_days integer, p_repetitions integer)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_interval_days is null then 'new'
    when p_interval_days >= 21  then 'mastered'
    when p_repetitions   >= 2   then 'review'
    else 'learning'
  end;
$$;

-- Paso SM-2. Devuelve el nuevo estado; no toca tablas.
create or replace function niju_private.sm2_next(
  p_ease numeric, p_interval integer, p_repetitions integer, p_correct boolean,
  out ease_factor numeric, out interval_days integer, out repetitions integer)
language plpgsql
immutable
set search_path = ''
as $$
declare
  q integer := case when p_correct then 4 else 2 end;
begin
  ease_factor := greatest(1.30, round(p_ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)), 2));
  if p_correct then
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

-- ----------------------------------------------------------------------------
-- Utilidades internas con acceso a tablas
-- ----------------------------------------------------------------------------

create or replace function niju_private.require_user()
returns uuid
language plpgsql
stable
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  return v_uid;
end;
$$;

create or replace function niju_private.local_today(p_user uuid)
returns date
language sql
stable
set search_path = ''
as $$
  select (now() at time zone coalesce(
            (select p.timezone from public.profiles p where p.id = p_user),
            'America/Bogota'))::date;
$$;

-- Otorga XP una sola vez por (usuario, motivo, referencia). Devuelve lo otorgado.
create or replace function niju_private.grant_xp(
  p_user uuid, p_amount integer, p_reason text, p_ref text)
returns integer
language plpgsql
set search_path = ''
as $$
declare
  v_inserted integer;
begin
  if p_amount <= 0 then
    return 0;
  end if;
  insert into public.xp_ledger (user_id, amount, reason, ref)
  values (p_user, p_amount, p_reason, p_ref)
  on conflict on constraint xp_ledger_once do nothing
  returning amount into v_inserted;

  if v_inserted is null then
    return 0;
  end if;
  update public.user_stats
     set xp_total = xp_total + v_inserted, updated_at = now()
   where user_id = p_user;
  return v_inserted;
end;
$$;

-- Actualiza la racha con días calendario en la zona horaria del usuario.
create or replace function niju_private.touch_streak(p_user uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  v_today date := niju_private.local_today(p_user);
begin
  update public.user_stats s
     set current_streak = case
           when s.last_study_date = v_today     then s.current_streak
           when s.last_study_date = v_today - 1 then s.current_streak + 1
           else 1
         end,
         longest_streak = greatest(s.longest_streak, case
           when s.last_study_date = v_today     then s.current_streak
           when s.last_study_date = v_today - 1 then s.current_streak + 1
           else 1
         end),
         last_study_date = v_today,
         updated_at = now()
   where s.user_id = p_user;
end;
$$;

-- Desbloquea los logros alcanzados. Devuelve los nuevos.
create or replace function niju_private.check_achievements(p_user uuid)
returns table (code text, title text, xp_reward integer)
language plpgsql
set search_path = ''
as $$
declare
  v_stats    public.user_stats;
  v_mastered integer;
  a          public.achievements;
  v_value    integer;
begin
  select * into v_stats from public.user_stats where user_id = p_user;
  select count(*) into v_mastered
    from public.user_word_progress
   where user_id = p_user and interval_days >= 21;

  for a in
    select ach.* from public.achievements ach
     where not exists (select 1 from public.user_achievements ua
                        where ua.user_id = p_user and ua.achievement_code = ach.code)
     order by ach.sort_order
  loop
    v_value := case a.metric
      when 'lessons_completed' then v_stats.lessons_completed
      when 'streak_days'       then v_stats.longest_streak
      when 'words_mastered'    then v_mastered
      when 'correct_reviews'   then v_stats.correct_reviews
    end;
    if v_value >= a.threshold then
      insert into public.user_achievements (user_id, achievement_code)
      values (p_user, a.code) on conflict do nothing;
      perform niju_private.grant_xp(p_user, a.xp_reward, 'achievement', a.code);
      code := a.code; title := a.title; xp_reward := a.xp_reward;
      return next;
    end if;
  end loop;
end;
$$;

-- ¿El usuario puede abrir esta lección? La primera de cada contexto siempre;
-- las siguientes cuando la anterior (por sort_order) está superada.
create or replace function niju_private.lesson_unlocked(p_user uuid, p_lesson_id integer)
returns boolean
language sql
stable
set search_path = ''
as $$
  with l as (select * from public.lessons where id = p_lesson_id and is_published),
  prev as (
    select p.id from public.lessons p, l
     where p.context_id = l.context_id and p.is_published and p.sort_order < l.sort_order
     order by p.sort_order desc limit 1)
  select exists (select 1 from l)
     and (not exists (select 1 from prev)
          or exists (select 1 from public.user_lesson_progress ulp, prev
                      where ulp.user_id = p_user and ulp.lesson_id = prev.id
                        and ulp.completed_at is not null));
$$;

-- Construye el enunciado, la pista y las opciones de un ejercicio.
-- Si se pide fill_blank y no hay una oración que contenga la palabra,
-- se cambia a es_to_en.
create or replace function niju_private.build_exercise(
  p_word_id integer, p_type public.exercise_type, p_context_id smallint,
  out exercise_type public.exercise_type, out prompt text, out hint text,
  out options text[])
language plpgsql
volatile
set search_path = ''
as $$
declare
  w         public.words;
  v_pattern text;
  v_answer  text;
  v_distr   text[];
begin
  select * into strict w from public.words where id = p_word_id;
  exercise_type := p_type;

  if p_type = 'fill_blank' then
    v_pattern := '\m' || regexp_replace(w.english, '([.^$*+?()\[\]{}|\\-])', '\\\1', 'g') || '\M';
    select regexp_replace(e.sentence_en, v_pattern, '_____', 'i'), e.sentence_es
      into prompt, hint
      from public.word_examples e
     where e.word_id = w.id and e.sentence_en ~* v_pattern
     order by (e.context_id is not distinct from p_context_id) desc, e.sort_order
     limit 1;
    if prompt is null then
      exercise_type := 'es_to_en';
    end if;
  end if;

  if exercise_type = 'en_to_es' then
    prompt := w.english; hint := w.pronunciation; v_answer := w.spanish;
  elsif exercise_type = 'es_to_en' then
    prompt := w.spanish; hint := null; v_answer := w.english;
  else
    v_answer := w.english;   -- fill_blank: prompt/hint ya asignados
  end if;

  -- Dos distractores distintos de la respuesta, preferentemente del mismo
  -- contexto y de la misma categoría gramatical.
  select array_agg(d.ans) into v_distr
    from (
      select c.ans
        from (
          select distinct on (niju_private.normalize_answer(
                   case when exercise_type = 'en_to_es' then o.spanish else o.english end))
                 case when exercise_type = 'en_to_es' then o.spanish else o.english end as ans,
                 exists (select 1 from public.lesson_words lw
                           join public.lessons l on l.id = lw.lesson_id
                          where lw.word_id = o.id and l.context_id = p_context_id) as same_ctx,
                 (o.part_of_speech is not distinct from w.part_of_speech) as same_pos
            from public.words o
           where o.id <> w.id
             and niju_private.normalize_answer(
                   case when exercise_type = 'en_to_es' then o.spanish else o.english end)
                 <> niju_private.normalize_answer(v_answer)
        ) c
       order by c.same_ctx desc, c.same_pos desc, random()
       limit 2
    ) d;

  select array_agg(x order by random()) into options
    from unnest(coalesce(v_distr, '{}'::text[]) || v_answer) as x;
end;
$$;

-- ============================================================================
-- RPC PÚBLICAS
-- ============================================================================

-- Inicia una sesión y devuelve sus ejercicios (sin las respuestas).
--   lesson   : requiere p_lesson_id; usa las palabras de la lección en orden.
--   review   : palabras con repaso vencido, las más atrasadas primero.
--   practice : palabras al azar del contexto (o de todo el catálogo).
create or replace function public.start_session(
  p_mode       public.session_mode,
  p_lesson_id  integer  default null,
  p_context_id smallint default null,
  p_limit      integer  default 10)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := niju_private.require_user();
  v_limit   integer := least(greatest(coalesce(p_limit, 10), 1), 20);
  v_ctx     smallint := p_context_id;
  v_session uuid;
  v_words   integer[];
  v_pos     integer := 0;
  v_word    integer;
  v_type    public.exercise_type;
  v_ex      record;
  v_types   public.exercise_type[] := array['en_to_es', 'es_to_en', 'fill_blank']::public.exercise_type[];
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

  elsif p_mode = 'review' then
    select array_agg(x.word_id order by x.due_at) into v_words
      from (select p.word_id, p.due_at
              from public.user_word_progress p
             where p.user_id = v_uid and p.due_at <= now()
             order by p.due_at
             limit v_limit) x;

  else -- practice
    select array_agg(x.id) into v_words
      from (select distinct w.id, random() as r
              from public.words w
              join public.lesson_words lw on lw.word_id = w.id
              join public.lessons l on l.id = lw.lesson_id and l.is_published
             where v_ctx is null or l.context_id = v_ctx
             order by r
             limit v_limit) x;
  end if;

  if v_words is null or cardinality(v_words) = 0 then
    raise exception '%', case p_mode when 'review' then 'no_due_reviews' else 'no_words' end
      using errcode = 'P0002';
  end if;

  insert into public.study_sessions (user_id, mode, lesson_id, context_id, total_items)
  values (v_uid, p_mode, case when p_mode = 'lesson' then p_lesson_id end,
          v_ctx, cardinality(v_words))
  returning id into v_session;

  foreach v_word in array v_words loop
    v_pos := v_pos + 1;
    v_type := case
      when p_mode = 'lesson' then v_types[1 + (v_pos - 1) % 3]   -- rotación fija
      else v_types[1 + floor(random() * 3)::integer]              -- variado
    end;
    select * into v_ex from niju_private.build_exercise(v_word, v_type, v_ctx);
    insert into public.session_items
      (session_id, position, word_id, exercise_type, prompt, hint, options)
    values (v_session, v_pos, v_word, v_ex.exercise_type, v_ex.prompt, v_ex.hint, v_ex.options);
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

-- Corrige una respuesta, actualiza SM-2, XP y racha. Cada ejercicio se
-- responde una sola vez.
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
  v_sm2      record;
  v_xp       integer := 0;
  v_status   text;
  v_today    date;
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
  if p_answer is null or length(p_answer) > 200 then
    raise exception 'invalid_answer' using errcode = '22023';
  end if;

  select * into strict v_word from public.words where id = v_item.word_id;
  v_correct_answer := case when v_item.exercise_type = 'en_to_es'
                           then v_word.spanish else v_word.english end;
  v_is_correct := niju_private.normalize_answer(p_answer)
                  = niju_private.normalize_answer(v_correct_answer);

  select * into v_prog from public.user_word_progress
   where user_id = v_uid and word_id = v_word.id for update;
  v_was_due := (v_prog.user_id is null) or v_prog.due_at <= now();

  if v_prog.user_id is null then
    -- Primera vez que el usuario ve esta palabra.
    select * into v_sm2 from niju_private.sm2_next(2.50, 0, 0, v_is_correct);
    insert into public.user_word_progress
      (user_id, word_id, ease_factor, interval_days, repetitions, lapses,
       correct_count, incorrect_count, last_reviewed_at, due_at)
    values (v_uid, v_word.id, v_sm2.ease_factor, v_sm2.interval_days, v_sm2.repetitions,
            case when v_is_correct then 0 else 1 end,
            case when v_is_correct then 1 else 0 end,
            case when v_is_correct then 0 else 1 end,
            now(), now() + make_interval(days => v_sm2.interval_days))
    returning * into v_prog;

  elsif v_was_due or not v_is_correct then
    -- Repaso que tocaba, o fallo (un fallo siempre cuenta como olvido).
    select * into v_sm2
      from niju_private.sm2_next(v_prog.ease_factor, v_prog.interval_days,
                                 v_prog.repetitions, v_is_correct);
    update public.user_word_progress
       set ease_factor      = v_sm2.ease_factor,
           interval_days    = v_sm2.interval_days,
           repetitions      = v_sm2.repetitions,
           lapses           = lapses + case when v_is_correct then 0 else 1 end,
           correct_count    = correct_count + case when v_is_correct then 1 else 0 end,
           incorrect_count  = incorrect_count + case when v_is_correct then 0 else 1 end,
           last_reviewed_at = now(),
           due_at           = now() + make_interval(days => v_sm2.interval_days)
     where user_id = v_uid and word_id = v_word.id
     returning * into v_prog;

    if v_is_correct then
      update public.user_stats set correct_reviews = correct_reviews + 1
       where user_id = v_uid;
    end if;

  else
    -- Acierto en una palabra que aún no tocaba: se registra, sin mover el calendario.
    update public.user_word_progress
       set correct_count = correct_count + 1
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
     is_correct, was_due, xp_awarded, response_ms)
  values (v_uid, p_item_id, v_word.id, v_item.exercise_type, p_answer,
          v_is_correct, v_was_due, v_xp, p_response_ms);

  update public.session_items set answered_at = now() where id = p_item_id;
  update public.study_sessions
     set correct_items = correct_items + case when v_is_correct then 1 else 0 end,
         xp_earned     = xp_earned + v_xp
   where id = v_session.id;

  perform niju_private.touch_streak(v_uid);
  v_status := public.mastery_status(v_prog.interval_days, v_prog.repetitions);

  return jsonb_build_object(
    'is_correct',     v_is_correct,
    'correct_answer', v_correct_answer,
    'xp_awarded',     v_xp,
    'was_due',        v_was_due,
    'status',         v_status,
    'interval_days',  v_prog.interval_days,
    'next_due_at',    v_prog.due_at);
end;
$$;

-- Cierra la sesión (idempotente) y devuelve el resumen.
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
  v_new_ach   jsonb := '[]'::jsonb;
  v_ach_xp    integer := 0;
  v_stats     public.user_stats;
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

    select coalesce(jsonb_agg(jsonb_build_object('code', c.code, 'title', c.title,
                                                 'xp_reward', c.xp_reward)), '[]'::jsonb),
           coalesce(sum(c.xp_reward), 0)
      into v_new_ach, v_ach_xp
      from niju_private.check_achievements(v_uid) c;

    update public.study_sessions
       set finished_at = now(), xp_earned = xp_earned + v_bonus + v_ach_xp
     where id = p_session_id
     returning * into v_session;
  end if;

  select * into v_stats from public.user_stats where user_id = v_uid;

  return jsonb_build_object(
    'session_id',       v_session.id,
    'mode',             v_session.mode,
    'total',            v_session.total_items,
    'answered',         v_answered,
    'correct',          v_session.correct_items,
    'score',            v_score,
    'xp_earned',        v_session.xp_earned,
    'lesson_completed', v_completed,
    'new_achievements', v_new_ach,
    'xp_total',         v_stats.xp_total,
    'level',            public.level_for_xp(v_stats.xp_total));
end;
$$;

-- Datos para Inicio y Perfil.
--   words_seen      : palabras que aparecieron en alguna sesión del usuario.
--   words_practiced : palabras con al menos una respuesta.
--   words_mastered  : palabras con intervalo SM-2 >= 21 días.
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
    'words_seen',      (select count(distinct i.word_id) from public.session_items i
                          join public.study_sessions s on s.id = i.session_id
                         where s.user_id = v_uid),
    'words_practiced', (select count(*) from public.user_word_progress p
                         where p.user_id = v_uid),
    'words_mastered',  (select count(*) from public.user_word_progress p
                         where p.user_id = v_uid and p.interval_days >= 21),
    'accuracy',        (select case when count(*) = 0 then null
                                    else round(100.0 * count(*) filter (where a.is_correct) / count(*), 1) end
                          from public.attempts a where a.user_id = v_uid));
end;
$$;

-- Progreso por contexto (pantalla Ruta).
create or replace function public.get_context_progress()
returns table (
  context_id smallint, slug text, name text, description text, icon text,
  lessons_total integer, lessons_completed integer,
  words_total integer, words_practiced integer, words_mastered integer,
  percent_complete numeric)
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
  select c.id, c.slug, c.name, c.description, c.icon,
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
                      where l.context_id = c.id and l.is_published), 0), 0), 0)
    from public.contexts c, me
   where c.is_published
   order by c.sort_order, c.id;
$$;

-- Lecciones de un contexto con su estado. La primera siempre está abierta;
-- cada lección siguiente se abre al completar la anterior.
create or replace function public.get_context_lessons(p_context_id smallint)
returns table (
  lesson_id integer, slug text, title text, description text, level public.cefr_level,
  sort_order smallint, word_count integer, best_score numeric,
  completed boolean, unlocked boolean)
language sql
stable
security definer
set search_path = ''
as $$
  with me as (select niju_private.require_user() as uid),
  ls as (
    select l.*,
           (select count(*) from public.lesson_words lw where lw.lesson_id = l.id)::integer as wc,
           ulp.best_score as bs,
           (ulp.completed_at is not null) as done
      from public.lessons l
      cross join me
      left join public.user_lesson_progress ulp
        on ulp.lesson_id = l.id and ulp.user_id = me.uid
     where l.context_id = p_context_id and l.is_published)
  select ls.id, ls.slug, ls.title, ls.description, ls.level, ls.sort_order, ls.wc,
         coalesce(ls.bs, 0), coalesce(ls.done, false),
         coalesce(lag(ls.done) over (order by ls.sort_order), true)
    from ls
   order by ls.sort_order;
$$;

-- Actividad diaria de los últimos p_days días (incluye días sin actividad).
create or replace function public.get_daily_activity(p_days integer default 30)
returns table (day date, answers integer, correct integer, xp integer)
language sql
stable
security definer
set search_path = ''
as $$
  with me as (
    select niju_private.require_user() as uid,
           coalesce((select p.timezone from public.profiles p
                      where p.id = auth.uid()), 'America/Bogota') as tz),
  days as (
    select generate_series(niju_private.local_today(me.uid)
                             - (least(greatest(p_days, 1), 365) - 1),
                           niju_private.local_today(me.uid), interval '1 day')::date as d
      from me)
  select days.d,
         (select count(*) from public.attempts a, me
           where a.user_id = me.uid and (a.created_at at time zone me.tz)::date = days.d)::integer,
         (select count(*) from public.attempts a, me
           where a.user_id = me.uid and a.is_correct
             and (a.created_at at time zone me.tz)::date = days.d)::integer,
         (select coalesce(sum(x.amount), 0) from public.xp_ledger x, me
           where x.user_id = me.uid and (x.created_at at time zone me.tz)::date = days.d)::integer
    from days
   order by days.d;
$$;

-- Palabras que requieren refuerzo: 3 o más olvidos, o más fallos que aciertos.
create or replace function public.get_struggling_words(p_limit integer default 20)
returns table (word_id integer, english text, spanish text, lapses integer,
               correct_count integer, incorrect_count integer, due_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  select w.id, w.english, w.spanish, p.lapses, p.correct_count, p.incorrect_count, p.due_at
    from public.user_word_progress p
    join public.words w on w.id = p.word_id
   where p.user_id = niju_private.require_user()
     and (p.lapses >= 3 or (p.incorrect_count >= 2 and p.incorrect_count > p.correct_count))
   order by p.lapses desc, p.incorrect_count desc
   limit least(greatest(p_limit, 1), 100);
$$;

-- Reinicia el progreso SM-2 de una palabra (o de todas si p_word_id es null).
-- El historial de intentos y el XP ya ganado se conservan.
create or replace function public.reset_progress(p_word_id integer default null)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := niju_private.require_user();
  v_n   integer;
begin
  delete from public.user_word_progress
   where user_id = v_uid and (p_word_id is null or word_id = p_word_id);
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

-- Elimina la cuenta y, en cascada, todos los datos personales.
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := niju_private.require_user();
begin
  delete from auth.users where id = v_uid;
end;
$$;

-- ============================================================================
-- PERMISOS DE EJECUCIÓN
-- ============================================================================
revoke all on all functions in schema niju_private from public, anon, authenticated;

revoke execute on function
  public.start_session(public.session_mode, integer, smallint, integer),
  public.submit_answer(bigint, text, integer),
  public.finish_session(uuid),
  public.get_dashboard(),
  public.get_context_progress(),
  public.get_context_lessons(smallint),
  public.get_daily_activity(integer),
  public.get_struggling_words(integer),
  public.reset_progress(integer),
  public.delete_my_account()
  from public, anon;

grant execute on function
  public.start_session(public.session_mode, integer, smallint, integer),
  public.submit_answer(bigint, text, integer),
  public.finish_session(uuid),
  public.get_dashboard(),
  public.get_context_progress(),
  public.get_context_lessons(smallint),
  public.get_daily_activity(integer),
  public.get_struggling_words(integer),
  public.reset_progress(integer),
  public.delete_my_account()
  to authenticated;
