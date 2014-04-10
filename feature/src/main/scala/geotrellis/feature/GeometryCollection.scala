/*
 * Copyright (c) 2014 Azavea.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.feature

import GeomFactory._

import com.vividsolutions.jts.{geom => jts}
import scala.collection.mutable

class GeometryCollection(val points: Set[Point],
                         val lines: Set[Line],
                         val polygons: Set[Polygon],
                         val multiPoints: Set[MultiPoint],
                         val multiLines: Set[MultiLine],
                         val multiPolygons: Set[MultiPolygon],
                         val geometryCollections: Set[GeometryCollection],
                         val jtsGeom: jts.GeometryCollection) extends Geometry {

  lazy val area: Double =
    jtsGeom.getArea

  override def equals(that: Any): Boolean = {
    that match {
      case other: GeometryCollection => jtsGeom == other.jtsGeom
      case _ => false
    }
  }

  override def hashCode(): Int  =
    jtsGeom.hashCode()
}

object GeometryCollection {
  implicit def jtsToGeometryCollection(gc: jts.GeometryCollection): GeometryCollection =
    apply(gc)

  def apply(points: Set[Point] = Set(), lines: Set[Line] = Set(), polygons: Set[Polygon] = Set(),
             multiPoints: Set[MultiPoint] = Set(),
             multiLines: Set[MultiLine] = Set(),
             multiPolygons: Set[MultiPolygon] = Set(),
             geometryCollections: Set[GeometryCollection] = Set()
           ): GeometryCollection =
  {
    val jtsGeom = factory.createGeometryCollection(
      (points ++ lines ++ polygons ++ multiPoints ++ multiLines ++ multiPolygons ++ geometryCollections)
        .map(_.jtsGeom).toArray
    )
    new GeometryCollection(points, lines, polygons, multiPoints, multiLines, multiPolygons, geometryCollections, jtsGeom)
  }

  def apply(geoms: Traversable[Geometry]): GeometryCollection = {
    val points = mutable.Set[Point]()
    val lines = mutable.Set[Line]()
    val polygons = mutable.Set[Polygon]()
    val multiPoints = mutable.Set[MultiPoint]()
    val multiLines = mutable.Set[MultiLine]()
    val multiPolygons = mutable.Set[MultiPolygon]()
    val collections = mutable.Set[GeometryCollection]()
    geoms.foreach{ _ match {
      case p: Point => points += p
      case mp: MultiPoint => multiPoints += mp
      case l: Line => lines += l
      case ml: MultiLine => multiLines += ml
      case p: Polygon => polygons += p
      case mp: MultiPolygon => multiPolygons += mp
      case gc: GeometryCollection => collections += gc
    }}

    apply(
      points.toSet, lines.toSet, polygons.toSet,
      multiPoints.toSet, multiLines.toSet, multiPolygons.toSet,
      collections.toSet
    )
  }

  def apply(gc: jts.GeometryCollection): GeometryCollection = {
    val (points, lines, polygons, multiPoints, multiLines, multiPolygons, collections) = collectGeometries(gc)
    new GeometryCollection(points, lines, polygons, multiPoints, multiLines, multiPolygons, collections, gc)
  }


  def unapply(gc: GeometryCollection): Some[(Set[Point], Set[Line], Set[Polygon])] =
    Some((gc.points, gc.lines, gc.polygons))

  @inline final private 
  def collectGeometries(gc: jts.GeometryCollection):
    (Set[Point], Set[Line], Set[Polygon], Set[MultiPoint], Set[MultiLine], Set[MultiPolygon], Set[GeometryCollection]) =
  {
    val points = mutable.Set[Point]()
    val lines = mutable.Set[Line]()
    val polygons = mutable.Set[Polygon]()
    val multiPoints = mutable.Set[MultiPoint]()
    val multiLines = mutable.Set[MultiLine]()
    val multiPolygons = mutable.Set[MultiPolygon]()
    val collections = mutable.Set[GeometryCollection]()

    val len = gc.getNumGeometries
    for(i <- 0 until len) {
      gc.getGeometryN(i) match {
        case p: jts.Point => points += p
        case mp: jts.MultiPoint => multiPoints += mp
        case l: jts.LineString => lines += l
        case ml: jts.MultiLineString => multiLines += ml
        case p: jts.Polygon => polygons += p
        case mp: jts.MultiPolygon => multiPolygons += mp
        case gc: jts.GeometryCollection => collections += gc
      }
    }

    (points.toSet, lines.toSet, polygons.toSet,
      multiPoints.toSet, multiLines.toSet, multiPolygons.toSet,
      collections.toSet)
  }
}
