# Beacon Project - Developer Guide

## Quick Start

### Prerequisites
- Android Studio (latest)
- Android SDK 26+ (minSdk), target SDK 34
- Firebase project (Google Cloud Console)
- Kotlin 1.9.0

### Project Structure
```
Beacon/
├── shared/              # Shared data models & constants
│   └── src/main/java/com/beacon/shared/
│       ├── models/      # Device, Location, Alert, Group, Settings
│       └── constants/   # Firebase paths, defaults, keys
├── tracker/             # Tracker app (device location sender)
│   └── src/main/java/com/beacon/tracker/
│       ├── auth/        # DeviceAuthManager (device_id + secret)
│       ├── repository/  # FirebaseTrackerRepository (data uploads)
│       ├── services/    # LocationTrackingService, BatteryMonitor, SignalMonitor
│       ├── receivers/   # BootCompletedReceiver, LocationUpdateReceiver
│       └── MainActivity.kt
└── admin/               # Admin app (device management + live tracking)
    └── src/main/java/com/beacon/admin/
        ├── auth/        # AuthManager (Firebase Auth wrapper)
        ├── repository/  # DeviceRepository (device CRUD)
        └── MainActivity.kt
```

---

## Key Implementation Details

### 1. Primary Tracking Mechanism
**File**: `tracker/src/main/java/com/beacon/tracker/services/LocationTrackingService.kt`

- **Design**: Foreground Service with Handler-based timer loop (NOT WorkManager primary)
- **Lifecycle**: Auto-starts on boot via BootCompletedReceiver
- **Interval**: Configurable 30-300 seconds (default 60s)
- **Location Upload Flow**:
  1. Request location from FusedLocationProviderClient
  2. On callback: upload to Firestore `location_history` subcollection
  3. Update device status (battery, signal, motion, timestamp)
  4. Write to RTDB `/live_locations/{device_id}` for real-time admin view
  5. Log any failures (retry via offline queue - TODO)

### 2. Device Authentication
**File**: `tracker/src/main/java/com/beacon/tracker/auth/DeviceAuthManager.kt`

- **First Launch**: Generate UUID for device_id + 32-char random secret
- **Storage**: EncryptedSharedPreferences with AES256_GCM (NOT Firestore, NOT plain SharedPrefs)
- **Admin Can't See**: Secret never transmitted or visible in admin UI
- **Server Validation**: Firestore Security Rules check device_id + secret on write

### 3. Admin Authentication
**File**: `admin/src/main/java/com/beacon/admin/auth/AuthManager.kt`

- **Firebase Auth**: Email/password only
- **No Cloud Storage of Passwords**: Firebase Auth SDK handles hashing/validation
- **Token Management**: Stored in EncryptedSharedPreferences
- **Session Handling**: Check `isAuthenticated()` on app launch

### 4. Data Models
**File**: `shared/src/main/java/com/beacon/shared/models/`

All models have `toMap()` (for Firestore write) and `fromMap()` (for Firestore read):
- **Device**: device_id, owner_admin, device_name, device_icon, status, battery, signal, last_location
- **Location**: timestamp, lat, lon, accuracy, provider, speed, heading, battery, signal, motion
- **Alert**: alert_id, device_id, type (LOW_BATTERY, OFFLINE, etc), severity (INFO/WARNING/CRITICAL)
- **Group**: group_id, name, device_ids, color

---

## Firebase Architecture

### Collections Structure
```
Firestore:
├── devices/
│   ├── {device_id}/
│   │   ├── location_history/
│   │   │   ├── {timestamp}/ (location doc)
│   │   │   └── {timestamp}/ ...
│   │   └── alerts/
│   │       └── {alert_id}/
│   └── ...
├── device_groups/
│   └── {group_id}/
├── admin_settings/
│   └── {admin_uid}/
└── fcm_tokens/
    └── {admin_uid}/

Realtime Database:
└── /live_locations/
    ├── {device_id}/
    │   ├── lat
    │   ├── lon
    │   ├── accuracy
    │   ├── timestamp
    │   └── battery
    └── {device_id}/ ...
```

### Security Rules (TODO)
```javascript
// devices collection
- Only device_id + secret can write own doc
- Only admin uid can read all devices
- location_history subcollection:
  - Only owner device writes
  - Only owner admin reads
- alerts subcollection:
  - Cloud Functions create (not editable)
```

---

## Next Development Steps

### Phase 3: Admin UI Screens
**Priority**: Build one feature at a time

1. **Device List Screen** 
   - Display devices from Firestore
   - Group by admin-defined categories
   - Show: device_name, status (online/offline), battery, signal
   - Quick toggle: pause/resume tracking
   - Action menu: settings, remove device

2. **Map Screen**
   - Google Maps with device pins
   - Real-time updates from RTDB `/live_locations`
   - Tap pin → see device details
   - Realtime listener (only active when screen visible)

3. **History Screen**
   - Date range picker
   - Timeline of location points
   - Export as KML/CSV

4. **Alerts Screen**
   - Active alerts tab (expandable, actions to resolve)
   - History tab (resolved + ignored)
   - Filter by device, severity, type
   - Notification handling

