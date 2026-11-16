# NtfySMS — Complete User & Setup Manual

NtfySMS is an Android application that captures incoming OTP SMS messages (and optional app push notifications), extracts verification codes, and forwards them to **ntfy** or **Gotify** servers.

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

Follow these steps to get NtfySMS up and running on your Android device:

### Step 1: Install & Launch
Download and install the signed APK (`NtfySMS-v1.0.0.apk`) on your phone (Android 8.0 / API 26 or higher).

### Step 2: Grant Permissions
Open NtfySMS and navigate to **Settings** (tab on the bottom right):
1. **SMS Permission (`RECEIVE_SMS`)**: Tap **Grant** so NtfySMS can read incoming SMS messages.
2. **Notification Permission (`POST_NOTIFICATIONS`)**: Tap **Grant** (required on Android 13+) so NtfySMS can show foreground status notifications.
3. **Ignore Battery Optimization**: Tap **Ignore** and exempt NtfySMS from battery optimization so background forwarding isn't delayed.

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
The notification shade will display `🟢 NtfySMS Active`.

---

## 3. Configuring ntfy & Gotify Servers

### Option A: Setting Up ntfy

#### 1. Using Public ntfy.sh Server (No Installation Needed)
If you don't run a home server, use the free public server `https://ntfy.sh`:
- **Server URL**: `https://ntfy.sh`
- **Topic**: Pick a long, unguessable topic string (e.g., `my-private-otp-channel-89102`).
- **Subscribe**: Open `https://ntfy.sh/my-private-otp-channel-89102` in your browser or install the official ntfy app from Google Play / F-Droid and add the topic.

#### 2. Self-Hosted ntfy via Docker
Run ntfy on your VPS or home lab:
```bash
docker run -d \
  --name ntfy \
  -p 8080:80 \
  -v /var/cache/ntfy:/var/cache/ntfy \
  -v /etc/ntfy:/etc/ntfy \
  binwiederhergestellt/ntfy serve
```
- **Server URL**: `http://192.168.1.50:8080` or `https://ntfy.home.arpa`
- **Protected Topics**: If your self-hosted server uses access control, create an access token (`ntfy token add username`) and paste the `tk_...` token into the **Access Token** field in NtfySMS.

---

### Option B: Setting Up Gotify

Gotify is an open-source push notification server designed for self-hosting.

#### 1. Deploy Gotify via Docker
```bash
docker run -d \
  --name gotify \
  -p 8080:80 \
  -v gotify_data:/app/data \
  gotify/server
```

#### 2. Generate an Application Token
1. Open Gotify Web UI in your browser (`http://192.168.1.50:8080` or `https://gotify.example.com`).
2. Log in with your admin account.
3. Click **Apps** in the top navigation bar → Click **Create Application**.
4. Enter name: `NtfySMS` → Click **CREATE**.
5. Copy the generated **Token** (e.g., `A1b2C3d4E5f6`).

#### 3. Enter Token in NtfySMS
In NtfySMS, tap **+ Add Node** → Select **🔔 Gotify**:
- **Server URL**: `https://gotify.example.com`
- **App Token**: Paste the generated `X-Gotify-Key` token (`A1b2C3d4E5f6`).
- Tap **Save Node**.

---

## 4. Target Delivery Nodes & Node-Level Pre-Checks

NtfySMS supports multiple target nodes simultaneously. You can add, edit, or toggle delivery nodes under **Settings → Delivery Target Nodes**.

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

## 6. Status Notifications & Quick Settings Tile

### Persistent Status Notifications
NtfySMS runs a low-priority foreground service (`SmsForwarderService`) to ensure Android does not terminate background monitoring:

- 🟢 **NtfySMS Active**: Normal background monitoring state.
- ⏳ **Waiting on Pre-Condition**: Trigger paused (e.g., `SMS from VM-HDFCBK: Waiting for Tailscale Target (VPN missing)`).
- 🚀 **Executing Forwarding Trigger**: Transmitting payload to target nodes.
- ✅ **Forwarding Trigger Executed**: Delivery completed successfully.

### Quick Settings (QS) Tile
Add the **NtfySMS Forwarder** tile to your Android Quick Settings shade to toggle SMS forwarding ON or OFF from anywhere.

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

If SMS messages are delayed or not forwarding while your phone screen is off:

1. **Battery Optimization Exemption**:
   - Go to **Settings** in NtfySMS.
   - Tap **Ignore** next to Battery Optimization and grant exemption.
2. **OEM Specific Background Permissions**:
   - **Xiaomi / Redmi (MIUI / HyperOS)**: Enable *Autostart* in Security App → Permissions → Autostart.
   - **Samsung (One UI)**: Move NtfySMS to *Never Sleeping Apps* under Battery settings.
   - **OnePlus / Oppo / Realme**: Set Battery Usage to *Allow Background Activity*.
3. **SMS vs RCS Note**: Standard Android SMS broadcasts (`RECEIVE_SMS`) capture SMS and MMS messages. Messages delivered via carrier RCS or Google Messages chat features bypass standard SMS broadcasts and cannot be intercepted.
