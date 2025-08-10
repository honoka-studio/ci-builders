#!/bin/bash

set -e

cd "$(dirname $0)/.."
JAR_PATH=./build/libs/ci-builders.jar

if [ ! -f "$JAR_PATH" ]; then
  ./gradlew bootJar
fi
echo -e '\n'

java -jar "$JAR_PATH" "$@"
