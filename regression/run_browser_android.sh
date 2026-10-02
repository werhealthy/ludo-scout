#!/usr/bin/env bash
set -euo pipefail
# Runs real SQLite/WebView tests in an SDK emulator; no third-party test library.
sdkmanager --install 'system-images;android-35;google_apis;x86_64'
printf 'no\n' | avdmanager create avd -n ludo-browser-tests -k 'system-images;android-35;google_apis;x86_64' --force
mkdir -p "$RUNNER_TEMP/ludo-browser-emulator"
emulator -avd ludo-browser-tests -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$RUNNER_TEMP/ludo-browser-emulator/emulator.log" 2>&1 &
trap 'adb emu kill || true' EXIT
timeout 180 adb wait-for-device
for attempt in $(seq 1 120); do
  if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then break; fi
  sleep 2
done
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
chmod +x gradlew
./gradlew --no-daemon :app:connectedDebugAndroidTest