5. **Settings Screen (Global)**
   - Default tracking interval
   - Alert thresholds (battery %, signal %, timeout)
   - Theme (light/dark/system)
   - Notification preferences

6. **Settings Screen (Per-Device)**
   - Tracking interval override
   - Accuracy level (GPS, mixed, network)
   - Custom alert thresholds
   - Icon and name

### Phase 4: Data Persistence & Sync
1. **Room Database** - Local cache for:
   - Devices (for offline display)
   - Location history (last 24h)
   - Alerts (active only)
   - Settings (sync when online)

2. **Offline Mode**:
   - Display cached data with "last updated" timestamp
   - Queue admin actions (settings changes) locally
   - Sync when back online

3. **Realtime Sync**:
   - Firestore listeners on device collection
   - RTDB listeners on live_locations (when in foreground)
   - Compare timestamps: fetch only new data

### Phase 5: Notifications & Monitoring
1. **Battery Monitoring** (Tracker):
   - BroadcastReceiver on Intent.ACTION_BATTERY_CHANGED
   - Update Firestore once per 5 minutes max

2. **Signal Monitoring** (Tracker):
   - Sample network signal strength every 10 seconds
   - Include in location uploads (batch, not separate writes)

3. **FCM Integration** (Admin):
   - Request FCM token on first auth
   - Store in Firestore `fcm_tokens` collection
   - Receive critical alerts (sound + vibration)

4. **Cloud Functions**:
   - Trigger on location write
   - Check thresholds (battery < 20%, offline > 5min, weak signal)
   - Create alert docs
   - Send FCM to admin

### Phase 6: Firebase Security & Deployment
1. **Deploy Security Rules**
2. **Create Cloud Functions** for alert generation
3. **Setup FCM** message templates
4. **Create Firestore Indexes** (compound queries)
5. **Configure Storage** if needed (logs, exports)

---

## Common Patterns

### Coroutines & Suspend Functions
```kotlin
// Repository methods return Result<T>
suspend fun uploadLocation(location: Location): Result<Unit> {
    return try {
        // Firebase operation
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// In ViewModel or Service
lifecycleScope.launch {
    val result = repository.uploadLocation(location)
    result.onSuccess { /* success case */ }
    result.onFailure { /* error handling */ }
}
```

### Firestore CRUD
```kotlin
// Add/Update
firestore.collection("devices")
    .document(device_id)
    .set(device.toMap())
    .await()

// Read
firestore.collection("devices")
    .document(device_id)
    .get()
    .await()
    .toObject<Device>()

// Listener (real-time)
firestore.collection("devices")
    .addSnapshotListener { snapshot, error ->
        snapshot?.documents?.forEach { doc ->
            val device = doc.toObject<Device>()
        }
    }
```

### Location Tracking
```kotlin
// Request location (LocationTrackingService)
val locationRequest = LocationRequest.Builder(trackingInterval * 1000L)
    .setPriority(when (accuracy) {
        HIGH -> Priority.PRIORITY_HIGH_ACCURACY
        MEDIUM -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        LOW -> Priority.PRIORITY_LOW_POWER
    })
    .build()

fusedLocationClient.requestLocationUpdates(locationRequest, callback, looper)

// On location received
override fun onLocationResult(result: LocationResult) {
    val location = result.lastLocation
    // Upload to Firebase
    repository.updateLiveLocation(location)
}
```

### Encryption
```kotlin
// EncryptedSharedPreferences
val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encPrefs = EncryptedSharedPreferences.create(
    context,
    "secret_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

encPrefs.edit().putString("device_secret", secret).apply()
```

---

## Testing Checklist

- [ ] Device onboarding → generates device_id + secret
- [ ] Tracker starts LocationTrackingService after permissions
- [ ] Location uploads to Firestore + RTDB every interval
- [ ] Admin signs up → Firebase Auth → can pair device
- [ ] Admin adds device → immediately synced in Firestore
- [ ] Pause tracking → service updates status, stops uploads
- [ ] Remove device → tracker goes to idle state gracefully
- [ ] Kill tracker app → BootReceiver restarts service on reboot
- [ ] Offline tracker → offline queue builds (TODO)
- [ ] Admin offline → cached data displayed (TODO)
- [ ] Alert generated → FCM sent to admin (TODO)
- [ ] Map shows live locations (TODO)

---

## Build & Deployment

### Debug Build
```bash
./gradlew assemble
```

### Release Build (requires keystore)
```bash
./gradlew bundleRelease
```

### Run on Device
```bash
./gradlew installDebug
adb shell am start -n com.beacon.tracker/.MainActivity
adb shell am start -n com.beacon.admin/.MainActivity
```

---

## Resources
- ARCHITECTURE.md - Full design decisions (8 rules, security model, data flows)
- IMPLEMENTATION_PROGRESS.md - What's done, what's TODO
- Android Docs: https://developer.android.com/
- Firebase: https://firebase.google.com/docs
- Jetpack Compose: https://developer.android.com/jetpack/compose

---

**Ready to build!** Follow the Phase 3-6 steps in order, one feature at a time. 🚀
