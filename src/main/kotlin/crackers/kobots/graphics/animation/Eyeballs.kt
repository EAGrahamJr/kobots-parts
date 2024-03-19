/*
 * Copyright 2022-2023 by E. A. Graham, Jr.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package crackers.kobots.graphics.animation

import java.awt.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Basic definition for an eye-like thing that can be drawn.
 */
interface Ocular {
    val bounds: Rectangle
    fun clear(graphics: Graphics2D)
    fun draw(graphics: Graphics2D)
}

/**
 * Models a cartoonish "eye". Assumes coordinates are in Graphics2D space. The eyes are currently not drawn with
 * outlines, so the eye color itself is initially assumed to be white, with a black background.
 *
 * TODO add an optional iris (fixed size)
 */
class Eye(val center: Point, val radius: Int, val pupil: Pupil) : Ocular {
    constructor(center: Point, radius: Int) : this(
        center,
        radius,
        Pupil((radius / 3.0).roundToInt())
    )

    override val bounds = Rectangle(center.x - radius, center.y - radius, radius * 2, radius * 2)

    enum class LidPosition {
        OPEN,
        CLOSED,
        HALF,
        THREE_QUARTERS,
        ONE_QUARTER
    }

    init {
        require(radius > 0) { "Radius must be positive" }
    }

    var eyeColor = Color.WHITE
    var eyeBackground = Color.BLACK

    var lidPosition = LidPosition.OPEN
    private var pupilLocation: Point = center
    private val MAX_PUPIL_SIZE = (radius * .75).roundToInt()

    /**
     * Set the pupil position relative to the eye center. The pupil is constrained to be within the eye and will show
     * at least 80% of the pupil at the outsides of the eye.
     *
     * TODO this should be handled as a radial difference from the center, not cartesian
     *
     * @param xDelta the relative position of the pupil in the x direction
     * @param yDelta the relative position of the pupil in the y direction
     */
    fun setPupilPosition(xDelta: Double, yDelta: Double) {
        // the 80% factor is to make sure the pupil is always visible
        val fractionRadii = (pupil.radius / 5.0).roundToInt()
        val maxX = center.x + radius - fractionRadii
        val maxY = center.y + radius - fractionRadii

        val xc = (center.x + xDelta * radius).roundToInt().coerceIn(bounds.x + fractionRadii, maxX)
        val yc = (center.y + yDelta * radius).roundToInt().coerceIn(bounds.y + fractionRadii, maxY)

        pupilLocation = Point(xc, yc)
    }

    override fun clear(graphics: Graphics2D) = with(graphics) {
        color = eyeBackground
        fillCircle(center.x, center.y, radius)
    }

    override fun draw(graphics: Graphics2D) = with(graphics) {
        clear(this)

        // draw the full eye
        color = eyeColor
        fillCircle(center.x, center.y, radius)

        // figure out where the lid hits and re-draw the background color as a rectangle
        color = eyeBackground
        when (lidPosition) {
            LidPosition.HALF -> fillRect(bounds.x, bounds.y, bounds.width, (bounds.height * .5).roundToInt())
            LidPosition.THREE_QUARTERS -> fillRect(bounds.x, bounds.y, bounds.width, (bounds.height * .75).roundToInt())
            LidPosition.ONE_QUARTER -> fillRect(bounds.x, bounds.y, bounds.width, (bounds.height * 0.25).roundToInt())
            LidPosition.CLOSED -> {
                fillCircle(center.x, center.y, radius)
                color = eyeColor
                val ogStroke = stroke
                stroke = BasicStroke(2.0f)
                drawArc(bounds.x, bounds.y, bounds.width, bounds.height, 225, 90)
                stroke = ogStroke
            }

            else -> {}
        }

        color = pupil.color
        // the pupil size should be no larger than 75% of the eye radius
        val pupilRadius = min(MAX_PUPIL_SIZE, pupil.radius)
        fillCircle(pupilLocation.x, pupilLocation.y, pupilRadius)
    }

    fun Graphics2D.fillCircle(x: Int, y: Int, radius: Int) {
        fillOval(x - radius, y - radius, radius * 2, radius * 2)
    }
}

/**
 * The thingie you see through. Note that the pupil is not drawn with an outline and the color is assumed to be black.
 */
class Pupil(private val baseRadius: Int, private val minRadius: Int = 2, var color: Color = Color.BLACK) {
    var size: Size = Size.NORMAL

    /**
     * The radius of the pupil, based on the [baseRadius] and the current [size]. Note that it cannot be smaller
     * than [minRadius]
     */
    val radius: Int
        get() = max(minRadius, (baseRadius * size.ratio).roundToInt())

    /**
     * Relative positions of the pupil within an eye. Note that the deltas are given in Graphics2D coordinates.
     *
     * **NOTE** "left" and "right" are relative to the screen, not the viewer.
     * TODO redefine
     */
    enum class Position(val xDelta: Double, val yDelta: Double) {
        UP(0.0, -1.0),
        DOWN(0.0, 1.0),
        LEFT(1.0, 0.0),
        RIGHT(-1.0, 0.0),
        CENTER(0.0, 0.0),
        HALF_UP(0.0, -.5),
        HALF_DOWN(0.0, .5),
        HALF_LEFT(.5, 0.0),
        HALF_RIGHT(-.5, 0.0);

        operator fun plus(other: Position) = Pair(this.xDelta + other.xDelta, this.yDelta + other.yDelta)

        fun shiftLeft() = this + HALF_LEFT
        fun shiftRight() = this + HALF_RIGHT
        fun fullLeft() = this + LEFT
        fun fullRight() = this + RIGHT
    }

