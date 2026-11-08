# NiJu

Aplicación Android para aprender vocabulario en inglés en 12 contextos reales, con lecciones,
9 tipos de ejercicio, repetición espaciada SM-2, evaluaciones, retos, logros y seguimiento del progreso.

## Tecnologías

| Capa | Tecnología |
|---|---|
| App | Kotlin, Views/XML, ViewModel + StateFlow, corrutinas, Material 3 (MVVM + repositorios) |
| Backend | Supabase: PostgreSQL con RLS, Auth y funciones RPC (`security definer`) |
| Cliente | supabase-kt 3.8 (auth-kt, postgrest-kt) + Ktor OkHttp |
| Inicio de sesión | Correo y contraseña (con recuperación) y Microsoft (Azure). Google preparado, desactivado |
| Audio | Clips locales Piper; respaldo con la voz en inglés del teléfono (TextToSpeech) |

## Estructura

```
app/src/main/java/com/example/niju_project/
├── data/
│   ├── model/        Modelos (reflejan las respuestas de las funciones SQL)
│   ├── remote/       Cliente Supabase y traducción de errores
│   └── repository/   AuthRepository, LearningRepository, ProfileRepository
├── domain/           Lógica pura: validación de cuenta, avance de sesión, ordenar palabras
├── audio/            Catálogo de clips, reproductor, voz del sistema
├── reminders/        Recordatorios (WorkManager + ReminderPolicy)
├── ui/               ViewModels por pantalla, auth/, results/, review/, rewards/, profile/, widget/
└── *Activity.kt      Pantallas principales
app/src/test/         Pruebas unitarias + respuestas reales del servidor (fixtures)
supabase/             Migraciones, contenido (3 seeds) y pruebas de la base de datos
docs/                 Manuales, plan de pruebas, decisiones, auditoría, audio e informe v2
```

## Cómo compilar

1. Crea la base de datos siguiendo `supabase/README.md` (6 migraciones + 3 seeds).
2. Copia `local.properties.example` como `local.properties` y completa la URL y la clave `anon`.
3. En Supabase activa el proveedor **Email** y permite la redirección `niju://login-callback`.
4. Abre el proyecto en Android Studio y ejecuta la configuración `app`.

Sin `local.properties` la app compila igual, pero la pantalla de inicio de sesión avisa que
falta conectar Supabase.

## Pruebas

```bash
./gradlew :app:testDebugUnitTest      # 75 pruebas unitarias (JVM, sin emulador)
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
bash supabase/tests/run_local_tests.sh  # 189 comprobaciones de la base (PostgreSQL local)
```

En Windows usa `.\gradlew.bat`. El plan completo, con la trazabilidad requisito → código → prueba,
está en `docs/PLAN_PRUEBAS.md`; el estado real de cada verificación, en `docs/INFORME_V2.md`.

## Documentación

| Documento | Contenido |
|---|---|
| `docs/INFORME_V2.md` | Informe de la versión 2: qué había, qué se hizo, pruebas y pendientes |
| `docs/MANUAL_USUARIO.md` | Cómo usar la app |
| `docs/MANUAL_TECNICO.md` | Arquitectura, código, configuración, publicación y mantenimiento |
| `docs/PLAN_PRUEBAS.md` | Pruebas automáticas, matriz de trazabilidad y pruebas manuales |
| `docs/REGISTRO_DECISIONES.md` | Decisiones del proyecto y cambios por etapa |
| `docs/AUDIO.md` | Fuentes, licencias y funcionamiento del audio |
| `supabase/README.md` | Base de datos: instalación, modelo, funciones y reglas de negocio |

## Flujo de una sesión de estudio

0. *(Lección)* Primero se muestran las tarjetas con `get_lesson_content`.
1. La app llama a `start_session` y recibe los ejercicios con sus opciones, **sin las respuestas**.
2. Cada respuesta se envía con `submit_answer`. El servidor corrige, calcula la calidad SM-2 (0–5),
   reprograma el repaso, suma XP, actualiza la racha y avanza los retos.
3. Al terminar, `finish_session` devuelve el resumen (bonos, logros, retos, subida de nivel) y la app
   muestra la pantalla de resultado.

La app no puede escribir puntos ni progreso directamente, ni leer las respuestas correctas:
lo impiden las políticas de seguridad (RLS) y los permisos por columna de la base de datos.
