#!/bin/bash

# Execute:
mkdir -p /tmp/redis-6380
(redis-server > logs/rds.log) &
(redis-server ./src/test/resources/redis-6380.conf > logs/rds2.log) &
