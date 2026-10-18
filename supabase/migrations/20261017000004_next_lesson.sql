-- =============================================================================
-- NiJu · Migración 4: "Continuar" (siguiente lección del estudiante)
-- Ejecutar después de la migración 3. No modifica tablas ni datos.
-- =============================================================================

-- Devuelve la lección que el estudiante debe hacer ahora:
--   · la primera lección DESBLOQUEADA y NO superada,
--   · prefiriendo el contexto de su última lección estudiada (para "continuar"),
--   · y, en igualdad, el orden del catálogo (contexto y luego lección).
-- Si ya superó todas las lecciones publicadas no devuelve filas.
create or replace function public.get_next_lesson()
returns table (
  lesson_id integer, lesson_slug text, lesson_title text, lesson_description text,
  level public.cefr_level, word_count integer,
  context_id smallint, context_slug text, context_name text,
  context_lessons_completed integer, context_lessons_total integer)
language sql
stable
security definer
set search_path = ''
as $$
  with me as (select niju_private.require_user() as uid),
  last_ctx as (
    select l.context_id
      from public.study_sessions s
      join public.lessons l on l.id = s.lesson_id, me
     where s.user_id = me.uid and s.mode = 'lesson'
     order by s.started_at desc
     limit 1)
  select l.id, l.slug, l.title, l.description, l.level,
         (select count(*) from public.lesson_words lw where lw.lesson_id = l.id)::integer,
         c.id, c.slug, c.name,
         (select count(*) from public.user_lesson_progress u
            join public.lessons l2 on l2.id = u.lesson_id
           where l2.context_id = c.id and u.user_id = me.uid
             and u.completed_at is not null)::integer,
         (select count(*) from public.lessons l2
           where l2.context_id = c.id and l2.is_published)::integer
    from me, public.lessons l
    join public.contexts c on c.id = l.context_id and c.is_published
   where l.is_published
     and not exists (select 1 from public.user_lesson_progress u
                      where u.user_id = me.uid and u.lesson_id = l.id
                        and u.completed_at is not null)
     and niju_private.lesson_unlocked(me.uid, l.id)
   order by (c.id is not distinct from (select context_id from last_ctx)) desc,
            c.sort_order, c.id, l.sort_order
   limit 1;
$$;

revoke execute on function public.get_next_lesson() from public, anon;
grant execute on function public.get_next_lesson() to authenticated;
revoke all on all functions in schema niju_private from public, anon, authenticated;