    enum class Size(val ratio: Double) {
        TINY(0.25),
        SMALL(0.5),
        NORMAL(1.0),
        LARGE(1.5),
        WIDE(2.0)
    }
}

/**
 * Convenience class to treat a pair of eyes the same. Note that although [left] and [right] are defined, they do
 * not have to be the on the left and right respectively (or even technically on the same screen).
 *
 * This is terribly biased towards bi-lateral representations, but it's a start.
 */
class PairOfEyes(val left: Eye, val right: Eye, val backgroundColor: Color = Color.BLACK) : Ocular {
    override val bounds by lazy {
        val minX = min(left.bounds.x, right.bounds.x)
        val minY = min(left.bounds.y, right.bounds.y)
        val maxX = max(left.bounds.x + left.bounds.width, right.bounds.x + right.bounds.width)
        val maxY = max(left.bounds.y + left.bounds.height, right.bounds.y + right.bounds.height)

        Rectangle(minX, minY, maxX - minX, maxY - minY)
    }

    /**
     * Clear the area bounded by [bounds] to the [backgroundColor].
     */
    override fun clear(graphics: Graphics2D) = with(graphics) {
        color = backgroundColor
        fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
    }

    /**
     * Draw both eyes.
     */
    override fun draw(graphics: Graphics2D) {
        left.draw(graphics)
        right.draw(graphics)
    }

    /**
     * Operator to apply an expression to both eyes. Example:
     *
     * `eyes(CannedExpressions.CLOSED.expression, CannedExpressions.NORMAL.expression)`
     */
    operator fun invoke(leftExpression: Expression, rightExpression: Expression) {
        leftExpression(left)
        rightExpression(right)
    }

    /**
     * Convenience operator to apply expressions to the eyes. Example:
     *
     * `eyes(Pair(CannedExpressions.CLOSED.expression, CannedExpressions.NORMAL.expression))`
     */
    operator fun invoke(expressions: Pair<Expression, Expression>) {
        this(expressions.first, expressions.second)
    }

    /**
     * Convenience operator to apply the same expression to both eyes. Example:
     *
     * `eyes(CannedExpressions.EYE_ROLL.expression)`
     */
    operator fun invoke(expression: Expression) = this(expression, expression)
}

/**
 * Defines the settings that can be "applied" to eyes.
 */
class Expression(
    val lidPosition: Eye.LidPosition = Eye.LidPosition.OPEN,
    val pupilPosition: Pair<Double, Double> = Pupil.Position.CENTER + Pupil.Position.CENTER,
    val eyeColor: Color = Color.WHITE,
    val pupilColor: Color = Color.BLACK,
    val pupilSize: Pupil.Size = Pupil.Size.NORMAL
) {

    /**
     * Apply the expression to the given [eye]. Example:
     *
     * `expression(eye)`
     */
    operator fun invoke(eye: Eye) {
        eye.lidPosition = lidPosition
        eye.eyeColor = eyeColor
        eye.pupil.color = pupilColor
        eye.pupil.size = pupilSize
        eye.setPupilPosition(pupilPosition.first, pupilPosition.second)
    }

    /**
     * Shift the pupil position by the given [position]. Example:
     *
     * `expression shift Pupil.Position.HALF_LEFT`
     */
    infix fun shift(position: Pupil.Position): Expression {
        // cheap way to get the pair
        val (x, y) = position + Pupil.Position.CENTER
        val newPosition = Pair(x + pupilPosition.first, y + pupilPosition.second)
        return Expression(
            lidPosition = lidPosition,
            pupilPosition = newPosition,
            eyeColor = eyeColor,
            pupilColor = pupilColor,
            pupilSize = pupilSize
        )
    }
}

/**
 * Pre-defined expressions for eyes.
 */
enum class CannedExpressions(val expression: Expression) {
    NORMAL(Expression()),
    CLOSED(Expression(lidPosition = Eye.LidPosition.CLOSED)),
    LOOK_DOWN(Expression(pupilPosition = Pupil.Position.CENTER + Pupil.Position.HALF_DOWN)),
    LOOK_UP(Expression(pupilPosition = Pupil.Position.CENTER + Pupil.Position.HALF_UP)),
    EYE_ROLL(Expression(pupilPosition = Pupil.Position.CENTER + Pupil.Position.UP)),
    WHATS_THIS(
        Expression(
            lidPosition = Eye.LidPosition.ONE_QUARTER,
            pupilPosition = Pupil.Position.CENTER + Pupil.Position.HALF_DOWN
        )
    ),
    ANNOYED(Expression(lidPosition = Eye.LidPosition.HALF)),
    HMMM(
        Expression(
            lidPosition = Eye.LidPosition.HALF,
            pupilPosition = Pupil.Position.CENTER + Pupil.Position.HALF_DOWN
        )
    ),
    PISSED(Expression(lidPosition = Eye.LidPosition.THREE_QUARTERS) shift Pupil.Position.HALF_DOWN),
    SURPRISED(Expression(pupilSize = Pupil.Size.WIDE));

    companion object {
        val GOOFY = Pair(PISSED.expression, NORMAL.expression)
        val OWWW = Pair(
            SURPRISED.expression,
            Expression(pupilSize = Pupil.Size.SMALL, lidPosition = Eye.LidPosition.ONE_QUARTER)
        )
        val RT_WINK = Pair(CLOSED.expression, Expression(lidPosition = Eye.LidPosition.ONE_QUARTER))
        val LT_WINK = Pair(Expression(lidPosition = Eye.LidPosition.ONE_QUARTER), CLOSED.expression)

        val sets = setOf(GOOFY, OWWW, RT_WINK, LT_WINK)
    }
}
