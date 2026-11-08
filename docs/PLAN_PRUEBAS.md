# NiJu · Plan de pruebas y trazabilidad

Estados: **Aprobada** = la prueba se ejecutó y pasó · **Pendiente** = falta ejecutarla
(requiere el proyecto real de Supabase o un teléfono).

Última ejecución automática: **2026-11-08** — base de datos en PostgreSQL 16 local; pruebas unitarias compiladas
con `kotlinc` 2.3 contra las mismas librerías del proyecto y ejecutadas con JUnit 4. **Gradle, aapt2 y Lint no se
pudieron ejecutar en el entorno de desarrollo** (sin acceso a Google Maven): esa columna queda Pendiente hasta que
el responsable ejecute los comandos en su equipo.

## 1. Pruebas automáticas

| Suite | Cómo ejecutarla | Resultado |
|---|---|---|
| Base de datos: `01_niju_tests.sql` (núcleo) | `bash supabase/tests/run_local_tests.sh` | 86/86 aprobadas |
| Base de datos: `02_niju_home_transport_tests.sql` | (mismo comando) | 21/21 aprobadas |
| Base de datos: `03_niju_v2_tests.sql` (v2) | (mismo comando) | 82/82 aprobadas |
| App: pruebas unitarias JVM (10 clases) | `./gradlew :app:testDebugUnitTest` | 75/75 aprobadas (kotlinc + JUnit); **con Gradle: pendiente** |
| App: recursos e ids de vistas | validadores propios (`check_res.py`, `check_ids.py`) | sin errores |
| App: compilación | `./gradlew :app:assembleDebug` | **Pendiente** (equipo del responsable) |
| App: análisis estático | `./gradlew :app:lintDebug` | **Pendiente** (v1: 0 errores) |

Las pruebas de la app usan **respuestas reales del servidor** (`app/src/test/resources/fixtures/*.json`),
generadas ejecutando las funciones SQL. Si una columna cambia en el servidor, fallan.

## 2. Matriz de trazabilidad

