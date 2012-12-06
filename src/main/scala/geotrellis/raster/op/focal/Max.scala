package geotrellis.raster.op.focal

import scala.math._

import geotrellis._
import geotrellis.raster._

case class Max(r:Op[Raster],n:Op[Neighborhood]) extends FocalOp(r,n)({
  (r,n) => new CursorCalculation with IntRasterDataResult { 
    def calc(r:Raster, cursor:Cursor) = {
      var m = Int.MinValue
      cursor.allCells.foreach { (x,y) => m = max(m,r.get(x,y)) }
      data.set(cursor.focusX,cursor.focusY,m)
    }
  }
})
