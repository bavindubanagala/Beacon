# Beacon: Two-App Android Tracking System - Architecture Design

## 1. System Overview

**Beacon** is a location tracking system with two Android apps:
- **Admin App**: Monitor devices, view maps, manage device groups, configure alerts
- **Tracker App**: Run as a background service, collect location data, send to Firebase

Both apps share one Firebase project. The tracker app continuously sends location and status data; the admin app receives, caches, and displays this data with alerts.

---

## 2. Firebase Data Model

### 2.1 Firestore Collections

#### `admin` (Single Document)
Stores global settings for the admin account. Authentication handled by Firebase Auth (not stored in Firestore).

```
Collection: admin
└── Document: account
    ├── email: string (for reference only; source of truth is Firebase Auth)
    ├── created_at: timestamp
    ├── global_settings:
    │   ├── default_tracking_interval: number (in seconds, default: 60)
    │   ├── map_refresh_rate: number (in seconds, default: 5)
    │   ├── low_battery_threshold: number (default: 15)
    │   ├── signal_strength_threshold: number (default: 20)
    │   ├── offline_threshold_minutes: number (default: 10)
    │   └── history_retention_days: number (default: 30)
```

#### `devices` (Collection of Tracker Devices)
Each tracker device has a document.

```
Collection: devices
└── Document: {device_id}
    ├── device_id: string (unique, auto-generated on tracker install)
    ├── icon_id: string (from fixed icon set: e.g., "car", "phone", "person", etc.)
    ├── group_id: string (reference to devices_groups)
    ├── created_at: timestamp
    ├── last_location:
    │   ├── latitude: number
    │   ├── longitude: number
    │   ├── accuracy: number (in meters)
    │   ├── timestamp: timestamp
    │   └── provider: string ("gps" or "network")
    ├── battery_level: number (0-100)
    ├── battery_is_charging: boolean
    ├── signal_strength: number (0-100, calculated from network signal)
    ├── last_seen: timestamp
    ├── status: string (enum: "online", "offline", "paused")
    ├── tracking_enabled: boolean
    ├── tracking_interval: number (in seconds, range 30-300)
    ├── device_motion_status: string (enum: "moving", "idle", "stationary")
    ├── per_device_settings:
    │   ├── tracking_enabled: boolean
    │   ├── tracking_interval: number
    │   ├── notifications_enabled: boolean
    │   ├── location_accuracy_level: string (enum: "high", "medium", "low")
    │   └── alert_thresholds:
    │       ├── low_battery: number
    │       ├── weak_signal: number
    │       └── offline_threshold_minutes: number
    └── metadata:
        ├── app_version: string
        └── os_version: string
```

**Note on device_secret**: Not stored in Firestore. Instead:
- Generated once on tracker first install
- Stored securely on tracker device in EncryptedSharedPreferences
- Included in Firebase Security Rules validation (server-side only)
- Admin cannot see or manage device secrets

#### `devices_groups` (Collection)
Group/folder structure for organizing devices.

```
Collection: devices_groups
└── Document: {group_id}
    ├── group_id: string (auto-generated)
    ├── name: string (e.g., "Fleet A", "Personal Phones")
    ├── created_at: timestamp
    ├── icon_color: string (optional, for visual distinction)
    └── order: number (for sorting in admin app)
```

#### `location_history` (Collection - Per Device Subcollection)
Stores historical location data for each device.

```
Collection: devices/{device_id}/location_history
└── Document: {timestamp}
    ├── timestamp: timestamp
    ├── latitude: number
    ├── longitude: number
    ├── accuracy: number
    ├── provider: string ("gps" or "network")
    ├── speed: number (optional, in m/s)
    ├── heading: number (optional, in degrees)
    ├── battery_level: number
    ├── signal_strength: number
    └── device_motion_status: string
```

#### `alerts` (Collection - Per Device Subcollection)
Triggered alerts for each device.

```
Collection: devices/{device_id}/alerts
└── Document: {alert_id}
    ├── alert_id: string (auto-generated)
    ├── alert_type: string (enum: "low_battery", "offline", "paused", "weak_signal", "stale_location")
    ├── severity: string (enum: "info", "warning", "critical")
    ├── message: string (e.g., "Battery level critically low")
    ├── created_at: timestamp
    ├── resolved_at: timestamp (null if unresolved)
    ├── resolved_by_admin: boolean
    └── data:
        ├── battery_level: number (for low_battery alerts)
        ├── signal_strength: number (for weak_signal alerts)
        ├── minutes_since_update: number (for stale_location)
        └── reason: string (for paused/offline)
```

#### `fcm_tokens` (Collection)
Store FCM tokens for admin devices so they can receive push notifications.

```
Collection: fcm_tokens
└── Document: {admin_device_id}
    ├── token: string (FCM token)
    ├── device_name: string (e.g., "admin-phone-1")
    ├── last_updated: timestamp
    └── is_active: boolean
```

---

### 2.2 Realtime Database (for Live Location Streaming)