| Requisito | Implementación | Verificación | Estado |
|---|---|---|---|
| RF01 Iniciar sesión con Microsoft | `LoginActivity`, `AuthRepository` (Azure), Supabase Auth | M01–M03 | Pendiente (requiere Supabase + Azure) |
| RF02 Conservar la sesión al reabrir | `SplashActivity` (`awaitInitialization`) | M04 | Pendiente |
| RF03 Crear perfil al registrarse | trigger `handle_new_user` | SQL §2 | Aprobada |
| RF04 Editar nombre y meta diaria | `EditProfileActivity`, `ProfileRepository.validate` | `ErrorsAndValidationTest.profileValidation`, SQL §4, M10 | Aprobada / M10 pendiente |
| RF05 Eliminar cuenta y todos los datos | `delete_my_account`, `SettingsActivity` | SQL §12, M11 | Aprobada / M11 pendiente |
| RF06 Catálogo de contextos | `contexts`, `ContextsActivity` | SQL §4, `ModelsDecodingTest` | Aprobada |
| RF07 Lecciones ordenadas con desbloqueo (≥ 70 %) | `lesson_unlocked`, `get_context_lessons`, `LessonActivity` | SQL §5, §13, §15; `FormattersAndRemindersTest.lessonStatusLabels` | Aprobada |
| RF08 Tarjetas de presentación (palabra, IPA, significado, ejemplo) | `get_lesson_content`, `WordCardsActivity` | SQL §13, `WordCardsViewModelTest`, `LessonsStatsDecodingTest.lessonContent` | Aprobada |
| RF09 Ejercicios: traducir EN→ES, ES→EN y completar | `build_exercise`, `start_session` | SQL §3, §5 | Aprobada |
| RF10 Corrección en el servidor, sin revelar respuestas | `submit_answer` | SQL §5, `ModelsDecodingTest.startSession_decodesItemsWithoutAnswers` | Aprobada |
| RF11 Retroalimentación por respuesta (correcta / respuesta correcta) | `PracticeActivity`, `PracticeViewModel` | `PracticeViewModelTest.fullLessonSession…` | Aprobada |
| RF12 Repetición espaciada SM-2 | `sm2_next`, `submit_answer` | SQL §1, §8 | Aprobada |
| RF13 Repaso de palabras vencidas | `start_session('review')`, botón de Inicio | SQL §8, `PracticeViewModelTest.noDueReviews…` | Aprobada |
| RF14 Práctica libre sin inflar el progreso | `submit_answer` (`was_due`) | SQL §7 | Aprobada |
| RF15 Puntos sin duplicados | `xp_ledger` (único), `grant_xp` | SQL §5, §7 | Aprobada |
| RF16 Niveles | `level_for_xp` | SQL §1 | Aprobada |
| RF17 Racha por días calendario | `touch_streak`, zona horaria del teléfono | SQL §9 | Aprobada |
| RF18 Logros con avance | `check_achievements`, `get_achievements`, `StatsActivity` | SQL §5, §9, §10, §15; `LessonsStatsDecodingTest` | Aprobada |
| RF19 Estadísticas: vistas / practicadas / dominadas, precisión, actividad 7 días | `get_dashboard`, `get_daily_activity`, `StatsActivity` | SQL §10; `ModelsDecodingTest`, `LessonsStatsDecodingTest` | Aprobada |
| RF20 Palabras para reforzar | `get_struggling_words` | SQL §10, `LessonsStatsDecodingTest` | Aprobada |
| RF21 Favoritas | `toggle_favorite`, `get_favorites`, `FavoritesActivity` | SQL §14, `WordCardsViewModelTest.favoriteToggle…` | Aprobada |
| RF22 Avance por contexto (Ruta) | `get_context_progress`, `RutaActivity` | SQL §10 | Aprobada |
| RF23 Recordatorio diario y aviso de repasos | `ReminderPolicy`, `ReminderWorker` | `FormattersAndRemindersTest.reminderPolicy`, M12 | Aprobada / M12 pendiente |
| RF24 Reiniciar progreso | `reset_progress` | SQL §10 | Aprobada |
| RF25 Registro con correo y contraseña | `RegisterActivity`, `AuthRepository.signUpWithEmail`, `AuthValidation` | `LogicV2Test` (validación), M25 | Validación aprobada / M25 pendiente |
| RF26 Ingreso con correo y cierre de sesión | `LoginActivity`, `signInWithEmail` | M26, M27 | Pendiente |
| RF27 Recuperar y cambiar la contraseña | `ForgotPasswordActivity`, `ResetPasswordActivity`, `AuthLinks` | `LogicV2Test` (enlaces), M28, M29 | Parcial / M28–M29 pendientes |
| RF28 Nueve tipos de ejercicio con corrección en el servidor | `build_exercise`, `submit_answer`, `PracticeActivity` | SQL 03 §3, §4; `ModelsV2DecodingTest`; `WordOrderState`; M30 | Aprobada / M30 pendiente |
| RF29 SM-2 con calidad 0–5 | `answer_quality`, `sm2_next_q` | SQL 03 §2 | Aprobada |
| RF30 Estados de palabra, urgentes y pantalla Repaso | `word_bucket`, `is_urgent`, `get_review_overview`, `ReviewActivity` | SQL 03 §5; `ModelsV2DecodingTest`; M31 | Aprobada / M31 pendiente |
| RF31 Evaluación por contexto (80 %) | `exam_unlocked`, `start_session('exam')`, `finish_session` | SQL 03 §9; M32 | Aprobada / M32 pendiente |
| RF32 Retos diarios, metas semanales y bonos de racha sin duplicados | `settle_challenges`, `streak_bonus_for`, `claim_rewards` | SQL 03 §7, §8 | Aprobada |
| RF33 20 logros | `achievements`, `check_achievements` | SQL 01, 03 | Aprobada |
| RF34 12 contextos con palabra, IPA, ejemplo, imagen, nivel y diálogo | `seed*.sql` | SQL 03 §1 | Aprobada |
| RF35 Perfil con estadísticas reales e historial | `get_profile_overview`, `get_session_history`, `ProfileActivity` | SQL 03 §10; `ModelsV2DecodingTest`; M36 | Aprobada / M36 pendiente |
| RF36 Pantalla de resultado | `SessionResultActivity`, `ResultTexts` | `LogicV2Test`; M37 | Aprobada / M37 pendiente |
| RF37 Audio con respaldo de voz del sistema | `PronunciationController`, `SystemSpeech` | `SpeechFallbackTest`; M33, M34 | Aprobada / M33–M34 pendientes |
| RNF10 La app no puede leer las respuestas | permisos por columna en `session_items` | SQL 03 §11 | Aprobada |
| RNF11 Contraseñas nunca en texto plano | Supabase Auth (hash); la app no las guarda ni registra | Revisión de código | Aprobada |
| RNF01 Cada usuario solo ve sus datos | RLS en todas las tablas personales | SQL §4, §6, §11, §16 | Aprobada |
| RNF02 La app no puede escribir puntos ni progreso | Sin permisos de escritura; solo RPC | SQL §4, §14 | Aprobada |
| RNF03 Sin secretos en el código | `local.properties` (no se sube), solo clave `anon` | Revisión de código | Aprobada |
| RNF04 Errores comprensibles en español | `NijuErrors` | `ErrorsAndValidationTest` | Aprobada |
| RNF05 Tolerancia a fallos de red y doble toque | `PracticeViewModel.runBusy`, `RetryAction` | `PracticeViewModelTest` | Aprobada |
| RNF06 Funciona con la base vacía | Estados vacíos en todas las listas | Prueba SQL sin seed; M13 | Aprobada / M13 pendiente |
| RNF07 Android 7.0+ (API 24) | `minSdk 24`, sin `java.time` | Lint `NewApi` sin errores | Aprobada |
| RNF08 Accesibilidad básica | descripciones, imágenes decorativas ocultas, encabezados | Lint `ContentDescription` sin avisos | Aprobada |
| RNF09 Modo oscuro | colores con variante `values-night` | M14 | Pendiente |

