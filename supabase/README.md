# NiJu · Base de datos (Supabase / PostgreSQL)

## Archivos

| Archivo | Qué hace |
|---|---|
| `migrations/20261017000001_schema.sql` | Tablas, relaciones, índices, triggers y seguridad (RLS) |
| `migrations/20261017000002_functions.sql` | Lógica del servidor: sesiones, SM-2, XP, racha, logros, estadísticas, bloqueo de lecciones |
| `migrations/20261017000003_lessons_favorites_stats.sql` | Tarjetas de lección, favoritas, avance de logros y los 6 logros |
| `migrations/20261017000004_next_lesson.sql` | `get_next_lesson()`: la lección que muestra "Continuar" en Inicio |
| `migrations/20261108000005_enum_values.sql` | **v2.** Valores nuevos de enum (6 tipos de ejercicio, modo `exam`, métricas de logros). **Ejecutar sola**, antes de la 6 |
| `migrations/20261108000006_learning_v2.sql` | **v2.** 9 tipos de ejercicio, SM-2 con calidad 0–5, estados de palabra, evaluaciones, retos diarios/semanales, bonos de racha, 20 logros, Repaso, Logros, Perfil e historial |
| `seed_v2_contexts.sql` | **v2.** 7 contextos (Escuela, Playa, Comercio, Salud, Tecnología, Trabajo, Viajes): 14 lecciones, 84 palabras con IPA, ejemplo, emoji y diálogo. Ordena el catálogo de 12 contextos. Ejecutar después de los otros dos seeds |
| `tests/03_niju_v2_tests.sql` | 82 comprobaciones de la v2 |
| `tests/run_local_tests.sh` | Crea 3 bases locales y ejecuta las 3 suites (189 comprobaciones) |
| `seed_home_transport.sql` | **Opcional.** Contextos Hogar y Transporte (4 lecciones, 24 palabras). Ejecutar después de `seed.sql` |
| `tests/02_niju_home_transport_tests.sql` | Pruebas de ese contenido y de `get_next_lesson` |
| `seed.sql` | **Opcional.** Contenido de demostración: 3 contextos, 6 lecciones, 36 palabras |
| `tests/00_local_supabase_stub.sql` | **Solo pruebas locales.** Simula `auth` de Supabase |
| `tests/01_niju_tests.sql` | 86 comprobaciones automáticas (se revierten al terminar; usan `seed.sql`) |

## Instalación en Supabase (una sola vez)

1. Crea el proyecto en https://supabase.com (plan gratuito). Región sugerida: `South America (São Paulo)`.
2. Abre **SQL Editor → New query** y ejecuta, en este orden, el contenido completo de:
   1. `migrations/20261017000001_schema.sql`
   2. `migrations/20261017000002_functions.sql`
   3. `migrations/20261017000003_lessons_favorites_stats.sql`
   4. `migrations/20261017000004_next_lesson.sql` (sin ella Inicio oculta la tarjeta "Continuar")
   5. `migrations/20261108000005_enum_values.sql` — **en una consulta aparte** y ejecútala sola:
      PostgreSQL no permite usar un valor de enum en la misma transacción que lo crea.
   6. `migrations/20261108000006_learning_v2.sql`
   7. Contenido: `seed.sql`, luego `seed_home_transport.sql`, luego `seed_v2_contexts.sql`.
      Están escritos para no duplicar filas si se ejecutan dos veces.
3. Comprueba en **Table Editor**: `achievements` = 20 filas, `challenge_templates` = 11,
   `contexts` = 12, `lessons` = 24 y `words` = 144 con los tres seeds.
   Sin contenido la app funciona y muestra pantallas vacías con su mensaje.
4. **Authentication → Providers → Email**: actívalo (la app lo usa para registro, ingreso y
   recuperación). Para pruebas puedes desactivar "Confirm email"; para la entrega, déjalo activo.
   **Authentication → URL Configuration → Redirect URLs**: agrega `niju://login-callback`
   (lo usan Microsoft, la confirmación de correo y el enlace de recuperación).
   Revisa en **Email Templates** los textos de *Confirm signup* y *Reset password*. El correo
   integrado de Supabase tiene un límite bajo de envíos por hora; para uso real configura SMTP propio.
5. **Project Settings → API**: copia `Project URL` y la clave `anon public`.
   Son las que usará la app. **Nunca** pongas la clave `service_role` en la app.

