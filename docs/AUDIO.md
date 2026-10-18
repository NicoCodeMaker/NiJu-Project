# NiJu · Audio de pronunciación (Sección 7)

Estado: **implementado con voz sintética local (Piper) pregenerada y empaquetada en la app**. Probado con pruebas
unitarias y reconocimiento automático de voz; **no probado en un teléfono real** (ver §9).

## 1. Fuentes investigadas

Datos consultados el 2026-10-17 en las páginas oficiales cuando fue posible. Lo que no pude confirmar se marca
**NO VERIFICADO**; antes de contratar cualquier servicio hay que revisar la página oficial.

| Fuente | Enlace oficial | Tipo de audio | Inglés US/UK | Costo / cuota (verificado) | Licencia / uso | Integración Android | Recomendación |
|---|---|---|---|---|---|---|---|
| **Piper (TTS local)** | https://github.com/OHF-Voice/piper1-gpl · voces: https://huggingface.co/rhasspy/piper-voices | Voz sintética, generada sin conexión | US y GB (según la voz) | Gratis | Herramienta GPL-3.0 (solo se usa al generar; no va en la app). **Cada voz tiene su propia licencia**: `en_US-ljspeech-medium` = conjunto LJ Speech de dominio público según su MODEL_CARD (el repositorio de voces la etiqueta MIT). `lessac` se **descartó**: su licencia es de investigación. | Fácil: se empaquetan OGG en `assets/` | **ELEGIDA** (hoy) |
| **Amazon Polly** | https://aws.amazon.com/polly/pricing/ | TTS en la nube | US y GB | Estándar: 5 M car./mes gratis (sin límite de tiempo según la página); Neural: 1 M car./mes los primeros 12 meses. Fuera de cuota: Estándar $4 / Neural $16 por 1 M car. La página dice que se puede guardar en caché y reproducir sin costo adicional | Redistribuir el audio dentro de una app publicada **no lo aclara la página consultada** → revisar términos del servicio | Media: SDK/credenciales; mejor pregenerar | Alternativa para voces más naturales |
| **Google Cloud TTS** | https://cloud.google.com/text-to-speech/pricing | TTS en la nube | US y GB | Estándar/WaveNet: 4 M car. gratis, $4 por 1 M; Neural2: 1 M gratis, $16 por 1 M; Studio $160; Chirp 3 HD $30 (según la tabla consultada) | Revisar términos sobre almacenar y distribuir audio | Media: facturación obligatoria | Alternativa |
| **Azure AI Speech** | https://azure.microsoft.com/pricing/details/cognitive-services/speech-services/ | TTS en la nube | US y GB | Nivel F0: 0,5 M car. neuronales gratis al mes. **Precio por 1 M: NO VERIFICADO** (la página oficial lo mostraba como "$-"; un sitio de terceros daba $15–16, incoherente) | Revisar términos | Media. Coherente con el login Microsoft ya usado | Alternativa |
| **Forvo** | https://forvo.com | Grabaciones de personas | Muchos acentos | API de pago (precio **NO VERIFICADO**) | Los términos consultados permiten usar las pronunciaciones **solo sin fines comerciales** | Alta (API + licencia) | **Descartada** para una app comercial sin acuerdo escrito |
| **Freesound** | https://freesound.org | Sonidos de la comunidad (no pronunciaciones sistemáticas) | — | Gratis | Por archivo: CC0, CC BY (atribución), CC BY-NC (no comercial) | Media | No sirve como fuente principal de vocabulario |
| **Openverse** | https://openverse.org | Buscador de obras con licencia abierta | — | Gratis | No verifica las licencias; hay que comprobarlas en la fuente | Media | Solo como buscador, nunca como garantía de licencia |
| **Wikimedia Commons / Lingua Libre** | https://commons.wikimedia.org · https://lingualibre.org | Grabaciones de voluntarios | Variable | Gratis | Licencia por archivo (no pude abrir la página de reutilización) → **NO VERIFICADO** | Media: descarga y control archivo por archivo | Posible para completar huecos, con registro de licencia por archivo |

**Regla:** que algo se pueda descargar no significa que sea libre para uso comercial. Cada audio de `manifest.json`
apunta a una voz (`voices`) con su fuente y licencia.

## 2. Estrategia elegida

