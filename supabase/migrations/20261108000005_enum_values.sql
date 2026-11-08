-- =============================================================================
-- NiJu · Migración 5: nuevos valores de tipos enumerados
--
-- Va en un archivo propio porque PostgreSQL no permite USAR un valor nuevo de
-- un enum en la misma transacción en que se agrega. Ejecuta este archivo
-- (solo) y después la migración 6.
-- Es segura de repetir (IF NOT EXISTS) y no modifica datos.
-- =============================================================================

-- Tipos de ejercicio v2 (los tres de la v1 se conservan):
--   image_match   : se muestra una imagen (emoji) y se elige la palabra en inglés.
--   listen_choose : se escucha la palabra y se elige cuál es.
--   type_answer   : se muestra el significado y se ESCRIBE la palabra en inglés.
--   word_order    : se ordenan las palabras de una oración.
--   spot_error    : se identifica la palabra incorrecta de una oración.
--   dialogue      : se completa una conversación corta.
alter type public.exercise_type add value if not exists 'image_match';
alter type public.exercise_type add value if not exists 'listen_choose';
alter type public.exercise_type add value if not exists 'type_answer';
alter type public.exercise_type add value if not exists 'word_order';
alter type public.exercise_type add value if not exists 'spot_error';
alter type public.exercise_type add value if not exists 'dialogue';

-- Modo de sesión v2:
--   exam : evaluación de comprensión de un contexto (sin pistas, umbral 80 %).
alter type public.session_mode add value if not exists 'exam';

-- Métricas de logros v2.
alter type public.achievement_metric add value if not exists 'words_practiced';
alter type public.achievement_metric add value if not exists 'perfect_sessions';
alter type public.achievement_metric add value if not exists 'exams_passed';
alter type public.achievement_metric add value if not exists 'xp_total';
alter type public.achievement_metric add value if not exists 'challenges_completed';
