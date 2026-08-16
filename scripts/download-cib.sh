#!/bin/bash

set -e

# region 参数校验
if [ -z "$GITHUB_WORKSPACE" ]; then
  echo 'Must specify GITHUB_WORKSPACE!'
  exit 10
fi

if [ -z "$CIB_VERSION" ]; then
  echo 'Must specify CIB_VERSION!'
  exit 10
fi
# endregion

cd "$GITHUB_WORKSPACE"

repo_name='honoka-studio/ci-builders'

if echo "$CIB_VERSION" | grep -q '\.'; then
  ref_name="tags"
else
  ref_name="heads"
fi

cib_url="https://github.com/$repo_name/archive/refs/$ref_name/$CIB_VERSION.tar.gz"
cib_tar_name="ci-builders"
cib_tar_file="$cib_tar_name.tar.gz"

echo "Downloading CI builders from $cib_url"
curl -L --fail -o $cib_tar_file $cib_url

tar -zxf $cib_tar_file
rm -f $cib_tar_file
mv $(echo $repo_name | cut -d'/' -f2)-$CIB_VERSION $cib_tar_name

cd $cib_tar_name
find . -type f -name '*.sh' | xargs chmod +x
chmod +x gradlew