## 3. Pruebas manuales (en teléfono, tras conectar Supabase)

| ID | Pasos | Resultado esperado | Estado |
|---|---|---|---|
| M01 | Abrir la app sin sesión → "Continuar con Microsoft" | Se abre el navegador con el inicio de sesión de Microsoft | Pendiente |
| M02 | Completar el inicio de sesión | La app vuelve sola y muestra Inicio con el nombre de la cuenta | Pendiente |
| M03 | Cerrar el navegador sin terminar | El botón se reactiva; no se queda cargando | Pendiente |
| M04 | Cerrar y reabrir la app | Entra directo a Inicio | Pendiente |
| M05 | Contextos → Restaurante → Lección 1 → tarjetas → ejercicios | 6 tarjetas, luego 6 ejercicios con retroalimentación y resumen | Pendiente |
| M06 | Terminar la lección con ≥ 70 % | "¡Lección superada!", la lección 2 queda abierta, logro "Primer paso" | Pendiente |
| M07 | Terminar con < 70 % | Ofrece "Repetir lección"; la lección 2 sigue bloqueada | Pendiente |
| M08 | Al día siguiente, Inicio | "Repasar ahora (N)" y la racha en 2 | Pendiente |
| M09 | Marcar una estrella en una tarjeta → Perfil → Favoritas | La palabra aparece; quitar la estrella la elimina | Pendiente |
| M10 | Editar perfil con meta 5 y luego 60 | Rechaza 5 con mensaje; guarda 60 y se ve en Inicio | Pendiente |
| M11 | Configuración → Eliminar cuenta | Vuelve al login; en Supabase ya no está el usuario | Pendiente |
| M12 | Activar el recordatorio (Android 13+) | Pide permiso; a las 7 p. m. llega el aviso si no se estudió | Pendiente |
| M13 | Con la base sin contenido | Contextos, Ruta y Favoritas muestran su mensaje vacío; nada se cierra | Pendiente |
| M14 | Activar el modo oscuro | Todas las pantallas legibles | Pendiente |
| M15 | Sin internet, responder un ejercicio | Mensaje "Sin conexión" y opción de continuar con la misma pregunta | Pendiente |
| M16 | Girar el teléfono durante una sesión | No se pierde la pregunta ni se repite el sonido | Pendiente |

