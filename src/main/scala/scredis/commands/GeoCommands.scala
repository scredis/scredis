package scredis.commands

import scredis.io.NonBlockingConnection
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry
import scredis.protocol.requests.GeoRequests.{GeoAdd, GeoHash, GeoPos}

import scala.concurrent.Future


trait GeoCommands { self: NonBlockingConnection =>

  /* TODO: NEEDS HEAVY COMMENTING */
  def geoAdd(key: String, geoAddEntries: GeoAddEntry*): Future[Long] = send(GeoAdd(key, geoAddEntries.toList))

  /* TODO: NEEDS HEAVY COMMENTING */
  def geoHash(key: String, locations: String*): Future[List[String]] = send(GeoHash(key, locations.toList))

  /* TODO: NEEDS HEAVY COMMENTING */
  def geoPos(key: String, members: String*): Future[List[(Double, Double)]] = send(GeoPos(key, members.toList))
}

