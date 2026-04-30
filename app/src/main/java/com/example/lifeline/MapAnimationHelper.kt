package com.example.lifeline

import android.animation.ValueAnimator
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

object MapAnimationHelper {

    fun animateMarker(
        map: MapView,
        marker: Marker,
        from: GeoPoint,
        to: GeoPoint
    ) {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1200

        animator.addUpdateListener {
            val fraction = it.animatedFraction

            val lat = from.latitude + ((to.latitude - from.latitude) * fraction)
            val lng = from.longitude + ((to.longitude - from.longitude) * fraction)

            marker.position = GeoPoint(lat, lng)
            map.invalidate()
        }

        animator.start()
    }
}