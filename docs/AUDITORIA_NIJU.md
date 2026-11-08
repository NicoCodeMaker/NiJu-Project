# NiJu · Auditoría inicial y resultado

Línea base medida antes de cambiar nada: compilación OK, 35 pruebas unitarias OK, 86 comprobaciones SQL OK.

## Problemas reales encontrados y estado

| # | Problema | Estado |
|---|---|---|
| 1 | Sin audio de ningún tipo (botón "Escuchar" inexistente) | Resuelto: 120 clips locales, ver `AUDIO.md` |
| 2 | Inicio mostraba una "Lección del día" estática (playa) que no dependía del usuario | Resuelto: tarjeta "Continuar" real (`get_next_lesson`, migración 4) |
| 3 | Cualquier contexto nuevo mostraba la ilustración de la playa | Resuelto: Hogar, Transporte y genérica |
| 4 | Contextos sin progreso ni estado completado | Resuelto: lecciones X/Y, barra, "✓ Completado" |
| 5 | Estado de lecciones solo por color (amarillo/azul/rojo) y texto casi invisible (`#EEF2FF`) | Resuelto: ✓ / número / 🔒 + color de texto con contraste |
| 6 | Colores hexadecimales dispersos en los layouts | Resuelto: tokens en `colors.xml` (claro/oscuro) |
| 7 | Alturas fijas que cortan texto con fuentes grandes | Resuelto en las pantallas tocadas (`minHeight`) |
| 8 | Textos en español escritos en el código | Resuelto en Inicio, Contextos, tarjetas, práctica |
| 9 | Solo 3 contextos | Añadidos Hogar y Transporte (4 lecciones, 24 palabras) |

## No resuelto / fuera de alcance

* Solo 5 de las 10 categorías pedidas existen; faltan 5 con su contenido y audio.
* Solo 3 tipos de ejercicio (los del enum de la base); no hay escucha-y-elige, ordenar, ni pronunciación.
* UK English, audio remoto/nube, y mensaje "pista" no implementados.
* Sin pruebas en dispositivo real ni con un Supabase real.

---

# Auditoría v2 (2026-11-08)

Línea base medida al empezar la v2 (commit `9ce10cf`): 86 + 21 comprobaciones SQL aprobadas en PostgreSQL 16;
50 pruebas unitarias. Hallazgos frente al pedido de la v2 y su estado:

| # | Hallazgo | Estado |
|---|---|---|
| 10 | Solo 5 de 12 contextos | Resuelto: 12 contextos, 24 lecciones, 144 palabras (`seed_v2_contexts.sql`) |
| 11 | 3 tipos de ejercicio, todos de opción múltiple con 3 opciones | Resuelto: 9 tipos (elegir, escribir, ordenar, escuchar, imagen, error, diálogo), 4 opciones |
| 12 | SM-2 binario: la velocidad y el tipo de respuesta no influían | Resuelto: calidad 0–5 en el servidor |
| 13 | Sin pantalla de repaso ni estados de palabra | Resuelto: pestaña Repaso con estados, urgentes, pronóstico y ficha |
| 14 | Gamificación limitada a XP, nivel, racha y 6 logros | Resuelto: retos diarios, metas semanales, bonos de racha, títulos, 20 logros, evaluaciones |
| 15 | Fin de sesión mostrado en un diálogo simple | Resuelto: pantalla de resultado con puntaje, recompensas y celebración |
| 16 | Solo inicio de sesión con Microsoft; sin recuperación | Resuelto: correo y contraseña con registro, recuperación y cambio (Microsoft se conserva) |
| 17 | Perfil con pocas estadísticas y sin historial | Resuelto: estadísticas del servidor e historial de sesiones |
| 18 | Ruta con datos parciales | Resuelto: pasos reales por contexto, con evaluación |
| 19 | Estilos repetidos en cada layout | Resuelto: tema Material 3 con estilos de texto, botones, tarjetas y campos |
| 20 | Sin imágenes por palabra | Resuelto con emoji por palabra concreta (sin archivos externos) |
| 21 | El documento de grado describe Firebase y MVC | Pendiente: se actualiza después del código (decisión del responsable) |

## No resuelto / fuera de alcance en la v2

* **Pronunciación con micrófono** (que el estudiante hable y se evalúe): no implementada. Los 9 tipos son de
  lectura, escritura y escucha.
* **Clips Piper para los 84 términos nuevos:** no generados (huggingface bloqueado en el entorno); usan TTS del teléfono.
* **Compilación con Gradle, Lint y pruebas en teléfono:** no ejecutadas en el entorno de desarrollo (sin acceso a
  los repositorios de Google/Maven). Ver `INFORME_V2.md` §5 para lo que sí se verificó.
