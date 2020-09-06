#!/bin/bash

docker run --rm -it --name redis -p 7000:7000 -p 7001:7001 -p 7002:7002 -p 7003:7003 -p 7004:7004 -p 7005:7005 grokzen/redis-cluster:6.0.1
