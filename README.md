# Jarvis — Personal Assistant (Android)

A voice personal assistant powered by a free Gemini API key. Talk to it, and it can chat,
set alarms/timers, make calls, send texts, tell you the weather, add calendar events,
search the web, and send an SOS with your location.

## How to get your APK (no computer coding needed)

1. Create a free account at https://github.com and sign in.
2. Click the **+** (top right) → **New repository**. Name it `jarvis`. Set it to **Public**
   (Actions are free on public repos). Click **Create repository**.
3. On the new empty repo page, click **uploading an existing file**.
4. Unzip the project on your computer, then drag **all the files and folders inside** the
   `Jarvis` folder into the upload box (not the outer folder itself — its contents).
5. Click **Commit changes**. The build starts automatically.
6. Go to the **Actions** tab → click the running/finished build → scroll to **Artifacts** →
   download **jarvis-apk**. Unzip it to get `app-debug.apk`.
7. Copy that APK to your phone, tap it, allow "install from unknown sources", install.

## First run

- Open the app → tap the gear (top right) → paste your **free Gemini key**
  (get it at https://aistudio.google.com → *Get API key*, no card needed).
- Add your name, city, and an SOS trusted contact. Save.
- Tap the mic and talk, or type. Grant the permissions it asks for.

## Change the AI model

Edit `MODEL` in `app/src/main/java/com/jarvis/assistant/GeminiClient.kt`.
