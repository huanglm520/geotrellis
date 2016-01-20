package geotrellis.raster.io.geotiff

import geotrellis.raster._
import geotrellis.raster.resample.ResampleMethod
import geotrellis.raster.io.geotiff.compression._
import geotrellis.vector.Extent

import java.util.BitSet

import spire.syntax.cfor._

object GeoTiffTile {
  def apply(
    compressedBytes: Array[Array[Byte]],
    decompressor: Decompressor,
    segmentLayout: GeoTiffSegmentLayout,
    compression: Compression,
    cellType: CellType
  ): GeoTiffTile = {
    cellType match {
      case ct: BitCells =>
        new BitGeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      // Bytes
      case ct: ByteCells =>
        new ByteGeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      // UBytes
      case ct: UByteCells =>
        new UByteGeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      // Shorts
      case ct: ShortCells =>
        new Int16GeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      // UShorts
      case ct: UShortCells =>
        new UInt16GeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      case ct: IntCells =>
        new Int32GeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      case ct: UIntCells =>
        new UInt32GeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      case ct: FloatCells =>
        new Float32GeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
      case ct: DoubleCells =>
        new Float64GeoTiffTile(compressedBytes, decompressor, segmentLayout, compression, ct)
    }
  }

  /** Convert a tile to a GeoTiffTile. Defaults to Striped GeoTIFF format. */
  def apply(tile: Tile): GeoTiffTile =
    apply(tile, GeoTiffOptions.DEFAULT)

  def apply(tile: Tile, options: GeoTiffOptions): GeoTiffTile = {
    val bandType = BandType.forCellType(tile.cellType)

    val segmentLayout = GeoTiffSegmentLayout(tile.cols, tile.rows, options.storageMethod, bandType)

    val segmentCount = segmentLayout.tileLayout.layoutCols * segmentLayout.tileLayout.layoutRows
    val compressor = options.compression.createCompressor(segmentCount)

    val compressedBytes = Array.ofDim[Array[Byte]](segmentCount)
    val segmentTiles = 
      options.storageMethod match {
        case _: Tiled => CompositeTile.split(tile, segmentLayout.tileLayout)
        case _: Striped => CompositeTile.split(tile, segmentLayout.tileLayout, extend = false)
      }

    cfor(0)(_ < segmentCount, _ + 1) { i =>
      val bytes = segmentTiles(i).toBytes
      compressedBytes(i) = compressor.compress(bytes, i)
    }

    apply(compressedBytes, compressor.createDecompressor, segmentLayout, options.compression, tile.cellType)
  }
}

