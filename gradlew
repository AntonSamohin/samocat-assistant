#!/bin/sh
exec "$(dirname "$0")/gradle/wrapper/gradle-wrapper-launcher.sh" "$@" 2>/dev/null || \
  gradle "$@"
