#!/bin/bash

sbt paradox
cp -r ./target/paradox/site/main/* docs/
