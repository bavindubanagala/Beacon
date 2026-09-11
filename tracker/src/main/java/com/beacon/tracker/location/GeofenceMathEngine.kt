package com.beacon.tracker.location

import android.location.Location
import com.beacon.shared.models.GeofenceEventType

object GeofenceMathEngine {

    /**
     * Checks if a point (currLat, currLng) is within the radial distance of a center point.
     */
    fun isInsideRadial(
        currLat: Double,
        currLng: Double,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Float
    ): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(currLat, currLng, centerLat, centerLng, results)
        return results[0] <= radiusMeters
    }

    /**
     * Checks if the device path segment (prev to curr) intersects the tripwire segment (tripA to tripB).
     * Returns the type of crossing (A_TO_B or B_TO_A) or null if no intersection.
     * 
     * Logic: CROSS_A_TO_B means moving from the "right" side of vector AB to the "left" side.
     */
    fun checkTripwireCrossing(
        prevLat: Double,
        prevLng: Double,
        currLat: Double,
        currLng: Double,
        tripALat: Double,
        tripALng: Double,
        tripBLat: Double,
        tripBLng: Double
    ): GeofenceEventType? {
        // Standard 2D line segment intersection logic using cross-products (orientation)
        // Segment 1: (prevLat, prevLng) to (currLat, currLng) -> P to Q
        // Segment 2: (tripALat, tripALng) to (tripBLat, tripBLng) -> A to B

        val o1 = getOrientation(tripALat, tripALng, tripBLat, tripBLng, prevLat, prevLng)
        val o2 = getOrientation(tripALat, tripALng, tripBLat, tripBLng, currLat, currLng)
        val o3 = getOrientation(prevLat, prevLng, currLat, currLng, tripALat, tripALng)
        val o4 = getOrientation(prevLat, prevLng, currLat, currLng, tripBLat, tripBLng)

        // General case: orientations are different
        if (o1 != o2 && o3 != o4) {
            // Crossing occurred. Determine direction based on side transition of AB
            // If o1 was Right (1) and o2 is Left (2), then it's CROSS_A_TO_B (passing vector AB from right to left)
            // Wait, standard orientation: 1 is Clockwise (Right), 2 is Counter-Clockwise (Left)
            return if (o1 == 1 && o2 == 2) {
                GeofenceEventType.CROSS_A_TO_B
            } else {
                GeofenceEventType.CROSS_B_TO_A
            }
        }

        // Note: Collinear cases (touching) are ignored for simplicity/noise reduction in tracking
        return null
    }

    /**
     * Orientation of triplet (p, q, r).
     * 0 -> Collinear
     * 1 -> Clockwise
     * 2 -> Counterclockwise
     */
    private fun getOrientation(px: Double, py: Double, qx: Double, qy: Double, rx: Double, ry: Double): Int {
        val valExpr = (qy - py) * (rx - qx) - (qx - px) * (ry - qy)
        if (valExpr == 0.0) return 0
        return if (valExpr > 0) 1 else 2
    }
}
