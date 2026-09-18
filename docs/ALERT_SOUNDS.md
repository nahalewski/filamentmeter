# Notification sounds and vibration

Setup → Alert sounds & vibration provides a test notification and Android channel settings for each YOLO failure type, plus printer event sound settings.

| Failure | Sound | Vibration |
| --- | --- | --- |
| Spaghetti | Three quick tones | Three short pulses |
| Lifting / warping | Two rising tones | Two long pulses |
| Layer shift | High then low | Short then long |

The WAV files are original synthesized chimes, reproducible with `tools/generate_alert_sounds.py`. Named Android resource URIs avoid depending on numeric resource IDs between builds. Android channels control the sounds and vibration, including when the app is in the background. The app requests VIBRATE and notification permission. Phone notification volume, silent mode, Do Not Disturb, blocked channels and notification cooldown can suppress or reduce alerts; the app does not bypass these settings.

Failure alerts still require three fresh consecutive detections. Each class has its own episode latch and five-minute cooldown, so one class does not suppress a different class. The same ongoing failure does not beep on every scan. No automatic printer commands are sent. The shared warning notification opens the printer that generated it.

Progress uses the existing silent channel. Printer events use a new sound-and-vibration channel because an existing channel's auditory behavior cannot be changed programmatically. Existing channels are retained and user changes to the new channels are respected on later launches. See [Android notification channels](https://developer.android.com/develop/ui/views/notifications/channels).

Device verification checks distinct channel sounds, vibration patterns, resource decoding, and delivery of clearly labeled test notifications. It does not measure speaker output or physical vibration amplitude.
