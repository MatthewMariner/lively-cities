#!/usr/bin/env bash
#
# Build in WSL, run the dev client natively on Windows.
#
# Why this exists: WSLg renders the Swing client slowly, and the Jagex Launcher
# writes credentials.properties into the *Windows* home — so a Windows-side run
# is both faster and needs no token copying.
#
# Why no Windows JDK is required: compiling happens here in WSL (JDK 21,
# targeting Java 11 bytecode), and RuneLite ships its own Temurin 11 JRE, which
# is all that is needed to *run* a self-contained jar.
#
# Usage:
#   ./run-windows.sh              # isolated home (default): deterministic character, your config untouched
#   ./run-windows.sh --real-home  # use your real ~/.runelite: your profiles and hub plugins,
#                                 #   but RuneLite picks the character from its own saved session,
#                                 #   and it WILL write to the profile it logs into
#   ./run-windows.sh --no-build   # skip the gradle step, relaunch the existing jar
#   ./run-windows.sh --timings    # frame-time stopwatch on; play a few minutes, read the report
#   ./run-windows.sh --audit      # walk every cache id the dataset needs, write a report, and say so
#
# The last two are the same capabilities as `./gradlew runWithTimings` and
# `./gradlew auditCacheIds` — and on this machine they are the ones to use.
# Those Gradle tasks run a *Linux-side* client whose user.home is ~, so they read
# ~/.runelite/credentials.properties: a different file from the one the Jagex
# Launcher writes at C:\Users\<you>\.runelite\. Relaunching a character in the
# launcher cannot change what they log in as, because nothing carries a Windows
# credentials file across the WSL boundary. Observed 2026-08-24: the Gradle task
# logged in as a stale account while the launcher had a different character
# selected, and RuneLite then refreshed the stale token on startup so the file
# looked freshly written. This script copies the Windows credentials in on every
# run, which is why it gets the character you actually picked.
set -euo pipefail

# Asked of Windows rather than hardcoded: this file is public, and a default here is
# somebody's account name shipped to strangers for no benefit. Override with
# WIN_USER=... if the detection is wrong, e.g. a Microsoft account whose profile
# directory does not match the display name.
WIN_USER="${WIN_USER:-$(cmd.exe /c 'echo %USERNAME%' 2>/dev/null | tr -d '\r\n')}"
if [ -z "$WIN_USER" ] || [ ! -d "/mnt/c/Users/${WIN_USER}" ]; then
	echo "could not work out your Windows username (got '${WIN_USER:-}')." >&2
	echo "Run:  WIN_USER=yourname $0 $*" >&2
	exit 1
fi
WIN_HOME_UNIX="/mnt/c/Users/${WIN_USER}"
JRE="${WIN_HOME_UNIX}/AppData/Local/RuneLite/jre/bin/java.exe"
STAGE_UNIX="${WIN_HOME_UNIX}/osrs-dev"
STAGE_WIN="C:\\Users\\${WIN_USER}\\osrs-dev"
JAR_NAME="lively-cities-all.jar"

# Isolated by default. With your real home present, RuneLite logs in as whoever
# its own saved session says — not as whoever credentials.properties names — and
# it saves a config patch into that profile on exit. Neither is wanted for a
# throwaway test client, so the safe mode is the default and the other is opt-in.
isolated=1
build=1
timings=0
audit=0
for arg in "$@"; do
	case "$arg" in
		--isolated) isolated=1 ;;
		--real-home) isolated=0 ;;
		--no-build) build=0 ;;
		--timings) timings=1 ;;
		--audit) audit=1 ;;
		*) echo "unknown option: $arg" >&2; exit 2 ;;
	esac
done

[ -x "$JRE" ] || { echo "RuneLite's bundled JRE not found at $JRE" >&2; exit 1; }

cd "$(dirname "$0")"

if [ "$build" = 1 ]; then
	echo "==> building (WSL, JDK $(javac -version 2>&1 | awk '{print $2}'))"
	./gradlew shadowJar -q
fi

