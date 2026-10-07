"""Export a YOLO26 .pt model to the files the Android app bundles.

    pip install ultralytics onnx onnxslim
    python tools/export_model.py --weights path/to/best.pt

Writes app/src/main/assets/model.onnx and labels.txt. YOLO26 is NMS-free, so the
ONNX output is [1, 300, 6] = (x1, y1, x2, y2, conf, class) per row, which is
exactly what app/.../ml/Detector.kt decodes. Use --imgsz 640 (the app's fixed
input size) unless you also change Detector.SIZE.
"""
import argparse
import shutil
from pathlib import Path

from ultralytics import YOLO

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--imgsz", type=int, default=640)
    ap.add_argument("--half", action="store_true", help="FP16 weights (smaller file)")
    a = ap.parse_args()

    model = YOLO(a.weights)
    out = Path(model.export(format="onnx", imgsz=a.imgsz, simplify=True, opset=17, half=a.half, dynamic=False, end2end=True))
    ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy(out, ASSETS / "model.onnx")
    (ASSETS / "labels.txt").write_text("\n".join(model.names[i] for i in sorted(model.names)) + "\n")
    print("classes:", model.names)
    print("wrote", ASSETS / "model.onnx", (ASSETS / "model.onnx").stat().st_size // 1024, "KiB")


if __name__ == "__main__":
    main()
