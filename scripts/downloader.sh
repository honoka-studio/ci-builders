#!/bin/bash

set -e

if [ -z "$GITHUB_WORKSPACE" ]; then
  echo 'Must specify a project root path!'
  exit 10
fi

cd "$GITHUB_WORKSPACE"

repo_name='honoka-studio/ci-builders'

if echo "$CI_BUILDERS_VERSION" | grep -q '\.'; then
  ref_name="tags"
else
  ref_name="heads"
fi

builders_url="https://github.com/$repo_name/archive/refs/$ref_name/$CI_BUILDERS_VERSION.tar.gz"

echo "Downloading builders from $builders_url"
curl -L --fail -o builders.tar.gz $builders_url

tar -zxf builders.tar.gz
rm -f builders.tar.gz
mv $(echo $repo_name | cut -d'/' -f2)-$CI_BUILDERS_VERSION ci-builders

cd ci-builders
find . -type f -name '*.sh' | xargs chmod +x
chmod +x gradlew
