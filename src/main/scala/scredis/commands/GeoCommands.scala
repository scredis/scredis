package scredis.commands

import scredis.io.NonBlockingConnection
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry
import scredis.protocol.requests.GeoDistUnit.GeoDistUnit
import scredis.protocol.requests.GeoRequests._
import scredis.protocol.requests.GeoSortOrder.GeoSortOrder
import scredis.protocol.requests.RadiusResult

import scala.concurrent.Future


trait GeoCommands { self: NonBlockingConnection =>

  /**
    * Adds the specified geospatial items (longitude, latitude, name) to the specified key.
    * Data is stored into the key as a sorted set, in a way that makes it possible to later retrieve items
    * using a query by radius with the GEORADIUS or GEORADIUSBYMEMBER commands.
    *
    * The command takes arguments in the standard format x,y so the longitude must be specified before the latitude.
    * There are limits to the coordinates that can be indexed: areas very near to the poles are not indexable.
    * The exact limits, as specified by EPSG:900913 / EPSG:3785 / OSGEO:41001 are the following:
    *
    * {{{
    *   Valid longitudes are from -180 to 180 degrees.
    *   Valid latitudes are from -85.05112878 to 85.05112878 degrees.
    * }}}
    *
    * The command will report an error when the user attempts to index coordinates outside the specified ranges.
    *
    * @example client.geoAdd("GeoSet", GeoAddEntry(1.11, 2.22, "Paris"), GeoAddEntry(5.55, 6.66, "Berlin"))
    *
    * @param key Name of a sortedSet to which points will be added
    * @param geoAddEntries points description consisting of 3 elements (longitude, latitude, name)
    * @return The number of elements added to the sorted set,
    *         not including elements already existing for which the score was updated.
    * @throws scredis.exceptions.RedisErrorResponseException if coordinates are out of expected range.
    * @since 3.2.0
    * @see [[https://redis.io/commands/geoadd]]
    */
  def geoAdd(key: String, geoAddEntries: GeoAddEntry*): Future[Long] = send(GeoAdd(key, geoAddEntries.toList))


  /**
    * Return valid Geohash strings representing the position of one or more elements in a sorted set value
    * representing a geospatial index (where elements were added using GEOADD).
    *
    * The command returns 11 characters Geohash strings,
    * so no precision is loss compared to the Redis internal 52 bit representation.
    * The returned Geohashes have the following properties:
    *
    * They can be shortened removing characters from the right.
    * It will lose precision but will still point to the same area.
    * It is possible to use them in geohash.org URLs such as http://geohash.org/<geohash-string>.
    * Strings with a similar prefix are nearby, but the contrary is not true,
    * it is possible that strings with different prefixes are nearby too.
    *
    * {{{
    * client.geoAdd("GeoSet", GeoAddEntry(1.11, 2.22, "Warsaw"))
    * client.geoHash("GeoSet", "Warsaw", "unknownloc")
    * }}}
    *
    * @param key Name of a sortedSet from which members will be used
    * @param members sequence of name entries present in set.
    * @return List with calculated geohash corresponding to each present member
    *         and None for not found members.
    * @since 3.2.0
    * @see [[https://redis.io/commands/geohash]]
    */
  def geoHash(key: String, members: String*): Future[List[Option[String]]] = send(GeoHash(key, members.toList))


  /**
    * Return the positions (longitude,latitude) of all the specified members of the geospatial index
    * represented by the sorted set at key.
    *
    * Given a sorted set representing a geospatial index, populated using the GEOADD command,
    * it is often useful to obtain back the coordinates of specified members.
    * When the geospatial index is populated via GEOADD the coordinates are converted into a 52 bit geohash,
    * so the coordinates returned may not be exactly the ones used in order to add the elements, but small errors may be introduced.
    *
    * {{{
    * client.geoAdd("GeoSet", GeoAddEntry(10, 10, "Paris"))
    * client.geoPos("GeoSet", "Paris", "Unknown")
    * }}}
    *
    * @param key Name of a sortedSet from which members will be used
    * @param members sequence of name entries present in set.
    * @return list where each entry represents coordinates (longitude, latitude)
    *         of corresponding member name or None if member is not found.
    * @since 3.2.0
    * @see [[https://redis.io/commands/geopos]]
    */
  def geoPos(key: String, members: String*): Future[List[Option[(Double, Double)]]] = send(GeoPos(key, members.toList))


