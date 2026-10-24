#!/usr/bin/env bash
# End-to-end test runner for the Cobo COUI frontend.
#
#   ./scripts/e2e.sh                          # all tests, read-only
#   ./scripts/e2e.sh --destructive            # also run like/follow write tests
#   ./scripts/e2e.sh --class com.cobo.app.e2e.UiSmokeTest
#   ./scripts/e2e.sh --serial 192.168.31.132:5555
#   ./scripts/e2e.sh --no-build --no-install  # reuse the APKs already on the device
#
# The device must be logged in: the suite reads the session stored by the app and
# skips session-dependent tests when it is missing.

set -euo pipefail

cd "$(dirname "$0")/.."

SERIAL="${ADB_SERIAL:-}"
DESTRUCTIVE=0
BUILD=1
INSTALL=1
SMOKE=1
CLASS_FILTER=""

APP_ID="com.cobo.app"
TEST_PACKAGE="${APP_ID}.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
REPORT_DIR="build/reports/e2e"

usage() {
    sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --destructive) DESTRUCTIVE=1 ;;
        --no-build) BUILD=0 ;;
        --no-install) INSTALL=0 ;;
        --no-smoke) SMOKE=0 ;;
        --serial) SERIAL="${2:?--serial 需要设备序列号}"; shift ;;
        --class) CLASS_FILTER="${2:?--class 需要类名}"; shift ;;
        -h|--help) usage ;;
        *) echo "未知参数：$1"; usage ;;
    esac
    shift
done

step() { printf '\n== %s ==\n' "$1"; }

ADB=(adb)
if [[ -z "$SERIAL" ]]; then
    DEVICES=()
    while IFS= read -r line; do
        DEVICES+=("$line")
    done < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    if [[ ${#DEVICES[@]} -eq 0 ]]; then
        echo "没有已连接的设备。先执行：adb connect <ip>:<port>"
        exit 2
    fi
    if [[ ${#DEVICES[@]} -eq 1 ]]; then
        SERIAL="${DEVICES[0]}"
    else
        echo "检测到多个设备，请用 --serial 指定："
        printf '  %s\n' "${DEVICES[@]}"
        exit 2
    fi
fi
ADB=(adb -s "$SERIAL")

step "检查设备"
if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
    echo "设备未连接：$SERIAL"
    exit 2
fi
echo "设备：$SERIAL"
"${ADB[@]}" shell getprop ro.product.model | tr -d '\r'

step "编译"
if [[ "$BUILD" -eq 1 ]]; then
    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
fi
[[ -f "$APP_APK" ]] || { echo "缺少 $APP_APK"; exit 2; }
[[ -f "$TEST_APK" ]] || { echo "缺少 $TEST_APK"; exit 2; }

step "安装（覆盖安装，保留登录态与草稿）"
if [[ "$INSTALL" -eq 1 ]]; then
    "${ADB[@]}" install -r --no-incremental "$APP_APK" >/dev/null
    "${ADB[@]}" install -r --no-incremental "$TEST_APK" >/dev/null
    echo "已安装 app 与 test APK"
fi

mkdir -p "$REPORT_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
INSTRUMENT_LOG="$REPORT_DIR/${STAMP}-instrument.txt"

ARGS=(-e)
if [[ -n "$CLASS_FILTER" ]]; then
    ARGS+=("class" "$CLASS_FILTER")
else
    ARGS+=("package" "com.cobo.app.e2e")
fi
if [[ "$DESTRUCTIVE" -eq 1 ]]; then
    ARGS+=(-e destructive true)
fi

step "运行 instrumented E2E"
"${ADB[@]}" shell am instrument -w "${ARGS[@]}" "${TEST_PACKAGE}/${RUNNER}" 2>&1 | tee "$INSTRUMENT_LOG"
STATUS=${PIPESTATUS[0]}

FAILED=0
if grep -q "FAILURES!!!" "$INSTRUMENT_LOG"; then
    FAILED=1
elif ! grep -q "^OK (" "$INSTRUMENT_LOG"; then
    FAILED=1
fi

if [[ "$SMOKE" -eq 1 ]]; then
    step "冷启动冒烟"
    SMOKE_LOG="$REPORT_DIR/${STAMP}-smoke.txt"
    "${ADB[@]}" logcat -c || true
    "${ADB[@]}" shell am force-stop "$APP_ID"
    "${ADB[@]}" shell am start -W -n "${APP_ID}/com.cobo.app.MainActivity" >/dev/null
    sleep 6
    DUMP="$("${ADB[@]}" exec-out uiautomator dump /dev/tty 2>/dev/null || true)"
    {
        echo "$DUMP" | grep -o 'text="[^"]*"' | head -40
        echo "--- logcat ---"
        "${ADB[@]}" logcat -d -t 600 | grep -E "FATAL EXCEPTION|AndroidRuntime" -A 8 || true
    } > "$SMOKE_LOG"

    if echo "$DUMP" | grep -q '推荐' && echo "$DUMP" | grep -q '关注'; then
        echo "冷启动成功，信息流可见（${SMOKE_LOG}）"
    else
        echo "冷启动失败：没有看到推荐/关注（${SMOKE_LOG}）"
        FAILED=1
    fi
fi

step "结果"
if [[ "$FAILED" -eq 0 && "$STATUS" -eq 0 ]]; then
    echo "通过。报告：$INSTRUMENT_LOG"
    exit 0
fi
echo "失败。报告：$INSTRUMENT_LOG"
exit 1
