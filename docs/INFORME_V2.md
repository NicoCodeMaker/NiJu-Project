# NiJu · Informe de la versión 2

Fecha: 2026-11-08 · Rama local `niju-v2` (4 commits sobre `9ce10cf`) · Copiada a la carpeta del
responsable (`C:\Users\Julian\AndroidStudioProjects\NiJu-Project`), sin borrar archivos.

Este informe distingue lo **verificado** (se ejecutó y pasó) de lo **pendiente** (no se pudo ejecutar
en el entorno de desarrollo). No se marca nada como probado si no se ejecutó.

## 1. Qué existía (línea base, commit `9ce10cf`)

- App Kotlin + Views/XML + ViewModel + StateFlow sobre Supabase (Firebase ya había sido retirado).
- Inicio de sesión solo con Microsoft.
- 5 contextos, 10 lecciones, 60 palabras; 3 tipos de ejercicio de opción múltiple con 3 opciones.
- SM-2 binario (acierto = 4, fallo = 2) en el servidor; XP, niveles, racha y 6 logros.
- 120 clips de audio Piper locales.
- Pruebas: 107 comprobaciones SQL (86 + 21) y 50 unitarias.

## 2. Qué se desarrolló

**Base de datos** (`migrations/…05`, `…06`, `seed_v2_contexts.sql`)
- 9 tipos de ejercicio generados y corregidos en el servidor: traducir EN→ES y ES→EN, completar, imagen,
  escuchar y elegir, escribir, ordenar palabras, detectar el error y completar un diálogo.
- Las respuestas se guardan en columnas que la app no puede leer.
- SM-2 clásico con calidad 0–5 derivada del acierto, el tipo de ejercicio y el tiempo de respuesta.
- Estados de palabra (nueva, aprendiendo, refuerzo, en repaso, dominada) y urgentes.
- Evaluación por contexto: 10 ítems, aprueba con 80 %, +100 XP.
- Retos: 3 diarios entre 8 plantillas y 3 metas semanales. Bonos de racha a 3, 7, 14, 30, 60 y 100 días.
- Títulos de nivel y 20 logros. Ninguna recompensa se paga dos veces: el libro de puntos usa claves únicas.
- 7 funciones nuevas para las pantallas Repaso, Logros, Perfil e historial.
- 7 contextos nuevos: Escuela, Playa, Comercio, Salud, Tecnología, Trabajo y Viajes. En total hay
  **12 contextos, 24 lecciones y 144 palabras**, cada una con IPA, ejemplo y nivel. Las palabras
  concretas tienen además emoji, y las nuevas tienen diálogo.

**App**
- Autenticación:
  - Registro con correo y contraseña, con confirmación por correo.
  - Inicio de sesión, cierre de sesión y sesión persistente.
  - Recuperación por enlace (`niju://login-callback`) y cambio de contraseña desde Configuración.
  - Validación en el teléfono y medidor de fuerza.
  - Microsoft se conserva.
- Práctica reescrita para los 9 tipos: botones de opción, campo de texto y fichas para ordenar. Muestra
  retroalimentación con la solución y lleva un contador de XP de la sesión.
- Pantalla de resultado: anillo de puntaje, confeti, bonos, retos cumplidos, logros y subida de nivel.
- Pantallas nuevas:
  - **Repaso:** estados, urgentes, pronóstico de 7 días, ficha de palabra y sesiones enfocadas.
  - **Logros:** retos, metas, próximo bono de racha, insignias y recompensas recientes.
- Pantallas renovadas:
  - **Perfil**, con estadísticas reales e historial.
  - **Inicio**, **Ruta**, **Contextos** y **Lecciones** (con tarjeta de evaluación).
- Audio: si una palabra no tiene clip local, suena la voz en inglés del teléfono.
- Barra inferior de 5 pestañas: Inicio, Aprender, Repaso, Logros y Perfil.

## 3. Errores corregidos durante la v2

| Error | Corrección |
|---|---|
| `home_level_value` sin el argumento de formato: Inicio se habría cerrado al abrir | String nuevo con dos argumentos |
| Uso de `java.time` (requiere API 26; la app soporta 24) | Reemplazado por `Calendar` |
| Dos `LoaderViewModel` compartían clave en Lecciones: una pantalla recibía los datos de la otra | Clave propia (`context_progress`) |
| `when` no exhaustivo con los tipos nuevos | Práctica reescrita por tipo de entrada |
| Orden de parámetros del controlador de audio rompía las pruebas existentes | Respaldo como parámetro opcional antes de `report` |
| Prueba con una aserción siempre verdadera | Corregida para comprobar el valor real |
| Nueva contraseña abierta desde Configuración redirigía a Inicio | Ahora solo regresa |

