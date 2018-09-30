#!/bin/bash

# Execute:
mkdir /tmp/redis-6380
(redis-server > rds.log) &
(redis-server ./src/test/resources/redis-6380.conf > rds2.log) &