Live location data is streamed to admins when the admin app is open and in foreground.

```
/live_locations/{device_id}
├── latitude: number
├── longitude: number
├── accuracy: number
├── battery_level: number
├── signal_strength: number
├── status: string ("online", "offline", "paused")
├── last_update_timestamp: timestamp
└── device_motion_status: string
```

Why RTDB instead of Firestore listeners for live data?
- Lower latency (true real-time)
- More efficient for high-frequency updates
- Scales better for active admin sessions

When admin closes app, live location listeners are removed and only Firestore is used for cached data.

---

## 3. Project Structure (Android Studio)

```
BeaconProject/
├── .gradle/
├── .idea/
├── build/
├── gradle/
│
├── app/
│   └── [Admin App Module]
│       ├── src/
│       │   └── main/
│       │       ├── AndroidManifest.xml
│       │       ├── java/com/beacon/admin/
│       │       │   ├── MainActivity.kt
│       │       │   ├── screens/
│       │       │   │   ├── DashboardScreen.kt
│       │       │   │   ├── MapScreen.kt
│       │       │   │   ├── DeviceListScreen.kt
│       │       │   │   ├── DeviceHistoryScreen.kt
│       │       │   │   ├── GroupManagementScreen.kt
│       │       │   │   ├── DeviceSettingsScreen.kt
│       │       │   │   ├── GlobalSettingsScreen.kt
│       │       │   │   ├── AddDeviceScreen.kt
│       │       │   │   └── AlertsScreen.kt
│       │       │   ├── services/
│       │       │   │   ├── FirebaseService.kt
│       │       │   │   ├── RealtimeLocationService.kt
│       │       │   │   ├── LocalCacheService.kt
│       │       │   │   ├── NotificationService.kt
│       │       │   │   └── AlertService.kt
│       │       │   ├── repository/
│       │       │   │   ├── DeviceRepository.kt
│       │       │   │   ├── LocationRepository.kt
│       │       │   │   ├── AlertRepository.kt
│       │       │   │   └── SettingsRepository.kt
│       │       │   ├── ui/
│       │       │   │   ├── components/
│       │       │   │   │   ├── DeviceCard.kt
│       │       │   │   │   ├── MapView.kt
│       │       │   │   │   ├── HistoryChart.kt
│       │       │   │   │   └── AlertBanner.kt
│       │       │   │   └── theme/
│       │       │   │       ├── Theme.kt
│       │       │   │       ├── Colors.kt
│       │       │   │       └── Icons.kt
│       │       │   ├── utils/
│       │       │   │   ├── Constants.kt
│       │       │   │   ├── LocationUtils.kt
│       │       │   │   ├── DateTimeUtils.kt
│       │       │   │   └── BatteryUtils.kt
│       │       │   ├── database/
│       │       │   │   ├── AppDatabase.kt
│       │       │   │   └── dao/
│       │       │   │       ├── LocalDeviceDao.kt
│       │       │   │       ├── LocalLocationDao.kt
│       │       │   │       └── LocalSettingsDao.kt
│       │       │   └── auth/
│       │       │       └── AuthManager.kt
│       │       └── res/
│       │           ├── drawable/
│       │           │   ├── ic_car.xml
│       │           │   ├── ic_phone.xml
│       │           │   ├── ic_person.xml
│       │           │   ├── ic_bike.xml
│       │           │   └── [other icons]
│       │           ├── layout/
│       │           └── values/
│       │               ├── strings.xml
│       │               ├── colors.xml
│       │               └── dimens.xml
│       └── build.gradle
│
├── tracker/
│   └── [Tracker App Module]
│       ├── src/
│       │   └── main/
│       │       ├── AndroidManifest.xml
│       │       ├── java/com/beacon/tracker/
│       │       │   ├── MainActivity.kt
│       │       │   ├── screens/
│       │       │   │   ├── OnboardingScreen.kt
│       │       │   │   ├── StatusScreen.kt
│       │       │   │   └── SettingsScreen.kt
│       │       │   ├── services/
│       │       │   │   ├── LocationTrackingService.kt (Foreground Service)
│       │       │   │   ├── LocationBroadcastReceiver.kt
│       │       │   │   ├── FirebaseTrackerService.kt
│       │       │   │   ├── LocalCacheService.kt
│       │       │   │   ├── BatteryMonitorService.kt
│       │       │   │   └── SignalStrengthMonitorService.kt
│       │       │   ├── repository/
│       │       │   │   ├── LocationRepository.kt
│       │       │   │   ├── DeviceRepository.kt
│       │       │   │   └── SettingsRepository.kt
│       │       │   ├── ui/
│       │       │   │   ├── components/
│       │       │   │   │   ├── StatusCard.kt
│       │       │   │   │   ├── LocationIndicator.kt
│       │       │   │   │   └── BatteryWidget.kt
│       │       │   │   └── theme/
│       │       │   │       ├── Theme.kt
│       │       │   │       └── Colors.kt
│       │       │   ├── utils/
│       │       │   │   ├── Constants.kt
│       │       │   │   ├── LocationUtils.kt
│       │       │   │   ├── NotificationUtils.kt
│       │       │   │   ├── DateTimeUtils.kt
│       │       │   │   └── BatteryUtils.kt
│       │       │   ├── database/
│       │       │   │   ├── AppDatabase.kt
│       │       │   │   └── dao/
│       │       │   │       └── LocationCacheDao.kt
│       │       │   └── auth/
│       │       │       └── DeviceAuthManager.kt
│       │       └── res/
│       │           ├── drawable/
│       │           ├── layout/
│       │           └── values/
│       │               ├── strings.xml
│       │               ├── colors.xml
│       │               └── dimens.xml
│       └── build.gradle
│
├── shared/
│   └── [Optional Shared Code Module]
│       └── src/main/java/com/beacon/shared/
│           ├── models/
│           │   ├── Device.kt
│           │   ├── Location.kt
│           │   ├── Alert.kt
│           │   └── Group.kt
│           ├── constants/
│           │   └── FirebaseConstants.kt
│           └── utils/
│               └── Validators.kt
│
├── build.gradle (root)
├── settings.gradle
├── gradle.properties
├── local.properties
└── README.md
```

