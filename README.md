# Inventory Scanner (Android)

Offline-first Android inventory app for retail stores, built on the YOLO26 detect → track → count
pipeline from [apple_counting](https://github.com/muhammadrahman-hehe/apple_counting), extended with
barcode scanning and a product/stock database.

## Features
- **Barcode scan** (ML Kit) → open or create the product, edit stock quantity.
- **AI count – shelf / pile photo:** capture a photo, the model counts items using overlapping-tile
  inference (same tiling + dedupe as the original), with a live confidence slider and ±1 manual correction.
- **AI count – live belt:** camera stream with tracker + line-crossing counter (receiving / unloading);
  items are counted once when they cross the line, per class. Adds the result to stock.
- **Inventory:** search, add/edit/delete, location (aisle/shelf), count history, CSV import / export (share sheet).
- Everything is stored locally (Room/SQLite); no server required.

## Project layout
```
app/src/main/java/com/inventoryscanner/app/
  ml/        Detector (ONNX Runtime), Tracker + LineCounter, tile merge  (ports of the Python utils/)
  data/      Room entities + DAO
  ui/        Compose screens: Inventory, Product, Scan, Count
app/src/main/assets/  model.onnx + labels.txt   (currently the apple model: apple, damaged_apple)
tools/export_model.py  .pt -> model.onnx + labels.txt
```

## Build
CI builds the APK on every push (`.github/workflows/android.yml`, artifact **inventory-scanner-apk**).
Locally: install Android Studio (or the SDK + JDK 17) and run `./gradlew assembleDebug`;
APK at `app/build/outputs/apk/debug/`. The release APK is signed with the debug key for sideloading —
add a real signing config before publishing to Play.

## Using your own products (important)
The bundled model only knows **apples**. To count store products, train YOLO26 on your own labelled photos
(Roboflow/CVAT, one class per SKU or product family, plus shelf photos taken in real store lighting), then:
```
pip install ultralytics onnx onnxslim
python tools/export_model.py --weights runs/detect/train/weights/best.pt
```
This overwrites `model.onnx` and `labels.txt`; no app code changes are needed. The export must be the
NMS-free `[1, 300, 6]` format (the script forces `end2end=True`) at 640×640.

## Known limitations
- Barcode scanning identifies *what* a product is; the AI counts *how many are visible*. Dense/occluded
  shelves will under-count — use the correction buttons and, for accuracy, keep photos close and well lit.
- Live belt mode runs single-pass inference (no tiling) for speed; tune the line position per setup.
- Not yet tested on physical devices; inference speed depends on the phone (ONNX Runtime CPU, 4 threads).
