#!/bin/bash

set -e

cd "$(dirname $0)/.."

./gradlew bootJar
echo -e '\n'

java -jar ./build/libs/ci-builders.jar "$@"
