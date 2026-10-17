# NiJu

Aplicación Android para aprender vocabulario en inglés con contextos reales, lecciones,
repetición espaciada (SM-2) y seguimiento del progreso.

## Tecnologías

| Capa | Tecnología |
|---|---|
| App | Kotlin, Views/XML + ViewBinding, ViewModel + StateFlow, corrutinas |
| Backend | Supabase: PostgreSQL, Auth, API REST (PostgREST) |
| Cliente | supabase-kt 3.8 (auth-kt, postgrest-kt) + Ktor OkHttp |
| Inicio de sesión | Microsoft (Azure) vía OAuth. Google preparado, desactivado |

## Estructura

```
app/src/main/java/com/example/niju_project/
├── data/
│   ├── model/        Modelos (reflejan tablas y funciones SQL)
│   ├── remote/       Cliente Supabase y traducción de errores
│   └── repository/   AuthRepository, LearningRepository, ProfileRepository
├── domain/session/   SessionProgress (lógica pura de una sesión)
├── reminders/        Recordatorios (WorkManager + ReminderPolicy)
├── ui/               ViewModels, formateadores y gráfica de actividad
└── *Activity.kt      Pantallas
app/src/test/         Pruebas unitarias + respuestas reales del servidor (fixtures)
supabase/             Migraciones SQL, contenido inicial y pruebas de la base de datos
docs/                 Registro de decisiones, manuales y plan de pruebas
```

## Cómo compilar

1. Crea la base de datos siguiendo `supabase/README.md`.
2. Copia `local.properties.example` como `local.properties` y completa la URL y la clave `anon`.
3. Abre el proyecto en Android Studio y ejecuta la configuración `app`.

Sin `local.properties` la app compila igual, pero la pantalla de inicio de sesión avisa que
falta conectar Supabase.

## Pruebas

```bash
./gradlew :app:testDebugUnitTest      # 35 pruebas de la app (JVM, sin emulador)
./gradlew :app:lintDebug              # análisis estático (0 errores)
```

Las 86 comprobaciones de la base de datos se describen en `supabase/README.md`.
El plan completo, con la trazabilidad requisito → código → prueba, está en `docs/PLAN_PRUEBAS.md`.

## Documentación

| Documento | Contenido |
|---|---|
| `docs/MANUAL_USUARIO.md` | Cómo usar la app |
| `docs/MANUAL_TECNICO.md` | Arquitectura, código, configuración, publicación y mantenimiento |
| `docs/PLAN_PRUEBAS.md` | Pruebas automáticas, matriz de trazabilidad y pruebas manuales |
| `docs/REGISTRO_DECISIONES.md` | Decisiones del proyecto y cambios por etapa |
| `supabase/README.md` | Base de datos: instalación, modelo, funciones y reglas de negocio |

## Flujo de una sesión de estudio

0. *(Lección)* Primero se muestran las tarjetas con `get_lesson_content`.
1. La app llama a `start_session` y recibe los ejercicios con sus opciones, **sin las respuestas**.
2. Cada respuesta se envía con `submit_answer`. El servidor corrige, actualiza el repaso SM-2,
   suma XP y actualiza la racha.
3. Al terminar, `finish_session` devuelve el resumen, el bono de la lección y los logros.

La app no puede escribir puntos ni progreso directamente: lo impiden las políticas de seguridad
(RLS) de la base de datos.