---

## 4. Admin App: Screens & Flow

### 4.1 Screen Hierarchy

**Tab-based Navigation:**
1. **Dashboard Tab** → Overview + Quick Actions
2. **Map Tab** → Live/Map View + Device Pins
3. **Devices Tab** → Device List + Groups
4. **Alerts Tab** → Active & Historical Alerts
5. **Settings Tab** → Global & Per-Device Settings

### 4.2 Detailed Screen Descriptions

#### Dashboard Screen
- Quick summary of all devices (online, offline, paused)
- Recent alerts banner
- Active tracking indicator (live mode on/off toggle)
- Quick buttons: Add Device, Refresh All, Manual Sync
- Device status cards (battery %, signal strength, last seen)

#### Map Screen
- Google Maps showing all active devices as pins
- Each pin: Device icon, name, battery %, signal strength
- Live refresh when app is in foreground (every 5 seconds, configurable)
- Manual refresh button
- Tap device pin → Show device details popup
- Zoom & pan controls standard to Maps

#### Device List Screen
- Organized by groups (collapsible/expandable groups)
- Each device card shows:
  - Icon (from fixed set)
  - Device name / ID
  - Current location (last lat/lon if available)
  - Battery % + charging indicator
  - Signal strength bar
  - Last seen timestamp
  - Current status: online/offline/paused
  - Quick toggle: tracking enabled/disabled
- Long press → More options (edit settings, remove, history)
- Pull to refresh

#### Device History Screen
- Accessed from device card (long press → "View History")
- Timeline view of location updates
- Date range picker (start & end date)
- Show each location point with:
  - Timestamp
  - Coordinates
  - Accuracy
  - Battery level at that time
  - Signal strength at that time
  - Optional: Map view of the path
- No export button (as per requirements)

#### Group Management Screen
- List of groups with device count
- Add group button
- Each group card: Name, device count, color indicator
- Tap → Edit group name or delete
- Drag to reorder groups

#### Device Settings Screen (Per-Device)
- Device Name: Display only (no renaming allowed)
- Device ID: Display + Copy button
- Group: Dropdown to change group
- Icon: Dropdown to select from fixed icon set
- Tracking settings:
  - Tracking Interval: Slider (30s to 300s)
  - Pause Tracking button (toggle on/off)
  - Location Accuracy: Dropdown (high/medium/low)
- Notification settings:
  - Enable/Disable notifications for this device
  - Alert type toggles (low battery, offline, weak signal, etc.)
- Battery threshold: Input field (default from global setting)
- Signal threshold: Input field (default from global setting)
- Offline threshold: Input field in minutes
- Reset to defaults button

#### Global Settings Screen
- Default tracking interval slider
- Default battery threshold
- Default signal threshold
- Default offline threshold
- Map refresh rate
- History retention days (informational only, not editable in MVP)
- Theme: Light/Dark/System
- Clear local cache button (with confirmation)
- Remove all devices button (with confirmation)

#### Add Device Screen
- Instructions: "Enter the device ID or code from the tracker app"
- Text input field for device ID/code
- Validate button (check if device exists in Firebase)
- If valid: Select group, select icon, then confirm add
- If invalid: Show error message

#### Alerts Screen
- Two tabs: Active | History
- Active tab shows unresolved alerts with:
  - Alert type (low battery, offline, etc.)
  - Device name & icon
  - Severity badge (info/warning/critical)
  - Message
  - Time triggered
  - Dismiss button
- History tab shows resolved alerts, filterable by date range
- No export option

---

## 5. Tracker App: Screens & Flow

### 5.1 Screen Hierarchy

**Simple Linear Flow:**
1. **Onboarding Screen** (First time only)
2. **Status Screen** (Main app, always visible)
3. **Settings Screen** (Secondary)

