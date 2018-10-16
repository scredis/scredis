#!/bin/bash

# Execute:
mkdir -p /tmp/redis-6380
(/usr/bin/redis-server > logs/rds.log) &
(/usr/bin/redis-server ./src/test/resources/redis-6380.conf > logs/rds2.log) &
