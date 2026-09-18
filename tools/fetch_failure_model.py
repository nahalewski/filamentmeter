"""Fetch the pinned research model for local evaluation; review its AGPL terms before distribution."""
import hashlib
import pathlib
import urllib.request

URL = "https://drive.google.com/uc?export=download&id=1HQSlUMMqAXdt0wYJzb0evgwyh2-q6hN4"
SHA256 = "583ca573c3dcdc900584153f169b03ed054676030c393b38415b3cfda3b7f6eb"
target = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/failure_detector.tflite"
with urllib.request.urlopen(URL, timeout=60) as response:
    data = response.read(20_000_001)
if len(data) > 20_000_000 or hashlib.sha256(data).hexdigest() != SHA256:
    raise SystemExit("Model checksum mismatch; no model was installed.")
target.write_bytes(data)
print("Installed pinned YOLO failure model for local evaluation. Model metadata license: AGPL-3.0.")