### 5.2 Detailed Screen Descriptions

#### Onboarding Screen (First Launch Only)
- Title: "Welcome to Beacon Tracker"
- Instructions: "This app sends your location to the admin. Device ID: [auto-generated ID]"
- Show generated device ID + Copy button
- "Ready?" button → Requests permissions and starts tracking
- Permissions requested:
  - Location (always on)
  - Background location (Android 12+)
  - Notification (for foreground service notification)
  - Battery optimization exemption request

#### Status Screen (Main Screen)
- Large status indicator: "Tracking" (green) or "Not Tracking" (red)
- Current location (if available):
  - Latitude/Longitude
  - Accuracy in meters
  - Last update timestamp
- Device info:
  - Device ID (copyable)
  - Battery level (large display) + charging status
  - Signal strength bars
  - Connection type (WiFi/4G/5G/etc.)
- Tracking status details:
  - Current tracking interval (e.g., "Updating every 60s")
  - Next update countdown timer
  - "Paused" indicator (if tracking is paused by admin)
- Manual actions:
  - "Pause Tracking" / "Resume Tracking" button (toggles locally; syncs with admin)
  - "Force Update Now" button (send location immediately)
- Foreground service notification (always visible when tracking):
  - "Beacon Tracker is running"
  - Current status, battery %, last update time

#### Settings Screen
- Display-only info:
  - Device ID (copyable)
  - App version
  - OS version
- Local controls:
  - Tracking Interval: Slider or dropdown showing current interval (updated by admin in cloud)
  - Manual Pause/Resume toggle
  - "Force Update Now" button
  - Notification Settings: Show/Mute notifications
- Debug info (hidden behind toggle, for troubleshooting):
  - Last sync timestamp
  - Last location timestamp
  - Error log (if any)
  - "Force Resync" button
- Uninstall warning: "If you uninstall this app, the admin will no longer receive location updates."

---

## 6. Key Services & Components

### 6.1 Admin App Services

#### AuthManager
- Authenticate admin with email/password via Firebase Authentication
- Firebase Auth handles password security, hashing, storage (not in Firestore)
- Store auth token locally in SharedPreferences/EncryptedSharedPreferences
- Refresh token on app launch
- Handle session timeout (30 minutes of inactivity)
- Support silent sign-in (persistent session)

#### FirebaseService
- Firestore read/write operations (devices, alerts, groups, history)
- Listen to device collection for real-time updates
- Create/update/delete devices
- Fetch historical location data
- Write alerts to Firestore

#### RealtimeLocationService
- Listen to Realtime Database `/live_locations/{device_id}` when admin app is in foreground
- Remove listeners when app goes to background
- Update UI in real-time with live location data
- Fallback to Firestore if RTDB is unavailable

#### LocalCacheService
- Room database to store recent device data locally
- Cache strategy: Store last 24 hours of location + last device status
- Sync with Firebase on app launch and periodically
- Clear cache when user manually clears it

#### NotificationService
- Receive FCM push notifications for alerts
- Build and display notification based on alert type
- Handle notification tap → Open app to Alerts screen

#### AlertService
- Monitor Firestore for new alerts
- Determine alert severity based on type and threshold
- Trigger local notifications for critical alerts
- Manage alert state (resolved/unresolved)

---

### 6.2 Tracker App Services

#### LocationTrackingService (Foreground Service - Primary)
- Start foreground service on device boot (BootCompleted receiver)
- Start when tracker app first launched
- Runs with persistent notification ("Beacon Tracker is Running")
- Uses internal Handler/Timer to request location updates at configured interval (30–300s)
- Fetch location every N seconds from LocationManager / FusedLocationProvider
- Interval based on `per_device_settings.tracking_interval` from Firestore
- Check if tracking is paused (from cloud settings)
- When paused: Stop requesting location, keep service running, wait for resume signal
- When device not authorized (removed by admin): Enter idle state, minimal resource usage
- Collect battery level, signal strength, motion status with each location
- Pass location data to FirebaseTrackerService for cloud sync
- Pass data to local cache simultaneously
- Survive device reboots (persist via BootCompleted receiver)
- Continue running until: user stops via Settings, device unauthorized, or app uninstalled

#### LocationBroadcastReceiver
- Listen for location updates (broadcast from LocationTrackingService)
- Pass to FirebaseTrackerService for cloud sync
- Pass to local cache

#### FirebaseTrackerService
- Upload location data to Firestore:
  - Write to `/devices/{device_id}/location_history/{timestamp}`
  - Update parent `devices/{device_id}` with latest location, battery, signal
  - Update Realtime Database `/live_locations/{device_id}` for admin live view
- Handle Firebase write failures
- Queue failed uploads locally and retry later
- Sync with cloud every time location is updated (or batch if offline)

#### LocalCacheService
- Room database to store location updates if Firebase is unavailable
- Queue up to 1000 recent location points
- Sync to Firebase when connection is restored
- Auto-delete old cached data (keep only recent)

