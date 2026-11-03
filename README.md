# Flow AI Beta 3 — Android Floating Assistant

**Flow AI Beta 3** is a native Android floating-assistant project with a dark liquid-glass-inspired interface, a glowing cyan/violet orb, and a draggable floating launcher.

> **Project note:** This project was fully vibe-coded using ChatGPT. Some popups or interface elements may look similar to those in other apps or models. Any such similarity is unintentional; no imitation was intended.

## Current build

- Release label: **Beta 3**
- App version: **1.2** (version code 3)
- Minimum Android version: Android 8.0 (API 26)
- Compile/target SDK: 35
- Java: 17

## Included

- Native launcher screen with overlay-permission status.
- Shortcut to Android's **Display over other apps** permission.
- Start and stop controls for the floating assistant.
- Draggable floating launcher bubble.
- Tablet-friendly glass-style popup with a close control.
- Bring Your Own API Key settings for OpenAI-compatible HTTPS providers.
- Provider API key encrypted locally with Android Keystore and retained between app launches.
- Direct online chat requests to the provider endpoint and selected model.

## Online AI status

To connect your own provider, open Flow AI and tap **Configure my AI API key**. Enter an API key, an OpenAI-compatible HTTPS base URL, and the model name. The default endpoint/model are Qwen-compatible examples; replace them with values from your provider's official documentation.

The key is encrypted on-device using Android Keystore and is not written to the repository or APK. Flow sends requests directly to the endpoint you configure. The key remains saved between launches until you remove it from settings or uninstall/reset the app. Only use providers and credentials you are authorized to use; API usage may incur charges. Never share your key or commit it to Git.

A project-managed backend remains available as a fallback if no local provider key is saved. See [docs/ONLINE_AI_BACKEND.md](docs/ONLINE_AI_BACKEND.md) and [backend/README.md](backend/README.md).

Voice input, always-on wake-word listening, and actions inside other apps are not enabled.

## Build and download the APK

GitHub Actions builds a debug APK on pushes to `main` and `feature/online-flow-ai`, as well as manual runs.

1. Open the repository's **Actions** tab.
2. Open the latest successful **Build Flow AI APK** run.
3. Download the **Flow-AI-Beta-3** artifact.
4. Extract the ZIP. The APK inside is named **Flow AI Beta 3.apk**.
5. Install it on your Android device. If Android asks, allow installation from the app used to open the APK.

You can also open the project in Android Studio and run the `app` configuration.

## Device setup

1. Install and open Flow AI.
2. Tap **Grant floating permission**.
3. In Android settings, allow **Display over other apps** for Flow AI.
4. Return to Flow AI and tap **Launch Flow AI**.
5. Tap the floating **F** bubble to open or close the panel. Drag it to reposition it.

## Privacy and safety

- The app sends chat messages only to the HTTPS backend URL configured at build time.
- The backend is designed to keep the provider key server-side and avoid logging message contents.
- Do not share API keys, passwords, or private credentials in source files, app resources, or GitHub commits.
- Keep Android's overlay permission enabled only while you want the floating assistant available.
