# NtfySMS - Personal OTP SMS Forwarder

NtfySMS is a personal-use Android application built in Kotlin that captures incoming OTP SMS messages matching user-defined allowlists and keyword filters, and forwards them to a self-hosted or public [ntfy](https://ntfy.sh) server.

## Features & Architecture
- **Tech Stack**: Kotlin, Jetpack Compose (Material 3), minSdk 26, targetSdk 34.
- **Queue & Storage**: Room database for local message queuing. Messages and OTP codes are deleted immediately upon successful delivery or expiry.
- **Retries**: Immediate retries (every 15s for ~1 minute) followed by WorkManager exponential backoff retries with network connectivity constraints.
- **Network Flush**: Automatic queue flush triggered when network connectivity changes (e.g., when Tailscale reconnects).
- **Security & Hardening**:
  - Encrypted storage for access tokens using `EncryptedSharedPreferences` (`androidx.security.crypto`).
  - Screen security: Screenshots and screen recordings disabled on the settings screen (`FLAG_SECURE`).
  - Strict URL Security: Enforces HTTPS by default. HTTP is blocked unless targeting `localhost` or explicitly enabling the private IP / Tailscale opt-in setting.
  - Zero OTP Logging: OTP message text and access tokens are never logged to Logcat, saved in system logs, or shown in event status lists.
  - Backup Protection: App database and preferences excluded from cloud backup and device transfer (`allowBackup="false"`).
- **Foreground Service**: Persistent low-priority notification while forwarding is enabled to prevent Android OS from killing the process.

---

## Installation & Sideloading

### 1. Build the APK
Clone the repository and build the debug or release APK using Gradle:
```bash
./gradlew assembleDebug
```
The generated APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Install via ADB
Connect your Android device or start an emulator, then install the APK:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Configuration & Setup

1. **Launch the App**: Open **NtfySMS**.
2. **Grant Permissions**:
   - Tap **Grant RECEIVE_SMS Permission** when prompted.
   - Tap **Grant POST_NOTIFICATIONS Permission** (Android 13+).
3. **Configure Delivery Provider & Connection**:
   - **Service Provider**: Select **ntfy**, **Gotify**, or **Custom Webhook (JSON)**.
   - **Server URL**: Enter your server URL (e.g., `https://ntfy.sh`, `https://gotify.example.com`, or `https://api.example.com/webhook`).
   - **Topic / Path**: Enter your target topic or webhook endpoint path.
   - **Token**: Enter your Bearer token or Gotify App Key (`X-Gotify-Key`) stored encrypted.
   - **Allow Insecure Private HTTP**: Enable ONLY if connecting to a self-hosted server over Tailscale (`100.x.x.x`) or private LAN (`192.168.x.x` / `10.x.x.x`).
4. **Define Filters**:
   - **Sender Allowlist**: Default is `.*` (matches all senders). Supports wildcard patterns separated by commas or newlines (e.g., `*HDFC*, *ICICI*, BANK-*`).
   - **Keyword Regex**: Default regex is `.*` (matches all message bodies). Can be customized (e.g., `(?i)(otp|one.?time|verification|code)`).
5. **Optional App Notification Forwarding**:
   - Enable **Forward App Notifications** to intercept incoming push notifications from banking apps, WhatsApp, or messaging apps matching your filter rules.
   - Tap **Grant Notification Access** to grant the `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` in system settings.
6. **Disable Battery Optimization**:
   - Tap **Request Ignore Battery Optimization** and allow NtfySMS to run unrestrained in the background.
7. **Enable Master Switch**: Toggle the top right switch to **ACTIVE**.

---

## Testing on Android Emulator

You can simulate incoming SMS messages using `adb emu sms send`:

### Command Format
```bash
adb emu sms send <sender> "<message_body>"
```

### Example 1: Matching SMS (Forwarded to ntfy)
```bash
adb emu sms send VM-HDFCBK "Your OTP for transaction is 654321"
```
*Expected Result*: Message matches allowlist pattern `*HDFC*` and keyword `OTP`. It is enqueued and posted to your ntfy topic. The event log displays `DELIVERED`.

### Example 2: Non-matching SMS (Ignored)
```bash
adb emu sms send SPAM-SENDER "Hello, check out our latest offers!"
```
*Expected Result*: Message fails allowlist filter and is ignored (not queued or posted).

### Test Button
You can also tap the **Send Test Message** button inside the app to post a harmless test payload directly to your ntfy topic.

---

## Known Limitations & Considerations

1. **OEM Battery Killers**: Aggressive battery optimization on OEMs (e.g., Xiaomi MIUI/HyperOS, Samsung One UI, Huawei EMUI, OnePlus OxygenOS) may still force-close background processes or delay SMS BroadcastReceivers despite battery optimization exemptions.
2. **Doze Mode Delays**: When the device enters deep Doze mode, background network requests scheduled via WorkManager might be deferred until the next maintenance window. Running the foreground service helps mitigate this.
3. **Carrier & RCS SMS Restrictions**: NtfySMS listens for standard SMS broadcasts (`android.provider.Telephony.SMS_RECEIVED`). Messages delivered via Google Messages RCS (Rich Communication Services) or OTT messaging apps bypass standard SMS broadcasts and cannot be intercepted.