#### BatteryMonitorService
- Listen to battery change broadcasts
- Update battery level periodically to Firestore
- Send critical low battery alert if threshold reached

#### SignalStrengthMonitorService
- Listen to network signal changes
- Calculate signal strength percentage (0-100)
- Update to Firestore periodically
- Send weak signal alert if threshold reached

#### DeviceAuthManager
- Generate unique device_id on first launch (UUID)
- Generate unique device_secret on first launch (secure random string)
- Store both in EncryptedSharedPreferences (never in plain text)
- device_id is shared with admin during onboarding (admin enters it to pair)
- device_secret never leaves tracker device (not transmitted to admin)
- Include device_id + device_secret in all Firebase write operations for authentication
- Firestore Security Rules validate credentials server-side
- Gracefully handle auth errors (device removed/unauthorized)

---

## 7. Important Assumptions & Edge Cases

### Authentication & Security
- **Admin Authentication**: Email/password via Firebase Authentication (source of truth)
  - No password stored in Firestore
  - Firebase Auth handles session tokens, password reset, security
  - Admin document in Firestore is optional reference only
- **Tracker Device Authentication**: device_id + device_secret
  - device_id: Unique identifier, visible to admin during pairing
  - device_secret: Generated on first install, stored securely in EncryptedSharedPreferences on tracker device only
  - Never exposed to admin UI
  - Firestore Security Rules validate device_id + device_secret on server-side
  - Tracker cannot read other tracker data
  - Tracker cannot read admin settings or alerts

### Background Tracking Behavior
**Foreground Service (Primary Mechanism)**:
- Start foreground service on device boot (via BootCompleted receiver)
- Service runs indefinitely with persistent notification
- Uses timer/handler to request location updates at configured interval (30–300 seconds)
- No WorkManager for the primary tracking loop
- Service thread maintains continuous location collection

**WorkManager (Recovery Only)**:
- Used only if foreground service crashes or is killed
- WorkManager reschedules service restart
- Does NOT handle the continuous 30–300 second tracking interval
- Exponential backoff: 10s → 1min → 5min → stop retrying
- Max 5 retry attempts before logging and deferring

**Permissions Required**:
- `ACCESS_FINE_LOCATION` (GPS)
- `ACCESS_COARSE_LOCATION` (Network)
- `ACCESS_BACKGROUND_LOCATION` (Android 10+, required for tracking when app not in foreground)
- `POST_NOTIFICATIONS` (Android 13+, for foreground service notification)
- Battery optimization exemption request (optional but recommended)

**Tracking Behavior**:
- When tracking_enabled = true: Collect location every N seconds (configurable per device)
- When tracking_enabled = false (paused by admin): Stop requesting location, keep service running
- When device not authorized (removed by admin): Service enters idle state, minimal resource usage
- Foreground service notification always visible (cannot be swiped away)

### Data Sync & Offline Handling
- Tracker: If no internet, location updates are cached locally and synced when online
- Admin: Only caches recent data (last 24 hours); does not fetch all history on first load
- Admin app only shows "recent" data; to view older history, user must select date range in history screen

### Location Accuracy Levels
- "High": Request GPS only, wait for best accuracy
- "Medium": Allow GPS + Network location
- "Low": Network location only (faster, less accurate, saves battery)

### Alert Thresholds & Severity
- **Low Battery**: Default 15%, customizable per device; Severity = Warning
- **Weak Signal**: Default 20%, customizable per device; Severity = Warning
- **Device Offline**: If no update for X minutes (default 10 min); Severity = Critical
- **Tracking Paused**: When admin pauses tracking; Severity = Info
- **Stale Location**: If no location update for 30+ minutes; Severity = Warning

### Device Groups
- One level only (no nesting)
- Ungrouped devices: "Default" or "Ungrouped" group
- Can reassign device to different group anytime
- Deleting a group: automatically move devices to default group

### UI Theme & Consistency
- Light/Dark mode support
- All icons: Custom drawable sets + Material Icons for supplementary UI
- Device icons: Fixed set (car, phone, person, bike, package, etc.)
- No user-uploaded icons; reduces complexity and security concerns

### Notification Edge Cases
- Admin receives FCM notification for critical alerts even if app is closed
- Notification tap opens app to Alerts screen
- If admin has disabled notifications for a device, FCM is still sent but silently dismissed
- Notification permission is requested at first app launch

### Performance Considerations
- Max ~100 devices per admin (assumption based on requirements)
- Location history: Query in batches; don't load all 30 days at once
- Firestore compound indexes: Create indexes for common queries (device_id + timestamp, group_id, etc.)
- Realtime Database listeners: Only active when admin app is in foreground

---

## 8. Firebase Security Rules (Summary)

