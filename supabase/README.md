# NiJu · Base de datos (Supabase / PostgreSQL)

## Archivos

| Archivo | Qué hace |
|---|---|
| `migrations/20261017000001_schema.sql` | Tablas, relaciones, índices, triggers y seguridad (RLS) |
| `migrations/20261017000002_functions.sql` | Lógica del servidor: sesiones, SM-2, XP, racha, logros, estadísticas, bloqueo de lecciones |
| `migrations/20261017000003_lessons_favorites_stats.sql` | Tarjetas de lección, favoritas, avance de logros y los 6 logros |
| `seed.sql` | **Opcional.** Contenido de demostración: 3 contextos, 6 lecciones, 36 palabras |
| `tests/00_local_supabase_stub.sql` | **Solo pruebas locales.** Simula `auth` de Supabase |
| `tests/01_niju_tests.sql` | 86 comprobaciones automáticas (se revierten al terminar; usan `seed.sql`) |

## Instalación en Supabase (una sola vez)

1. Crea el proyecto en https://supabase.com (plan gratuito). Región sugerida: `South America (São Paulo)`.
2. Abre **SQL Editor → New query** y ejecuta, en este orden, el contenido completo de:
   1. `migrations/20261017000001_schema.sql`
   2. `migrations/20261017000002_functions.sql`
   3. `migrations/20261017000003_lessons_favorites_stats.sql`
   4. *(Opcional)* `seed.sql`, si quieres contenido de demostración.
3. Comprueba en **Table Editor** que `achievements` tiene 6 filas (y `words` 36 si cargaste el seed).
   Sin contenido la app funciona y muestra pantallas vacías con su mensaje.
4. **Authentication → Providers → Email**: actívalo. Para pruebas puedes desactivar
   "Confirm email"; para la entrega, déjalo activo.
5. **Project Settings → API**: copia `Project URL` y la clave `anon public`.
   Son las que usará la app. **Nunca** pongas la clave `service_role` en la app.

> No ejecutes `tests/00_local_supabase_stub.sql` en Supabase: crea un esquema `auth` falso.

## Pruebas locales

Requiere PostgreSQL 15 o superior.

```bash
createdb niju
psql -d niju -f tests/00_local_supabase_stub.sql
psql -d niju -f migrations/20261017000001_schema.sql
psql -d niju -f migrations/20261017000002_functions.sql
psql -d niju -f migrations/20261017000003_lessons_favorites_stats.sql
psql -d niju -f seed.sql
psql -d niju -f tests/01_niju_tests.sql      # termina con "TODAS LAS PRUEBAS PASARON"
```

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
| `start_session(mode, lesson_id?, context_id?, limit?)` | Crea una sesión `lesson`, `review` o `practice` y devuelve sus ejercicios con opciones (sin respuestas) |
| `submit_answer(item_id, answer, response_ms?)` | Corrige, actualiza SM-2, XP y racha. Devuelve si acertó y la respuesta correcta |
| `finish_session(session_id)` | Cierra la sesión, aplica el bono de lección y logros. Se puede llamar dos veces sin duplicar |
| `get_dashboard()` | XP, nivel, meta diaria, racha, repasos pendientes, palabras vistas/practicadas/dominadas |
| `get_context_progress()` | Avance por contexto (pantalla Ruta) |
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
`invalid_answer`, `invalid_timezone`.

## Reglas de negocio

**Repetición espaciada (SM-2 simplificado).** Las respuestas son de opción múltiple, así que solo
hay acierto o fallo:

- Acierto → calidad 4: el factor de facilidad no cambia. Intervalos: 1, 6, 15, 38 días…
- Fallo → calidad 2: el factor baja 0,32 (mínimo 1,30), las repeticiones vuelven a 0 y la palabra
  se repasa al día siguiente. Cada fallo suma un olvido (`lapses`).
- Si se acierta una palabra **antes** de su fecha de repaso (práctica libre), se registra el
  intento pero el calendario no se adelanta. Un fallo sí cuenta siempre.
- Los repasos atrasados se ofrecen primero los más antiguos, hasta 20 por sesión.

**Vista, practicada, dominada.**

- Vista: apareció en alguna sesión del estudiante.
- Practicada: tiene al menos una respuesta.
- Dominada: su intervalo SM-2 es de 21 días o más, es decir, se ha recordado en repasos
  espaciados durante al menos tres semanas.

**Palabra que requiere refuerzo:** 3 o más olvidos, o al menos 2 fallos y más fallos que aciertos.

**Puntos (XP):**

| Acción | XP |
|---|---|
| Acierto en palabra nueva o con repaso vencido | 10 |
| Acierto en práctica de palabra no vencida (una vez por palabra y día) | 2 |
| Superar una lección por primera vez (≥ 70 % de aciertos) | 50 |
| Logro | según la tabla `achievements` |

Cada XP queda en `xp_ledger` con una clave única, así que la misma acción no puede premiarse dos veces.

**Nivel:** el nivel *n* requiere 100·(n−1)² XP (nivel 2: 100, nivel 3: 400, nivel 4: 900…).

**Racha:** días calendario seguidos, en la zona horaria del estudiante, con al menos una respuesta.
Si pasa un día completo sin estudiar, la racha se muestra en 0; el récord se conserva.

**Lecciones:** la primera de cada contexto está abierta; cada siguiente se desbloquea al superar la anterior
(≥ 70 %). El servidor lo exige en `start_session` y `get_lesson_content`, no solo la pantalla.

## Cómo agregar contenido

El contenido se administra desde el panel de Supabase (Table Editor) o con SQL. Orden:
`contexts` → `lessons` (con `context_id` y `sort_order`) → `words` → `word_examples`
(la oración debe contener la palabra exacta para el ejercicio "completar") → `lesson_words`
(lección, palabra y posición). `seed.sql` es un ejemplo completo. Si cambia el número total de
lecciones, ajusta el umbral del logro `all_lessons`.
