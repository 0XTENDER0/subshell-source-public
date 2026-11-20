#!/usr/bin/env bash
# LEAK02_LEAKCANARY_IS_READ_AT_EVERY_PHONE_CHECK_V1
# Lists what LeakCanary found in a lane (or debug) app on the phone, so a leak
# is seen at the phone check before a land instead of in a silent notification.
#
#   tools/check-leaks.sh --lane dev1|dev2|debug [--serial SERIAL] [--since 'YYYY-MM-DD HH:MM']
#
# Reads databases/leaks.db through run-as into a temp dir outside the repo.
# Prints each heap analysis (local time, leak count) with its leaks and
# ends with RESULT=NO_LEAKS or RESULT=LEAKS_FOUND (exit 1).
set -euo pipefail

lane=""
serial="${ANDROID_SERIAL:-}"
since=""
while [ $# -gt 0 ]; do
    case "$1" in
        --lane) lane="$2"; shift 2 ;;
        --serial) serial="$2"; shift 2 ;;
        --since) since="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done
case "$lane" in
    dev1|dev2) package="io.github.brealorg.subshell.debug.$lane" ;;
    debug) package="io.github.brealorg.subshell.debug" ;;
    *) echo "--lane dev1|dev2|debug is required" >&2; exit 2 ;;
esac
command -v sqlite3 >/dev/null || { echo "sqlite3 is required" >&2; exit 2; }

adb_args=()
[ -n "$serial" ] && adb_args=(-s "$serial")

work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
if ! adb "${adb_args[@]}" exec-out run-as "$package" cat databases/leaks.db > "$work/leaks.db" 2>/dev/null \
    || [ ! -s "$work/leaks.db" ]; then
    echo "PACKAGE=$package"
    echo "RESULT=NO_LEAK_DATABASE"
    exit 0
fi

since_ms=0
if [ -n "$since" ]; then
    since_ms=$(( $(date -d "$since" +%s) * 1000 ))
fi

echo "PACKAGE=$package"
sqlite3 -separator ' | ' "$work/leaks.db" "
    select datetime(h.created_at_time_millis / 1000, 'unixepoch', 'localtime'),
           'leaks=' || h.leak_count,
           coalesce(group_concat(distinct t.class_simple_name || ' via ' || l.short_description
               || case l.is_library_leak when 1 then ' (library leak)' else '' end), '-'),
           coalesce(h.exception_summary, '')
    from heap_analysis h
    left join leak_trace t on t.heap_analysis_id = h.id
    left join leak l on l.id = t.leak_id
    where h.created_at_time_millis >= $since_ms
    group by h.id
    order by h.created_at_time_millis;"
found=$(sqlite3 "$work/leaks.db" \
    "select coalesce(sum(leak_count), 0) from heap_analysis where created_at_time_millis >= $since_ms;")
if [ "$found" -gt 0 ]; then
    echo "LEAKS=$found"
    echo "RESULT=LEAKS_FOUND"
    exit 1
fi
echo "RESULT=NO_LEAKS"
