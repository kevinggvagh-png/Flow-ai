# Flow AI — Android floating assistant

Flow AI is a native Android floating-assistant starter with a dark liquid-glass-inspired interface, a glowing cyan/violet orb, and a draggable floating launcher.

## Current release

- App version: **1.1** (version code 2)
- Minimum Android version: Android 8.0 (API 26)
- Compile/target SDK: 35
- Java: 17

## Included

- Native launcher screen with a live overlay-permission status.
- One-tap route to Android's **Display over other apps** permission.
- Start and stop controls for the floating assistant.
- Draggable floating launcher bubble.
- Centered, tablet-friendly glass-style popup with a close control.
- Original greeting: **“I am Flow. Your digital intelligence is online. Systems ready. State your objective.”**
- No microphone permission is requested by this UI-only build.

## Important: AI connection status

This release is the upgraded **interface and floating-window build**, not yet a connected conversational AI. Sending a message currently displays a clear setup notice; it does not contact an AI model or pretend to have generated an answer.

A real AI connection needs a secure HTTPS backend. The model-provider secret must be stored on that backend (for example, as a hosting-provider secret), never embedded in the APK or committed to this repository. No API key is included in this project.

Voice input, always-on wake-word listening, and actions inside other apps are not enabled in this build. Those features require separate, user-visible Android permissions and careful platform-specific implementation.

## Build an APK

The repository's GitHub Actions workflow builds a debug APK on each push to `main` and uploads it as the `Flow-AI-debug-apk` artifact.

1. Open the repository's **Actions** tab.
2. Select **Build Flow AI APK** and open the latest successful run.
3. Download the **Flow-AI-debug-apk** artifact.
4. Extract the ZIP and install `app-debug.apk` on your Android tablet.
5. If Android asks, allow installation from the app you used to open the APK.

You can also open the project in Android Studio and run the `app` configuration.

## Device setup

1. Install and open Flow AI.
2. Tap **Grant floating permission**.
3. In Android settings, allow **Display over other apps** for Flow AI.
4. Return to Flow AI and tap **Launch Flow AI**.
5. Tap the floating **F** bubble to open or close the panel. Drag the bubble to reposition it.

## Privacy and safety

- The current UI-only build does not record audio or send messages to a server.
- Keep Android's overlay permission enabled only while you want the floating assistant available.
- Do not put API keys, passwords, or private credentials in source files, app resources, or GitHub commits.
