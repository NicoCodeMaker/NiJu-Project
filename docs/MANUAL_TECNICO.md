# NiJu · Manual técnico

## 1. Arquitectura

```
┌──────────────────────── App Android (Kotlin) ────────────────────────┐
│ Presentación   Activities + layouts XML (ViewBinding/findViewById)    │
│                ViewModels con StateFlow (ui/…)                        │
│ Dominio        SessionProgress, ReminderPolicy, Formatters (puros)   │
│ Datos          AuthRepository · LearningRepository · ProfileRepository│
│                SupabaseProvider (supabase-kt + Ktor OkHttp)          │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS · JWT del usuario
┌───────────────────────────────▼──────────────────────────────────────┐
│ Supabase                                                              │
│  Auth (proveedor Azure; Google preparado)                             │
│  PostgREST → funciones RPC (security definer)                         │
│  PostgreSQL: contenido compartido + datos por usuario con RLS         │
└──────────────────────────────────────────────────────────────────────┘
```

**Regla central:** la lógica que afecta a puntos y progreso (corrección, SM-2, XP, racha,
logros, desbloqueo de lecciones) vive en el servidor. La app no tiene permiso para escribir
esas tablas; solo llama a las funciones. Así un cliente modificado no puede regalarse puntos.

## 2. Código de la app

| Paquete / archivo | Responsabilidad |
|---|---|
| `data/remote/SupabaseProvider` | Cliente único; lee `SUPABASE_URL` y `SUPABASE_ANON_KEY` de `BuildConfig` |
| `data/remote/NijuErrors` | Códigos de error del servidor → mensajes en español |
| `data/model/Models.kt` | Modelos `@Serializable` que reflejan las respuestas SQL |
| `data/repository/*` | Llamadas a Supabase envueltas en `Result` |
| `domain/session/SessionProgress` | Avance local de una sesión |
| `ui/practice/PracticeViewModel` | Flujo start → submit → finish, reintentos, anti doble toque |
| `ui/lessons/WordCardsViewModel` | Tarjetas de lección y favoritas (cambio optimista) |
| `ui/stats/*` | Estadísticas (4 consultas en paralelo), favoritas, gráfica `ActivityChartView` |
| `ui/LoaderViewModel` | Pantallas de solo lectura |
| `reminders/StudyReminders` | Canal, permisos, WorkManager y `ReminderPolicy` |
| `*Activity.kt` | Pantallas: Splash, Login, Home, Contexts, Lesson, WordCards, Practice, Ruta, Profile, Stats, Favorites, Settings, EditProfile |

**Navegación:** Splash → (Login) → Home. Contextos → Lecciones → Tarjetas → Sesión (lección).
Inicio → Sesión (repaso o práctica). Perfil → Estadísticas / Favoritas / Configuración → Editar perfil.

**Inicio de sesión OAuth:** `signInWith(Azure)` abre una pestaña del navegador; Supabase redirige a
`niju://login-callback`, que recibe `LoginActivity` (`launchMode=singleTask`, intent-filter en el
manifest) y procesa con `handleDeeplinks`. La sesión se guarda y se renueva sola.

## 3. Base de datos

Ver `supabase/README.md`: tablas, funciones RPC, reglas de negocio (SM-2, XP, niveles, racha,
logros) y cómo agregar contenido. Migraciones en orden:

1. `20261017000001_schema.sql`: tablas, índices, triggers, RLS y permisos.
2. `20261017000002_functions.sql`: sesiones, SM-2, XP, racha, estadísticas.
3. `20261017000003_lessons_favorites_stats.sql`: tarjetas, favoritas, logros.

`seed.sql` es opcional (contenido de demostración).

## 4. Configuración

`local.properties` (no se sube a git; plantilla en `local.properties.example`):

| Clave | Uso |
|---|---|
| `SUPABASE_URL` | URL del proyecto (`https://….supabase.co`) |
| `SUPABASE_ANON_KEY` | Clave pública `anon`. **Nunca** la `service_role` |
| `AUTH_GOOGLE_ENABLED` | `true` muestra el botón de Google (requiere activarlo en Supabase) |

Redirección OAuth que debe permitirse en Supabase (Authentication → URL Configuration →
Redirect URLs): `niju://login-callback`.

## 5. Compilar, probar y publicar

```bash
./gradlew :app:assembleDebug          # APK de prueba
./gradlew :app:testDebugUnitTest      # 35 pruebas unitarias
./gradlew :app:lintDebug              # análisis estático (0 errores)
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
| Kotlin / AGP | 2.3.21 / 8.13.0 | Compilación |

## 7. Mantenimiento

- **Contenido nuevo:** solo SQL o el panel de Supabase; no requiere publicar la app.
- **Cambiar una regla** (XP, umbral de 70 %, dominio a 21 días): editar la función SQL, sus
  pruebas en `supabase/tests` y la documentación. Si cambia la forma de una respuesta, regenerar
  los fixtures de `app/src/test/resources/fixtures` y actualizar `Models.kt`.
- **Copias de seguridad:** revisa qué copias incluye tu plan de Supabase y, como respaldo,
  exporta la base periódicamente (`pg_dump`) si se usa con estudiantes reales.
