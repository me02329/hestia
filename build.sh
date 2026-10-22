#!/usr/bin/env bash
# Builds Hestia.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs JDK 17, build-tools 34.0.0 and platform android-34. See SAFE-DEPLOYMENT.md.
set -euo pipefail
umask 077
cd "$(dirname "$0")"

# --- Tools
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" /opt/homebrew/share/android-commandlinetools \
           /usr/local/share/android-commandlinetools /usr/lib/android-sdk; do
    if [ -d "$d/build-tools" ]; then SDK="$d"; break; fi
  done
fi
[ -n "$SDK" ] && [ -d "$SDK/build-tools" ] || { echo "Android SDK not found. Install it and set ANDROID_HOME (see README)."; exit 1; }
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
[ -x "$BT/aapt2" ] && [ -f "$AJ" ] || { echo 'Install build-tools;34.0.0 and platforms;android-34.'; exit 1; }
for jdk in "$HOME/.local/share/hestia/jdk17" "$HOME/.local/share/free-my-tv/jdk17"; do   # second: older setups
  if [ -z "${JAVA_HOME:-}" ] && [ -x "$jdk/bin/javac" ]; then export JAVA_HOME="$jdk"; fi
done
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
command -v "$JAVAC" >/dev/null 2>&1 || { echo "JDK not found. Install JDK 17 and set JAVA_HOME."; exit 1; }

case "$("$JAVAC" -version 2>&1)" in
  'javac 17.'*) ;;
  *) echo 'Use JDK 17 (set JAVA_HOME). The pinned D8 tool is not compatible with all newer JDKs.'; exit 1 ;;
esac

# --- Settings: config.properties -> Config.java
if [ ! -f config.properties ]; then
  cp config.example.properties config.properties
  echo "Created config.properties: set your city there (optional), then build again."
fi
mkdir -p out/gen/io/hestia/tv
python3 tools/generate_config.py config.properties out/gen/io/hestia/tv/Config.java
# Only generated class files are cleaned; source/configuration/signing files are never removed.
if [ -d out/classes ]; then find out/classes -type f -name '*.class' -delete; fi
mkdir -p out/classes

# --- Build
"$BT/aapt2" compile --dir app/res -o out/res.zip
# Fonts uncompressed: Android reads them straight from the APK.
"$BT/aapt2" link -o out/base.apk -I "$AJ" --manifest app/AndroidManifest.xml --java out/gen out/res.zip \
  --min-sdk-version 33 --target-sdk-version 33 -A app/assets -0 ttf
"$JAVAC" -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -classpath "$AJ" \
  -d out/classes $(find app/src out/gen -name '*.java')
"$BT/d8" --release --min-api 33 --lib "$AJ" --output out $(find out/classes -name '*.class')
(cd out && zip -qj base.apk classes.dex)
"$BT/zipalign" -f -p 4 out/base.apk out/aligned.apk

# --- Local signing identity: random password, private files, never checked into git.
# Signing key for io.hestia.tv: keep both files, a different key can't update the installed app.
if [ ! -f hestia-tv.keystore ] && [ ! -f hestia-tv.keystore.pass ]; then
  python3 -c 'import secrets; from pathlib import Path; Path("hestia-tv.keystore.pass").write_text(secrets.token_urlsafe(48))'
  "$KEYTOOL" -genkeypair -keystore hestia-tv.keystore -alias hestia -keyalg RSA -keysize 3072 -validity 10000 \
    -storepass:file hestia-tv.keystore.pass -keypass:file hestia-tv.keystore.pass -dname "CN=Hestia Local" >/dev/null 2>&1
fi
[ -f hestia-tv.keystore ] && [ -f hestia-tv.keystore.pass ] || { echo 'Signing key/password missing. Restore the matching private files.'; exit 1; }
chmod 600 hestia-tv.keystore hestia-tv.keystore.pass config.properties
"$BT/apksigner" sign --ks hestia-tv.keystore --ks-pass file:hestia-tv.keystore.pass --out out/signed.apk out/aligned.apk
"$BT/apksigner" verify --verbose out/signed.apk
mv out/signed.apk Hestia.apk
sha256sum Hestia.apk > Hestia.apk.sha256
echo "Built Hestia.apk. Installation and activation are separate: see SAFE-DEPLOYMENT.md."