> **Reinicio desde cero.** Cada migración que crea una tabla primero la borra (`drop table if exists … cascade`).
> La migración 1 además borra **todo** lo de NiJu (tablas con sus datos, tipos, funciones y el trigger de
> registro) y lo vuelve a crear. Por eso, si vuelves a ejecutar la migración 1, ejecuta después las 2 a 6 y los
> tres seeds, en orden. Los usuarios de **Authentication** no se borran: la migración 1 les vuelve a crear el
> perfil vacío (pierden XP, racha y progreso). Si ejecutas solo la 6, se vacían sus tablas
> (`word_dialogues`, evaluaciones y retos): vuelve a ejecutar `seed_v2_contexts.sql` para recuperar los diálogos.
> Úsalo así solo en el proyecto de pruebas, o haz una copia (Database → Backups) antes.

> No ejecutes `tests/00_local_supabase_stub.sql` en Supabase: crea un esquema `auth` falso.

## Pruebas locales

Requiere PostgreSQL 15 o superior.

```bash
bash tests/run_local_tests.sh     # crea las bases, aplica todo y ejecuta 01, 02 y 03
```

Usa las variables estándar de `psql` (`PGHOST`, `PGUSER`…). Resultado de la última ejecución
(2026-11-08, PostgreSQL 16): 01 = 86, 02 = 21, 03 = 82 → **189/189 aprobadas**. Además el script vuelve a
ejecutar todas las migraciones y seeds sobre una base con datos y comprueba que se reconstruye completa y que un
usuario existente recupera su perfil.

## Modelo de datos

```
CONTENIDO (compartido, solo lectura)          PROGRESO (de cada estudiante)
contexts ─┬─< lessons ─< lesson_words >─ words ─< word_examples
          │                                │
          │                                ├─< user_word_progress  (SM-2 por usuario y palabra)
          │                                └─< attempts            (historial de respuestas)
achievements ─< user_achievements
auth.users ── profiles ── user_stats
                  ├─< study_sessions ─< session_items ── attempts
                  ├─< user_lesson_progress
                  └─< xp_ledger            (libro de puntos, sin duplicados)
```

- Una palabra está una sola vez en `words` y puede usarse en varias lecciones (`lesson_words`).
- El progreso de cada estudiante con cada palabra vive en `user_word_progress`, separado del contenido.
- La app **no escribe** en las tablas de progreso: solo llama a las funciones de abajo.

## Funciones que usa la app (RPC)

| Función | Para qué |
|---|---|
| `start_session(mode, lesson_id?, context_id?, limit?, focus?)` | Crea una sesión `lesson`, `review`, `practice` o `exam` y devuelve sus ejercicios (sin respuestas). `focus`: `reinforce`, `urgent` o `favorites` |
| `submit_answer(item_id, answer, response_ms?)` | Corrige, calcula la calidad 0–5, actualiza SM-2, XP y racha. Devuelve acierto, respuesta correcta, solución, calidad, estado de la palabra y bono de racha |
| `finish_session(session_id)` | Cierra la sesión: bono de lección o evaluación, sesión perfecta, retos y logros. Idempotente |
| `get_dashboard()` | XP, nivel y título, meta diaria, racha, repasos pendientes y urgentes, tiempo de estudio de hoy, retos |
| `get_context_progress()` | Avance por contexto (Ruta y Contextos), con emoji y estado de la evaluación |
| `get_review_overview()` | Palabras por estado y pronóstico de repasos de 7 días |
| `get_review_words(bucket?, limit?)` | Lista de palabras de un estado (o urgentes) |
| `get_word_detail(word_id)` | Ficha de una palabra: SM-2, próximos repasos e intentos recientes |
| `get_rewards()` / `claim_rewards()` | Retos del día y de la semana, próximo bono de racha, recompensas recientes / liquida retos cumplidos |
| `get_profile_overview()` | Perfil con estadísticas reales (precisión, tiempo, mejor racha, logros) |
| `get_session_history(limit?)` | Últimas sesiones con modo, puntaje, XP y duración |
| `get_context_lessons(context_id)` | Lecciones con estado: completada, desbloqueada, mejor puntaje |
| `get_daily_activity(days)` | Actividad por día para gráficas |
| `get_struggling_words(limit)` | Palabras que requieren refuerzo |
| `get_lesson_content(lesson_id)` | Tarjetas de la lección: palabra, pronunciación, significado, ejemplo, favorita, estado |
| `toggle_favorite(word_id)` | Marca o desmarca una favorita; devuelve el estado final |
| `get_favorites()` | Palabras favoritas con ejemplo y estado |
| `get_achievements()` | Todos los logros con avance (`current_value / threshold`) y fecha de desbloqueo |
| `reset_progress(word_id?)` | Reinicia el progreso de una palabra o de todas |
| `delete_my_account()` | Borra la cuenta y todos sus datos |

Errores que la app debe reconocer: `not_authenticated`, `lesson_not_found`, `lesson_locked`, `word_not_found`, `no_due_reviews`,
`no_words`, `item_not_found`, `item_already_answered`, `session_finished`, `session_not_found`,
`invalid_answer`, `invalid_timezone`, `exam_locked`, `context_not_found`, `no_favorites`.