```
Firestore Rules:
- admin collection: Only admin user can read/write
- devices collection: Admin can read all, each tracker can write only to own doc
- devices/{device_id}/location_history: Each tracker writes only to own, admin reads all
- devices/{device_id}/alerts: Firebase Cloud Functions create alerts, admin reads, tracker cannot read
- devices_groups: Admin read/write only
- fcm_tokens: Admin writes own token, update only own token

Realtime Database Rules:
- /live_locations/{device_id}: Each tracker can write to own, admin can read all
- Structure: Only current live location, no history
```

---

## 9. Data Retention & Cleanup

- Location history: Kept for 30 days, then auto-deleted
- Alerts: Kept indefinitely (small size), can be archived or cleaned up manually
- FCM tokens: Updated on each admin app launch, stale tokens (>30 days) can be deleted
- Local cache: Cleared when user manually clears or when reaching size limit

---

## 10. Implementation Roadmap (High Level)

### Phase 1: Foundation
- Firebase project setup + Firestore/RTDB/Auth/FCM
- Shared data models (Device, Location, Alert, Group)
- Auth flow (admin login)

### Phase 2: Admin App - Core Screens
- Dashboard, Device List, Map view
- Local cache & Firestore integration
- Device detail & settings screens

### Phase 3: Admin App - Alerts & Notifications
- Alert generation logic
- FCM integration
- Alerts screen

### Phase 4: Tracker App - Core Tracking
- Onboarding & device registration
- LocationTrackingService (foreground service)
- Firebase upload

### Phase 5: Tracker App - Background & Monitoring
- Battery & signal monitoring
- Local cache for offline support
- Pause/resume tracking from admin

### Phase 6: Live Updates & Real-time
- Realtime Database integration
- Live map refresh in admin app
- Sync optimization

### Phase 7: Polish & Optimization
- UI refinement (light/dark theme)
- Performance tuning
- Testing & bug fixes
- APK build & sign

### Phase 8: Deployment Readiness
- Security hardening
- Crash analytics setup
- Performance monitoring
- Prepare for Google Play Store

---

## 11. Key Assumptions You Should Know

1. **One admin, single account**: No multi-admin support (can be added later)
2. **No user-generated content**: No notes, no custom device names → Reduces complexity
3. **Fixed icon set**: Simplifies UI, no image upload overhead
4. **Device limit ~100**: Scales well for Firestore queries
5. **Foreground service required**: Tracker app will show a persistent notification
6. **Google Play Store**: APKs must comply with location tracking policies (background location, privacy policy, etc.)
7. **Firebase project**: Shared by both apps; no data isolation between apps
8. **No encrypted communication**: Firebase handles encryption in transit + at rest
9. **Tracker device pairing**: Manual entry of device ID (not QR); simpler to implement, less error-prone

---

## 12. Final Design Decisions - User Confirmed

### 12.1 Offline Map Display (CONFIRMED)
**Requirement**: Admin should view cached device locations when offline.

**Implementation**:
1. Admin app caches last 24 hours of device positions locally (Room database)
2. When offline detected (no network connection):
   - Map displays all cached device locations as pins
   - Each pin shows last-known position with timestamp of cached data
   - Add banner at top of map: "📴 Offline Mode - Showing Cached Data"
3. Live refresh button disabled with tooltip: "Network unavailable"
4. Manual map interactions (pan, zoom) work normally
5. When connection restored:
   - Automatically fetch latest live data
   - Update map with current positions
   - Remove offline banner
6. Device cards on Device List screen also show cached data when offline with visual indicator

### 12.2 Alert Notifications (CONFIRMED)
**Requirement**: Sound + vibration for important alerts; less critical alerts silent.

**Critical Alerts** (Sound + Vibration + Visual):
- Device offline (status changed to offline)
- Low battery (below threshold)
- Tracking paused (by admin)
- Weak signal (below threshold)

**Non-Critical Alerts** (Silent + Visual Only):
- Stale location (no update for 30+ minutes)

**FCM Notification Behavior**:
- All alerts delivered via Firebase Cloud Messaging
- Critical alerts: Play system notification sound + 200ms vibration + LED flash (if device supports)
- Non-critical alerts: No sound/vibration, visual notification only
- Notification tap always opens app to Alerts screen
- Notification can be dismissed without opening app
- If admin has disabled notifications for a specific device, alert still recorded but notification silently dismissed

### 12.3 Device Removal - Graceful Failure (CONFIRMED)
**Requirement**: Tracker fails gracefully when removed, stops retrying.

**When Admin Removes Device**:
1. Admin deletes device from Firebase
2. Tracker app next sync attempt gets Firebase auth error (unauthorized)
3. Tracker detects unauthorized status
4. Tracker logs error once and reduces retry frequency significantly
5. Tracker UI displays: "⚠️ Device Not Authorized"
6. Service continues running in idle state (minimal CPU/battery usage)
7. No aggressive retrying loops, no spam of failed requests to Firebase

**Tracker App State After Removal**:
- Location tracking stops
- Battery/signal monitoring pauses
- Settings screen shows: "Device not authorized. Reinstall app to re-pair with new ID."
- Service stays active (low resource) in case admin re-adds within same session
- After 1 hour of idle state, service can gracefully stop

