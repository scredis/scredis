# Testing setup

To run the Scredis tests, we need

* Redis 4.0.0+
* Docker installed and daemon running


From `scredis` directory:

    # for tests using multiple clients talking to single instances not in cluster
    ./start-redis.sh
    # for tests needing redis cluster (it should stay active, not go back to shell prompt)
    # other docker container named 'redis' cannot be running or this will fail.
    ./run-redis-cluster.sh

Run tests from sbt as usual.

To shut it down:
    
    # single instances can be killed using script:
    ./stop-redis.sh
    # stopping redis cluster is just Ctrl-C in terminal where docker was started
    # or if docker is still running (docker is named 'redis')
    `docker kill redis`

