#!/bin/bash
# Compiles the climate system and the pieces it plugs into against the hand-written Paper API stubs in ./stubs,
# then runs the plain-main tests. The unrelated storm managers are replaced by tiny shims (tools/shims) because the
# sandbox stubs do not model enough of Bukkit to compile them. This is NOT a real Maven/Paper build: run
# `mvn clean package` for that. (src/test is skipped by Maven on purpose, see maven.test.skip in the pom.)
set -e
cd "$(dirname "$0")/.."
B="$(mktemp -d)"
mkdir -p "$B/stubs" "$B/main" "$B/test"
M=src/main/java/com/nexusuniverse/seasons
javac -encoding UTF-8 -nowarn -d "$B/stubs" $(find stubs -name "*.java")
CORE="$M/Season.java $M/KeepInventoryWindow.java $M/SeasonClock.java $M/SeasonsConfig.java $M/WeatherCycleManager.java $M/weather/WeatherHudManager.java $(find $M/climate -name '*.java')"
javac -encoding UTF-8 -Xlint:all,-this-escape -Werror -cp "$B/stubs" -d "$B/main" $CORE $(find tools/shims -name "*.java")
javac -encoding UTF-8 -Xlint:all,-this-escape -Werror -cp "$B/stubs:$B/main" -d "$B/test" $(find src/test/java -name "*.java")
status=0
python3 -I tools/check-resources.py || status=1
for t in $(cd src/test/java && find . -name "*Test.java" | sed 's#^\./##;s#\.java$##' | sort); do
  echo "== $t"
  rc=0
  out="$(java -cp "$B/stubs:$B/main:$B/test" "$(echo "$t" | tr / .)" 2>&1)" || rc=$?
  echo "$out" | grep -E "FAIL|checks|Exception|Error|at " | head -30 || true
  if [ "$rc" -ne 0 ]; then status=1; fi
done
rm -rf "$B"
exit $status
