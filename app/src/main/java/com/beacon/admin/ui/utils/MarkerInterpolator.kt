package com.beacon.admin.ui.utils

import org.osmdroid.util.GeoPoint

object MarkerInterpolator {

    fun interpolate(fraction: Float, start: GeoPoint, end: GeoPoint): GeoPoint {
        val lat = (end.latitude - start.latitude) * fraction + start.latitude
        val lng = (end.longitude - start.longitude) * fraction + start.longitude
        return GeoPoint(lat, lng)
    }

    fun computeBearing(start: GeoPoint, end: GeoPoint): Double {
        val startLatRad = Math.toRadians(start.latitude)
        val startLngRad = Math.toRadians(start.longitude)
        val endLatRad = Math.toRadians(end.latitude)
        val endLngRad = Math.toRadians(end.longitude)

        val dLng = endLngRad - startLngRad
        val y = Math.sin(dLng) * Math.cos(endLatRad)
        val x = Math.cos(startLatRad) * Math.sin(endLatRad) -
                Math.sin(startLatRad) * Math.cos(endLatRad) * Math.cos(dLng)

        val initialBearing = Math.atan2(y, x)
        return (Math.toDegrees(initialBearing) + 360) % 360
    }
}