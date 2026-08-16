#!/bin/bash

set -e

cd "$(dirname $0)/.."
SCRIPTS_PATH=./build/install/ci-builders/bin

if [ ! -e "$SCRIPTS_PATH" ] || [ "$1" != "--rebuild" ]; then
  ./gradlew installDist
fi
echo -e '\n'

cd $SCRIPTS_PATH
chmod +x *
./ci-builders "$@"
