# Smart Home Android App

A modern, reactive Smart Home management application built with **Jetpack Compose**. This app allows users to monitor sensors, manage device schedules, control multi-channel relays, receive system notifications, and dynamically switch backend API and WebSocket servers in real time.

---

## 🚀 Key Features & Implementation Status

### 1. Dashboard (Sensors)
- **Real-Time Monitoring**: View current temperature, humidity, battery levels, and link quality powered by live API and WebSocket updates.
- **Thermostat Control**: Interactive circular dial to adjust the "Set" temperature (Range: 0°C to 30°C).
- **Unit Conversion**: Toggle between Celsius (°C) and Fahrenheit (°F) across the entire app.
- **Visual Alerts**: 
    - Highlights sensors requiring heating (Current < Set).
    - Red alerts for low battery (< 20%).
    - Orange alerts for stale data (> 1 hour).

### 2. Device Schedules
- **Switch Management**: Define active time windows for devices like Heaters, Boilers, and Garden Lights.
- **Precise Timing**: Set "From" and "Till" hours (0-23) using an interactive slider interface.
- **Overlapping Validation**: Client-side and server-side validation to prevent overlapping schedule windows.

### 3. Relay Controllers
- **Multi-Channel Control**: Support for modules with 1 to 16 independent switches.
- **Responsive Toggles**: Instant on/off switching with reactive UI updates.
- **Custom Labels**: Each switch within a relay has its own unique name and state.

### 4. Notification Center (Alerts)
- **Production Push Notifications**: Full **Firebase Cloud Messaging (FCM)** integration and background event processing.
- **Inbox System**: Real-time unread badges on the navigation bar, read/unread states, and clear-all functionality.

### 5. Custom API & WebSocket Server Mode (Local / Offline Endpoint)
- **Dynamic Endpoint Switch**: Switch from default `.env` API URL (`BuildConfig.API_BASE_URL`) to any custom local server endpoint (e.g. `http://192.168.1.100:8000/api/`) directly from the UI.
- **Custom & Auto-Derived WebSocket Endpoint**: The WebSocket URL automatically adapts to the selected server host (e.g., `ws://192.168.1.100:8080/?clientId=...`). Optionally, users can specify an explicit custom WebSocket URL.
- **Accessible Everywhere**: Available via the settings icon on both the **Login Screen** and **Dashboard TopAppBar**.
- **Persistent Preferences**: API and WebSocket endpoint configurations are stored securely in `DataStore` preferences and saved across logouts and app restarts.
- **Instant Payload Updates**: Real-time WebSocket payloads trigger 0ms in-memory UI state updates combined with background API sync.

### 6. Adaptive Tablet & Large Screen Optimizations
- **Master-Detail Split View**: On tablets (`>= 600.dp` screen width), the Sensors tab displays a side-by-side split screen—Sensor list on the left pane (360dp) and live interactive Thermostat controls on the right pane.
- **Persistent Navigation Menu**: The main navigation menu remains visible at all times on tablets when interacting with sensors.
- **Adaptive Multi-Column Grids**: Relays and Device Schedules automatically format into 2 or 3 responsive grid columns on large screens instead of stretching cards.
- **Centered Form Containers**: Login and setting dialogs are constrained to an optimal width (`widthIn(max = 440.dp)`) centered on screen for ergonomics.

---

## 📱 Running on Emulators & Physical Phones

### A. Launching the Pixel 8 & Pixel Tablet Emulators
1. **From Terminal**:
   - **Pixel 8 (Phone)**:
     ```bash
     emulator -avd Pixel_8
     ```
   - **Pixel Tablet (Tablet)**:
     ```bash
     emulator -avd Pixel_Tablet
     ```
2. **From Android Studio**:
   Open **Tools** $\rightarrow$ **Device Manager** $\rightarrow$ Click **Play** next to `Pixel_8` or `Pixel_Tablet`.

3. **Creating a new AVD (if needed)**:
   ```bash
   # Create Pixel 8 AVD
   avdmanager create avd -n Pixel_8 -k "system-images;android-34;google_apis;arm64-v8a" -d "pixel_8"

   # Create Pixel Tablet AVD
   avdmanager create avd -n Pixel_Tablet -k "system-images;android-34;google_apis;arm64-v8a" -d "pixel_tablet"
   ```

### B. Installing & Running on Emulator
With the emulator running:
```bash
# Build and install on active emulator
./gradlew installDebug

# Launch main activity
adb shell am start -n com.smarthome.lv/com.smarthome.MainActivity
```

### C. Installing on a Physical Android Phone
1. **Enable Developer Options & USB Debugging**:
   - On your phone: **Settings** $\rightarrow$ **About Phone** $\rightarrow$ Tap **Build Number** 7 times.
   - Go to **Settings** $\rightarrow$ **System / Additional Settings** $\rightarrow$ **Developer Options** $\rightarrow$ Enable **USB Debugging**.
2. **Connect Phone via USB**:
   - Plug phone into Mac via USB and accept **"Allow USB Debugging?"** prompt.
   - Verify connection:
     ```bash
     adb devices
     ```
3. **Install the App**:
   ```bash
   ./gradlew installDebug
   ```

### D. Testing the Tablet Adaptive Layout
- **Method 1 (Rotate Running Phone Emulator to Landscape)**:
  Rotate your running phone emulator to **Landscape mode** to increase width past `600dp` and trigger the tablet layout:
  - Click the **Rotate** button on the emulator toolbar.
  - Or run via terminal:
    ```bash
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 1
    ```
