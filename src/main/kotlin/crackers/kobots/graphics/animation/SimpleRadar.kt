package crackers.kobots.graphics.animation

import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Arc2D
import java.awt.geom.Point2D
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

interface KobotRadar {
    data class RadarScan(val bearing: Int, val range: Float)

    fun updateScan(scan: RadarScan)
    fun Graphics2D.paintRadar()
}

/**
 * Draws a primitive, simple radar output. "Bogeys" (pings) are represented as simple dots, and the "current sweep"
 * location is drawn as a line.
 */
class SimpleRadar(
    upperLeft: Point2D,
    private val drawRadius: Double,
    startAngle: Double = 45.0,
    sweepAngle: Double = 90.0,
    private val bogeySize: Int = 3
) : KobotRadar {

    private val centerX = upperLeft.x + drawRadius
    private val centerY = upperLeft.y + drawRadius
    private val radarArcShape: Arc2D

    private val bogeyRadius = bogeySize / 2

    init {
        val diameter = 2 * drawRadius // because we actually draw it in a box
        // create the pie shape for the radar
        radarArcShape = Arc2D.Double(upperLeft.x, upperLeft.y, diameter, diameter, startAngle, sweepAngle, Arc2D.PIE)
    }

    private val bogeys = ConcurrentHashMap<Int, Double>()
    private val lastScan = AtomicReference<KobotRadar.RadarScan>()

    // if the range is under the radius, store the bogey (otherwise clear)
    override fun updateScan(scan: KobotRadar.RadarScan) {
        if (scan.range < drawRadius) {
            bogeys.put(scan.bearing, scan.range.toDouble())
        } else {
            bogeys.remove(scan.bearing)
        }
        lastScan.set(scan)
    }

    override fun Graphics2D.paintRadar() {
        // clear radar area and draw the "screen"
        color = Color.BLACK
        fill(radarArcShape)

        color = Color.WHITE
        draw(radarArcShape)

        // N.B. negative angle because coordinates are upside down from eyeballs

        // draw the sweep line -- this is based on the given scan angle
        lastScan.get()?.run {
            val (sweepX, sweepY) = locatePoint(bearing, drawRadius)
            color = Color.GREEN
            drawLine(centerX.toInt(), centerY.toInt(), sweepX, sweepY)
        }

        // draw all the current bogies
        color = Color.RED
        bogeys.forEach { angle, range ->
            val (bx, by) = locatePoint(angle, range)
            fillOval(bx - bogeyRadius, by - bogeyRadius, bogeySize, bogeySize)
        }
    }

    private fun locatePoint(angle: Int, r: Double): Pair<Int, Int> {
        val radians = angle.reverseAngleInRadians()
        val sweepX = centerX + r * Math.cos(radians)
        val sweepY = centerY + r * Math.sin(radians)
        return Pair(sweepX.toInt(), sweepY.toInt())
    }

    private fun Int.reverseAngleInRadians(): Double = Math.toRadians(-this.toDouble())
}
