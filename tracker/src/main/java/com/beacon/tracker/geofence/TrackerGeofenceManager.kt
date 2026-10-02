package com.beacon.tracker.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.beacon.shared.models.GeofenceType
import com.beacon.shared.models.GeofenceZone
import com.beacon.tracker.receiver.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.max

class TrackerGeofenceManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        const val PREFS_NAME = "beacon_tracker_geofences"

        @Volatile
        private var lastSignature: String? = null

        @Volatile
        private var tripwireCache: List<StoredTripwire>? = null
    }

    data class StoredGeofence(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double,
        val radiusMeters: Float,
        val alertOnEnter: Boolean,
        val alertOnExit: Boolean,
        val arrivalLiveEnabled: Boolean,
        val arrivalLiveIntervalMillis: Long,
        val revertOnExit: Boolean,
        val activeDaysOfWeek: List<Int>,
        val activeUntil: Long,
        val alertFrequency: String = "EVERY_TIME"
    )

    data class StoredTripwire(
        val id: String,
        val name: String,
        val aLat: Double,
        val aLng: Double,
        val bLat: Double,
        val bLng: Double,
        val direction: String,
        val alertFrequency: String,
        val activeDaysOfWeek: List<Int>,
        val activeUntil: Long
    )

    fun loadStored(): List<StoredGeofence> {
        return try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("zones_json", null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<StoredGeofence>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val daysArray = obj.optJSONArray("activeDaysOfWeek")
                val daysList = mutableListOf<Int>()
                if (daysArray != null) {
                    for (j in 0 until daysArray.length()) {
                        daysList.add(daysArray.getInt(j))
                    }
                }
                list.add(
                    StoredGeofence(
                        id = obj.getString("id"),
                        name = obj.optString("name", ""),
                        lat = obj.getDouble("lat"),
                        lng = obj.getDouble("lng"),
                        radiusMeters = obj.getDouble("radiusMeters").toFloat(),
                        alertOnEnter = obj.optBoolean("alertOnEnter", true),
                        alertOnExit = obj.optBoolean("alertOnExit", true),
                        arrivalLiveEnabled = obj.optBoolean("arrivalLiveEnabled", false),
                        arrivalLiveIntervalMillis = obj.optLong("arrivalLiveIntervalMillis", 10_000L),
                        revertOnExit = obj.optBoolean("revertOnExit", true),
                        activeDaysOfWeek = daysList,
                        activeUntil = obj.optLong("activeUntil", 0L),
                        alertFrequency = obj.optString("alertFrequency", "EVERY_TIME")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e("TrackerGeofence", "Failed to load stored geofences", e)
            emptyList()
        }
    }

    fun loadTripwires(): List<StoredTripwire> {
        tripwireCache?.let { return it }
        val list = try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("tripwires_json", null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            val result = mutableListOf<StoredTripwire>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val daysArray = obj.optJSONArray("activeDaysOfWeek")
                val daysList = mutableListOf<Int>()
                if (daysArray != null) {
                    for (j in 0 until daysArray.length()) {
                        daysList.add(daysArray.getInt(j))
                    }
                }
                result.add(
                    StoredTripwire(
                        id = obj.getString("id"),
                        name = obj.optString("name", ""),
                        aLat = obj.getDouble("aLat"),
                        aLng = obj.getDouble("aLng"),
                        bLat = obj.getDouble("bLat"),
                        bLng = obj.getDouble("bLng"),
                        direction = obj.optString("direction", "BOTH"),
                        alertFrequency = obj.optString("alertFrequency", "EVERY_TIME"),
                        activeDaysOfWeek = daysList,
                        activeUntil = obj.optLong("activeUntil", 0L)
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e("TrackerGeofence", "Failed to load stored tripwires", e)
            emptyList()
        }
        tripwireCache = list
        return list
    }

    fun shouldSendEvent(zoneId: String, alertFrequency: String, nowMillis: Long): Boolean {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "last_event_at_$zoneId"
        val lastEventAt = prefs.getLong(key, 0L)

        val upperFreq = alertFrequency.trim().uppercase()
        return when (upperFreq) {
            "ONCE_EVER" -> {
                if (lastEventAt == 0L) {
                    prefs.edit().putLong(key, nowMillis).apply()
                    true
                } else {
                    false
                }
            }
            "ONCE_PER_DAY" -> {
                if (lastEventAt == 0L) {
                    prefs.edit().putLong(key, nowMillis).apply()
                    true
                } else {
                    val calLast = Calendar.getInstance().apply { timeInMillis = lastEventAt }
                    val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
                    val sameDay = calLast.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                                  calLast.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
                    if (!sameDay) {
                        prefs.edit().putLong(key, nowMillis).apply()
                        true
                    } else {
                        false
                    }
                }
            }
            else -> true
        }
    }

    @SuppressLint("MissingPermission")
    fun applyZones(zones: List<GeofenceZone>) {
        val now = System.currentTimeMillis()
        val filtered = zones.filter { zone ->
            val zoneRadius = zone.radiusMeters
            val zoneActiveUntil = zone.activeUntil
            zone.type == GeofenceType.RADIAL &&
            zone.centerLat != null &&
            zone.centerLng != null &&
            zoneRadius != null &&
            zoneRadius > 0.0 &&
            (zoneActiveUntil == null || zoneActiveUntil > now)
        }.distinctBy { it.id }.take(90)

        val storedList = filtered.map { zone ->
            val days = zone.activeDaysOfWeek ?: emptyList()
            val storedDays = if (days.size >= 7 && (1..7).all { it in days }) emptyList() else days
            StoredGeofence(
                id = zone.id,
                name = zone.name,
                lat = zone.centerLat!!,
                lng = zone.centerLng!!,
                radiusMeters = zone.radiusMeters!!.toFloat(),
                alertOnEnter = zone.alertOnEnter,
                alertOnExit = zone.alertOnExit,
                arrivalLiveEnabled = zone.arrivalLiveEnabled,
                arrivalLiveIntervalMillis = zone.arrivalLiveIntervalMillis,
                revertOnExit = zone.revertOnExit,
                activeDaysOfWeek = storedDays,
                activeUntil = zone.activeUntil ?: 0L,
                alertFrequency = zone.alertFrequency.name
            )
        }

        val tripwireFiltered = zones.filter { zone ->
            val aLat = zone.pointALat
            val aLng = zone.pointALng
            val bLat = zone.pointBLat
            val bLng = zone.pointBLng
            val zoneActiveUntil = zone.activeUntil
            zone.type == GeofenceType.TRIPWIRE &&
            aLat != null && aLng != null && bLat != null && bLng != null &&
            (zoneActiveUntil == null || zoneActiveUntil > now)
        }.distinctBy { it.id }.take(50)

        val tripwireList = tripwireFiltered.map { zone ->
            val days = zone.activeDaysOfWeek ?: emptyList()
            val storedDays = if (days.size >= 7 && (1..7).all { it in days }) emptyList() else days
            StoredTripwire(
                id = zone.id,
                name = zone.name,
                aLat = zone.pointALat!!,
                aLng = zone.pointALng!!,
                bLat = zone.pointBLat!!,
                bLng = zone.pointBLng!!,
                direction = zone.directionality?.name ?: "BOTH",
                alertFrequency = zone.alertFrequency.name,
                activeDaysOfWeek = storedDays,
                activeUntil = zone.activeUntil ?: 0L
            )
        }

        val signature = storedList.toString() + "|" + tripwireList.toString()
        if (signature == lastSignature) {
            return
        }

        try {
            val twArray = JSONArray()
            for (tw in tripwireList) {
                val obj = JSONObject().apply {
                    put("id", tw.id)
                    put("name", tw.name)
                    put("aLat", tw.aLat)
                    put("aLng", tw.aLng)
                    put("bLat", tw.bLat)
                    put("bLng", tw.bLng)
                    put("direction", tw.direction)
                    put("alertFrequency", tw.alertFrequency)
                    put("activeDaysOfWeek", JSONArray(tw.activeDaysOfWeek))
                    put("activeUntil", tw.activeUntil)
                }
                twArray.put(obj)
            }
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("tripwires_json", twArray.toString())
                .apply()
            tripwireCache = tripwireList
        } catch (e: Exception) {
            Log.e("TrackerGeofence", "Failed to save tripwires to prefs", e)
        }

        try {
            val jsonArray = JSONArray()
            for (sg in storedList) {
                val obj = JSONObject().apply {
                    put("id", sg.id)
                    put("name", sg.name)
                    put("lat", sg.lat)
                    put("lng", sg.lng)
                    put("radiusMeters", sg.radiusMeters)
                    put("alertOnEnter", sg.alertOnEnter)
                    put("alertOnExit", sg.alertOnExit)
                    put("arrivalLiveEnabled", sg.arrivalLiveEnabled)
                    put("arrivalLiveIntervalMillis", sg.arrivalLiveIntervalMillis)
                    put("revertOnExit", sg.revertOnExit)
                    put("activeDaysOfWeek", JSONArray(sg.activeDaysOfWeek))
                    put("activeUntil", sg.activeUntil)
                    put("alertFrequency", sg.alertFrequency)
                }
                jsonArray.put(obj)
            }
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("zones_json", jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e("TrackerGeofence", "Failed to save geofences to prefs", e)
        }

        val geofencingClient = LocationServices.getGeofencingClient(appContext)

        if (storedList.isEmpty()) {
            geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
                lastSignature = signature
                Log.d("TrackerGeofence", "Cleared all geofences (empty list)")
            }
            return
        }

        val hasFine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasFine || !hasBackground) {
            Log.w("TrackerGeofence", "Missing location permissions for geofencing registration")
            return
        }

        val geofences = mutableListOf<Geofence>()
        for (sg in storedList) {
            val enter = sg.alertOnEnter || sg.arrivalLiveEnabled
            val exit = sg.alertOnExit || (sg.arrivalLiveEnabled && sg.revertOnExit)
            var transitionType = 0
            if (enter) transitionType = transitionType or Geofence.GEOFENCE_TRANSITION_ENTER
            if (exit) transitionType = transitionType or Geofence.GEOFENCE_TRANSITION_EXIT

            if (transitionType == 0) continue

            val geofence = Geofence.Builder()
                .setRequestId(sg.id)
                .setCircularRegion(sg.lat, sg.lng, max(sg.radiusMeters, 100f))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transitionType)
                .build()
            geofences.add(geofence)
        }

        if (geofences.isEmpty()) {
            geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
                lastSignature = signature
            }
            return
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()

        geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
            geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener {
                    lastSignature = signature
                    Log.d("TrackerGeofence", "Successfully registered ${geofences.size} geofences")
                }
                .addOnFailureListener { e ->
                    Log.e("TrackerGeofence", "Failed to add geofences", e)
                }
        }
    }

    private val pendingIntent: PendingIntent by lazy {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(appContext, 0, Intent(appContext, GeofenceBroadcastReceiver::class.java), flags)
    }

    fun clearAll() {
        LocationServices.getGeofencingClient(appContext).removeGeofences(pendingIntent)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().remove("zones_json").remove("tripwires_json")
        for ((k, _) in prefs.all) {
            if (k.startsWith("last_event_at_")) {
                editor.remove(k)
            }
        }
        editor.apply()
        tripwireCache = null
        lastSignature = null
        Log.d("TrackerGeofence", "Cleared all stored and active geofences and tripwires")
    }
}
