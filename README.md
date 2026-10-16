# Nova AI Assistant

Nova is a Kotlin Android assistant focused on on-device privacy, voice interaction, practical phone actions, and optional online search.

## Features

- Native Android chat interface with speech input and text-to-speech replies.
- Optional “Hey Nova” listener using Android's on-device speech recognizer when the device has an offline speech pack. This is a speech-recognition-based fallback, not a dedicated low-power keyword model.
- Optional downloadable Qwen2.5 0.5B Instruct GGUF model (about 491 MB). Download it once in Settings; afterward, offline AI answers run on the phone CPU without sending prompts to a server. The model is not bundled in the APK.
- Optional Gemini API key in Settings for online AI summaries.
- In-app web search results; explicit web searches and current-news requests use the internet.
- Local on-device memory: say “remember that …”, “what do you remember”, or “forget all memories”.
- Opens known and installed apps by displayed name, where Android exposes their launcher activity.
- Permission-based flashlight control, media-volume up/down/mute, Wi-Fi/Bluetooth and other Android settings panels.
- Directions in Maps, weather lookup, timer/alarm intents, and dialer handoff.
- User-controlled background notification service and battery-settings shortcut.
- No app account or Nova backend.

## Offline AI setup

1. Install Nova and open **Settings**.
2. Download the optional offline model (roughly 491 MB). Use a stable connection and keep enough free storage.
3. Turn on **Use offline AI for ordinary questions**.
4. Ordinary questions then use the local model. Explicit web searches, latest-news requests, and weather lookup still need internet. The small model is less capable than a larger cloud model and may be slower on some phones.

The model weights are provided by the Qwen2.5-0.5B-Instruct-GGUF project; check the model repository's license and terms before redistributing model files. The model is downloaded on demand and is not packaged inside Nova's APK.

## Important Android limitations

- Android may block background activity launches. After saying “Hey Nova”, Nova may show a notification to tap rather than open itself automatically.
- A microphone foreground service is visible in notifications, consumes battery, and may be stopped by Android or the phone manufacturer. Force-stop cannot be bypassed.
- On-device speech recognition requires a supported Android version and a downloaded offline language pack. The listener can be less reliable and less power-efficient than a dedicated keyword model.
- Nova cannot bypass a secure lock screen, PIN, password, biometrics, or Android permission prompts.
- Android restricts direct control of some system toggles. Nova opens the relevant system panel when direct changes are not allowed.
- Voice recognition may use a network-backed system service when on-device recognition is unavailable for the manually started microphone button; the dedicated wake listener explicitly requires on-device recognition.
- Web search and Gemini require internet. Gemini requires the user's own API key.

## Build APK

Open **Actions → Build Nova APK** and download the `nova-debug-apk` artifact from a successful workflow run. APK size will grow when the native inference library is included; the 491 MB model is downloaded separately.

## License

MIT for Nova app source. Third-party libraries and model weights retain their own licenses.