1. **Audio local pregenerado en `assets/`** (120 clips, 1,2 MB): funciona sin conexión, sin claves, sin cuotas.
2. Los textos salen de los `seed*.sql` (fuente única); `tools/audio/generate_audio.py` los lee, genera OGG, normaliza
   y escribe el manifiesto con hash SHA-256 y duración.
3. Variantes: hoy solo `en_US`. El catálogo ya distingue variante (`en_GB` quedaría igual con otra voz).
4. TTS en la nube y audio remoto: **no implementados** (requieren credenciales y decisión de licencias).

## 3. Estructura de carpetas

```
app/src/main/assets/audio/
├── manifest.json
└── en_us/
    ├── words/kitchen.ogg ...
    └── sentences/i_cook_dinner_in_the_kitchen.ogg ...
tools/audio/generate_audio.py, requirements.txt
```

## 4. Datos (palabra → audio)

Clave: minúsculas, todo lo que no sea `a-z0-9` → `_` (`AudioKey.of`, idéntica a `audio_key()` de Python; hay vectores
compartidos en `AudioTests.kt`). Entrada del manifiesto:

```json
{ "key": "kitchen", "kind": "word", "variant": "en_US", "text": "kitchen", "ipa": "/ˈkɪtʃɪn/",
  "file": "audio/en_us/words/kitchen.ogg", "voice": "piper_en_US-ljspeech-medium",
  "sha256": "…", "durationMs": 0 }
```

La búsqueda es **exacta** por (variante, tipo, clave). Si no existe, no suena nada.

## 5. Reproducción en Kotlin

`PronunciationController` (lógica, probada en JVM) + `MediaPlayerEngine` (MediaPlayer):

* un solo reproductor a la vez; se libera al terminar, al fallar, al pedir otro audio y en `onDestroy`;
* `USAGE_MEDIA`/`CONTENT_TYPE_SPEECH`; la app **no cambia el volumen global**;
* velocidad (0,75× / 1×) aplicada después de `start()`;
* estados: `Idle`, `Playing`, `Unavailable` (sin audio en el catálogo), `Failed` (archivo ausente/dañado);
* sin audio → mensaje al usuario y un registro una sola vez; **nunca se sustituye por otro archivo**.

Botones: "Escuchar" en tarjetas de palabra y de oración, y en la retroalimentación de cada ejercicio ("Repetir" =
volver a tocar). Ajustes: variante y velocidad.

## 6. Cómo agregar vocabulario

1. Añadir filas al `seed_words` del SQL.
2. `python tools/audio/generate_audio.py --model <voz.onnx> --voice-id piper_en_US-ljspeech-medium` (`--check` solo valida).
3. La prueba `everySeedWordAndExampleHasAudio` falla si falta algún audio.

## 7. Optimización

OGG Vorbis mono, ~10 KB por palabra; catálogo en memoria cargado en segundo plano; sin descargas en tiempo de ejecución.

## 8. Documentación de licencias

El manifiesto lleva, por voz: fuente del modelo, licencia del conjunto de datos, licencia del repositorio, uso
comercial y atribución. Procedimiento: (a) elegir voz, (b) leer su MODEL_CARD y la licencia del conjunto de datos,
(c) anotar la fecha de revisión (`reviewedOn`), (d) ante la duda, no usar la voz. **Antes de publicar la app, revisar
esta tabla con quien responda legalmente por la licencia** — no soy abogado.

## 9. Validación y límites honestos

* Unitarias: clave, manifiesto (120 entradas, archivo y hash existentes), búsqueda exacta, controlador con motor falso,
  cobertura de audio de todo el contenido de los seeds.
* Reconocimiento automático (Whisper, `small.en`) sobre los 120 clips: **17 no coincidieron** (14 palabras sueltas
  como *aisle, bag, bike, key, lamp, map, luggage, taxi…* y 3 oraciones). Las palabras aisladas de una sílaba son
  difíciles para un reconocedor, por lo que **no prueba que estén mal ni que estén bien**: hace falta que una persona
  hable inglés las escuche (pendiente). Si alguna suena mal se regenera con `--force` o se cambia la voz.
* No se probó en un dispositivo real: MediaPlayer, velocidad y TalkBack están sin comprobar en hardware.
