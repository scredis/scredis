#!/bin/bash

set -e

if [ "$#" -ne 1 ]; then
    echo "You should provide single parameter with a new version"
    exit 1
fi

VER=$1

git add README.md
git commit -m "Release version v$VER"

git tag -a "v$VER" -m "Release version $VER"

./gen-doc.sh
git add docs/*
git commit --amend --no-edit

git tag -fa "v$VER" -m "Release version $VER"

git push origin v$VER
git push
