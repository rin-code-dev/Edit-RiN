#!/usr/bin/env bash
# ==============================================================================
# Edit:RiN Token-Saving Build Script (トークン節約ビルドスクリプト)
# ==============================================================================
# ビルド時の膨大なGradleログをファイルに退避し、AIコンテキスト消費を極小化（約50トークン）
# します。成功時は成果物パスとサイズのみを出力し、失敗時はエラー要約のみを表示します。
#
# 使用法:
#   ./scripts/build-apk.sh [debug|release] [--clean]
# ==============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_DIR="${PROJECT_ROOT}/android"
LOG_FILE="/tmp/edit-rin-build.log"

BUILD_TYPE="debug"
DO_CLEAN=false

for arg in "$@"; do
  case "$arg" in
    release) BUILD_TYPE="release" ;;
    debug)   BUILD_TYPE="debug" ;;
    --clean) DO_CLEAN=true ;;
  esac
done

cd "${ANDROID_DIR}"

GRADLE_TASK="assembleDebug"
OUTPUT_APK="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
if [ "${BUILD_TYPE}" = "release" ]; then
  GRADLE_TASK="assembleRelease"
  OUTPUT_APK="${ANDROID_DIR}/app/build/outputs/apk/release/app-release.apk"
fi

CLEAN_CMD=""
if [ "${DO_CLEAN}" = true ]; then
  CLEAN_CMD="clean"
fi

START_TIME=$(date +%s)

echo "==> Edit:RiN ${BUILD_TYPE} build started (logging to ${LOG_FILE})..."

set +e
./gradlew ${CLEAN_CMD} ${GRADLE_TASK} --max-workers=2 > "${LOG_FILE}" 2>&1
EXIT_CODE=$?
set -e

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
MINUTES=$((ELAPSED / 60))
SECONDS=$((ELAPSED % 60))

if [ ${EXIT_CODE} -eq 0 ] && [ -f "${OUTPUT_APK}" ]; then
  APK_SIZE=$(ls -lh "${OUTPUT_APK}" | awk '{print $5}')
  APK_SHA=$(sha256sum "${OUTPUT_APK}" | awk '{print $1}')
  
  cat << EOF
=== BUILD SUCCESSFUL ===
Type:     ${BUILD_TYPE}
Duration: ${MINUTES}m ${SECONDS}s
APK:      ${OUTPUT_APK}
Size:     ${APK_SIZE}
SHA256:   ${APK_SHA}
Log:      ${LOG_FILE}
========================
EOF
  exit 0
else
  cat << EOF
=== BUILD FAILED (exit code: ${EXIT_CODE}) ===
Type:     ${BUILD_TYPE}
Duration: ${MINUTES}m ${SECONDS}s
Log:      ${LOG_FILE}

--- Error Snippet (last 30 lines) ---
EOF
  grep -A 25 -E "(FAILURE:|\* What went wrong:|e: )" "${LOG_FILE}" 2>/dev/null || tail -n 30 "${LOG_FILE}"
  echo "========================================"
  exit ${EXIT_CODE}
fi
