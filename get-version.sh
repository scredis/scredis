#!/bin/bash

sbt "show dynver" | tail -2 | head -n 1 > version.tmp         
VER=`cut -d' ' -f2 version.tmp`
echo $VER
