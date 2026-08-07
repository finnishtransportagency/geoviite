#!/usr/bin/env bash
# Runs ktfmt on the .kt files that differ from HEAD in the working tree.
#
# Usage:
#   ktfmt.sh                             format changed (tracked + untracked) .kt files
#   ktfmt.sh --all                       format every .kt file in the repo instead
#   ktfmt.sh --dry-run                   only report which files would change; exit 1 if any would
#   ktfmt.sh [--dry-run] F.kt G.kt ...   format the given files instead
#
# On the first run this downloads the pinned ktfmt jar from Maven Central (~70 MB) and compiles a small
# runner against it. Everything lands in a cache directory outside the repo (GEOVIITE_KTFMT_CACHE, by
# default under ~/.cache/geoviite).
set -euo pipefail

ktfmt_version="0.64"
ktfmt_sha256="5b3d5286fd2defcc7dc8e28c21ddf156cc6b2d8682bdcd929ce4333e7a6201f2"
ktfmt_url="https://repo1.maven.org/maven2/com/facebook/ktfmt/${ktfmt_version}/ktfmt-${ktfmt_version}-with-dependencies.jar"

repo_root="$(git rev-parse --show-toplevel)"
script_dir="$repo_root/infra/script"
self="$script_dir/ktfmt.sh"
runner_src="$script_dir/KtfmtRunner.java"

# Options are parsed before the setup below, so that a typo fails immediately rather than after a download.
usage() { sed -n '2,/^set -euo/p' "$self" | sed -e 's/^# \{0,1\}//' -e '$d'; }

dry_run=()
format_all=false
while (($# > 0)); do
    case "$1" in
        -h | --help)
            usage
            exit 0
            ;;
        -n | --dry-run)
            dry_run=(--dry-run)
            shift
            ;;
        --all)
            format_all=true
            shift
            ;;
        --)
            shift
            break
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
        *) break ;;
    esac
done

if $format_all && (($# > 0)); then
    echo "--all cannot be combined with explicit files." >&2
    exit 2
fi

# Under Git Bash or Cygwin the JVM is a native Windows program, so anything handed to it needs a Windows path
# and a ";" classpath separator; the shell's own /c/... paths mean nothing to it. "cygpath -m" gives
# "C:/dir/file" rather than "C:\dir\file" -- the JVM accepts either, but only the forward-slash form is safe
# in the @argument file built at the bottom, where a backslash is an escape character.
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
        windows=true
        cp_sep=";"
        native() { cygpath -m -- "$@"; }
        ;;
    *)
        windows=false
        cp_sep=":"
        native() { printf '%s\n' "$@"; }
        ;;
esac

cache_dir="${GEOVIITE_KTFMT_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/geoviite/ktfmt}"

java_bin="java"
javac_bin="javac"
jar_bin="jar"
java_home="${JAVA_HOME:-}"
# On Windows JAVA_HOME is a native path ("C:\Program Files\...") and the tools under it are .exe files.
# Without both allowances the test below just fails, silently falling back to whatever "java" is on PATH.
if $windows && [[ -n "$java_home" ]]; then
    java_home="$(cygpath -u -- "$java_home")"
fi
if [[ -n "$java_home" ]] && [[ -x "$java_home/bin/java" ]]; then
    java_bin="$java_home/bin/java"
    javac_bin="$java_home/bin/javac"
    jar_bin="$java_home/bin/jar"
elif $windows && [[ -n "$java_home" ]] && [[ -x "$java_home/bin/java.exe" ]]; then
    java_bin="$java_home/bin/java.exe"
    javac_bin="$java_home/bin/javac.exe"
    jar_bin="$java_home/bin/jar.exe"
fi

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | cut -d' ' -f1
    else
        echo "Neither sha256sum nor shasum found; cannot verify the ktfmt download." >&2
        exit 1
    fi
}

[[ -f "$runner_src" ]] || { echo "Runner source not found at $runner_src" >&2; exit 1; }

# Cache artifacts are named after the ktfmt version and the runner source hash, so a version bump or an edit
# to KtfmtRunner.java simply produces new files rather than a stale-looking old one. (Superseded files are
# left behind; "rm -r" the cache directory if they ever bother you.)
src_hash="$(sha256_of "$runner_src" | cut -c1-12)"
jar="$cache_dir/ktfmt-${ktfmt_version}-with-dependencies.jar"
runner_jar="$cache_dir/runner-${ktfmt_version}-${src_hash}.jar"
cds="$cache_dir/runner-${ktfmt_version}-${src_hash}.jsa"
class_path="$(native "$runner_jar")$cp_sep$(native "$jar")"

# Scratch space for the setup steps and for the argument file below, cleaned up on any exit. Everything is
# built under here and moved into place with mv, which is atomic within the same directory, so concurrent
# first runs can never see a half-written jar.
tmp_dir=""
cleanup_tmp() {
    [[ -n "$tmp_dir" ]] && rm -rf "$tmp_dir"
    tmp_dir=""
}
ensure_tmp() {
    if [[ -z "$tmp_dir" ]]; then
        mkdir -p "$cache_dir"
        tmp_dir="$(mktemp -d "$cache_dir/tmp.XXXXXX")"
        trap cleanup_tmp EXIT
    fi
}

