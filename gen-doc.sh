#!/bin/bash

sbt paradox
cp -r ./target/paradox/site/main/* docs/

sbt doc
cp -r ./target/scala-2.13/api/ docs/
