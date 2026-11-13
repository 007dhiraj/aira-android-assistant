# AIRA — Android Voice Assistant

AIRA is a native Android voice-assistant project. The first milestone focuses on a clean Android app shell, Hindi speech input, spoken responses, and safe hand-off to built-in Android intents.

## Current milestone
- Native Android app built with Kotlin and Jetpack Compose
- Hindi speech recognition through Android SpeechRecognizer
- Spoken responses through Android TextToSpeech
- Basic commands: open supported apps, dial a number, compose an SMS, set an alarm, and report battery level
- Runtime microphone permission handling
- GitHub Actions Android debug-build workflow

## Build
Open this repository in Android Studio (Hedgehog or newer), allow Gradle sync, then run the `app` configuration on an Android device/emulator.

A debug APK is also built by the GitHub Actions workflow on pushes and pull requests to `main`.

## Android limitations
Speech recognition availability and quality depend on the device and installed speech services. Calls and messages are handed to Android's dialer/messaging apps; AIRA does not silently place calls or send messages. Screen-off wake-word listening, WhatsApp automation, and owner voice verification require separate implementation and device-specific testing.

## Project
- Application ID: `com.aira.assistant`
- Minimum Android version: Android 8.0 (API 26)
