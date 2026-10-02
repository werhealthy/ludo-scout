#!/usr/bin/env bash
set -euo pipefail
# Runs real SQLite/WebView tests in an SDK emulator; no third-party test library.
export ANDROID_USER_HOME="$RUNNER_TEMP/ludo-browser-android"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME"
sdkmanager --install 'system-images;android-35;google_apis;x86_64'
printf 'no\n' | avdmanager create avd -n ludo-browser-tests -k 'system-images;android-35;google_apis;x86_64' -p "$ANDROID_AVD_HOME/ludo-browser-tests.avd" --force
test -f "$ANDROID_AVD_HOME/ludo-browser-tests.ini"
emulator -list-avds
mkdir -p "$RUNNER_TEMP/ludo-browser-emulator"
emulator -avd ludo-browser-tests -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$RUNNER_TEMP/ludo-browser-emulator/emulator.log" 2>&1 &
emulator_pid=$!
trap 'adb logcat -d -b crash || true; tail -80 "$RUNNER_TEMP/ludo-browser-emulator/emulator.log"; adb emu kill || true' EXIT
sleep 5
if ! kill -0 "$emulator_pid" 2>/dev/null; then
  echo 'Emulator process exited before ADB became available'
  exit 1
fi
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
