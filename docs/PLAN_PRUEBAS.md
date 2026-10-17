# NiJu · Plan de pruebas y trazabilidad

Estados: **Aprobada** = la prueba se ejecutó y pasó · **Pendiente** = falta ejecutarla
(requiere el proyecto real de Supabase o un teléfono).

Última ejecución automática: 2026-10-16 (PostgreSQL 16 local, JDK 21, Android Gradle Plugin 8.13).

## 1. Pruebas automáticas

| Suite | Cómo ejecutarla | Resultado |
|---|---|---|
| Base de datos: `supabase/tests/01_niju_tests.sql` (16 secciones) | ver `supabase/README.md` | 86/86 comprobaciones aprobadas |
| App: pruebas unitarias JVM (7 clases) | `./gradlew :app:testDebugUnitTest` | 35/35 aprobadas |
| App: análisis estático | `./gradlew :app:lintDebug` | 0 errores (92 advertencias menores) |
| App: compilación | `./gradlew :app:assembleDebug` | APK generado |

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

## 4. Limitaciones conocidas

- No se pudo ejecutar la app en un emulador (el entorno de desarrollo no lo permite). Las pruebas
  M01–M16 son la validación final.
- `delete_my_account` borra de `auth.users` desde una función del servidor. Funciona en PostgreSQL
  y es el método habitual en Supabase; confirmarlo con M11.
- La minificación R8 de la versión *release* está desactivada hasta probarla en un dispositivo.
- La validación pedagógica y de usabilidad con estudiantes y docentes no se ha realizado.