  /**
    * Return the distance between two members in the geospatial index represented by the sorted set.
    *
    * Given a sorted set representing a geospatial index, populated using the GEOADD command,
    * the command returns the distance between the two specified members in the specified unit.
    *
    * Units: [[scredis.protocol.requests.GeoDistUnit]]
    *
    * The distance is computed assuming that the Earth is a perfect sphere, so errors up to 0.5% are possible in edge cases.
    *
    * {{{
    * client.geoAdd("GeoSet", GeoAddEntry(12.123, -3.321, "Chicago"), GeoAddEntry(-11.4, 3.99, "Prague"))
    * client.geoDist("GeoSet", "Chicago", "Prague")
    * }}}
    *
    * @param key Name of a sortedSet from which members will be used
    * @param member1 name present in 'key' set
    * @param member2 name present in 'key' set
    * @param unit Measure unit used in response [[scredis.protocol.requests.GeoDistUnit]]
    * @return distance between two members in a set in specified measure unit
    *         or None if any of members not found in a set.
    * @since 3.2.0
    * @see [[https://redis.io/commands/geodist]]
    */
  def geoDist(key: String, member1: String, member2: String, unit: Option[GeoDistUnit] = None): Future[Option[Double]] =
    send(GeoDist(key, member1, member2, unit))


  /**
    * Return the members of a sorted set populated with geospatial information using GEOADD,
    * which are within the borders of the area specified
    * with the center location and the maximum distance from the center (the radius).
    *
    * The common use case for this command is to retrieve geospatial items near a specified point
    * not farther than a given amount of meters (or other units).
    * This allows, for example, to suggest mobile users of an application nearby places.
    *
    * {{{
    * client.geoAdd("GeoSet", GeoAddEntry(1.111, 1.5, "Tokyo"), GeoAddEntry(1.222, 1.6, "Kyoto"), GeoAddEntry(1.5, 1.333, "Nagasaki"))
    * client.geoRadius("GeoSet", 1, 1, 3500, GeoDistUnit.km)
    * }}}
    *
    * @param key Name of a sortedSet in which points will be searched.
    * @param longitude First coordinate of a point.
    * @param latitude Second coordinate of a point.
    * @param radius Distance between given point and an edge of a circle. Specified in 'radiusUnit' units.
    * @param radiusUnit Units of measure in which radius is expressed [[scredis.protocol.requests.GeoDistUnit]]
    * @return List of member names from 'key' set.
    * @since 3.2.0
    * @see [[https://redis.io/commands/georadius]]
    */
  def geoRadius(key: String, longitude: Double, latitude: Double, radius: Double, radiusUnit: GeoDistUnit): Future[List[String]] =
    send(GeoRadius(key, longitude, latitude, radius, radiusUnit))

  def geoRadiusComplex(key: String, longitude: Double, latitude: Double, radius: Double, radiusUnit: GeoDistUnit,
                       sort: Option[GeoSortOrder]=None, count: Option[Int]=None): Future[List[RadiusResult]] =
    send(GeoRadiusComplex(key, longitude, latitude, radius, radiusUnit, sort, count))


  /**
    * This command is exactly like GEORADIUS with the sole difference that instead of taking,
    * as the center of the area to query, a longitude and latitude value,
    * it takes the name of a member already existing inside the geospatial index represented by the sorted set.
    *
    * The position of the specified member is used as the center of the query.
    *
    * @note [[scredis.commands.GeoCommands.geoRadius]]
    *
    * {{{
    * client.geoAdd("GeoSet", GeoAddEntry(1.111, 1.5, "Tokyo"), GeoAddEntry(1.222, 1.6, "Kyoto"), GeoAddEntry(1.5, 1.333, "Nagasaki"))
    * client.geoRadiusByMember("GeoSet", "Tokyo", 60000, GeoDistUnit.km)
    * }}}
    *
    * @param key Name of a sortedSet in which points will be searched.
    * @param member Name present in 'key' set
    * @param radius Radius in which returned elements are found. Specified in 'radiusUnit' units.
    * @param radiusUnit Units of measure in which radius is expressed [[scredis.protocol.requests.GeoDistUnit]]
    * @return List of member names from 'key' set.
    * @since 3.2.0
    * @see [[https://redis.io/commands/georadiusbymember]]
    * @throws scredis.exceptions.RedisErrorResponseException if member is not found in set.
    */
  def geoRadiusByMember(key: String, member: String, radius: Double, radiusUnit: GeoDistUnit): Future[List[String]] =
    send(GeoRadiusByMember(key, member, radius, radiusUnit))

  def geoRadiusByMemberComplex(key: String, member: String, radius: Double, radiusUnit: GeoDistUnit,
                               sort: Option[GeoSortOrder]=None, count: Option[Int]=None): Future[List[RadiusResult]] =
    send(GeoRadiusByMemberComplex(key, member, radius, radiusUnit, sort, count))
}

