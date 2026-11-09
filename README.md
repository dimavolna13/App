# CSV Cleaner for Android

A minimal native Android app written in Java. It reads CSV files locally, optionally trims whitespace, removes empty rows and duplicate rows, and exports the cleaned CSV using Android's system file picker. No internet permission, accounts or backend.

## Build an APK

1. Install Android Studio (with Android SDK 35).
2. Open the `CSV_Cleaner_Android` folder as a project.
3. Allow Android Studio to install the required Android Gradle Plugin 8.7.3 and Gradle version it recommends (8.9).
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. The APK will appear in `app/build/outputs/apk/debug/app-debug.apk`.

## Limitations

- UTF-8, comma-separated CSV only; max 5 MB.
- No XLSX, semicolon-separated or other encodings in version 1.
- Duplicate removal is based on identical complete rows (after optional trimming), including the header if repeated.
- CSV processing is currently synchronous and can pause the UI for larger files.
- The project has not been compiled in this environment because Android SDK and Gradle are unavailable.

## Phone-only cloud build (GitHub Actions)

A `.github/workflows/build-apk.yml` workflow is included. From an Android browser, create a GitHub repository, upload these project files preserving folders (GitHub Codespaces browser terminal is one option), and open **Actions > Build Android APK > Run workflow**. When complete, download the **CSV-Cleaner-APK** artifact and extract its `app-debug.apk`. It is a debug build for personal testing, not a signed Play Store release. GitHub account and hosted build availability required. This workflow has not been run here.
