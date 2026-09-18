"""Generate original short notification chimes; no third-party audio assets."""
import math
import struct
import wave
from pathlib import Path

RATE = 24000
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
ROOT.mkdir(parents=True, exist_ok=True)
PATTERNS = {
    'alert_spaghetti': [(880, .13), (0, .09), (880, .13), (0, .09), (1175, .22)],
    'alert_lifting': [(523, .26), (0, .14), (784, .4)],
    'alert_layer_shift': [(988, .16), (0, .12), (440, .42)],
}
for name, notes in PATTERNS.items():
    samples = []
    for frequency, duration in notes:
        count = int(duration * RATE)
        for i in range(count):
            # Short attack/release prevents clicks. Gentle harmonic makes phone playback clearer.
            envelope = min(1, i / (RATE * .012), (count-1-i) / (RATE * .045))
            phase = 2 * math.pi * frequency * i / RATE
            value = .5 * envelope * (math.sin(phase) + .15 * math.sin(phase*2)) if frequency else 0
            samples.append(struct.pack('<h', round(value * 32767)))
    with wave.open(str(ROOT / f'{name}.wav'), 'wb') as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b''.join(samples))
