# AiCaller

An Android app that can screen incoming calls, auto-answer on your behalf, hold a short
AI-driven conversation with the caller (speech-to-text → LLM → text-to-speech), and keep
a log of every call with a transcript.

This is a real, working Kotlin/Jetpack Compose project — not a mockup. Open it in Android
Studio to build and run it. It will **not** build or run inside this sandbox (no Android
SDK / emulator here), so the steps below are for your own machine.

## What's included

```
app/src/main/java/com/aicaller/app/
  call/
    AiCallScreeningService.kt   # pre-ring screening (silently block likely spam)
    AiInCallService.kt          # answers calls, routes audio to the AI loop
    AiConversationService.kt    # foreground service running the live conversation
  ai/
    LlmClient.kt                # pluggable LLM backend (swap endpoint/model/key)
    VoiceIO.kt                  # SpeechRecognizer (listen) + TextToSpeech (speak)
  data/
    CallRecord.kt, CallRecordDao.kt, AppDatabase.kt   # Room DB for call history
    SettingsRepository.kt       # DataStore-backed user settings
  ui/
    MainActivity.kt             # nav shell + permission/role setup
    CallLogScreen.kt            # call history list
    SettingsScreen.kt           # greeting, screening mode, AI backend config
```

## Setup

1. **Open in Android Studio** (Hedgehog/2023.1+ recommended). It will auto-generate the
   Gradle wrapper and sync dependencies the first time you open the project — no manual
   wrapper setup needed.
2. Run on a **physical device** running Android 10 (API 29) or newer. Call-screening /
   in-call APIs behave unreliably or not at all on emulators.
3. On first launch, the app will ask you to:
   - Grant phone, contacts, microphone, and notification permissions.
   - Set AiCaller as your **Caller ID & spam app** (or default Phone app) under
     *Settings → Apps → Default apps → Phone app*. This is required for the app to see
     and act on incoming calls — Android locks these APIs to the one app holding that role.
4. In the **Settings** tab, set your name, screening mode (off / unknown numbers only /
   all calls), your greeting message, and your AI backend details (endpoint, model, API key).

## How it works

- **`AiCallScreeningService`** fires before the phone rings and can silently reject calls
  that fail caller-ID verification (likely spoofed/spam numbers).
- **`AiInCallService`** receives the call once it's ringing. If the number isn't in your
  contacts (or screening mode is "all calls"), it auto-answers.
- Once active, **`AiConversationService`** starts: it speaks your greeting, listens for the
  caller's reply, sends the transcript to your configured LLM along with a system prompt,
  speaks the reply back, and loops until the call ends (capped at 20 turns as a safety limit).
- Every call is logged to a local Room database with its transcript, visible in the Call Log tab.

## Important limitations to know before you rely on this

- **Call audio capture is the hard part.** `SpeechRecognizer` is built to listen to the
  phone's mic, not cleanly to "the other party's voice during an active call." Reliable
  capture of in-call audio varies a lot by Android version and device manufacturer — some
  OEMs restrict `VOICE_CALL`-source audio access. Test thoroughly on your actual target
  device(s); you may need a device-specific audio routing workaround.
- **The default-dialer/call-screening role is a real, user-granted permission.** Android
  will not let any app silently gain control of calls — the person using the app must
  explicitly set it as their Caller ID & spam app or default phone app in system settings.
- **Don't ship your LLM API key inside the app.** As written, `LlmClient` calls the LLM
  directly from the device using a key you type into Settings — that's fine for testing,
  but a key bundled in or typed into a shipped app can be extracted from the device. For
  production, replace this with calls to your own backend server that holds the real key.
- **Call recording/consent laws vary a lot by jurisdiction.** Many places (including
  many US states) require one-party or all-party consent before recording a phone call,
  and some have specific rules about disclosing that an AI is on the line. The Settings
  screen includes a recording toggle and an on-screen warning, but you are responsible for
  configuring the greeting and recording behavior to comply with the law where you and
  your callers are located — e.g., many jurisdictions expect the AI to identify itself as
  an automated assistant early in the call, which the default greeting already does.
- **iOS is not possible for this feature set.** Apple does not allow third-party apps to
  answer calls or inject audio into the call path, so there's no equivalent iOS build of
  this app's core feature.

## Customizing the AI backend

`LlmClient.kt` is written against the Anthropic Messages API shape by default, but the
endpoint, model, and key are all read from Settings at runtime — point it at any
compatible HTTP backend (your own proxy, another provider formatted the same way, etc.)
by editing the request/response parsing in `LlmClient.nextReply()`.
