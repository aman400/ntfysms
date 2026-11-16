# NtfySMS — Personal OTP SMS & Notification Forwarder

NtfySMS is an Android application built in Kotlin that captures incoming OTP SMS messages (and app push notifications), extracts verification codes using regex, and forwards them to **ntfy** or **Gotify** servers.

It is designed for personal use, home lab owners, and self-hosters running servers behind private VPNs (like Tailscale or WireGuard) or requiring HTTP healthcheck pings.

---

## Key Features

- **ntfy & Gotify Destinations**: Support for public ntfy.sh servers, self-hosted ntfy instances, and Gotify push servers.
- **Node-Level Pre-Checks**: Configure per-node requirements for active Internet, Tailscale/WireGuard VPN connection, or custom HTTP healthchecks (`GET`/`HEAD`/`POST`, headers, expected HTTP status).
- **Dynamic Auto-Execution**: Unsent messages stay queued and auto-execute as soon as pre-conditions are met.
- **Regex Extraction & Custom Templates**: Extract variables `${code}`, `${otp}`, `${group1}` and format custom payload templates (e.g. `[${sender}] OTP Code: ${code}`).
- **Zero OTP Local Storage**: Message text and verification codes are permanently deleted from local storage immediately upon successful delivery or expiry.
- **Real-Time Status Notifications & QS Tile**: Foreground status notifications (`⏳ Waiting on Pre-Condition`, `🚀 Executing`, `✅ Delivered`) and Quick Settings shade tile.
- **Privacy & Security**: Encrypted token storage (`EncryptedSharedPreferences`), `FLAG_SECURE` screen security, and opt-in private HTTP for Tailscale.

---

## Complete User Documentation & Release Guides

- **📖 [Full User & Setup Manual](DOCUMENTATION.md)** — Comprehensive guide covering ntfy & Gotify setup, Tailscale VPN integration, regex extraction, and OEM battery settings.
- **📦 [Signed Release & GitHub Publishing Guide](RELEASE_GUIDE.md)** — Guide on building signed release APKs (`NtfySMS-v1.0.0.apk`) and setting up GitHub Actions.
- **🌐 [GitHub Pages Landing Page](https://aman400.github.io/ntfysms)** — Web documentation and APK download showcase.

---

## Quick Build & Installation

### 1. Build Signed Release APK
```bash
./gradlew assembleRelease
```
The optimized release binary is generated at:
`app/build/outputs/apk/release/NtfySMS-v1.0.0.apk`

### 2. Install via ADB
```bash
adb install app/build/outputs/apk/release/NtfySMS-v1.0.0.apk
```

---

## Testing with ADB Emulator

Simulate incoming SMS messages on Android Studio Emulator:

```bash
adb emu sms send VM-HDFCBK "Your OTP for transaction of Rs 500 is 482910."
```

---

## License

Released under the [MIT License](LICENSE).
