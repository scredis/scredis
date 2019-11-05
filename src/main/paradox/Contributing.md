### How to run the tests

- Install the latest version of redis-server supported by scredis  
- Start two redis servers  
  1. Original config (port = 6379, no password)  
    - Run `redis-server`  
  2. Custom config (port = 6380, password = 'foobar')  
    - Create a redis config file, i.e. `/path/custom.conf`  
    - Configure `port 6380` and `requirepass foobar`  
    - Run `redis-server /path/custom.conf`  
- Run the tests using sbt

> Note: you must not create any external connections to either of the redis servers while the tests are running as it might interfere with them.

### Running the benchmarks

- Uncomment [scredis.ClientBenchmark](https://github.com/scredis/scredis/blob/master/src/test/scala/scredis/ClientBenchmark.scala)
- Run `redis-server` with default configuration (port = 6379, no password)
- Run `test-only *ClientBenchmark*` via sbt
- It can take quite some time to complete, usually about 15 minutes

> Note: it would be even better to run the benchmarks against a redis server hosted on a separate machine in the same LAN. That way redis-server's CPU usage would not impact scredis. 
