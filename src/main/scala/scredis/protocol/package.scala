package scredis

package object protocol {
  type Decoder[X] = PartialFunction[Response, X]
  type RedisStreamElement = (String, Map[String, String])
}