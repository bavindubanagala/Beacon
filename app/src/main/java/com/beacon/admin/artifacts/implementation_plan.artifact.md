# UI Refinements and Map Filter Customization

## User Review Required

- No breaking design decisions.

## Proposed Changes

### MapScreen
- Customize map filters based on whether a device is selected (`initialDeviceId != null`).
- Reduce top bar height.

### DeviceListScreen (and overall UI)
- Reduce bottom navigation bar height.

### Device/Map Interaction
- Add "Refresh" functionality to the new slide-up bottom sheet for device info.
- Verify connection/status sync logic.

---

## Verification Plan

### Manual Verification
- Verify top bar and bottom bar height changes.
- Verify that map filters are hidden when viewing a specific device's map.
- Verify the manual refresh button triggers a data update.
- Double-check the status (online/offline) sync.

### Automated Tests
- N/A
