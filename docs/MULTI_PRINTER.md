# Multiple printers (development build)

Setup contains Your printers, per-printer material cost summaries, expandable print history and waste records. Choose Add another printer or Find all printers on network, enter that printer's LAN access code, then save. Discovery collects distinct Bambu LAN announcements/replies on both supported discovery ports for ten seconds. A printer that is asleep, on another VLAN, or whose multicast traffic is blocked may need manual entry. Discovery cannot retrieve LAN access codes.

The Meter tab lists every saved printer with its live status. The Printer tab's Active printer menu chooses the target for controls and camera. All configured printers retain their MQTT monitoring connections in the foreground service. The widget and quiet progress notification follow the active printer; event notifications identify and open their source printer. Disconnect stops monitoring for all printers.

Existing connection settings migrate into the first profile without clearing credentials, pricing or waste counters. Each saved profile has separate connection details and prices. Saving another serial adds a profile instead of overwriting the earlier printer. Use recognizable names when multiple printers share a model.

History begins when the app observes an active print. It does not download older printer history. Material costs are estimates based on manually entered sliced grams and spool pricing, with an immutable price/grams snapshot when the print is first observed. Percentage-based consumption is approximate; it does not account for nonuniform extrusion, electricity, depreciation or labor. If the app misses the end of a print, the record is marked End not observed. Repeated jobs started and completed entirely while monitoring is unavailable cannot be recovered.

Waste records preserve dated changes to each printer's Purging, Failed prints and Scraps counters. Existing totals migrate as an opening balance. Decreasing a counter records a negative correction; updating price alone does not reprice old records. Waste is shown separately from print cost because failed-print material can overlap and must not be counted twice.

The supplied H2S artwork is bundled unchanged. H2S/O1S/O1S-V2 are recognized using [BambuStudio's O1S model definition](https://github.com/bambulab/BambuStudio/blob/master/resources/printers/O1S.json). Unsupported camera models show an explicit message: adding artwork does not add their video protocol. Current local JPEG streaming and experimental YOLO operate on supported P1/A1 family cameras and only for the active printer.

Validation: JVM tests and Android lint; six isolated Android device tests cover profile migration, per-printer separation, price snapshots, waste adjustments, repeated job identities, stale completed jobs, and H2S identity. Physical multi-printer and H2S camera validation requires the corresponding hardware.