## Reglas de negocio

**Repetición espaciada (SM-2, v2).** El servidor calcula la calidad *q* (0–5) de cada respuesta:

| Respuesta | q |
|---|---|
| Correcta y rápida (≤ 4 s en elección; ≤ 8 s en completar, diálogo y error; ≤ 10 s en escribir y ordenar) | 5 |
| Correcta en hasta 3 veces ese tiempo, o sin tiempo medido | 4 |
| Correcta pero lenta | 3 |
| Incorrecta en reconocimiento (elegir) | 2 |
| Incorrecta en producción (escribir, ordenar, detectar el error) | 1 |

- Factor de facilidad: EF' = EF + (0,1 − (5−q)(0,08 + (5−q)·0,02)), mínimo 1,30.
- q ≥ 3: intervalos 1, 6 y luego round(I · EF'). q < 3: repeticiones a 0, repaso al día siguiente
  y un olvido más (`lapses`).
- Si se acierta una palabra **antes** de su fecha de repaso (práctica libre), se registra el
  intento pero el calendario no se adelanta. Un fallo sí cuenta siempre.
- Los repasos atrasados se ofrecen primero los más antiguos, hasta 20 por sesión.

**Vista, practicada, dominada.**

- Vista: apareció en alguna sesión del estudiante.
- Practicada: tiene al menos una respuesta.
- Dominada: su intervalo SM-2 es de 21 días o más, es decir, se ha recordado en repasos
  espaciados durante al menos tres semanas.

**Estados (`word_bucket`):** nueva (sin respuestas) · dominada (intervalo ≥ 21 días) · refuerzo
(3+ olvidos, o 2+ fallos y más fallos que aciertos) · aprendiendo (menos de 2 repasos acertados
seguidos) · repasar (en el calendario). **Urgente:** repaso vencido hace 2+ días, o en refuerzo y vencido.

**Puntos (XP):**

| Acción | XP |
|---|---|
| Acierto en palabra nueva o con repaso vencido | 10 |
| Acierto en práctica de palabra no vencida (una vez por palabra y día) | 2 |
| Superar una lección por primera vez (≥ 70 % de aciertos) | 50 |
| Aprobar la evaluación de un contexto por primera vez (≥ 80 %) | 100 |
| Reto diario o meta semanal cumplida | según `challenge_templates` (20–120) |
| Racha de 3 / 7 / 14 / 30 / 60 / 100 días (una vez por racha) | 15 / 40 / 80 / 150 / 300 / 500 |
| Logro (20 en total) | según la tabla `achievements` |

Cada XP queda en `xp_ledger` con una clave única, así que la misma acción no puede premiarse dos veces.

**Nivel:** el nivel *n* requiere 100·(n−1)² XP (nivel 2: 100, nivel 3: 400, nivel 4: 900…).
Títulos: Principiante (1), Explorador (3), Viajero (5), Conversador (8), Experto (12), Maestro (16), Leyenda (20).

**Retos:** cada día se eligen 3 de 8 plantillas diarias (hash del usuario y la fecha, estable durante
el día) y hay 3 metas semanales (XP = 5 × meta diaria, 5 días distintos, 3 lecciones). Se liquidan al
terminar una sesión o con `claim_rewards`, con clave única por periodo.

**Evaluación de contexto:** se abre al superar todas las lecciones del contexto; 10 ejercicios sin
pistas; se aprueba con 80 %.

**Tipos de ejercicio:** `en_to_es`, `es_to_en`, `fill_blank`, `image_match` (emoji), `listen_choose`,
`type_answer`, `word_order`, `spot_error`, `dialogue`. Una lección alterna dos secuencias de tipos según
el intento. Las respuestas (`answer_key`, `solution`) no son legibles por la app (permisos por columna).

**Racha:** días calendario seguidos, en la zona horaria del estudiante, con al menos una respuesta.
Si pasa un día completo sin estudiar, la racha se muestra en 0; el récord se conserva.

**Lecciones:** la primera de cada contexto está abierta; cada siguiente se desbloquea al superar la anterior
(≥ 70 %). El servidor lo exige en `start_session` y `get_lesson_content`, no solo la pantalla.

## Cómo agregar contenido

El contenido se administra desde el panel de Supabase (Table Editor) o con SQL. Orden:
`contexts` → `lessons` (con `context_id` y `sort_order`) → `words` → `word_examples`
(la oración debe contener la palabra exacta para el ejercicio "completar") → `lesson_words`
(lección, palabra y posición). `seed.sql` es un ejemplo completo. Si cambia el número total de
lecciones, ajusta el umbral del logro `all_lessons` (hoy 24). Para el tipo `dialogue` agrega una fila en `word_dialogues`; para `image_match`, un `emoji` en `words`.
