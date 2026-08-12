package com.navrot.aifuelassistant.ui.map

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Shader
import android.location.Location
import android.view.animation.LinearInterpolator
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Google Maps–style location dot: accuracy halo, white ring, blue core, bearing cone.
 * Drawn below markers; position and bearing animate smoothly (~300 ms).
 */
class MyLocationDot : Overlay() {

    private var mapView: MapView? = null

    private var displayPoint: GeoPoint? = null
    private var targetPoint: GeoPoint? = null
    private var accuracyMeters: Float = 0f

    private var displayBearing: Float = 0f
    private var lastKnownBearing: Float = 0f

    private var positionAnimator: ValueAnimator? = null
    private var bearingAnimator: ValueAnimator? = null

    private val screenPoint = Point()

    private val accuracyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ACCURACY_COLOR
    }
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RING_COLOR
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = DOT_COLOR
    }
    private val conePath = Path()

    fun update(
        mapView: MapView,
        location: UserLocationState,
        routePoints: List<GeoPoint>?
    ) {
        this.mapView = mapView
        val geoPoint = location.toGeoPoint()
        targetPoint = geoPoint
        accuracyMeters = location.accuracy.coerceAtLeast(0f)

        val targetBearing = resolveBearing(location, routePoints, geoPoint)

        if (displayPoint == null) {
            displayPoint = geoPoint
            displayBearing = targetBearing
            lastKnownBearing = targetBearing
            mapView.invalidate()
            return
        }

        animatePosition(displayPoint!!, geoPoint, mapView)
        animateBearing(displayBearing, targetBearing, mapView)
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val point = displayPoint ?: return
        val density = mapView?.context?.resources?.displayMetrics?.density ?: return
        projection.toPixels(point, screenPoint)
        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()

        val dotRadiusPx = DOT_RADIUS_DP * density
        val ringStrokePx = RING_STROKE_DP * density
        val coneLengthPx = CONE_LENGTH_DP * density

        if (accuracyMeters > 0f) {
            val accuracyPx = projection.metersToPixels(
                accuracyMeters,
                point.latitude,
                projection.zoomLevel
            )
            canvas.drawCircle(cx, cy, accuracyPx, accuracyPaint)
        }

        drawBearingCone(canvas, cx, cy, displayBearing, coneLengthPx)

        ringPaint.strokeWidth = ringStrokePx
        canvas.drawCircle(cx, cy, dotRadiusPx + ringStrokePx / 2f, ringPaint)

        canvas.drawCircle(cx, cy, dotRadiusPx, dotPaint)
    }

    override fun onDetach(mapView: MapView?) {
        positionAnimator?.cancel()
        bearingAnimator?.cancel()
        this.mapView = null
    }

    private fun drawBearingCone(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        bearing: Float,
        coneLengthPx: Float
    ) {
        canvas.save()
        canvas.rotate(bearing, cx, cy)

        conePath.reset()
        conePath.moveTo(cx, cy)
        val halfRad = Math.toRadians(CONE_HALF_ANGLE.toDouble())
        val baseAngle = Math.toRadians(-90.0)
        val x1 = cx + coneLengthPx * cos(baseAngle - halfRad).toFloat()
        val y1 = cy + coneLengthPx * sin(baseAngle - halfRad).toFloat()
        val x2 = cx + coneLengthPx * cos(baseAngle + halfRad).toFloat()
        val y2 = cy + coneLengthPx * sin(baseAngle + halfRad).toFloat()
        conePath.lineTo(x1, y1)
        conePath.lineTo(x2, y2)
        conePath.close()

        conePaint.shader = LinearGradient(
            cx, cy, cx, cy - coneLengthPx,
            CONE_COLOR_START,
            CONE_COLOR_END,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(conePath, conePaint)
        conePaint.shader = null
        canvas.restore()
    }

    private fun resolveBearing(
        location: UserLocationState,
        routePoints: List<GeoPoint>?,
        current: GeoPoint
    ): Float {
        if (location.speed > SPEED_THRESHOLD && location.hasBearing) {
            lastKnownBearing = normalizeBearing(location.bearing)
            return lastKnownBearing
        }
        routePoints?.let { points ->
            findNextRoutePoint(current, points)?.let { next ->
                lastKnownBearing = bearingBetween(current, next)
                return lastKnownBearing
            }
        }
        return lastKnownBearing
    }

    private fun animatePosition(from: GeoPoint, to: GeoPoint, mapView: MapView) {
        if (from.latitude == to.latitude && from.longitude == to.longitude) return
        positionAnimator?.cancel()
        val startLat = from.latitude
        val startLon = from.longitude
        val endLat = to.latitude
        val endLon = to.longitude
        positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                displayPoint = GeoPoint(
                    startLat + (endLat - startLat) * t,
                    startLon + (endLon - startLon) * t
                )
                mapView.invalidate()
            }
            start()
        }
    }

    private fun animateBearing(from: Float, to: Float, mapView: MapView) {
        val delta = shortestBearingDelta(from, to)
        if (delta == 0f) return
        bearingAnimator?.cancel()
        bearingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                displayBearing = normalizeBearing(from + delta * t)
                mapView.invalidate()
            }
            start()
        }
    }

    companion object {
        private const val ANIMATION_MS = 300L
        private const val SPEED_THRESHOLD = 1f
        private const val DOT_RADIUS_DP = 9f
        private const val RING_STROKE_DP = 3f
        private const val CONE_LENGTH_DP = 48f
        private const val CONE_HALF_ANGLE = 30f

        private const val ACCURACY_COLOR = 0x264285F4.toInt()
        private const val DOT_COLOR = 0xFF4285F4.toInt()
        private const val RING_COLOR = 0xFFFFFFFF.toInt()
        private const val CONE_COLOR_START = 0x4D4285F4.toInt()
        private const val CONE_COLOR_END = 0x004285F4.toInt()

        fun findNextRoutePoint(current: GeoPoint, routePoints: List<GeoPoint>): GeoPoint? {
            if (routePoints.size < 2) return null
            var closestIdx = 0
            var minDist = Double.MAX_VALUE
            routePoints.forEachIndexed { idx, pt ->
                val dist = distanceMeters(current, pt)
                if (dist < minDist) {
                    minDist = dist
                    closestIdx = idx
                }
            }
            val nextIdx = (closestIdx + 1).coerceAtMost(routePoints.lastIndex)
            return if (nextIdx != closestIdx) routePoints[nextIdx] else null
        }

        fun bearingBetween(from: GeoPoint, to: GeoPoint): Float {
            val start = Location("").apply {
                latitude = from.latitude
                longitude = from.longitude
            }
            val end = Location("").apply {
                latitude = to.latitude
                longitude = to.longitude
            }
            return normalizeBearing(start.bearingTo(end))
        }

        fun shortestBearingDelta(from: Float, to: Float): Float {
            var delta = (to - from) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return delta
        }

        fun normalizeBearing(bearing: Float): Float {
            var b = bearing % 360f
            if (b < 0f) b += 360f
            return b
        }

        private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val results = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
            return results[0].toDouble()
        }
    }
}