## 4. Cambios visuales

- Tema Material 3 con roles de color azul claro y morado, en variante clara y oscura.
- Degradados en los encabezados y tarjetas redondeadas.
- Estilos unificados (`TextAppearance.NiJu.*` y `Widget.NiJu.*`) para texto, botones, tarjetas, campos,
  barras de progreso y chips.
- Animaciones de transición entre pantallas, aparición del resultado y sacudida en las respuestas incorrectas.
- Estados de carga, error y vacío en las pantallas nuevas.
- Vistas propias para el confeti, el anillo de puntaje y las barras del pronóstico.
- 239 textos nuevos en recursos (`strings_v2.xml`, más plurales y un arreglo); en los layouts solo quedan 3 emoji decorativos escritos directamente.

## 5. Pruebas ejecutadas y resultados

| Prueba | Cómo | Resultado |
|---|---|---|
| Base de datos (3 suites) | `run_local_tests.sh` en PostgreSQL 16 local, con un esquema `auth` simulado | **189/189** (86 + 21 + 82). Re-ejecutadas el 2026-11-08 |
| Compilación Kotlin de `main` y `test` | `kotlinc` 2.3 contra las mismas librerías del proyecto, con stubs de `R`/`BuildConfig`/ViewBinding generados | **Compila sin errores** |
| Pruebas unitarias | JUnit 4 sobre lo compilado | **75/75** |
| Recursos e ids | Validadores propios: referencias, estilos padre, atributos e ids por pantalla. Se comprobó que detectan errores introducidos a propósito | **Sin errores** |
| Gradle `assembleDebug`, `testDebugUnitTest`, `lintDebug` | — | **No ejecutado.** El entorno no tiene acceso a los repositorios de Google/Maven. Lo ejecuta el responsable |
| App en teléfono o emulador | — | **No ejecutada.** Pruebas M01–M40 de `PLAN_PRUEBAS.md` pendientes |
| Contra el Supabase real | — | **No ejecutada.** Migraciones 5 y 6 aún sin aplicar |

Las pruebas unitarias usan respuestas JSON generadas con las funciones SQL reales (fixtures `v2_*.json`).

## 6. Configuración externa pendiente (responsable)

1. Compilar en Windows:
   `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
   Si aapt2 o Lint encuentran algo que los validadores no vieron, corregirlo.
2. En el SQL Editor de Supabase, en este orden:
   1. `20261108000005_enum_values.sql`, **solo**.
   2. `20261108000006_learning_v2.sql`.
   3. `seed_v2_contexts.sql`. Si la base no tiene `seed.sql` ni `seed_home_transport.sql`, ejecutarlos antes.
3. En Authentication:
   - Activar el proveedor **Email**.
   - Agregar `niju://login-callback` en Redirect URLs.
   - Revisar las plantillas *Confirm signup* y *Reset password*.
   - Para uso real, configurar SMTP propio: el correo integrado de Supabase tiene un límite bajo.
4. Ejecutar las pruebas manuales M01–M40 en un teléfono.

## 7. Lo que no se hizo

- **Décimo tipo de actividad (pronunciación con micrófono):** no implementado. Hay 9 tipos calificados
  más las tarjetas de presentación. Evaluar la voz exige reconocimiento de voz y una decisión sobre
  privacidad del audio.
- **Clips Piper de los 84 términos nuevos:** no generados, porque huggingface.co está bloqueado en el
  entorno. Esos términos suenan con la voz del teléfono. Para generarlos, ver `AUDIO.md` §10.
- **Imágenes ilustradas por palabra:** se usan emoji; no se añadieron archivos ni enlaces externos.
- **Recursos antiguos sin uso:** no se borraron (cadenas `ui_etapa*`, `item_option_*`). Se puede hacer
  con Lint (`UnusedResources`) en el equipo del responsable.
- **Documento de grado** (`Proyecto Grado_ NiJu.docx`): aún describe Firebase y MVC. Se actualiza en el
  paso siguiente, como decidió el responsable.
- **Validación pedagógica y de usabilidad** con estudiantes: no realizada.

## 8. Cómo ejecutar

1. Base de datos: `supabase/README.md` (6 migraciones y 3 seeds).
2. `local.properties`:
   - `sdk.dir`
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY` (la clave `anon`; nunca la `service_role`).
3. Android Studio → configuración `app` → ejecutar en un teléfono con Android 7.0 o superior.
4. Pruebas: `.\gradlew.bat :app:testDebugUnitTest`; base de datos: `bash supabase/tests/run_local_tests.sh`
   (requiere PostgreSQL 15 o superior).
