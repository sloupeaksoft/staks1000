#!/usr/bin/env bash
# Автосборка: перебирает версии Gradle и RetroFuturaGradle, пока сборка не пройдёт.
set -u
CURRENT=$(curl -s https://services.gradle.org/versions/current | grep -o '"version" *: *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
echo "Последний Gradle: $CURRENT"

try_build() {
  local gv="$1" rfg="$2"
  echo ""
  echo "================ Gradle $gv + RFG $rfg ================"
  if [ ! -x "/tmp/gradle-$gv/bin/gradle" ]; then
    curl -sSfL -o "/tmp/gradle-$gv.zip" "https://services.gradle.org/distributions/gradle-$gv-bin.zip" || return 1
    unzip -q -o "/tmp/gradle-$gv.zip" -d /tmp || return 1
  fi
  rm -rf build .gradle
  "/tmp/gradle-$gv/bin/gradle" build --no-daemon --stacktrace -PrfgVersion="$rfg" || return 1
  ls build/libs/*.jar >/dev/null 2>&1 || return 1
}

for combo in "$CURRENT|+" "8.14.3|+" "8.14.3|1.+" "8.10.2|1.+" "8.5|1.+"; do
  gv="${combo%%|*}"; rfg="${combo##*|}"
  [ -z "$gv" ] && continue
  if try_build "$gv" "$rfg"; then
    echo ""
    echo "УСПЕХ: Gradle $gv + RFG $rfg"
    ls -la build/libs
    exit 0
  fi
  echo ">>> Не получилось с Gradle $gv + RFG $rfg, пробую дальше"
done
echo "Все варианты сборки упали"
exit 1