- **Method 2 (Run Dedicated `Pixel_Tablet` Emulator)**:
  ```bash
  emulator -avd Pixel_Tablet
  ./gradlew installDebug
  adb shell am start -n com.smarthome.lv/com.smarthome.MainActivity
  ```

---

## 🛠 Setup & Installation

### Prerequisites
- **Android Studio** (Ladybug or newer recommended).
- **JDK 17** or higher.
- **Android SDK 34** (API Level 34).

### Running the Project
1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd androidapp
   ```
2. **Configure Environment Variables**:
   - Copy `.env.example` to `.env`:
     ```bash
     cp .env.example .env
     ```
3. **Firebase Configuration**:
   - Package name: `com.smarthome.lv`.
   - Copy `app/google-services.json.example` to `app/google-services.json` and insert your Firebase credentials:
     ```bash
     cp app/google-services.json.example app/google-services.json
     ```
4. **Build & Run**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🔗 Backend API & WebSocket

The Android app talks to the `smarthomeapi` backend (a single Go server) over
two channels on the same host: a REST API under `/api`, and a WebSocket push
channel for live updates. In production both live behind the same TLS host,
`https://upanet.org`; a custom/self-hosted backend is also supported (see
[Custom API & WebSocket Server Mode](#5-custom-api--websocket-server-mode-local--offline-endpoint)
above) as long as it implements the same contract.

### 1. REST API — see the live docs

Rather than duplicate an endpoint table here that inevitably drifts out of
sync with the server, the backend publishes its own authoritative API
reference:

- **Interactive Swagger UI**: https://upanet.org/docs/
- **OpenAPI 3.0 spec (JSON)**: https://upanet.org/openapi.json

Both describe every `/api/*` route, request/response schema, and auth
requirement currently deployed. Endpoints are grouped by resource: `sensors`,
`alarm-sensors`, `relays` (including per-channel switches), `schedules`,
`notifications`, `pairing` (Zigbee discovery/join), and `login`/`register`.

**Auth**: every `/api/*` request (except `login`/`register`) uses HTTP Basic
Auth with the device's serial number as username and its OTP as password —
there's no bearer/JWT token in play. `POST /api/login` and
`POST /api/register` just validate that pair and return a status message;
the app doesn't store or replay anything from the response.

### 2. WebSocket Push Channel

#### Connection URL

The effective WebSocket URL is derived from the active API base URL
(`AuthPreferences.getEffectiveWebSocketUrl`):

| API base URL scheme | WebSocket URL used |
| :--- | :--- |
| `https://` (cloud/production) | `wss://<host>/ws?clientId=<serialNumber>` |
| `http://` (local/custom dev server) | `ws://<host>:8080/?clientId=<serialNumber>` |

The upgrade request requires the same `Authorization: Basic
base64(serialNumber:otp)` header as the REST API — the server identifies the
connection from the verified credential, not from `clientId`, which is
cosmetic.

#### Message Format (Server → Client)
```json
{
  "event": "<EVENT_NAME>",
  "data": { ... }
}
```

#### Events

| Event Name | Fires On | Example `data` | App Action |
| :--- | :--- | :--- | :--- |
| `refresh_sensors` | Setpoint change, rename, or delete | `{"sensorId": "1", "setTemp": 22.5}` | Immediate in-memory update + background `GET /api/sensors` |
| `refresh_relays` | Switch toggle, relay/switch create, patch, or delete | `{"relayId": "r1", "switchId": "rs1", "isOn": true}` | Immediate in-memory update + background `GET /api/relays` |
| `refresh_schedules` | Schedule created, updated, or deleted | `{"scheduleId": "s1", "fromHour": 7, "fromMinute": 0, "toHour": 9, "toMinute": 0}` | Background `GET /api/schedules` |
| `refresh_notifications` | New notification, mark-read, or clear-all | `{"notificationId": "n1"}` | Background `GET /api/notifications` |
| `refresh_settings` | Pump configuration changed | `{"setting": "pump"}` | Background settings re-fetch |
| `refresh_garden` | Garden watering configuration changed | `{}` | Background garden config re-fetch |
| `refresh_pairing` | Permit-join started/stopped, or a device is discovered/confirmed while pairing | `{"deviceId": "d1"}` | Background `GET /api/pairing/*` re-fetch |
| `refresh_all` | Full backend resync (e.g. a Pi agent reconnects) | `{}` | Refreshes every data domain |

Alarm sensors (contact/occupancy/water-leak) are intentionally **not** pushed
over the WebSocket — MQTT readings arrive too frequently for that — the app
polls `GET /api/alarm-sensors` instead.

---

## 📁 Project Structure

- `com.smarthome.data`: Repositories (`ProductionAuthRepository`, `ProductionSensorRepository`, `ProductionNotificationRepository`), `AuthPreferences`, and `SmartHomeFirebaseService`.
- `com.smarthome.data.network`: `ApiService`, `AuthInterceptor`, `DynamicBaseUrlInterceptor`, and `NetworkClient`.
- `com.smarthome.ui.components`: UI dialogs like `CustomApiServerDialog`.
- `com.smarthome.ui.dashboard`: Main feature views (`DashboardScreen`, `SchedulesScreen`, `RelaysScreen`, `NotificationsScreen`, `ThermostatControl`).
- `com.smarthome.ui.auth`: `LoginScreen` with custom server settings.
- `com.smarthome.navigation`: Navigation host and routing logic.
- `com.smarthome.MainActivity`: Entry point and FCM initialization.

---

## 📡 Sending Push Notifications
1. Locate the **Device Token** in Logcat (filter by `FCM`).
2. Send test notification via Firebase Console or cURL to the backend API.
3. The app receives and displays system alerts in real-time.
