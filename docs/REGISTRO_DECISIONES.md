# NiJu · Registro maestro de decisiones y cambios

Estados: **Aprobada** (la decidió el responsable del proyecto) · **Provisional** (propuesta,
pendiente de aprobación) · **Implementada** · **Probada**.

## Decisiones

| # | Fecha | Decisión | Estado | Motivo |
|---|---|---|---|---|
| D1 | 2026-10-16 | Se abandona Firebase; la base de datos será **Supabase (PostgreSQL)** | Aprobada · implementada | El responsable prefiere un modelo relacional |
| D2 | 2026-10-16 | El cronograma RUP se elaborará al final del proyecto | Aprobada | Decisión del responsable |
| D3 | 2026-10-16 | Se trabaja por **etapas** verificables (ver plan) | Aprobada | Menos retrabajo y menos consumo |
| D4 | 2026-10-17 | Puntos, progreso, SM-2 y racha se calculan **en el servidor** (funciones PostgreSQL); la app no escribe en tablas de progreso | Implementada y probada | Integridad de puntos y resultados |
| D5 | 2026-10-17 | SM-2 simplificado con respuesta binaria (acierto = calidad 4, fallo = calidad 2) | Provisional | Ejercicios de opción múltiple; sin autoevaluación 0–5 |
| D6 | 2026-10-17 | "Dominada" = intervalo SM-2 ≥ 21 días | Provisional | Criterio verificable de retención |
| D7 | 2026-10-17 | Ejercicios v1: `en_to_es`, `es_to_en`, `fill_blank` (opción múltiple, 3 opciones) | Provisional | Alcance realista; los demás tipos quedan para versiones futuras |
| D8 | 2026-10-17 | Reglas de XP, nivel, racha y logros según `supabase/README.md` | Provisional | Premian el aprendizaje, no los clics |
| D9 | 2026-10-17 | Contenido v1: 3 contextos, 6 lecciones, 36 palabras (A1–A2) | Provisional | Base para demostrar el flujo completo |
| D10 | 2026-10-17 | Se mantienen Kotlin + Views/XML + ViewModel en Android | Implementada | Código existente; reescribir no aporta valor |
| D11 | 2026-10-16 | Autenticación **solo con Microsoft** (Supabase Auth, proveedor Azure). Google queda **preparado y desactivado** (`AUTH_GOOGLE_ENABLED`) | Aprobada · implementada | Decisión del responsable |
| D12 | 2026-10-16 | Se eliminan: correo y contraseña, teléfono (SMS), 2FA propio con TOTP, registro manual | Implementada | Consecuencia de D11: Microsoft gestiona la identidad y su seguridad |
| D13 | 2026-10-16 | La base de datos de Supabase se crea desde cero; el paso a paso se dará cuando el responsable lo pida | Aprobada | Decisión del responsable |
| D14 | 2026-10-16 | Perfil editable: nombre visible y meta diaria (10–500 XP). Sin foto propia, biografía ni país | Provisional | No hay almacenamiento de archivos en el alcance v1 |
| D15 | 2026-10-16 | No se carga contenido real por ahora; `seed.sql` queda como demostración opcional. La app muestra estados vacíos | Aprobada · implementada | Decisión del responsable: priorizar la lógica |
| D16 | 2026-10-16 | Los logros son reglas del sistema: se crean en la migración 3, no en el seed | Implementada | Deben existir aunque no haya contenido |
| D17 | 2026-10-16 | El bloqueo de lecciones (≥ 70 % en la anterior) se exige en el servidor | Implementada y probada | Integridad: no se puede saltar por API |
| D18 | 2026-10-16 | Lección = tarjetas de presentación + ejercicios. "Repetir lección" si no se supera | Implementada | Primero comprender, luego practicar |
| D19 | 2026-10-16 | Favoritas por palabra (estrella en las tarjetas) | Implementada y probada | Reemplaza la maqueta de Favoritos |
| D20 | 2026-10-16 | Recordatorios opcionales (apagados por defecto): diario a las 7 p. m. y aviso de repasos, con WorkManager | Implementada | Pide permiso solo cuando el usuario los activa |
| D21 | 2026-10-16 | El acceso "Amigos" de Inicio pasa a "Contextos" | Implementada | Función social fuera del alcance v1 |
| D22 | 2026-10-16 | R8 (minificación) desactivado hasta probar en dispositivo | Provisional | Riesgo de fallos en ejecución no detectables sin probar |

## Plan por etapas

