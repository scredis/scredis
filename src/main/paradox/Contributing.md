## Contributing

### Running the benchmarks

- Uncomment [scredis.ClientBenchmark](https://github.com/scredis/scredis/blob/master/src/test/scala/scredis/ClientBenchmark.scala)
- Run `redis-server` with default configuration (port = 6379, no password)
- Run `test-only *ClientBenchmark*` via sbt
- It can take quite some time to complete, usually about 15 minutes

> Note: it would be even better to run the benchmarks against a redis server hosted on a separate machine in the same LAN. That way redis-server's CPU usage would not impact scredis. 
