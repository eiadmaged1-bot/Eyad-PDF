# Eyad PDF

Eyad PDF is an offline Android workspace for viewing, organizing, converting, compressing, securing and extracting content from PDF and image files.

## Current release candidate

- Branch: `android/release-candidate-v0.15`
- Application ID: `com.eyadpdf.android`
- Version: `0.15.0` (`versionCode 215`)
- Minimum Android: API 26
- Target/compile SDK: 36
- JDK: 17
- UI: Kotlin + Jetpack Compose
- Offline distribution flavor: `opensource`

## Privacy contract

The open-source build is checked in CI to ensure that no INTERNET permission is present. PDF and image operations run locally on the device.

## Build verification

```bash
./gradlew --no-daemon :app:testOpensourceDebugUnitTest :app:lintOpensourceDebug :app:assembleOpensourceDebug
```

## Permanent signed releases

The manual `Eyad PDF Signed Release` workflow expects these protected GitHub Actions secrets: `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Never commit the keystore or its passwords to the repository.

## Attribution

The project began from the Apache-2.0 `Karna14314/Pdf_Tools` codebase. See `THIRD_PARTY_ATTRIBUTIONS.md` and `LICENSE`.
