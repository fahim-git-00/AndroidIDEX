#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════════════
#  AIDEX — Termux toolchain bootstrapper (JDK 21 era)
#  Downloads latest Kotlin / ECJ / R8 / AAPT2 / android.jar into ./tools
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$PROJECT_ROOT/tools"
LIBS="$PROJECT_ROOT/app/libs"
mkdir -p "$TOOLS" "$LIBS"

# ── Version pins (latest working, Oct 2024+) ──────────────────────────
KOTLIN_VERSION="2.0.21"                        # Kotlin 2.x — K2 compiler
KOTLINX_COROUTINES="1.9.0"
ECJ_VERSION="3.39.0"                           # Java 21 compatible
R8_VERSION="8.7.18"                            # Latest R8 (from Google Maven)
AAPT2_VERSION="8.7.2-12006047"                 # AGP 8.7.x
SDK_PLATFORM="34"
BUILD_TOOLS="34.0.0"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing: $1 — run: pkg install $2"; exit 1; }; }
need curl curl
need unzip unzip

echo "▶ Kotlin compiler ${KOTLIN_VERSION}…"
curl -fL --retry 3 -o "$TOOLS/kotlin-compiler-embeddable.jar" \
  "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/${KOTLIN_VERSION}/kotlin-compiler-embeddable-${KOTLIN_VERSION}.jar"

echo "▶ Kotlin stdlib ${KOTLIN_VERSION}…"
curl -fL --retry 3 -o "$LIBS/kotlin-stdlib.jar" \
  "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/${KOTLIN_VERSION}/kotlin-stdlib-${KOTLIN_VERSION}.jar"

echo "▶ Kotlin reflect ${KOTLIN_VERSION}…"
curl -fL --retry 3 -o "$TOOLS/kotlin-reflect.jar" \
  "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-reflect/${KOTLIN_VERSION}/kotlin-reflect-${KOTLIN_VERSION}.jar"

echo "▶ kotlinx-coroutines-core ${KOTLINX_COROUTINES}…"
curl -fL --retry 3 -o "$TOOLS/kotlinx-coroutines-core.jar" \
  "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/${KOTLINX_COROUTINES}/kotlinx-coroutines-core-jvm-${KOTLINX_COROUTINES}.jar"

echo "▶ Eclipse Compiler for Java (ecj) ${ECJ_VERSION}…"
curl -fL --retry 3 -o "$TOOLS/ecj.jar" \
  "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/${ECJ_VERSION}/ecj-${ECJ_VERSION}.jar"

echo "▶ D8 / R8 ${R8_VERSION}…"
curl -fL --retry 3 -o "$TOOLS/r8.jar" \
  "https://maven.google.com/com/android/tools/r8/${R8_VERSION}/r8-${R8_VERSION}.jar"

echo "▶ AAPT2 ${AAPT2_VERSION}…"
curl -fL --retry 3 -o "$TOOLS/aapt2.jar" \
  "https://maven.google.com/com/android/tools/build/aapt2/${AAPT2_VERSION}/aapt2-${AAPT2_VERSION}-linux.jar"
( cd "$TOOLS" && unzip -o aapt2.jar aapt2 >/dev/null && chmod +x aapt2 )

echo "▶ Android command-line tools…"
curl -fL --retry 3 -o "$TOOLS/$CMDLINE_TOOLS_ZIP" \
  "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
rm -rf "$TOOLS/cmdline-tools" "$TOOLS/cmdline-tools-tmp"
mkdir -p "$TOOLS/cmdline-tools-tmp"
unzip -q -o "$TOOLS/$CMDLINE_TOOLS_ZIP" -d "$TOOLS/cmdline-tools-tmp"
mv "$TOOLS/cmdline-tools-tmp/cmdline-tools" "$TOOLS/cmdline-tools"
rm -rf "$TOOLS/cmdline-tools-tmp" "$TOOLS/$CMDLINE_TOOLS_ZIP"

# ── android.jar (fetched via proot so glibc sdkmanager runs) ─────────
if [ ! -f "$TOOLS/android.jar" ]; then
  echo "▶ android.jar — trying direct download…"
  curl -fL --retry 3 -o "$TOOLS/android.jar" \
    "https://github.com/Sable/android-platforms/raw/master/android-34/android.jar" \
    || echo "  ⚠ Direct fetch failed — run inside proot-distro ubuntu sdkmanager if needed."
fi

echo
echo "✅ Toolchain installed in $TOOLS:"
ls -lh "$TOOLS"
echo
echo "✅ Runtime libs in $LIBS:"
ls -lh "$LIBS"
