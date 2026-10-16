# Nova AI Assistant

Nova is a Kotlin Android assistant focused on on-device privacy, voice interaction, practical phone actions, and optional online search.

## Features

- Native Android chat interface with speech input and text-to-speech replies.
- Optional “Hey Nova” listener using Android's on-device speech recognizer when the device has an offline speech pack. This is a speech-recognition-based fallback, not a dedicated low-power keyword model.
- **Qwen2.5 0.5B Instruct GGUF model bundled inside the APK.** You do not need to download a separate model file.
- Optional Gemini API key in Settings for online AI summaries.
- In-app web search results; explicit web searches and current-news requests use the internet.
- Local on-device memory: say “remember that …”, “what do you remember”, or “forget all memories”.
- Opens known and installed apps by displayed name, where Android exposes their launcher activity.
- Permission-based flashlight control, media-volume up/down/mute, Wi-Fi/Bluetooth and other Android settings panels.
- Directions in Maps, weather lookup, timer/alarm intents, and dialer handoff.
- User-controlled background notification service and battery-settings shortcut.
- No app account or Nova backend.

## Download Nova (APK with Qwen included)

The GitHub Actions build downloads Qwen automatically and packages it into the APK. The model is not stored in the Git repository, and there is no separate model download to perform.

1. Open the **[Nova Actions builds](https://github.com/Inexra/nova-ai-assistant/actions/workflows/android-apk.yml)**.
2. Select the latest run named **Build Nova APK**. Wait until it says **Success**. If the latest run is still running, come back after it finishes. Do not download an older run if you want the bundled-model version.
3. Scroll to the **Artifacts** section near the bottom of that run's summary.
4. Select **nova-debug-apk-with-qwen-model**. GitHub downloads a ZIP file containing **app-debug.apk**.
5. Extract/unzip that ZIP. You only need **app-debug.apk** to install Nova; you do not need to download or copy a separate GGUF model.
6. Transfer the APK to your Android phone if you downloaded it on a computer. If Android asks, allow your browser or file manager to **Install unknown apps**, then open **app-debug.apk** and tap **Install**.
7. Open Nova and grant only the permissions you want to use, such as microphone for voice input and camera permission for flashlight control.
8. In Nova's **Settings**, turn on **Use offline AI for ordinary questions**.
9. Ask Nova an ordinary question. On the first local AI request, Nova copies the Qwen model from the installed APK into its private app storage. Keep the app open, wait for the copy to finish, and ensure you have at least around **1 GB of free storage**. Later local requests use that stored copy.

### Which files do I need?

- **Required:** **app-debug.apk**, found inside the downloaded **nova-debug-apk-with-qwen-model** artifact ZIP.
- **Not required separately:** **qwen2.5-0.5b-instruct-q4_k_m.gguf**. The build downloads and embeds this model in the APK.
- **Not required for installation:** source code, Gradle files, or the workflow YAML. Those are for developers who want to inspect or build Nova themselves.

The APK will be much larger than a typical Android app because it includes the model (roughly 500 MB or more). The APK may take time to download and install. Make sure you use the artifact from a **successful** build; a running or failed run will not have the completed APK artifact.

## Offline AI and online features

Qwen2.5 0.5B Instruct runs locally on the phone after installation, without a Gemini API key or internet connection for inference. The first local request copies the bundled model into app-private storage. The small model is less capable than larger cloud models and may be slow on some phones.

Explicit web searches, latest-news requests, and weather lookup still need internet. Gemini summaries also need internet and the user's own Gemini API key.

The Qwen2.5-0.5B-Instruct-GGUF model is obtained from the upstream [Qwen model repository](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF). The model has its own license and terms, separate from Nova's MIT source license; review the upstream license before redistributing.

## Important Android limitations

- Android may block background activity launches. After saying “Hey Nova”, Nova may show a notification to tap rather than open itself automatically.
- A microphone foreground service is visible in notifications, consumes battery, and may be stopped by Android or the phone manufacturer. Force-stop cannot be bypassed.
- On-device speech recognition requires a supported Android version and a downloaded offline language pack. The listener can be less reliable and less power-efficient than a dedicated keyword model.
- Nova cannot bypass a secure lock screen, PIN, password, biometrics, or Android permission prompts.
- Android restricts direct control of some system toggles. Nova opens the relevant system panel when direct changes are not allowed.
- Manual microphone and wake-phrase voice recognition require an on-device recognizer; install an offline speech pack if needed.

## Build APK (developers)

The workflow at .github/workflows/android-apk.yml downloads the Qwen GGUF model during the cloud build, places it in the app assets, then builds and uploads the APK. To build manually, use a compatible Android SDK and Java 17, and place the model at app/src/main/assets/qwen2.5-0.5b-instruct-q4_k_m.gguf before running the Gradle assembleDebug task.

## License

MIT for Nova app source. Third-party libraries and model weights retain their own licenses.
