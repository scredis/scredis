# Testing setup

To run the Scredis tests, we need

* Redis 4.0.0+
* Docker installed and daemon running


From `scredis` directory:

    ./run-redis-cluster.sh

Run tests from sbt as usual.

To shut it down:

    killall redis-server

