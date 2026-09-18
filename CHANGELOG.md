# Changelog

## 0.1.0-beta.2

Second beta: multiple printers, cost records, current-print previews, and experimental local failure detection.

- Display the active print's sliced model thumbnail on Meter and Printer, with tap-to-enlarge. Read the exact reported 3MF from LAN FTPS, cache previews per printer/job, and avoid guessing a plate in ambiguous multi-plate files.

- Add original distinct notification chimes and vibration patterns for spaghetti, lifting/warping, and layer shifts; per-class alert cooldowns; audible/vibrating printer-event channel; and Setup controls for testing and customizing alerts. Routine progress remains silent.

- Draw labeled YOLO candidate boxes over the live camera and fullscreen preview, with confidence, scan status, matching fit/crop/zoom transforms, and automatic expiry of stale boxes.

- Add H2S artwork and identity, saved printer profiles, collection of network discovery results, a fleet list in Meter, and an active-printer selector in Printer.
- Monitor configured printers concurrently; preserve per-printer estimated print history and dated waste adjustments with historical price snapshots. Notification and widget actions select their source printer. See docs/MULTI_PRINTER.md for scope and limitations.

- Experimental local YOLO print watch for spaghetti, layer shifts, and warping, with shared background camera monitoring, repeated-frame confirmation, and warning evidence. P1S lifting accuracy remains unvalidated; no automatic printer commands. See docs/PRINT_WATCH.md for model provenance and release licensing requirements.

- Add saved manual waste counters for purging, failed prints, and scraps, with individual costs and a combined total in Setup.

## 0.1.0-beta.1

First public beta release.

- Printer dashboard, P1-series live camera, and confirmed print controls.
- Cost estimates and responsive home-screen widget.
- Printer artwork, multiple AMS detection, and corrected humidity display.
- Background monitoring, quiet progress notifications, and distinct printer event alerts.
- Bambu error descriptions and troubleshooting links.
- LAN access code setup instructions.
- Fixed stale preparation percentages in notifications, completed widget progress, reconnect races, and discovery timeouts.

Validation: 15 unit tests; release build and Android lint; live P1S monitoring, camera, and background notification checks on Pixel Fold and Pixel 9 Pro Fold with development builds.
