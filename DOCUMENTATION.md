# ZopHop — Complete User & Setup Manual

ZopHop is an Android application that captures incoming OTP SMS messages (and optional app push notifications), extracts verification codes, and forwards them to **ntfy** or **Gotify** servers.

It is built specifically for personal use, home lab owners, and self-hosters who need reliable forwarding to servers hosted locally or behind private networks (such as Tailscale or WireGuard).

---

## Table of Contents
1. [Overview & Flow Architecture](#1-overview--flow-architecture)
2. [First-Time App Setup & How to Use](#2-first-time-app-setup--how-to-use)
3. [Configuring ntfy & Gotify Servers](#3-configuring-ntfy--gotify-servers)
4. [Target Delivery Nodes & Node-Level Pre-Checks](#4-target-delivery-nodes--node-level-pre-checks)
5. [Filters, Regex Extraction & Payload Templates](#5-filters-regex-extraction--payload-templates)
6. [Status Notifications & Quick Settings Tile](#6-status-notifications--quick-settings-tile)
7. [Testing with ADB Emulator](#7-testing-with-adb-emulator)
8. [Troubleshooting & OEM Battery Settings](#8-troubleshooting--oem-battery-settings)
9. [Security, Privacy & Data Hardening Architecture](#9-security-privacy--data-hardening-architecture)

---

## 1. Overview & Flow Architecture

When an incoming SMS arrives on your phone:

1. **Broadcast Receiver (`SmsReceiver`)**: Intercepts the SMS PDU and extracts the sender header and message text.
2. **Filter Engine (`SmsFilter`)**: Checks if the sender matches your allowlist (e.g. `*HDFC*, *BANK*`) and if the text matches your keyword regex. Non-matching messages are dropped immediately.
3. **Variable Extractor (`MessageTemplateParser`)**: Runs your extraction regex to capture verification codes (`${code}`, `${otp}`, `${group1}`) and formats your custom payload template.
4. **Pre-Check Manager (`PreCheckManager`)**: Checks node requirements (Internet connection, active VPN, or custom HTTP healthcheck endpoint).
5. **Execution / Dynamic Queueing (`ForwardingManager`)**:
   - **Pre-checks pass**: Sends payload to your ntfy/Gotify server immediately. Upon successful delivery, the OTP text is purged from local storage.
   - **Pre-checks fail**: Marks the message status as `QUEUED` with a waiting note (e.g. `⏳ Waiting for Pre-Conditions: Tailscale Node (VPN missing)`). An active 10-second polling loop monitors connectivity and automatically dispatches the payload as soon as the pre-condition passes.

---

## 2. First-Time App Setup & How to Use

Follow these steps to get ZopHop up and running on your Android device:

### Step 1: Install & Launch
Download and install the signed APK (`ZopHop-v1.0.0.apk`) on your phone (Android 8.0 / API 26 or higher).

> **⚠️ Bypassing / Disabling Google Play Protect**:
> Because ZopHop requests sensitive SMS permissions (`RECEIVE_SMS`) and is self-published rather than distributed via the Play Store, Google Play Protect will flag sideloaded installations.
>
> **Method 1: Install Anyway (Recommended for One-Time Install)**
> 1. When the *"Blocked by Play Protect"* pop-up appears, tap **"More details"** (or the small dropdown arrow).
> 2. Tap **"Install anyway"**.
>
> **Method 2: Disable Play Protect Scanning**
> 1. Open the **Google Play Store** app.
> 2. Tap your **Profile Icon** (top right) → **Play Protect** → **Gear Icon (Settings)**.
> 3. Toggle OFF **Scan apps with Play Protect** and **Improve harmful app detection**.
>
> **Method 3: Disable Google Play Store App (For Dedicated Forwarder Devices)**
> On dedicated forwarding phones, you can disable the Play Store package completely via ADB to permanently stop Play Protect background prompts:
> ```bash
> adb shell pm disable-user --user 0 com.android.vending
> ```
> *(To re-enable Play Store later: `adb shell pm enable com.android.vending`)*

### Step 2: Grant Permissions
Open ZopHop and navigate to **Settings** (tab on the bottom right):
1. **SMS Permission (`RECEIVE_SMS`)**: Tap **Grant** so ZopHop can read incoming SMS messages.
2. **Notification Permission (`POST_NOTIFICATIONS`)**: Tap **Grant** (required on Android 13+) so ZopHop can show foreground status notifications.
3. **Ignore Battery Optimization**: Tap **Ignore** and exempt ZopHop from battery optimization so background forwarding isn't delayed.

### Step 3: Add a Delivery Target Node
1. Under **Delivery Target Nodes**, tap **+ Add Node**.
2. Enter a **Node Display Name** (e.g., `Home Tailscale ntfy`).
3. Select your provider (**🚀 ntfy** or **🔔 Gotify**).
4. Enter your **Server URL** and **Topic Name** (for ntfy) or **App Token** (for Gotify).
5. Configure pre-checks (e.g., enable **Require Active VPN** if using Tailscale).
6. Tap **Save Node**.

### Step 4: Configure Filters & Payload Format (Optional)
- **Sender Allowlist**: Leave as `.*` to match all senders, or enter comma-separated patterns (e.g., `*HDFC*, *BANK*`).
- **Custom Payload Template**: Customize how forwarded messages appear (e.g., `[${sender}] OTP: ${code}`). Use the **Live Parsing & Formatting Tester** box to verify output.

### Step 5: Activate Forwarding
Toggle the status switch in the top app bar to **ACTIVE**.
The notification shade will display `🟢 ZopHop Active`.

---

## 3. Configuring ntfy & Gotify Servers

### Option A: Setting Up ntfy

[ntfy](https://ntfy.sh) ([GitHub: binwiederhier/ntfy](https://github.com/binwiederhier/ntfy)) is an open-source HTTP-based pub-sub notification service.

#### 1. Using Public ntfy.sh Server (No Installation Needed)
If you don't run a home server, use the free public server `https://ntfy.sh`:
- **Server URL**: `https://ntfy.sh`
- **Topic**: Pick a long, unguessable topic string (e.g., `my-private-otp-channel-89102`).
- **Subscribe**: Open `https://ntfy.sh/my-private-otp-channel-89102` in your browser or install the official ntfy app from Google Play / F-Droid and add the topic.

#### 2. Docker Compose Deployment for ntfy
Create a `docker-compose.yml` file on your server:

```yaml
version: '3.8'

services:
  ntfy:
    image: binwiederhier/ntfy
    container_name: ntfy
    command:
      - serve      
    user: 1001:1001 # optional: replace with your own user/group or uid/gid
    volumes:
      - ./ntfy:/var/lib/ntfy
    environment:
      NTFY_BASE_URL: http://ntfy.example.com
      NTFY_CACHE_FILE: /var/lib/ntfy/cache.db
      NTFY_AUTH_FILE: /var/lib/ntfy/auth.db
      NTFY_AUTH_DEFAULT_ACCESS: deny-all
      NTFY_ATTACHMENT_CACHE_DIR: /var/lib/ntfy/attachments
    ports:
      - 80:80
    healthcheck: # optional: remember to adapt the host:port to your environment
      test: ["CMD-SHELL", "wget -q --tries=1 http://localhost:80/v1/health -O - | grep -Eo '\"healthy\"\\s*:\\s*true' || exit 1"]
      interval: 60s
      timeout: 10s
      retries: 3
      start_period: 40s
    restart: unless-stopped
    init: true # needed, if healthcheck is used. Prevents zombie processes
```

#### 3. Setting Up Write-Only & Read-Only ACL Topic Permissions
For optimal security, configure topic ACL permissions so ZopHop has **write-only** access, while your receiver devices have **read-only** access:

```bash
# 1. Create separate user accounts
ntfy user add publisher
ntfy user add receiver

# 2. Grant WRITE-ONLY permission on otp-* topics to ZopHop app
ntfy access publisher "otp-*" write-only

# 3. Grant READ-ONLY permission on otp-* topics to Receiver App
ntfy access receiver "otp-*" read-only

# 4. Generate Bearer Token for ZopHop
ntfy token add publisher
```

- **Server URL**: `http://192.168.1.50:8080` or `https://ntfy.home.arpa`
- **Topic**: `otp-myfeed`
- **Access Token**: Paste the generated `tk_...` token into the **Access Token** field in ZopHop.

---

### Option B: Setting Up Gotify

[Gotify](https://gotify.net) ([GitHub: gotify/server](https://github.com/gotify/server)) is a self-hosted push notification server.

#### 1. Deploy Gotify via Docker Compose
Create a `docker-compose.yml` for Gotify:

```yaml
version: '3.8'

services:
  gotify:
    image: gotify/server:latest
    container_name: gotify
    restart: unless-stopped
    environment:
      - GOTIFY_SERVER_PORT=80
      - GOTIFY_DATABASE_DIALECT=sqlite3
      - GOTIFY_DATABASE_CONNECTION=data/gotify.db
      - GOTIFY_PASSWORSTRENGTH=10
    volumes:
      - ./gotify_data:/app/data
    ports:
      - "8081:80"
```

#### 2. Generate an Application Token
1. Open Gotify Web UI in your browser (`http://192.168.1.50:8081` or `https://gotify.example.com`).
2. Log in with your admin account.
3. Click **Apps** in the top navigation bar → Click **Create Application**.
4. Enter name: `ZopHop` → Click **CREATE**.
5. Copy the generated **Token** (e.g., `A1b2C3d4E5f6`).

#### 3. Enter Token in ZopHop
In ZopHop, tap **+ Add Node** → Select **🔔 Gotify**:
- **Server URL**: `https://gotify.example.com`
- **App Token**: Paste the generated `X-Gotify-Key` token (`A1b2C3d4E5f6`).
- Tap **Save Node**.

---

### Server Security Considerations & Best Practices

1. **Reverse Proxy & Mandatory TLS/HTTPS**: Never expose raw HTTP ports to the open internet. Reverse proxy via Caddy, Nginx Proxy Manager, or Traefik to enforce TLS encryption.
2. **Private Network Binding (Tailscale / WireGuard)**: Bind ntfy/Gotify to private Tailscale (`100.x.x.x`) or local IP ranges (`192.168.x.x`), and configure ZopHop's **Require Active VPN** pre-check.
3. **Least-Privilege ACL Tokens**: Never use admin credentials for forwarding. Use write-only tokens for ZopHop and read-only tokens for receiver clients.
4. **Disable Anonymous Features**: Set `auth-default-access: "deny-all"` in ntfy to block unauthorized publishing.

---

## 4. Target Delivery Nodes & Node-Level Pre-Checks

ZopHop supports multiple target nodes simultaneously. You can add, edit, or toggle delivery nodes under **Settings → Delivery Target Nodes**.

### Node-Level Pre-Checks
Pre-checks pause forwarding when network conditions aren't met, preventing errors:

1. **Require Internet Connection**: Verifies active network capability (`NET_CAPABILITY_INTERNET`) before sending.
2. **Require Active VPN**: Checks for active VPN transport (`TRANSPORT_VPN`) or virtual interfaces (`tun0`, `ppp0`, `tailscale`, `wireguard`, `utun`). Pauses forwarding until VPN connects.
3. **Require Server Healthcheck Ping**: Performs a pre-flight HTTP request to verify server reachability before sending the actual payload.

### Custom HTTP Healthcheck Parameters
- **HTTP Method**: `GET`, `HEAD`, or `POST`.
- **Healthcheck URL**: Specific status endpoint (e.g., `https://my-server.com/healthz`). Defaults to `<server_url>/health` if left blank.
- **Expected Status Code**: Expected HTTP response status (default `200`).
- **Custom HTTP Headers**: Headers required by reverse proxies or auth firewalls (e.g., `X-Health-Key: secret123`).

---

## 5. Filters, Regex Extraction & Payload Templates

### Sender Allowlist
Controls which senders trigger forwarding.
- **Default**: `.*` (matches all senders).
- **Wildcard Format**: Comma-separated or newline-separated patterns (e.g. `*HDFC*, *ICICI*, BANK-*`).

### Keyword Regex
Ensures only relevant SMS messages (like OTPs or alerts) are processed.
- **Default**: `.*` (matches all text).
- **Recommended OTP Regex**: `(?i)(otp|code|verification|one.?time|login|password)`

### Body Extraction Regex
Extracts numbers or codes from the SMS body using capture groups.
- **Default**: `\b\d{4,8}\b` (finds any 4 to 8 digit sequence).
- **Custom Capture Groups**: Regular expression capture groups like `(\d{6})` map to `${group1}` or `${code}`.

### Custom Payload Format Templates
- **Default**: `${body}`
- **Available Variables**:
  - `${sender}` — Originating sender ID (e.g. `VM-HDFCBK`)
  - `${body}` — Full SMS body text
  - `${code}` or `${otp}` — Extracted verification code
  - `${group1}`, `${group2}` — Capture group values
- **Template Example**: `[${sender}] Verification Code: ${code}`

---

### Real-World Indian Bank & Service SMS Examples

Below are tested regex patterns and payload templates for major Indian banks and online services:

| Bank / Service | Typical Sender Header | Sample Incoming SMS Text | Allowlist Pattern | Extraction Regex | Extracted `${code}` | Output Payload (`[${sender}] OTP: ${code}`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **HDFC Bank** | `VM-HDFCBK`, `AD-HDFCBK` | `482910 is your OTP for HDFC Bank card transaction of Rs 1,250.00 at Amazon.` | `*HDFCBK*` | `\b\d{6}\b` | `482910` | `[VM-HDFCBK] OTP: 482910` |
| **ICICI Bank** | `AX-ICICIB`, `VM-ICICIB` | `OTP for transaction of Rs 850.00 at Swiggy is 918234. Do not share with anyone.` | `*ICICIB*` | `\b\d{6}\b` | `918234` | `[AX-ICICIB] OTP: 918234` |
| **State Bank of India (SBI)** | `AD-SBINBK`, `VM-SBINBK` | `771029 is the OTP for your SBI netbanking login. Valid for 10 mins.` | `*SBINBK*` | `\b\d{6}\b` | `771029` | `[AD-SBINBK] OTP: 771029` |
| **Axis Bank** | `VK-AXISBK`, `AD-AXISBK` | `Use OTP 603912 to authorize Rs 3,400.00 on Axis Bank Credit Card.` | `*AXISBK*` | `\b\d{6}\b` | `603912` | `[VK-AXISBK] OTP: 603912` |
| **Kotak Mahindra** | `VM-KOTAKB`, `AD-KOTAKB` | `Your One Time Password (OTP) for Kotak Bank transaction is 829103.` | `*KOTAKB*` | `\b\d{6}\b` | `829103` | `[VM-KOTAKB] OTP: 829103` |
| **Amazon / Flipkart** | `AD-AMAZON`, `VM-FLPKRT` | `492018 is your Amazon verification code.` | `*AMAZON*, *FLPKRT*` | `\b\d{6}\b` | `492018` | `[AD-AMAZON] OTP: 492018` |
| **Google** | `Google` | `G-884920 is your Google verification code.` | `Google` | `G-(\d{6})` | `884920` | `[Google] OTP: 884920` |

#### Recommended Configuration Presets
- **Multi-Bank Sender Allowlist**:
  ```text
  *HDFCBK*, *ICICIB*, *SBINBK*, *AXISBK*, *KOTAKB*, *PNBSMS*, *BOBTXT*, *YESBNK*, *AMAZON*, *FLPKRT*
  ```
- **Generic Banking Keyword Regex**:
  ```regex
  (?i)(otp|one.?time|verification.?code|code|login|authorize|transaction)
  ```
- **OTP Extraction Regex**:
  ```regex
  \b\d{4,8}\b
  ```

---

## 6. Status Notifications & Quick Settings Tile

### Persistent Status Notifications
ZopHop runs a low-priority foreground service (`SmsForwarderService`) to ensure Android does not terminate background monitoring:

- 🟢 **ZopHop Active**: Normal background monitoring state.
- ⏳ **Waiting on Pre-Condition**: Trigger paused (e.g., `SMS from VM-HDFCBK: Waiting for Tailscale Target (VPN missing)`).
- 🚀 **Executing Forwarding Trigger**: Transmitting payload to target nodes.
- ✅ **Forwarding Trigger Executed**: Delivery completed successfully.

### Quick Settings (QS) Tile
Add the **ZopHop Forwarder** tile to your Android Quick Settings shade to toggle SMS forwarding ON or OFF from anywhere.

---

## 7. Testing with ADB Emulator

Simulate incoming SMS messages on Android Studio Emulator:

```bash
adb emu sms send <sender> "<message_body>"
```

### Command Examples

**1. Banking OTP:**
```bash
adb emu sms send VM-HDFCBK "Your OTP for transaction of Rs 500 is 482910."
```

**2. Custom Verification Code:**
```bash
adb emu sms send Google "Your Google verification code is 884920."
```

---

## 8. Troubleshooting & OEM Battery Settings

Aggressive OEM background managers (such as Xiaomi MIUI/HyperOS, Samsung One UI, OnePlus OxygenOS, and Vivo Funtouch) can terminate background processes or delay SMS BroadcastReceivers when the phone screen is off.

Follow the step-by-step instructions below for your specific device manufacturer to ensure 100% reliable background SMS forwarding:

---

### 📱 Xiaomi / Redmi / POCO (MIUI & HyperOS)

1. **Enable Autostart Permission**:
   - Open **Security App** → **Permissions** → **Autostart** → Locate **ZopHop** → Toggle **ON**.
2. **Set Battery Saver to No Restrictions**:
   - Open **Settings** → **Apps** → **Manage Apps** → Select **ZopHop**.
   - Tap **Battery Saver** → Select **No Restrictions**.
3. **Lock App in Recent Apps Screen**:
   - Open the Recent Apps screen → Press & hold the **ZopHop** window card → Tap the **Lock Icon** 🔒.

---

### 📱 Samsung (One UI)

1. **Add to Never Sleeping Apps**:
   - Open **Settings** → **Battery and Device Care** → **Battery** → **Background Usage Limits**.
   - Tap **Never Sleeping Apps** → Tap **+ (Add)** → Select **ZopHop** → Tap **Add**.
2. **Disable Auto-Optimization Restarts**:
   - Open **Settings** → **Device Care** → **Auto Optimization** → Turn **OFF** *Auto Restart*.

---

### 📱 OnePlus / Oppo / Realme (OxygenOS & ColorOS)

1. **Allow Background Activity & Auto Launch**:
   - Open **Settings** → **Apps** → **App Management** → Select **ZopHop**.
   - Tap **Battery Usage** → Enable **Allow Background Activity** AND **Allow Auto Launch**.
2. **Set Battery Optimization to Don't Optimize**:
   - Open **Settings** → **Battery** → **Advanced Settings** → **Optimize Battery Use**.
   - Locate **ZopHop** → Change selection to **Don't Optimize**.

---

### 📱 Vivo / iQOO (Funtouch OS & OriginOS)

1. **Allow High Background Power Consumption**:
   - Open **Settings** → **Battery** → **Background Power Consumption Management**.
   - Locate **ZopHop** → Select **High Background Power Consumption**.
2. **Enable Autostart**:
   - Open **Settings** → **Apps** → **Autostart** → Toggle **ON** for **ZopHop**.

---

### 📱 Google Pixel / Motorola / Nokia (Stock Android)

1. **Set App Battery Usage to Unrestricted**:
   - Open **Settings** → **Apps** → **See All Apps** → Select **ZopHop**.
   - Tap **App Battery Usage** (or *Battery*) → Change selection from *Optimized* to **Unrestricted**.

---

### ℹ️ Important Notes

- **SMS vs Carrier RCS Messaging**:
  Standard Android SMS broadcasts (`android.provider.Telephony.SMS_RECEIVED`) capture standard cellular SMS and MMS messages. Messages delivered via Google Messages RCS (Rich Communication Services) or OTT chat apps bypass standard SMS broadcasts and cannot be intercepted by broadcast receivers.
- **Reference Guide**: For additional OEM background task troubleshooting, visit [dontkillmyapp.com](https://dontkillmyapp.com).

---

## 9. Security, Privacy & Data Hardening Architecture

ZopHop is engineered with strict local security and privacy protections:

1. **Encrypted Token Storage (`EncryptedSharedPreferences`)**:
   - All server access tokens, Bearer keys, and Gotify keys are encrypted at rest using AES-256 GCM authenticated encryption, backed by hardware keys in the Android KeyStore (`MasterKey`).
2. **Screen Security (`FLAG_SECURE`)**:
   - Screenshots, screen recordings, and task-switcher previews are disabled on application screens in release builds to protect displayed tokens and verification codes.
3. **Zero Local OTP Persistence**:
   - Verification code numbers and full message bodies are permanently purged from the local SQLite database (`Room`) immediately upon successful delivery or message expiry.
4. **Cloud Backup & Device Transfer Exclusion (`allowBackup="false"`)**:
   - Android cloud backup (`Google Drive`) and device-to-device transfer rules (`data_extraction_rules.xml` and `backup_rules.xml`) explicitly exclude the app database and preferences to prevent sensitive tokens from leaving the physical device.
5. **No Analytics or Telemetry**:
   - ZopHop contains zero third-party analytics, crash reporting SDKs, tracking libraries, or external ads. Outgoing network requests are made strictly to your configured target delivery nodes.
