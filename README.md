# Nova AI Assistant

Nova is a Kotlin Android assistant focused on on-device privacy, voice interaction, practical phone actions, and optional online search.

## Features

- Native Android chat interface with speech input and text-to-speech replies.
- Optional “Hey Nova” listener using Android's on-device speech recognizer when the device has an offline speech pack. This is a speech-recognition-based fallback, not a dedicated low-power keyword model.
- Qwen2.5 0.5B Instruct GGUF model bundled inside the APK. The first local AI request copies the included model into Nova's private app storage; no separate model download is required.
- Optional Gemini API key in Settings for online AI summaries.
- In-app web search results; explicit web searches and current-news requests use the internet.
- Local on-device memory: say “remember that …”, “what do you remember”, or “forget all memories”.
- Opens known and installed apps by displayed name, where Android exposes their launcher activity.
- Permission-based flashlight control, media-volume up/down/mute, Wi-Fi/Bluetooth and other Android settings panels.
- Directions in Maps, weather lookup, timer/alarm intents, and dialer handoff.
- User-controlled background notification service and battery-settings shortcut.
- No app account or Nova backend.

## Offline AI setup

1. Download and install the Nova APK that includes Qwen.
2. Open Nova and enable **Use offline AI for ordinary questions** in Settings.
3. The first local question copies the model from the APK to app-private storage, so allow some time and keep at least 1 GB of free storage available.
4. Ordinary questions then use the local model. Explicit web searches, latest-news requests, and weather lookup still need internet. The small model is less capable than a larger cloud model and may be slower on some phones.

The Qwen2.5-0.5B-Instruct-GGUF model is obtained from the upstream [Qwen model repository](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF). The model has its own license and terms, separate from Nova's MIT source license; review the upstream license before redistributing.

## Important Android limitations

- The APK is much larger because it includes the model (roughly 500 MB or more). Android installation also needs extra free space while copying the model into app-private storage.
- Android may block background activity launches. After saying “Hey Nova”, Nova may show a notification to tap rather than open itself automatically.
- A microphone foreground service is visible in notifications, consumes battery, and may be stopped by Android or the phone manufacturer. Force-stop cannot be bypassed.
- On-device speech recognition requires a supported Android version and a downloaded offline language pack. The listener can be less reliable and less power-efficient than a dedicated keyword model.
- Nova cannot bypass a secure lock screen, PIN, password, biometrics, or Android permission prompts.
- Android restricts direct control of some system toggles. Nova opens the relevant system panel when direct changes are not allowed.
- Manual microphone and wake-phrase voice recognition require an on-device recognizer; install an offline speech pack if needed.
- Web search and Gemini require internet. Gemini requires the user's own API key.

## Build APK

Open **Actions → Build Nova APK** and download the **nova-debug-apk-with-qwen-model** artifact from a successful workflow run. The model is downloaded by the build workflow and included inside the APK; it is not committed to the source repository.

## License

MIT for Nova app source. Third-party libraries and model weights retain their own licenses.
