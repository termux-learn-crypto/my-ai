# My AI — working notes

## Repo
- https://github.com/termux-learn-crypto/my-ai (public, branch main)
- APK: https://github.com/termux-learn-crypto/my-ai/releases/latest/download/MyAI.apk
- Workflow: .github/workflows/build.yml (push + workflow_dispatch) -> gradle 8.7 (setup-gradle, no wrapper jar) -> assembleRelease -> release tag "latest" asset MyAI.apk
- Signing: app/myai-release.jks committed (alias myai, pass myai12345) so updates install over old version
- Separate from Maira / JarvisAssistant — never touch those.

## Stack
- Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06.00 (compiler ext 1.5.14), compileSdk/targetSdk 34, minSdk 24, JDK 17
- R8 minify + shrinkResources on release
- Package com.vilas.myai, app name "My AI"

## STT / TTS choice (Step 1, 2026-11-04)
- STT: Android SpeechRecognizer, explicitly bound to Google's RecognitionService (googlequicksearchbox > tts > as) because MIUI default can be Xiaomi's. Partial results live, silence auto-stop (1.5 s hint). Free, best Hindi/Marathi accuracy on phone, no API key.
  - Auto mode: Android 14+ EXTRA_ENABLE_LANGUAGE_DETECTION + LANGUAGE_SWITCH (hi-IN, mr-IN, en-IN); older -> last detected/chosen tag.
- TTS: Microsoft Edge "read aloud" neural voices via websocket (port of python edge-tts 7.3.1 incl. Sec-MS-GEC **v2** token — tts/EdgeToken.kt, verified byte-exact vs python; tts/EdgeTtsClient.kt tested on JVM: Hindi ~1.2 s first call). Fallback: Android TextToSpeech (Google engine).
  - Voices: hi Swara/Madhur, mr Aarohi/Manohar, en-IN Neerja/Prabhat. Gender + speed in settings sheet.
  - Roman Hinglish/Marathi -> Devanagari via Google Input Tools (inputtools.google.com, itc hi-t-i0-und / mr-t-i0-und) then Hindi/Marathi voice.
  - If edge-tts upstream changes token/version (CHROMIUM_FULL_VERSION, TRUSTED_CLIENT_TOKEN), update EdgeToken.kt from rany2/edge-tts src/edge_tts/{constants,drm}.py.
- Research (2025-26): Azure/Edge neural rated good for Indic; Google Chirp best STT but paid; SpeechRecognizer free & on-device-capable.

## Languages
- Chips: Auto, हिंदी (hi-IN), Hinglish (hi-IN recognizer + Hindi voice), मराठी (mr-IN), English (en-IN)
- Marathi vs Hindi Devanagari: detected/chosen STT tag first, then small Marathi word-hint list.

## Files
- Lang.kt (modes + script heuristics), MainViewModel.kt, MainActivity.kt (Compose UI), stt/SpeechInput.kt, tts/{EdgeToken,EdgeTtsClient,Transliterator,VoiceOut}.kt

## Next steps
1. Chat UI polish (history save, copy, better animations)
2. Brain (Qwen fine-tune on Kaggle vs free Gemini API — Vilas to decide)
3. Phone actions (accessibility, calls, apps)
