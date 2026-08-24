# Beacon Project Codebase Audit Report

## 1. Project & Module Structure
- **Modules**:
    - `:app`: Main "Admin" application for device management.
    - `:tracker`: Companion application for tracking.
    - `:shared`: Shared logic, models, and constants used by both apps.
- **SDKs & Dependencies**:
    - **Compile/Target SDK**: 35
    - **Min SDK**: 26
    - **Key Dependencies**: Firebase (Auth, Firestore, Database, Messaging, Analytics), Jetpack Compose (BOM 2024.09.00), Room, Coroutines, Retrofit, WorkManager, Google Maps (OSMDroid), Play Services Location.

## 2. Location Tracking & Background Architecture
- **Admin App**: `LocationForegroundService.kt` and the new `LocationService.kt` handle background location tracking using `FusedLocationProviderClient`.
- **Tracker App**: Uses `LocationTrackingService.kt`.
- **Requirements**: Foreground Service types are configured in `AndroidManifest.xml` (using `location` and `specialUse` for SOS).

## 3. Data, Storage & Cloud Synchronization Pipeline
- **Persistence**: Room database is used in both modules (`LocationDatabase.kt`, `AppDatabase.kt`).
- **Cloud Sync**: Firebase Firestore and Realtime Database act as the primary sync backends.
- **Queueing**: `WorkManager` is present and utilized in the app module.

## 4. Feature Capabilities
- **Geofencing**: Managed via `FenceRepository.kt`.
- **SOS**: Managed by `AdminSosService.kt`.
- **Authentication**: Managed via `AuthManager`.
- **Permissions**: Handled via standard Android runtime requests in Compose (see `MainScreen.kt` and `MainActivity.kt`).

## 5. State Management & UI Layer
- **State Management**: Heavily uses `MutableStateFlow` and `StateFlow` within ViewModels (`MainViewModel.kt`).
- **UI**: Built with Jetpack Compose. Navigation uses `NavHost`. Screens include `AuthScreen`, `MainScreen`, `HomeScreen`, `MapScreen`, `AlertsScreen`, etc.

## 6. Incomplete Logic & Technical Debt
- **TODOs**: Identified multiple TODOs related to offline queueing, security rules, and troubleshooting features.
- **Stale Files**: Some potentially unused service/repository files may need cleanup.
