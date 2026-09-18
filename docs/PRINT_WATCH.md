# Experimental YOLO print watch

The Printer tab can enable on-device print-failure warnings. The monitor shares a single P1 LAN camera socket with the preview and keeps analyzing while the app is backgrounded and the printer reports RUNNING. No frames are sent to a cloud service, and detection never sends pause/stop commands.

It waits 60 seconds after starting an active-print monitoring session, samples at most once every 5 seconds, and requires the same failure class in three consecutive frames. A camera gap longer than 20 seconds breaks confirmation. It alerts once per episode, requiring six clear samples and a five-minute cooldown before another alert. Disabling detection or stopping monitoring releases its camera work when preview is not needed.

The default confidence threshold is 70%, adjustable from 50–95%. Lower thresholds may detect more failures but increase false alarms. The warning includes an in-memory captured frame with normalized detection boxes. Dismiss clears that image; images are not written to a gallery or uploaded.

## Model and limitations

Evaluation model source: [SebTC/FDM-Failure-Detection](https://github.com/SebTC/FDM-Failure-Detection), using the author's linked TFLite export. The embedded model metadata lists:

- Classes: 0 layer shifting, 1 spaghetti, 2 warping.
- Input: 1 × 640 × 640 × 3 float RGB, normalized to 0–1.
- Output: YOLO detect head without NMS, class scores after normalized xywh.
- Embedded license: **AGPL-3.0**. The repository's MIT label does not replace the license embedded in the model.
- SHA-256: `583ca573c3dcdc900584153f169b03ed054676030c393b38415b3cfda3b7f6eb`.

Starting with 0.1.0-beta.2, the pinned model is bundled in the APK and tracked in Git. The app is licensed under AGPL-3.0; see [licensing and model provenance](../THIRD_PARTY_NOTICES.md). Run `python tools/fetch_failure_model.py` only if you need to restore the original model asset. Without that asset, the app reports detection unavailable. License texts and a release-source link are available offline in Setup > Licenses and source (opening the source website requires internet access).

The author evaluated primarily on an Ender 3 camera setup and disabled warping in their example configuration because of the camera angle. This integration exposes warping as an experimental class, **not verified P1S lift protection**. Visible raised edges may be detected; hidden edges, small defects, dark filament, occlusion, and low camera frame rates can cause misses. A clean result means no confident failure in that frame, not that the print is guaranteed good. Printed shapes can resemble failures.

There is no auto-pause: inspect the evidence and live camera, then use the app's existing print controls. True failure detection rates on the P1S require a labeled collection of normal and failed prints; software tests and live normal-print monitoring do not establish those rates.

## Validation (2026-09-17)

- 22 JVM tests passed, including output decoding/NMS, invalid scores, repeated-frame confirmation, gaps, and alert cooldown.
- Pixel Fold instrumented inference test passed with the real model: a blank image produced no detections; the camera-photo region of the author's spaghetti example produced a spaghetti detection at approximately 70.5%, above the default 70% threshold. The reference fixture is test-only and carries its source license.
- A short live normal P1S print check produced no candidates at 70%; inference took roughly 350–540 ms on Pixel Fold. Frames continued being analyzed after returning to the home screen.
- Build and Android lint passed. These checks do not establish sensitivity, specificity, or lifting detection accuracy on the P1S. No physical failure was deliberately caused, and no automatic printer control was sent.
