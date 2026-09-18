# Licensing and third-party notices

Filament Meter: Copyright (C) 2026 Filament Meter contributors.
The application source is licensed under the GNU Affero General Public License,
version 3 (AGPL-3.0-only). You may redistribute and modify it under that license.
It is provided WITHOUT ANY WARRANTY, including MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See LICENSE for the full terms. Third-party components
retain their own licenses and copyright notices.

Release source, build scripts, and the pinned model are available at:
https://github.com/nahalewski/filamentmeter/tree/v0.1.0-beta.2
Build instructions are in README.md. The release also supplies a source archive.
Private signing keys and printer credentials are not required to build your own APK.

## YOLO print-failure model

The unmodified `app/src/main/assets/failure_detector.tflite` was supplied by
Sebastián Torres through https://github.com/SebTC/FDM-Failure-Detection.
Its embedded metadata identifies Ultralytics 8.4.37 and AGPL-3.0 licensing.
The metadata and full license are preserved in `app/src/main/assets/licenses`.
Ultralytics framework source: https://github.com/ultralytics/ultralytics/tree/v8.4.37
Model download: https://drive.google.com/uc?export=download&id=1HQSlUMMqAXdt0wYJzb0evgwyh2-q6hN4
SHA-256: 583ca573c3dcdc900584153f169b03ed054676030c393b38415b3cfda3b7f6eb

The upstream detector repository has an MIT license, reproduced in the license
directory; that does not replace the model's embedded AGPL notice. Filament Meter
provides its Kotlin preprocessing, decoding, NMS, overlay, and alert integration
as source. The upstream project does not publish the original training notebook,
training dataset, or editable PyTorch checkpoint; these are not included or
claimed to be reproducible from this source archive. The distributed TFLite
weights are included unchanged.

## Android and inference libraries

- AndroidX / Jetpack Compose: Copyright The Android Open Source Project; Apache-2.0.
  https://android.googlesource.com/platform/frameworks/support/
- Kotlin: Copyright JetBrains s.r.o. and Kotlin Programming Language contributors;
  Apache-2.0. https://github.com/JetBrains/kotlin
- Kotlin coroutines: Copyright JetBrains s.r.o.; Apache-2.0.
  https://github.com/Kotlin/kotlinx.coroutines
- LiteRT 1.4.2: Copyright The TensorFlow Authors; Apache-2.0.
  https://github.com/google-ai-edge/LiteRT
- Apache Commons Net 3.11.1: Copyright The Apache Software Foundation;
  Apache-2.0. Its LICENSE and NOTICE are reproduced in the license directory.
  https://commons.apache.org/proper/commons-net/
- Eclipse Paho Java MQTT 1.2.5: Copyright IBM Corp. and other contributors.
  Distributed under its Eclipse Distribution License 1.0 option; full EDL text
  is included. https://github.com/eclipse-paho/paho.mqtt.java/tree/v1.2.5

License texts are bundled in the APK and available in Setup > Licenses and source.
Dependency versions are specified by the Gradle build files. Test-only fixtures
retain their separate source and license in `app/src/androidTest/assets`.

## Artwork and printer information

Printer and AMS artwork was supplied by the project owner. Bambu Lab names,
logos, and marks belong to their respective owners; this project is independent
and does not grant trademark rights or imply endorsement. Error catalog
provenance is preserved in `app/src/main/assets/printer_errors_source.txt`.
