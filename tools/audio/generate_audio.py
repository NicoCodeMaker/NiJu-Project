#!/usr/bin/env python3
"""
NiJu · Generador de audios de pronunciación (Piper TTS, sin conexión).

Lee el vocabulario de supabase/seed*.sql (tabla temporal `seed_words`), sintetiza cada
palabra y cada oración de ejemplo con Piper, los convierte a OGG/Vorbis y escribe:

    app/src/main/assets/audio/<variante>/words/<clave>.ogg
    app/src/main/assets/audio/<variante>/sentences/<clave>.ogg
    app/src/main/assets/audio/manifest.json        (catálogo que lee la app)

La <clave> se calcula igual que en la app (AudioKey.kt): minúsculas, todo lo que no sea
a-z0-9 pasa a "_". Por eso la app encuentra el audio solo con el texto en inglés,
sin cambiar la base de datos.

Uso:
    python3 -m venv .venv && .venv/bin/pip install -r tools/audio/requirements.txt
    .venv/bin/python tools/audio/generate_audio.py \
        --model /ruta/en_US-ljspeech-medium.onnx --voice-id piper_en_US-ljspeech-medium
Requiere ffmpeg con libvorbis. Ver docs/AUDIO.md (licencias de cada voz).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import wave
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEED_GLOB = "supabase/seed*.sql"
ASSETS = ROOT / "app/src/main/assets"

# Metadatos de licencia de las voces que se han revisado. Para usar otra voz,
# añade su entrada tras verificar su licencia (ver docs/AUDIO.md, sección 6).
VOICES = {
    "piper_en_US-ljspeech-medium": {
        "variant": "en_US",
        "engine": "Piper TTS (OHF-Voice/piper1-gpl, GPL-3.0; herramienta de generación, no se distribuye en la app)",
        "model": "en_US-ljspeech-medium",
        "modelSource": "https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/ljspeech/medium",
        "dataset": "LJ Speech Dataset (https://keithito.com/LJ-Speech-Dataset/)",
        "datasetLicense": "Dominio público (según la MODEL_CARD del modelo)",
        "modelRepoLicense": "MIT (etiqueta del repositorio rhasspy/piper-voices en Hugging Face)",
        "commercialUse": "permitido según las licencias indicadas; verificar antes de publicar",
        "attributionRequired": False,
        "attribution": "Voz sintética Piper en_US-ljspeech-medium (entrenada con LJ Speech, dominio público).",
        "reviewedOn": "2026-10-17",
    },
}


def audio_key(text: str) -> str:
    """Debe coincidir EXACTAMENTE con AudioKey.of() en Kotlin."""
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def sql_literals(line: str):
    """Extrae los literales 'texto' (con '' escapado) y los enteros de una tupla SQL."""
    out = []
    for m in re.finditer(r"'((?:[^']|'')*)'|(?<![\w'])(\d+)(?![\w'])", line):
        out.append(m.group(1).replace("''", "'") if m.group(1) is not None else int(m.group(2)))
    return out


def read_vocabulary():
    """[(lesson, english, ipa, sentence_en)] de todos los seed*.sql."""
    items = []
    for path in sorted(ROOT.glob(SEED_GLOB)):
        in_block = False
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if line.lower().startswith("insert into seed_words"):
                in_block = True
                continue
            if not in_block:
                continue
            if line.startswith("--") or not line:
                continue
            if not line.startswith("("):
                in_block = False
                continue
            vals = sql_literals(line)
            if len(vals) != 9:
                raise SystemExit(f"{path.name}: tupla inesperada ({len(vals)} campos): {line}")
            lesson, _pos, english, _es, ipa, _tag, _lvl, sentence_en, _sentence_es = vals
            items.append((lesson, english, ipa, sentence_en))
            if line.endswith(");"):
                in_block = False
    return items


def duration_ms(wav_path: Path) -> int:
    with wave.open(str(wav_path), "rb") as w:
        return round(1000 * w.getnframes() / w.getframerate())


def to_ogg(wav: Path, ogg: Path):
    # Recorta silencios de los extremos, normaliza volumen y comprime (mono, ~10-25 KB por clip).
    trim = ("silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.05,"
            "areverse,silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.08,areverse,"
            "loudnorm=I=-18:TP=-2:LRA=7")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-af", trim,
         "-ac", "1", "-ar", "22050", "-c:a", "libvorbis", "-q:a", "3", str(ogg)],
        check=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True, help="ruta al .onnx de Piper (junto a su .onnx.json)")
    ap.add_argument("--voice-id", required=True, choices=sorted(VOICES), help="voz con licencia revisada")
    ap.add_argument("--speed", type=float, default=1.0, help="1.0 normal; >1 más lento (length_scale)")
    ap.add_argument("--force", action="store_true", help="regenerar aunque el archivo exista")
    ap.add_argument("--check", action="store_true", help="solo lista lo que haría; no sintetiza")
    args = ap.parse_args()

    voice_meta = VOICES[args.voice_id]
    variant = voice_meta["variant"]
    vdir = variant.lower()

    # Texto único -> (clave, tipo, texto, ipa)
    todo: dict[tuple[str, str], dict] = {}
    for _lesson, english, ipa, sentence in read_vocabulary():
        for kind, text, pron in (("word", english, ipa), ("sentence", sentence, None)):
            key = audio_key(text)
            if not key:
                raise SystemExit(f"Texto sin clave válida: {text!r}")
            prev = todo.get((kind, key))
            if prev and prev["text"].lower() != text.lower():
                raise SystemExit(f"Colisión de clave {key!r}: {prev['text']!r} vs {text!r}")
            todo.setdefault((kind, key), {"kind": kind, "key": key, "text": text, "ipa": pron})

    print(f"{len(todo)} audios únicos "
          f"({sum(1 for k in todo if k[0] == 'word')} palabras, "
          f"{sum(1 for k in todo if k[0] == 'sentence')} oraciones)")
    if args.check:
        for (kind, key), it in sorted(todo.items()):
            print(f"  {kind:8} {key:45} {it['text']}")
        return 0

    from piper import PiperVoice, SynthesisConfig  # import tardío: --check no lo necesita
    voice = PiperVoice.load(args.model)
    cfg = SynthesisConfig(length_scale=args.speed)

    entries = []
    with tempfile.TemporaryDirectory() as tmp:
        for (kind, key), it in sorted(todo.items()):
            folder = "words" if kind == "word" else "sentences"
            rel = f"audio/{vdir}/{folder}/{key}.ogg"
            ogg = ASSETS / rel
            ogg.parent.mkdir(parents=True, exist_ok=True)
            wav = Path(tmp) / f"{kind}_{key}.wav"
            if args.force or not ogg.exists():
                with wave.open(str(wav), "wb") as wf:
                    voice.synthesize_wav(it["text"], wf, syn_config=cfg)
                to_ogg(wav, ogg)
                print("  generado", rel)
            # La duración se mide sobre el OGG final para que el manifiesto sea fiel al archivo.
            probe = subprocess.run(
                ["ffprobe", "-v", "error", "-show_entries", "format=duration",
                 "-of", "default=nw=1:nk=1", str(ogg)], capture_output=True, text=True, check=True)
            entries.append({
                "key": key, "kind": kind, "variant": variant, "text": it["text"],
                **({"ipa": it["ipa"]} if it["ipa"] else {}),
                "file": rel, "voice": args.voice_id,
                "sha256": hashlib.sha256(ogg.read_bytes()).hexdigest(),
                "durationMs": round(float(probe.stdout.strip()) * 1000),
            })

    manifest = {
        "schema": 1,
        "generatedBy": "tools/audio/generate_audio.py",
        "generatedOn": date.today().isoformat(),
        "voices": {args.voice_id: voice_meta},
        "entries": entries,
    }
    out = ASSETS / "audio/manifest.json"
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    total = sum((ASSETS / e["file"]).stat().st_size for e in entries)
    print(f"Manifiesto: {out.relative_to(ROOT)} · {len(entries)} entradas · {total/1024:.0f} KB de audio")
    return 0


if __name__ == "__main__":
    sys.exit(main())