| Etapa | Contenido | Estado |
|---|---|---|
| 1 | Base de datos Supabase: esquema, seguridad, lógica de sesiones, contenido, pruebas | Implementada y probada (local, PostgreSQL 16) |
| 2 | Android: quitar Firebase, integrar Supabase (Microsoft + RPC), sesiones de práctica y repaso, Inicio/Perfil/Ruta con datos reales | Implementada; compila y pasa 22 pruebas unitarias y Lint sin errores. **No probada en dispositivo ni contra Supabase real** |
| 3 | Lecciones: lista por contexto (bloqueo/desbloqueo), tarjetas de presentación + ejercicios | Implementada; pruebas SQL y unitarias aprobadas |
| 4 | Estadísticas (actividad diaria, palabras con refuerzo), logros con avance, Favoritas | Implementada; pruebas SQL y unitarias aprobadas |
| 5 | Pulido: recordatorios, estados vacíos, textos a recursos, accesibilidad, privacidad/términos, manuales y plan de pruebas | Implementada; Lint 0 errores. Falta: pruebas en dispositivo (M01–M16) y cronograma |
| — | **Conexión y prueba final:** crear Supabase y Azure, configurar `local.properties`, ejecutar M01–M16 | Pendiente (responsable) |

## Cambios de la etapa 2 (2026-10-16)

- **Eliminados:** Firebase (Auth, Firestore, Analytics, `google-services.json`), `RegisterActivity`,
  `PhoneAuthActivity`, `TwoFactorActivity`, TOTP/cifrado/prefs propios, modelos y repositorios de Firestore,
  `SpacedRepetitionService` (ahora está en el servidor).
- **Nuevos:** `SupabaseProvider`, `NijuErrors`, `Models.kt`, `AuthRepository`, `LearningRepository`
  (+ interfaz `SessionRepository`), `ProfileRepository`, `SessionProgress`, `DashboardViewModel`,
  `RutaViewModel`, `Navigation.kt`.
- **Reescritos:** Splash (recupera la sesión), Login (Microsoft), Home, Contextos, Ruta, Práctica, Perfil,
  Configuración (reiniciar progreso, eliminar cuenta), Editar perfil.
- **Seguridad:** `allowBackup=false`; solo Login y Splash son `exported`; la clave `anon` va en
  `local.properties`, que no se sube a git.
- **Base de datos:** el registro copia la foto de perfil del proveedor si existe y es https.

## Errores del diagnóstico inicial y su resolución

| Error (código Firebase) | Resolución |
|---|---|
| La racha nunca pasaba de 1 | `touch_streak` en el servidor; probado |
| Racha por 24 h en vez de días calendario | Días en la zona horaria del usuario (la app la envía al iniciar sesión); probado |
| "XP del día" calculado como `xp % meta` | `xp_today` = suma real del libro de puntos; probado y mostrado en Inicio y Perfil |
| "Mastery" = 100 % con un acierto | Estados vista / practicada / dominada (≥ 21 días); probado |
| Fallo no ajustaba el factor SM-2 | Fallo baja el factor 0,32; probado |
| Nada consultaba `nextReview` | Modo `review`: botón "Repasar ahora (N)" en Inicio; probado |
| XP y progreso escritos desde el cliente | RLS + funciones del servidor; probado |
| Ruta con 40 % fijo | Ruta muestra el avance real (`get_context_progress`) |
| Perfil mostraba XP como "palabras" y un plan "Pro/Free" inventado | Muestra palabras practicadas, lecciones superadas y palabras dominadas |
| Final de sesión anticipado contaba mal el total | El resumen lo calcula el servidor (`finish_session`) |
| `LessonActivity` con 40 % fijo | Pendiente (etapa 3); hoy ninguna pantalla la abre |

## Cambios de las etapas 3–5 (2026-10-16)

- **Base de datos:** migración 3 (`user_favorite_words`, `get_lesson_content`, `toggle_favorite`,
  `get_favorites`, `get_achievements`, logros); `lesson_unlocked` y error `lesson_locked` en la migración 2.
  Pruebas: 86 comprobaciones (antes 60).
- **App, nuevas pantallas:** `LessonActivity` (reescrita: lista de lecciones), `WordCardsActivity`,
  `StatsActivity` (con `ActivityChartView`), `FavoritesActivity` (reescrita).
- **App, nuevos componentes:** `LoaderViewModel`, `WordCardsViewModel`, `StatsViewModel`,
  `FavoritesViewModel`, `Formatters`, `reminders/StudyReminders` (+ `ReminderWorker`, `ReminderPolicy`).
- **Pulido:** 129 textos de layouts pasados a `strings_ui.xml`; 38 imágenes decorativas ocultas a
  TalkBack; 34 recursos sin uso eliminados; diálogos de privacidad y términos; estados vacíos en
  Contextos, Lecciones, Tarjetas, Favoritas y Estadísticas. Advertencias de Lint: 312 → 92.
- **Pruebas de la app:** 35 (antes 22). Documentos nuevos: `MANUAL_USUARIO.md`, `MANUAL_TECNICO.md`,
  `PLAN_PRUEBAS.md`.

## Pendientes conocidos

- Conectar Supabase y Microsoft (Azure) y ejecutar las pruebas manuales M01–M16 de `PLAN_PRUEBAS.md`.
- Cronograma RUP (D2): se elaborará al cierre.
- Validación de usabilidad y pedagógica con estudiantes y docentes: no realizada.
- R8 desactivado (D22). Advertencias de Lint restantes: menores (concatenación de textos, versiones de dependencias).
