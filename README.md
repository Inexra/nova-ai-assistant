# Nova AI Assistant

Nova is a privacy-first Android assistant project designed to grow toward a local AI assistant with a “Hey Nova” wake phrase.

## Current MVP
- Native Android app with a chat-style interface
- Android system text-to-speech
- On-device speech recognition when Android exposes an offline recognizer
- Basic local demo commands
- No account, analytics, or app backend

## Important limitations
This first build is a foundation, not yet a full offline LLM assistant. Speech recognition availability depends on Android's installed recognition service and language packs. A persistent hotword listener and local language model are planned next; they are not claimed to work in this MVP. Android requires user-granted permissions and limits background microphone use. An ordinary app cannot control every part of the phone without restrictions.

## Build APK
Open **Actions → Build Nova APK → Run workflow** or push a commit. Download the `nova-debug-apk` artifact from a successful run.

## Roadmap
1. Stable Android app and repeatable cloud APK builds
2. Importable quantized local model with storage and compute checks
3. Local inference integration and conversation memory stored on-device
4. Offline speech model and configurable wake phrase
5. Permission-based app actions and optional user-enabled Accessibility Service

## Privacy
Nova does not send chat text to a remote AI service in this MVP. The Android speech recognizer selected by the OS may have its own network/privacy behavior; use the explicit on-device recognizer option where available.

## License
MIT