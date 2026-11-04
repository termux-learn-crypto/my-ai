# My AI

Vilas ka personal AI Android app (step by step).

**Step 1 – Voice echo:** mic dabao, bolo (Hindi / Hinglish / Marathi / English) → app text likhta hai aur wahi baat neural awaaz me bolta hai.

- STT: Android SpeechRecognizer (Google service preferred), live partial text, Auto language detection on Android 14+
- TTS: Microsoft Edge neural voices (Swara/Madhur, Aarohi/Manohar, Neerja/Prabhat) → fallback Android Google TTS
- Roman Hinglish/Marathi → Devanagari transliteration (Google Input Tools) so voice pronounces right

APK: https://github.com/termux-learn-crypto/my-ai/releases/latest/download/MyAI.apk
