# NiJu · Manual técnico

## 1. Arquitectura

```
┌──────────────────────── App Android (Kotlin) ────────────────────────┐
│ Presentación   Activities + layouts XML (ViewBinding/findViewById)    │
│                ViewModels con StateFlow (ui/…)                        │
│ Dominio        SessionProgress, WordOrderState, AuthValidation,     │
│                ReminderPolicy, Formatters (puros, probados en JVM)   │
│ Datos          AuthRepository · LearningRepository · ProfileRepository│
│                SupabaseProvider (supabase-kt + Ktor OkHttp)          │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS · JWT del usuario
┌───────────────────────────────▼──────────────────────────────────────┐
│ Supabase                                                              │
│  Auth (correo y contraseña + Azure; Google preparado)                 │
│  PostgREST → funciones RPC (security definer)                         │
│  PostgreSQL: contenido compartido + datos por usuario con RLS         │
└──────────────────────────────────────────────────────────────────────┘
```

Patrón: **MVVM** (Activity ↔ ViewModel con `StateFlow<UiState>` ↔ repositorio ↔ Supabase).

**Regla central:** la lógica que afecta a puntos y progreso (corrección, calidad SM-2, XP, racha,
retos, logros, evaluaciones, desbloqueo de lecciones) vive en el servidor. La app no tiene permiso para escribir
esas tablas; solo llama a las funciones. Así un cliente modificado no puede regalarse puntos.

## 2. Código de la app

| Paquete / archivo | Responsabilidad |
|---|---|
| `data/remote/SupabaseProvider` | Cliente único; lee `SUPABASE_URL` y `SUPABASE_ANON_KEY` de `BuildConfig` |
| `data/remote/NijuErrors` | Códigos de error del servidor → mensajes en español |
| `data/model/Models.kt` | Modelos `@Serializable` que reflejan las respuestas SQL |
| `data/repository/*` | Llamadas a Supabase envueltas en `Result` |
| `domain/session/SessionProgress` | Avance local de una sesión (XP de respuesta + bono de racha) |
| `domain/session/WordOrderState` | Estado del ejercicio "ordenar palabras" (colocar, quitar, deshacer) |
| `domain/auth/AuthValidation` | Validación de correo, contraseña (8–72, letras y números), nombre y fuerza |
| `audio/*` | `PronunciationController` (clip local → `SystemSpeech` → no disponible), `ExerciseAudio`, catálogo |
| `ui/practice/PracticeViewModel` | Flujo start → submit → finish para lección, repaso, práctica y evaluación; reintentos, anti doble toque |
| `ui/auth/*` | Registro, olvido de contraseña, nueva contraseña, medidor de fuerza |
| `ui/results/*` | Pantalla de resultado (anillo de puntaje, confeti, retos y logros) |
| `ui/review/*`, `ui/rewards/*`, `ui/profile/*` | Repaso (estados y pronóstico), Logros y retos, Perfil |
| `ui/widget/*` | Vistas propias: `ConfettiView`, `ScoreRingView`, `ForecastBarsView` |
| `ui/lessons/WordCardsViewModel` | Tarjetas de lección y favoritas (cambio optimista) |
| `ui/stats/*` | Estadísticas (4 consultas en paralelo), favoritas, gráfica `ActivityChartView` |
| `ui/LoaderViewModel` | Pantallas de solo lectura |
| `reminders/StudyReminders` | Canal, permisos, WorkManager y `ReminderPolicy` |
| `*Activity.kt` | Pantallas: Splash, Login, Home, Contexts, Lesson, WordCards, Practice, Review, Rewards, Ruta, Profile, Stats, Favorites, Settings, EditProfile (+ Register, ForgotPassword, ResetPassword, SessionResult en `ui/`) |

**Navegación:** barra inferior de 5 pestañas (Inicio, Aprender = contextos, Repaso, Logros, Perfil).
Splash → (Login ↔ Registro / Olvidé mi contraseña) → Inicio. Contextos → Lecciones → Tarjetas → Sesión →
Resultado. Lecciones → Evaluación del contexto. Repaso → sesión enfocada (urgentes, refuerzo, favoritas).
Inicio → Ruta. Perfil → Estadísticas / Favoritas / Configuración → Editar perfil / Cambiar contraseña.