# Those moves tolerate the destination already existing: Windows refuses to rename over a file that a
# concurrent run has already opened, and "someone else got there first" is a success either way.
download_jar() {
    ensure_tmp
    echo "Downloading ktfmt $ktfmt_version from $ktfmt_url" >&2
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 -o "$tmp_dir/ktfmt.jar" "$ktfmt_url"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$tmp_dir/ktfmt.jar" "$ktfmt_url"
    else
        echo "Neither curl nor wget found; cannot download ktfmt." >&2
        exit 1
    fi
    local actual
    actual="$(sha256_of "$tmp_dir/ktfmt.jar")"
    if [[ "$actual" != "$ktfmt_sha256" ]]; then
        echo "Checksum mismatch for $ktfmt_url" >&2
        echo "  expected $ktfmt_sha256" >&2
        echo "  actual   $actual" >&2
        exit 1
    fi
    mv -f "$tmp_dir/ktfmt.jar" "$jar" || [[ -f "$jar" ]]
}

build_runner() {
    ensure_tmp
    # The runner goes in a jar (not a classes dir) because CDS archiving below refuses non-empty directories
    # on the classpath.
    "$javac_bin" -proc:none -cp "$(native "$jar")" -d "$(native "$tmp_dir/classes")" "$(native "$runner_src")"
    "$jar_bin" cf "$(native "$tmp_dir/runner.jar")" -C "$(native "$tmp_dir/classes")" .
    mv -f "$tmp_dir/runner.jar" "$runner_jar" || [[ -f "$runner_jar" ]]
}

# Nearly all of a short ktfmt run is JVM startup rather than formatting: loading the Kotlin compiler's
# classes, and C2 recompiling them at the top tier for code that only runs once. The C1-only JIT and serial
# GC used here and below skip the recompilation and heap sizing; this archive skips most of the loading.
build_cds_archive() {
    ensure_tmp
    printf 'fun main() {}\n' >"$tmp_dir/Warmup.kt"
    if "$java_bin" -XX:TieredStopAtLevel=1 -XX:+UseSerialGC \
        -XX:ArchiveClassesAtExit="$(native "$tmp_dir/ktfmt.jsa")" \
        -Xlog:cds=off -cp "$class_path" KtfmtRunner "$(native "$tmp_dir/Warmup.kt")" >/dev/null 2>&1 &&
        [[ -f "$tmp_dir/ktfmt.jsa" ]]; then
        mv -f "$tmp_dir/ktfmt.jsa" "$cds" || [[ -f "$cds" ]]
    else
        echo "Could not create a CDS archive; continuing without it (formatting will be slower)." >&2
    fi
}

[[ -f "$jar" ]] || download_jar
[[ -f "$runner_jar" ]] || build_runner
[[ -f "$cds" ]] || build_cds_archive

files=()
if (($# > 0)); then
    # Explicitly named files may be POSIX-style paths that the JVM would not understand.
    while IFS= read -r file; do
        files+=("$file")
    done < <(native "$@")
else
    # git reports paths relative to a root it prints in native form, so these need no further conversion.
    repo_root_native="$(native "$repo_root")"
    while IFS= read -r -d '' file; do
        [[ -f "$repo_root/$file" ]] && files+=("$repo_root_native/$file")
    done < <(
        {
            if $format_all; then
                git -C "$repo_root" ls-files -z -- '*.kt'
            else
                git -C "$repo_root" diff --name-only -z --diff-filter=ACMR HEAD -- '*.kt'
            fi
            # Untracked-but-not-ignored files count as changed, and are part of "all" too.
            git -C "$repo_root" ls-files --others --exclude-standard -z -- '*.kt'
        } | sort -zu
    )
fi

if ((${#files[@]} == 0)); then
    $format_all && echo "No .kt files found." || echo "No changed .kt files."
    exit 0
fi

jvm_flags=(-XX:TieredStopAtLevel=1 -XX:+UseSerialGC)
# A stale or otherwise unusable archive isn't fatal: the JVM ignores it and runs without it.
[[ -f "$cds" ]] && jvm_flags+=(-XX:SharedArchiveFile="$(native "$cds")" -Xlog:cds=off)

# The file list goes into a java @argument file rather than onto the command line, which Windows caps at 32k
# characters -- less than half of what "--all" needs here. @-expansion stops once the main class is found, so
# KtfmtRunner has to be named inside the file too. The quotes are there for paths containing spaces.
ensure_tmp
arg_file="$tmp_dir/ktfmt_args.txt"
{
    echo "KtfmtRunner"
    if ((${#dry_run[@]} > 0)); then echo "--dry-run"; fi
    printf '%s\n' "${files[@]}" | sed 's/.*/"&"/'
} >"$arg_file"

# Deliberately not exec, so that the trap installed above still gets to delete the argument file.
status=0
"$java_bin" "${jvm_flags[@]}" -cp "$class_path" "@$(native "$arg_file")" || status=$?
exit "$status"
