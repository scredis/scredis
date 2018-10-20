package scredis.commands

import scredis.io.NonBlockingConnection
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry
import scredis.protocol.requests.GeoDistUnit.GeoDistUnit
import scredis.protocol.requests.GeoRequests.{GeoAdd, GeoDist, GeoHash, GeoPos}

import scala.concurrent.Future


trait GeoCommands { self: NonBlockingConnection =>

  /* TODO: NEEDS HEAVY COMMENTING */
  def geoAdd(key: String, geoAddEntries: GeoAddEntry*): Future[Long] = send(GeoAdd(key, geoAddEntries.toList))

  /* TODO: NEEDS HEAVY COMMENTING */
  def geoHash(key: String, locations: String*): Future[List[Option[String]]] = send(GeoHash(key, locations.toList))

  /* TODO: NEEDS HEAVY COMMENTING */
  def geoPos(key: String, members: String*): Future[List[Option[(Double, Double)]]] = send(GeoPos(key, members.toList))

  def geoDist(key: String, member1: String, member2: String, unit: Option[GeoDistUnit] = None): Future[Option[Double]] = send(GeoDist(key, member1, member2, unit))
}

