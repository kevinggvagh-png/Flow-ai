# Flow AI — Android floating assistant

Liquid-glass-inspired Android floating assistant starter for phones and tablets.

## Build

Open this project in Android Studio and build the `app` debug APK, or use the GitHub Actions workflow in `.github/workflows/android.yml`.

## Features

- Native Android launcher activity
- User-controlled display-over-other-apps permission flow
- Visible foreground microphone service with a persistent notification and Stop action
- Experimental speech-recognition wake phrase support
- Floating glass-style assistant panel

## Limitations

This is a starter implementation, not a fully capable conversational AI. It does not include a hosted AI backend or API key. Android speech recognition is not a guaranteed always-on low-power hotword engine; the operating system may stop recognition. Listening must be started by the user while the app is visible, and Android shows a microphone indicator and persistent notification.

## Requirements

- Android SDK 35
- Minimum Android API 26
- JDK 17

See the project source and workflow for build details.