**Inicio de sesión OAuth:** `signInWith(Azure)` abre una pestaña del navegador; Supabase redirige a
`niju://login-callback`, que recibe `LoginActivity` (`launchMode=singleTask`, intent-filter en el
manifest) y procesa con `handleDeeplinks`. La sesión se guarda y se renueva sola.

**Correo y contraseña:** `signUpWith(Email)` (con `full_name` en los metadatos y `redirectUrl`
`niju://login-callback`); si el proyecto exige confirmación, la app muestra "revisa tu correo".
`signInWith(Email)` ingresa. **Recuperación:** `resetPasswordForEmail` envía el enlace; al abrirlo,
`handleDeeplinks` recibe una sesión de tipo `recovery` y `LoginActivity` abre `ResetPasswordActivity`,
que llama a `updateUser { password = … }`. Las contraseñas solo viajan por HTTPS a Supabase Auth,
que guarda el hash; la app no las escribe en disco ni en el registro.

## 3. Base de datos

Ver `supabase/README.md`: tablas, funciones RPC, reglas de negocio (SM-2, XP, niveles, racha,
logros) y cómo agregar contenido. Migraciones en orden:

1. `20261017000001_schema.sql`: tablas, índices, triggers, RLS y permisos.
2. `20261017000002_functions.sql`: sesiones, SM-2, XP, racha, estadísticas.
3. `20261017000003_lessons_favorites_stats.sql`: tarjetas, favoritas, logros.

4. `20261017000004_next_lesson.sql`: lección de "Continuar".
5. `20261108000005_enum_values.sql`: valores de enum nuevos (**ejecutar sola**).
6. `20261108000006_learning_v2.sql`: ejercicios v2, SM-2 con calidad, retos, evaluaciones, Repaso, Logros, Perfil.

Contenido: `seed.sql` → `seed_home_transport.sql` → `seed_v2_contexts.sql` (12 contextos, 144 palabras).

## 4. Configuración

`local.properties` (no se sube a git; plantilla en `local.properties.example`):

| Clave | Uso |
|---|---|
| `SUPABASE_URL` | URL del proyecto (`https://….supabase.co`) |
| `SUPABASE_ANON_KEY` | Clave pública `anon`. **Nunca** la `service_role` |
| `AUTH_GOOGLE_ENABLED` | `true` muestra el botón de Google (requiere activarlo en Supabase) |

Redirección que debe permitirse en Supabase (Authentication → URL Configuration →
Redirect URLs): `niju://login-callback`. Además: Authentication → Providers → **Email** activo.

## 5. Compilar, probar y publicar

```bash
./gradlew :app:assembleDebug          # APK de prueba
./gradlew :app:testDebugUnitTest      # 75 pruebas unitarias
./gradlew :app:lintDebug              # análisis estático
```

Pruebas de la base de datos: `supabase/README.md`. Plan completo: `docs/PLAN_PRUEBAS.md`.

Para una versión *release*: crear un keystore propio, configurar `signingConfigs`, y probar en
un dispositivo antes de activar `isMinifyEnabled` (R8) junto con las reglas para
`kotlinx.serialization` y Ktor.

## 6. Dependencias principales

| Librería | Versión | Para qué |
|---|---|---|
| supabase-kt (auth-kt, postgrest-kt) | 3.8.0 | Auth y API |
| Ktor client OkHttp | 3.5.1 | HTTP |
| AndroidX Browser | 1.9.0 | Pestaña del login OAuth |
| WorkManager | 2.11.2 | Recordatorios |
| Material Components | 1.13.0 | Tema Material 3, tarjetas, chips, hojas inferiores |
| Kotlin / AGP | 2.3.21 / 8.13.0 | Compilación |

## 7. Mantenimiento

- **Contenido nuevo:** solo SQL o el panel de Supabase; no requiere publicar la app.
- **Audio de contenido nuevo:** sin clip local suena la voz del teléfono; para clips propios,
  ver `docs/AUDIO.md` §6.
- **Cambiar una regla** (XP, umbral de 70 % u 80 %, dominio a 21 días, retos): editar la función SQL, sus
  pruebas en `supabase/tests` y la documentación. Si cambia la forma de una respuesta, regenerar
  los fixtures de `app/src/test/resources/fixtures` y actualizar `Models.kt`.
- **Copias de seguridad:** revisa qué copias incluye tu plan de Supabase y, como respaldo,
  exporta la base periódicamente (`pg_dump`) si se usa con estudiantes reales.
