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
- Online-AI client that sends chat messages to a configurable HTTPS backend.

## Online AI status

The Android app is prepared to connect to an online AI backend, and the repository includes backend source code in `backend/`. **The backend is not deployed or configured yet**, so a working live AI connection is not included automatically in this build.

To enable it, an eligible adult/project administrator must arrange a provider account and hosting that permit this app's intended use, deploy the backend, and store the provider API key as a server-side environment secret. Never put API keys in the APK, source code, or Git commits.

Set the Android build property `FLOW_API_URL` to the deployed backend's HTTPS base URL when building. See [docs/ONLINE_AI_BACKEND.md](docs/ONLINE_AI_BACKEND.md) and [backend/README.md](backend/README.md).

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