## 3b. Pruebas manuales añadidas (audio y rediseño) — NO ejecutadas aún

| Id | Prueba | Esperado |
|---|---|---|
| M17 | Tarjeta de palabra → Escuchar (normal y lento) | Suena la palabra; el botón cambia mientras suena |
| M18 | Escuchar dos veces seguidas | Reinicia, sin superponer |
| M19 | Responder un ejercicio → Escuchar | Suena la palabra o la oración completa |
| M20 | Modo avión | El audio sigue sonando (es local) |
| M21 | Inicio → Continuar | Abre la siguiente lección real; con todo superado muestra "Ruta completa" |
| M22 | Contextos tras terminar una lección | Sube X/Y y la barra al volver |
| M23 | TalkBack en Inicio, Contextos y lecciones | Todo se anuncia con su estado (no solo color) |
| M24 | Fuente grande + pantalla pequeña | Sin texto cortado |

## 3c. Pruebas manuales v2 — NO ejecutadas aún

| Id | Prueba | Esperado |
|---|---|---|
| M25 | Crear cuenta con correo (con "Confirm email" activo) | Pantalla "Revisa tu correo"; el enlace abre la app y entra a Inicio |
| M26 | Iniciar sesión con correo y contraseña; con contraseña errónea | Entra / "Correo o contraseña incorrectos" |
| M27 | Cerrar sesión y reabrir la app | Vuelve al login; no entra directo |
| M28 | ¿Olvidaste tu contraseña? → abrir el enlace en el teléfono | Abre "Nueva contraseña"; tras guardarla entra a Inicio |
| M29 | Configuración → Cambiar contraseña | Guarda y regresa a Configuración |
| M30 | Una lección completa: verificar los 6 tipos de la secuencia y repetirla | Cada tipo se responde y corrige; al repetir cambia la secuencia |
| M31 | Repaso: tocar cada estado, abrir la ficha de una palabra, Repasar urgentes | Listas reales; la ficha muestra intentos; la sesión solo trae urgentes |
| M32 | Superar todas las lecciones de un contexto → Evaluación | Se abre; 10 ítems; con ≥ 80 % queda "Aprobada" y +100 XP |
| M33 | Escuchar una palabra de Escuela/Playa/Salud (sin clip) | Suena la voz del teléfono en inglés |
| M34 | Desinstalar la voz en inglés del teléfono y repetir M33 | Mensaje de audio no disponible; nada se cierra |
| M35 | Logros: cumplir un reto diario | Al terminar la sesión aparece en el resultado; no se puede cobrar dos veces |
| M36 | Perfil tras varias sesiones | Estadísticas e historial coinciden con lo hecho |
| M37 | Sesión perfecta y subida de nivel | Resultado con confeti, "Sin errores" y nuevo nivel |
| M38 | Modo oscuro en las pantallas nuevas | Todo legible |
| M39 | TalkBack en Práctica (ordenar palabras) y Repaso | Se anuncian las palabras y su estado |
| M40 | Girar el teléfono en Práctica a mitad de "ordenar palabras" | No se pierde la pregunta |

## 4. Limitaciones conocidas

- No se pudo ejecutar la app en un emulador (el entorno de desarrollo no lo permite). Las pruebas
  M01–M40 son la validación final.
- En la v2 tampoco se pudo ejecutar Gradle: el código se compiló con `kotlinc` y los recursos se revisaron con
  validadores propios. aapt2 puede encontrar errores que esos validadores no ven.
- `delete_my_account` borra de `auth.users` desde una función del servidor. Funciona en PostgreSQL
  y es el método habitual en Supabase; confirmarlo con M11.
- La minificación R8 de la versión *release* está desactivada hasta probarla en un dispositivo.
- La validación pedagógica y de usabilidad con estudiantes y docentes no se ha realizado.
