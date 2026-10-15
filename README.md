# Study Guardian (Android prototype)

## Included
- Native Android app, minSdk 29 (Android 10+).
- Add and list homework tasks.
- Accept text/image shared from apps such as Zalo (availability depends on what Zalo shares).
- Select a homework photo and ask Gemini 2.5 Flash to inspect whether it appears blank, identify visible work, compare against the supplied prompt, and point out errors.
- Local Gemini API key setting and a 30-minute reminder timer.
- GitHub Actions workflow builds a debug APK.

## Important limitations
- The app does NOT lock other apps or prove who completed the work.
- AI verification can make mistakes; review the result yourself.
- The app currently handles one image per verification. Paste the assignment text into the prompt box for better comparison.
- Gemini API usage may incur quota/charges according to your Google AI account. Do not share your API key or publish it in a repository.
- This prototype sends the selected image and prompt to Google's Gemini API for analysis. Do not send private messages or other people's personal information without permission.
- The API key is stored in local app preferences, not in a secure backend. Use only for personal testing.
- The current implementation is an early prototype and should be tested before relying on it.

## Build APK without Android Studio
1. Create a GitHub repository and upload all files in this folder.
2. Open the repository's Actions tab.
3. Run **Build Study Guardian APK** (or push to `main`).
4. Open the completed workflow run and download artifact `StudyGuardian-debug-apk`.
5. Extract the artifact ZIP and install `app-debug.apk` on an Android 10+ device. You may need to allow installing apps from that source.

## API key
Create a Gemini API key in Google AI Studio and enter it in the app's settings. Do not paste the key into source code.
