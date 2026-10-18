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