**When Tracker is Re-Paired**:
1. User uninstalls and reinstalls tracker app
2. New unique device_id generated
3. Onboarding screen shown again
4. Admin adds tracker using new device ID
5. Full authorization re-established

### 12.4 Location Permission (CONFIRMED)
**Requirement**: Request "Allow All The Time" for continuous background tracking.

**Permission Strategy**:
- On tracker app first launch (onboarding), request permissions in order:
  1. `ACCESS_FINE_LOCATION` (GPS)
  2. `ACCESS_COARSE_LOCATION` (Network)
  3. `ACCESS_BACKGROUND_LOCATION` (Android 10+) - Request "Allow all the time"
  4. `POST_NOTIFICATIONS` (Android 13+) - For foreground service notification
  5. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (Intent to settings)

**Rationale**:
- "Allow All The Time" required for background location tracking when app is minimized/closed
- Enables continuous location collection via WorkManager even if screen off
- Required for foreground service to function properly
- No degraded tracking if permission denied (app simply won't start tracking)

**Foreground Service Notification** (Always Required):
- Persistent notification while tracker running
- "Beacon Tracker is Running"
- Shows current status, battery %, last update time
- Cannot be swiped away (system requirement for foreground service)
- Notification tap opens tracker app to Status screen

### 12.5 Tracker Uninstall (CONFIRMED)
**Requirement**: Keep it simple—no remote uninstall capability.

**Implementation**:
- Admin dashboard does NOT have "remotely uninstall tracker" button
- Only manual removal: admin deletes device from Firebase (covered in 12.3)
- Tracker app uninstalled manually on device or via Android Settings
- When tracker uninstalled, admin sees device as "offline" after offline threshold
- Admin can manually remove device from admin app

**Tracker App Behavior**:
- Settings screen has warning: "⚠️ If you uninstall this app, the admin will no longer receive location updates."
- No "Uninstall Device" button in tracker app settings
- Simple, clean approach—no complex remote management logic

---

## 13. Admin App - Offline & Connectivity Behavior

**Network Detection**:
- Use ConnectivityManager to detect network changes
- Show connectivity status in UI
- Handle transitions gracefully

**When Online**:
- Sync with Firestore automatically
- Listen to Realtime Database for live location updates
- Update local cache continuously
- Show "Live" indicator on UI

**When Offline**:
- Cease Realtime Database listeners
- Stop live refresh polling
- Show "Offline" indicator in banner
- Display cached data with timestamps
- Allow manual map interactions
- Queue any admin actions (settings changes) locally
- Sync queued changes when online again

**Sync Strategy**:
- Full sync on app launch (fetch recent devices, settings, alerts)
- Incremental sync every 5 minutes if online
- Manual sync button available (pull-to-refresh)
- Aggressive sync when network state changes from offline to online

---

## 14. Tracker App - Background Service Resilience

**WorkManager Configuration** (Recovery Only):
- Do NOT use for the primary 30–300 second tracking loop
- Use only if foreground service crashes or is killed by OS
- Schedule a job to restart LocationTrackingService if it dies
- Exponential backoff: 10s → 1min → 5min → stop retrying
- Max 5 retry attempts before logging error
- Once foreground service is running, WorkManager job completes

**Foreground Service**:
- Start on device boot (via BootCompleted receiver)
- Start when tracker app first launched
- Continue running indefinitely until:
  - User manually stops (via Settings button)
  - Device not authorized (graceful idle state)
  - App explicitly uninstalled
- Notification always visible with current status

**Battery & Signal Monitoring**:
- Battery changes broadcast listener (update every 1% change or max 5 min)
- Signal strength sampled every 10 seconds
- Updates to Firebase batched with location uploads
- No standalone Firebase writes for battery/signal (piggyback on location sync)

---

## 15. Admin App - Device Cache & Sync Limits

**Local Cache (Room Database)**:
- Store last 24 hours of device locations only
- Store latest device status (battery, signal, last_seen, tracking_enabled, etc.)
- Store latest alert for each device
- Cache limit: 1000 location entries per device max
- Auto-purge entries older than 24 hours

**Firestore Queries**:
- On app launch: Fetch all devices + latest settings
- On demand (history screen): Query location_history for selected date range in batches (100 entries per query)
- Listen to device collection for changes (device added, settings updated, status changed)
- Do NOT fetch all 30 days of history by default
- Pagination for large result sets

**Realtime Database Usage**:
- Live location listeners active ONLY when admin app in foreground
- Remove listeners when app goes to background
- Reconnect listeners when app returns to foreground
- 5-second update interval (configurable in global settings)

---

## 16. Alert Generation & Management

**Alert Types & Triggers** (In Firestore):
- **Low Battery**: battery_level < per_device_threshold (default 15%)
- **Weak Signal**: signal_strength < per_device_threshold (default 20%)
- **Device Offline**: no update received for X minutes (default 10 min)
- **Tracking Paused**: tracking_enabled = false set by admin
- **Stale Location**: last_location.timestamp > 30 minutes ago

**Alert Lifecycle**:
1. Tracker detects condition → creates alert in Firestore `/devices/{device_id}/alerts/{alert_id}`
2. Firebase Cloud Function detects new alert → sends FCM to admin
3. Admin receives notification → alert shown in UI
4. Admin marks resolved or auto-resolve after 24 hours
5. Historical alerts retained for reference

**Alert Severity Mapping**:
- INFO: Tracking paused
- WARNING: Low battery, weak signal, stale location
- CRITICAL: Device offline

---

## 17. Security & Privacy Considerations

**Admin Authentication**:
- Email/password via Firebase Auth
- Session tokens stored securely in SharedPreferences/EncryptedSharedPreferences
- Auto-logout after 30 minutes of inactivity
- Biometric unlock optional (future enhancement)

**Tracker Device Authentication**:
- Each device: unique device_id + device_secret (stored locally, never transmitted to admin)
- device_id + device_secret validated server-side in Firestore Security Rules
- Tracker cannot read other tracker data
- Tracker cannot write to other device documents

**Data Privacy**:
- Location data stored only in Firebase (encrypted at rest)
- No third-party analytics on location data
- Location history auto-deleted after 30 days
- Local cache cleared on app uninstall (Room database)
- FCM tokens stored but not used for tracking, only for alerts

---

## 19. Single Firebase Project Architecture

**One Project, Two Apps**:
- Admin app and Tracker app both authenticate with same Firebase project
- Firestore as single source of truth for all data
- Realtime Database for live location streaming only
- Firebase Auth handles admin authentication
- Firebase Cloud Messaging delivers alerts to admin
- Security Rules isolate data: admin reads all, trackers write only to own documents

**Data Flow**:
1. Tracker app collects location → Sends to Firestore + Realtime DB
2. Admin app listens to Firestore for changes + RTDB for live updates
3. Alerts created in Firestore → FCM notification sent to admin
4. Admin changes settings → Written to Firestore → Tracker reads and applies
5. Tracker removed from Firestore → Tracker detects auth error → Enters idle state

**Benefits of Single Project**:
- Simplified backend management
- Shared authentication context
- Real-time data sync between apps
- Lower operational complexity
- No data duplication across projects

---

## 20. Simplified & Implementation-Ready Architecture

**Core Principles**:
- **Foreground Service is Primary**: Handler-based timer for location collection, not WorkManager
- **Firebase Auth for Admin**: No password hashing in Firestore
- **Device Secret Protected**: Stored locally in EncryptedSharedPreferences, never exposed
- **Clear Data Model**: Firestore for persistence, RTDB for live streaming
- **No Over-Engineering**: Only features required by spec, sensible defaults for everything else
- **Production-Ready**: Security rules, error handling, offline support, graceful degradation

**Architectural Simplification**:
- Remove unused complexity from original design
- Direct service communication (no unnecessary intermediaries)
- Batch operations where possible (don't create separate Firebase writes for battery, signal)
- Reuse room database for both admin cache and tracker offline queue
- Clear separation: Tracker = collect/upload, Admin = receive/display

---

## 21. Implementation Checklist Before Code Generation

**Firebase Setup**:
- [ ] Firebase project created in Google Console
- [ ] Firestore database initialized (US region, production mode)
- [ ] Realtime Database initialized (US region)
- [ ] Firebase Auth enabled (email/password provider)
- [ ] Firebase Cloud Messaging enabled
- [ ] Security Rules written and deployed

**Android Project Structure**:
- [ ] Root project with two modules: `admin` and `tracker`
- [ ] Optional shared module for models & constants
- [ ] Gradle dependencies finalized (Firebase SDK, Google Maps, WorkManager, Room, Jetpack Compose, etc.)

**Assets & Resources**:
- [ ] Device icons (fixed set: car, phone, person, bike, package, briefcase, etc.) as drawable XML
- [ ] App theme colors, dimens, strings defined

**Code Architecture**:
- [ ] Data models (Device, Location, Alert, Group) defined in Kotlin
- [ ] Room database schema designed
- [ ] Firestore collection structure finalized
- [ ] Security Rules drafted
- [ ] API constants and endpoints documented

---

**Status**: ✅ **ARCHITECTURE COMPLETE, CORRECTED & IMPLEMENTATION-READY**

**Corrections Applied**:
1. ✅ Firebase Auth for admin (no password_hash in Firestore)
2. ✅ Single Firebase project for both apps
3. ✅ Firestore for data, RTDB for live streaming only
4. ✅ Foreground Service as primary tracking mechanism (Handler-based, not WorkManager primary)
5. ✅ Device secret protected in EncryptedSharedPreferences (not exposed)
6. ✅ Simplified, implementation-ready architecture
7. ✅ All existing features preserved

**Next Step**: Awaiting user approval to proceed with code generation.
