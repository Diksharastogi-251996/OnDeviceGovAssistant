# CivicVoice

CivicVoice is an English-only hackathon prototype for Track 1: AI for Digital Public Infrastructure & Governance. It helps citizens understand public-service procedures using voice input and a local demonstration knowledge base.

## Problem and solution

Public-service procedures can be difficult to understand. CivicVoice accepts an English voice question, shows the transcript, classifies the service and intent locally, retrieves matching reference information, and presents a simple answer.

## Architecture

`CivicVoiceScreen` → `CivicVoiceViewModel` → `SpeechRecognitionManager` → `QueryUnderstandingEngine` → `PublicServiceRepository` → structured result UI.

The recognized question is sent directly to the downloaded LiteRT-LM/Gemma model. Gemma generates the final natural-language answer. The local service classifier is only optional display metadata and does not provide facts or block questions.

## Privacy and offline behavior

- No AIDL, backend, Firebase, cloud database, or application server is used.
- The app does not upload voice recordings or transcripts to its own server.
- Public-service reference data is packaged locally in the application.
- Gemma inference works without internet after the model download is complete.
- Android speech-recognition offline availability depends on the device and installed English language pack. The app does not claim offline speech recognition universally.
- The Gemma model download requires internet the first time and is stored in app-private storage.

## Demo services

Birth Certificate, Death Certificate, Address/Residence Certificate, Income Certificate, and Marriage Certificate. All requirements are demonstration data and may vary by jurisdiction.

## Demo flow

1. Open CivicVoice.
2. Tap the microphone and ask in English: “I want to apply for a birth certificate. What documents do I need?”
3. Show the transcript, local understanding stage, and structured result.
4. Open “How it works” to explain the privacy and local-processing architecture.

## Build and run

Open the project in Android Studio, allow Gradle to sync, and run the `app` module on an Android device or emulator with API 33 or newer. Grant microphone permission for voice input. Typed questions remain available if speech recognition is unavailable.

## Limitations and future work

This is not a production government service and has no government affiliation. The reference requirements are not universal legal guidance. Future versions could add jurisdiction-specific datasets, verified official sources, a user-selected offline speech engine, Room-backed data updates, and optional on-device language-model rewriting with strict grounding to retrieved local facts.
