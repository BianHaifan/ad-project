# Community Checkpoint B Android Emulator Preparation

- Replaced the pre-existing Debug-only global `usesCleartextTraffic="true"` overlay with a Debug-only network-security configuration.
- Cleartext HTTP is permitted only for `10.0.2.2`, the Android Emulator alias for the local Windows host.
- This allows the Debug build to use its existing `http://10.0.2.2:8081/api/v1/` backend address.
- Main and Release manifests are unchanged. There is no global cleartext opt-in and no physical-device HTTP support.
- This configuration is only for local Emulator Checkpoint B hand testing; production/release networking remains unchanged.