src=$(ls -t build/libs/*all.jar | head -1)
mkdir -p "$STAGE_UNIX"
cp "$src" "${STAGE_UNIX}/${JAR_NAME}"
echo "==> staged $(du -h "${STAGE_UNIX}/${JAR_NAME}" | cut -f1) -> ${STAGE_WIN}\\${JAR_NAME}"

# Credentials sanity: the Jagex Launcher rewrites this file for whichever
# character it last launched, so say who we are about to log in as. A stale or
# absent file is why a dev client sits on the login screen.
creds="${WIN_HOME_UNIX}/.runelite/credentials.properties"
if [ -f "$creds" ]; then
	echo "==> credentials: $(grep -oE '^JX_DISPLAY_NAME=.*' "$creds" | cut -d= -f2-) (written $(date -r "$creds" '+%H:%M:%S'))"
else
	echo "==> WARNING: no credentials.properties — the client will stop at the login screen."
	echo "    Launch RuneLite once via the Jagex Launcher with the character you want."
fi

# cmd.exe cannot hold a WSL path as its working directory ("UNC paths are not
# supported"), so drive it from a real Windows directory.
cd "$STAGE_UNIX"

jvm_args=(-ea)
if [ "$isolated" = 1 ]; then
	mkdir -p "${STAGE_UNIX}/home"
	# Redirecting user.home moves .runelite wholesale: fresh cache, no profiles,
	# no hub plugins, and your real config is untouched. Credentials must be
	# copied in for auto-login to work.
	if [ -f "$creds" ]; then
		mkdir -p "${STAGE_UNIX}/home/.runelite"
		cp "$creds" "${STAGE_UNIX}/home/.runelite/credentials.properties"
	fi
	# Seed the game cache from the real home. Without this the client starts with
	# an empty cache and client.loadModelData() returns null for everything —
	# which renders as citizens missing limbs or not appearing at all. Observed
	# 2026-08-23: 84 distinct model ids failed against a 280 KB cold cache while
	# the real one held 26 MB. Copy once; afterwards the isolated cache warms
	# itself like any other.
	for d in cache jagexcache; do
		real="${WIN_HOME_UNIX}/.runelite/${d}"
		[ -d "$real" ] || real="${WIN_HOME_UNIX}/${d}"
		iso="${STAGE_UNIX}/home/.runelite/${d}"
		[ "$d" = jagexcache ] && iso="${STAGE_UNIX}/home/${d}"
		if [ -d "$real" ] && [ "$(du -s "$real" 2>/dev/null | cut -f1)" -gt 2048 ]; then
			cur=$(du -s "$iso" 2>/dev/null | cut -f1 || echo 0)
			if [ "${cur:-0}" -lt 2048 ]; then
				echo "==> seeding $d from your real home ($(du -sh "$real" | cut -f1)) — one-off"
				mkdir -p "$(dirname "$iso")"
				# NOT `|| true`. A swallowed copy failure leaves a cold cache, and a cold
				# cache is exactly the "citizens missing limbs" symptom this seeding exists
				# to prevent — so a silent failure here reproduces the bug it is fixing.
				# Fail loudly, and verify the result rather than trusting the exit code:
				# a partial copy (interrupted run, locked file) exits 0 on some paths.
				if ! cp -r "$real" "$(dirname "$iso")/"; then
					echo "!! seeding $d FAILED — models will not load. Aborting rather than" >&2
					echo "   launching a client that renders citizens with missing parts." >&2
					echo "   Close any running client (it may hold a lock) and retry." >&2
					exit 1
				fi
				got=$(du -s "$iso" 2>/dev/null | cut -f1 || echo 0)
				want=$(du -s "$real" 2>/dev/null | cut -f1 || echo 0)
				# 90% is slack for filesystem/block-size differences across the 9p mount,
				# not for missing files.
				if [ "${got:-0}" -lt $(( want * 9 / 10 )) ]; then
					echo "!! seeded $d is only ${got}K of ${want}K — partial copy. Aborting." >&2
					echo "   Delete ${iso} and retry." >&2
					exit 1
				fi
			fi
		fi
	done
	jvm_args+=("-Duser.home=${STAGE_WIN}\\home")
	echo "==> isolated: user.home -> ${STAGE_WIN}\\home (profiles untouched, cache seeded)"
else
	echo "==> using your real C:\\Users\\${WIN_USER}\\.runelite (profiles, hub plugins, cache)"
fi

# The two dev-only capabilities. Each needs BOTH its system property and
# --developer-mode, which is passed unconditionally below — the same AND gate the
# Gradle tasks satisfy, and the reason neither can be switched on by a hub user.
# Property names are duplicated from build.gradle on purpose: this launcher does
# not go through Gradle at all. FrameTimingsTest greps this file so the two
# cannot drift apart silently.
if [ "$timings" = 1 ]; then
	jvm_args+=(-Dlivelycities.frameTimings=true)
fi
if [ "$audit" = 1 ]; then
	jvm_args+=(-Dlivelycities.validateCacheIds=true)
fi

# Where the reports land. In isolated mode user.home is redirected, so they do
# NOT appear in your real ~/.runelite — saying so here is cheaper than hunting
# for a file that was written correctly somewhere else.
if [ "$timings" = 1 ] || [ "$audit" = 1 ]; then
	if [ "$isolated" = 1 ]; then
		report_dir_win="${STAGE_WIN}\\home\\.runelite\\lively-cities"
		report_dir_unix="${STAGE_UNIX}/home/.runelite/lively-cities"
	else
		report_dir_win="C:\\Users\\${WIN_USER}\\.runelite\\lively-cities"
		report_dir_unix="${WIN_HOME_UNIX}/.runelite/lively-cities"
	fi
	[ "$timings" = 1 ] && echo "==> frame timings ON — play a few minutes; a report lands every 3 min"
	[ "$audit" = 1 ] && echo "==> cache id audit ON — runs once at startup, no play needed"
	echo "==> reports: ${report_dir_win}"
	echo "    (from WSL: ${report_dir_unix})"
fi

echo "==> launching"
exec "$JRE" "${jvm_args[@]}" -jar "${STAGE_WIN}\\${JAR_NAME}" --developer-mode --debug
