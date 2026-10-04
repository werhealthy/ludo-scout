# Build and distribution priority

Use Windows first when the user's computer is available. Current checkout:
`D:\Users\defaultuser0\Documents\GitHub\ludo-scout`.

Use CircleCI Free when Windows is unavailable or cannot complete the build.
GitHub Actions is the last fallback, only after checking free quota; a new month
does not automatically make it primary. Never increase spending budgets or enable
paid plans without explicit permission.

Windows entrypoint: `scripts/build-windows.ps1`.
Requires Android Studio SDK platform 35 / build-tools 35.0.0 and the original
debug signing key. BGG_TOKEN remains in user Gradle properties or local environment.
Firebase distribution is optional and requires the existing service-account JSON
outside the repository, GOOGLE_APPLICATION_CREDENTIALS, FIREBASE_APP_ID,
FIREBASE_TESTERS and an installed Firebase CLI. Never commit credentials.

VersionSequence maps to versionCode = 1000000 + VersionSequence. Before any
distribution on ANY provider, choose a sequence higher than the latest distributed
APK. CircleCI currently uses 2000 + CIRCLE_BUILD_NUM; GitHub uses its run number.
After Windows overtakes these, adjust the fallback sequence before distributing;
do not assume independent provider counters are globally monotonic.

Windows script runs Gradle unit tests and builds, verifies signing before and
after build, and optionally installs with adb install -r or sends to Firebase.
It does not yet execute the 103 Python/Node/Java regression commands in CircleCI:
those still require the cloud pipeline or a separately provisioned local runtime.
No physical Windows validation or successful CircleCI distribution is claimed.