abstract class GeoTiffTile(
  val segmentLayout: GeoTiffSegmentLayout,
  compression: Compression // Compression to use moving forward
) extends Tile with GeoTiffImageData {
  val cellType: CellType

  val bandCount = 1

  val cols: Int = segmentLayout.totalCols
  val rows: Int = segmentLayout.totalRows

  private val isTiled = segmentLayout.isTiled

  def convert(newCellType: CellType): Tile = {
    val arr = Array.ofDim[Array[Byte]](segmentCount)
    val compressor = compression.createCompressor(segmentCount)
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val newBytes = segment.convert(newCellType)
      arr(segmentIndex) = compressor.compress(newBytes, segmentIndex)
    }

    GeoTiffTile(
      arr,
      compressor.createDecompressor(),
      segmentLayout,
      compression,
      newCellType
    )
  }

  val segmentCount = compressedBytes.size

  def getSegment(i: Int): GeoTiffSegment

  def get(col: Int, row: Int): Int = {
    val segmentIndex = segmentLayout.getSegmentIndex(col, row)
    val i = segmentLayout.getSegmentTransform(segmentIndex).gridToIndex(col, row)

    getSegment(segmentIndex).getInt(i)
  }

  def getDouble(col: Int, row: Int): Double = {
    val segmentIndex = segmentLayout.getSegmentIndex(col, row)
    val i = segmentLayout.getSegmentTransform(segmentIndex).gridToIndex(col, row)

    getSegment(segmentIndex).getDouble(i)
  }

  def foreach(f: Int => Unit): Unit = {
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val segmentSize = segment.size

      if(isTiled) {
        // Need to check for bounds
        val segmentTransform = segmentLayout.getSegmentTransform(segmentIndex)
        cfor(0)(_ < segmentSize, _ + 1) { i =>
          val col = segmentTransform.indexToCol(i)
          val row = segmentTransform.indexToRow(i)
          if(col < cols && row < rows) {
            f(segment.getInt(i))
          }
        }
      } else {
        cfor(0)(_ < segmentSize, _ + 1) { i =>
          f(segment.getInt(i))
        }
      }
    }
  }

  def foreachDouble(f: Double => Unit): Unit = {
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val segmentSize = segment.size

      if(isTiled) {
        // Need to check for bounds
        val segmentTransform = segmentLayout.getSegmentTransform(segmentIndex)
        cfor(0)(_ < segmentSize, _ + 1) { i =>
          val col = segmentTransform.indexToCol(i)
          val row = segmentTransform.indexToRow(i)
          if(col < cols && row < rows) {
            f(segment.getDouble(i))
          }
        }
      } else {
        cfor(0)(_ < segmentSize, _ + 1) { i =>
          f(segment.getDouble(i))
        }
      }
    }
  }

  def map(f: Int => Int): GeoTiffTile = {
    val arr = Array.ofDim[Array[Byte]](segmentCount)
    val compressor = compression.createCompressor(segmentCount)
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val newBytes = segment.map(f(_))
      arr(segmentIndex) = compressor.compress(newBytes, segmentIndex)
    }

    GeoTiffTile(
      arr,
      compressor.createDecompressor(),
      segmentLayout,
      compression,
      cellType
    )
  }

  def mapDouble(f: Double => Double): GeoTiffTile = {
    val arr = Array.ofDim[Array[Byte]](segmentCount)
    val compressor = compression.createCompressor(segmentCount)
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val newBytes = segment.mapDouble(f)
      arr(segmentIndex) = compressor.compress(newBytes, segmentIndex)
    }

    GeoTiffTile(
      arr,
      compressor.createDecompressor(),
      segmentLayout,
      compression,
      cellType
    )
  }

  def foreachIntVisitor(visitor: IntTileVisitor): Unit = {
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val segmentSize = segment.size
      val segmentTransform = segmentLayout.getSegmentTransform(segmentIndex)
      cfor(0)(_ < segmentSize, _ + 1) { i =>
        val col = segmentTransform.indexToCol(i)
        val row = segmentTransform.indexToRow(i)
        if(col < cols && row < rows) {
          visitor(col, row, segment.getInt(i))
        }
      }
    }
  }

  def foreachDoubleVisitor(visitor: DoubleTileVisitor): Unit = {
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val segmentSize = segment.size
      val segmentTransform = segmentLayout.getSegmentTransform(segmentIndex)
      cfor(0)(_ < segmentSize, _ + 1) { i =>
        val col = segmentTransform.indexToCol(i)
        val row = segmentTransform.indexToRow(i)
        if(col < cols && row < rows) {
          visitor(col, row, segment.getDouble(i))
        }
      }
    }
  }
   
  def mapIntMapper(mapper: IntTileMapper): Tile = {
    val arr = Array.ofDim[Array[Byte]](segmentCount)
    val compressor = compression.createCompressor(segmentCount)
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val segmentTransform = segmentLayout.getSegmentTransform(segmentIndex)
      val newBytes = segment.mapWithIndex { (i, z) =>
        val col = segmentTransform.indexToCol(i)
        val row = segmentTransform.indexToRow(i)
        if(col < cols && row < rows) {
          mapper(col, row, z)
        } else { 0 }

      }
      arr(segmentIndex) = compressor.compress(newBytes, segmentIndex)
    }

    GeoTiffTile(
      arr,
      compressor.createDecompressor(),
      segmentLayout,
      compression,
      cellType
    )
  }

  def mapDoubleMapper(mapper: DoubleTileMapper): Tile = {
    val arr = Array.ofDim[Array[Byte]](segmentCount)
    val compressor = compression.createCompressor(segmentCount)
    cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
      val segment = getSegment(segmentIndex)
      val segmentTransform = segmentLayout.getSegmentTransform(segmentIndex)
      val newBytes = segment.mapDoubleWithIndex { (i, z) =>
        val col = segmentTransform.indexToCol(i)
        val row = segmentTransform.indexToRow(i)
        if(col < cols && row < rows) {
          mapper(col, row, z)
        } else { 0.0 }
      }
      arr(segmentIndex) = compressor.compress(newBytes, segmentIndex)
    }

    GeoTiffTile(
      arr,
      compressor.createDecompressor(),
      segmentLayout,
      compression,
      cellType
    )
  }

  def combine(other: Tile)(f: (Int, Int) => Int): Tile =
    other match {
      case otherGeoTiff: GeoTiffTile if segmentLayout.tileLayout == otherGeoTiff.segmentLayout.tileLayout =>
        // GeoTiffs with the same segment sizes, can map over segments.
        val arr = Array.ofDim[Array[Byte]](segmentCount)
        val compressor = compression.createCompressor(segmentCount)
        cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
          val segment = getSegment(segmentIndex)
          val otherSegment = otherGeoTiff.getSegment(segmentIndex)
          val newBytes = segment.mapWithIndex { (i, z) =>
            f(z, otherSegment.getInt(i))
          }
          arr(segmentIndex) = compressor.compress(newBytes, segmentIndex)
        }

        GeoTiffTile(
          arr,
          compressor.createDecompressor(),
          segmentLayout,
          compression,
          cellType
        )
      case _ =>
        this.map { (col, row, z) =>
          f(z, other.get(col, row))
        }
    }

  def combineDouble(other: Tile)(f: (Double, Double) => Double): Tile = 
    other match {
      case otherGeoTiff: GeoTiffTile if segmentLayout.tileLayout == otherGeoTiff.segmentLayout.tileLayout =>
        // GeoTiffs with the same segment sizes, can map over segments.
        val arr = Array.ofDim[Array[Byte]](segmentCount)
        val compressor = compression.createCompressor(segmentCount)
        cfor(0)(_ < segmentCount, _ + 1) { segmentIndex =>
          val segment = getSegment(segmentIndex)
          val otherSegment = otherGeoTiff.getSegment(segmentIndex)
          val newBytes = segment.mapDoubleWithIndex { (i, z) =>
            f(z, otherSegment.getDouble(i))
          }
          arr(segmentIndex) = compressor.compress(newBytes, segmentIndex)
        }

        GeoTiffTile(
          arr,
          compressor.createDecompressor(),
          segmentLayout,
          compression,
          cellType
        )
      case _ =>
        this.mapDouble { (col, row, z) =>
          f(z, other.get(col, row))
        }
    }

  def toArray(): Array[Int] = 
    toArrayTile.toArray

  def toArrayDouble(): Array[Double] =
    toArrayTile.toArrayDouble

  def toArrayTile(): ArrayTile = mutable

  def mutable: MutableArrayTile

  def toBytes(): Array[Byte] =
    toArrayTile.toBytes
}
