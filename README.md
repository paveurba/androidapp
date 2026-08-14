# Smart Home Android App

A modern, reactive Smart Home management application built with **Jetpack Compose**. This app allows users to monitor sensors, manage device schedules, control multi-channel relays, receive system notifications, and dynamically switch backend API and WebSocket servers in real time.

---

## 🚀 Key Features & Implementation Status

### 1. Dashboard (Sensors)
- **Real-Time Monitoring**: View current temperature, humidity, battery levels, and link quality powered by live Symfony API and WebSocket updates.
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
adb shell am start -n com.upasmarthome.app/com.smarthome.MainActivity
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
  adb shell am start -n com.upasmarthome.app/com.smarthome.MainActivity
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
   - Package name: `com.upasmarthome.app`.
   - Copy `app/google-services.json.example` to `app/google-services.json` and insert your Firebase credentials:
     ```bash
     cp app/google-services.json.example app/google-services.json
     ```
4. **Build & Run**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🔗 Backend API & WebSocket Data Contracts

The Android app communicates with the **Symfony 8.1 API Platform** backend (`smarthomeapi`) via REST HTTP endpoints and a real-time WebSocket push listener.

### 1. REST API Endpoints (`http://<HOST>:8000/api/`)

| Method | Endpoint | Description | Payload / Query |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/login` | Authenticates device credentials | `{"serialNumber": "SN123456", "otp": "12345678"}` |
| `POST` | `/api/register` | Registers a new device serial number | `{"serialNumber": "SN123456", "otp": "12345678"}` |
| `GET` | `/api/sensors` | Fetches all smart temperature & humidity sensors | Auth: Basic (`serialNumber:otp`) |
| `PATCH` | `/api/sensors/{id}` | Updates setpoint temperature for a sensor | `{"setTemp": 22.5}` |
| `GET` | `/api/relays` | Fetches all multi-channel relay modules | Auth: Basic (`serialNumber:otp`) |
| `POST` | `/api/relays/{relayId}/toggle/{switchId}` | Toggles state of a specific switch | Auth: Basic (`serialNumber:otp`) |
| `GET` | `/api/schedules` | Fetches active device operation schedules | Auth: Basic (`serialNumber:otp`) |
| `PATCH` | `/api/schedules/{id}` | Updates schedule time window | `{"fromHour": 7, "toHour": 9}` |
| `GET` | `/api/notifications` | Fetches inbox notifications | Auth: Basic (`serialNumber:otp`) |
| `PATCH` | `/api/notifications/{id}` | Marks a notification as read | `{"isRead": true}` |
| `DELETE` | `/api/notifications` | Clears all notifications in inbox | Auth: Basic (`serialNumber:otp`) |

---

### 2. WebSocket Push Server (`ws://<HOST>:8080/`)

#### Connection URL Format
```
ws://127.0.0.1:8080/?clientId=SN123456
```
As of versionCode 11 (0.0.7), the upgrade request also requires the same
`Authorization: Basic base64(serialNumber:otp)` header as the REST API (see
[`ws.Hub.Handler`](../smarthomeapi/pkg/core/ws/hub.go) in `smarthomeapi`) —
the server derives the client identity from the verified credential, not
from `clientId`, which is now cosmetic/ignored. See
[Security Notes](#-security-notes) below for why.

#### Incoming Event Payload Structure (Server $\rightarrow$ Client)
```json
{
  "event": "<EVENT_NAME>",
  "data": { ... }
}
```

#### Supported Events & Client Handling

| Event Name | Example `data` Payload | App Action Taken |
| :--- | :--- | :--- |
| `refresh_sensors` | `{"sensorId": "1", "setTemp": 22.5}` | 0ms immediate in-memory setpoint update + background `GET /api/sensors` fetch |
| `refresh_relays` | `{"relayId": "r1", "switchId": "rs1", "isOn": true}` | 0ms immediate in-memory switch state toggle + background `GET /api/relays` fetch |
| `refresh_schedules` | `{"scheduleId": "s1"}` | Background `GET /api/schedules` fetch |
| `refresh_notifications` | `{}` | Background `GET /api/notifications` fetch |
| `refresh_all` | `{}` | Refreshes all 4 data domains simultaneously |

---

### 3. API Data Models & Field Specifications

#### A. Sensor Resource (`TempSensor`)
Represents smart temperature and humidity sensors in the system.
```json
{
  "id": "1",
  "name": "Living Room",
  "currentTemp": 22.5,
  "setTemp": 22.0,
  "humidity": 45.0,
  "batteryLevel": 85,
  "linkQuality": 200,
  "lastUpdated": 1722019200000
}
```
- `id` (`String`): Unique identifier of the sensor entity.
- `name` (`String`): Human-readable room/device display name.
- `currentTemp` (`Float`): Live measured temperature in Celsius.
- `setTemp` (`Float`): Desired target thermostat setpoint temperature in Celsius.
- `humidity` (`Float`): Relative humidity percentage (`0` - `100%`).
- `batteryLevel` (`Int`): Remaining battery percentage (`0` - `100%`). Below `20%` triggers a low-battery alert.
- `linkQuality` (`Int`): Signal link quality indicator (LQI: `0` - `255`).
- `lastUpdated` (`Long`): Millisecond Unix timestamp of last received reading. Older than 1 hour triggers a stale data alert.

#### B. Relay Module Resource (`Relay` & `RelaySwitch`)
Represents multi-channel switch relay hardware modules.
```json
{
  "id": "r1",
  "name": "Living Room Relay",
  "switches": [
    { "id": "rs1", "label": "Main Light", "isOn": true },
    { "id": "rs2", "label": "Socket 1", "isOn": false }
  ]
}
```
- `id` (`String`): Unique identifier of the relay controller module.
- `name` (`String`): Display name of the relay controller.
- `switches` (`Array<RelaySwitch>`): List of individual switch channels (`1` to `16` channels).
  - `id` (`String`): Unique channel ID.
  - `label` (`String`): Label describing connected appliance (e.g. *"Main Light"*).
  - `isOn` (`Boolean`): Active state (`true` = ON, `false` = OFF).

#### C. Schedule Resource (`SensorSchedule`)
Defines automated active operation windows for devices.
```json
{
  "id": "s1",
  "sensorName": "Main Heater",
  "fromHour": 7,
  "toHour": 9
}
```
- `id` (`String`): Unique schedule ID.
- `sensorName` (`String`): Name of target hardware device or zone.
- `fromHour` (`Int`): Operation start hour (`0` to `23`).
- `toHour` (`Int`): Operation end hour (`0` to `23`).

#### D. Notification Resource (`AppNotification`)
System inbox alerts and push notifications.
```json
{
  "id": "1",
  "title": "Security Alert",
  "message": "Motion detected in Living Room at 2:00 AM.",
  "timestamp": 1722015600000,
  "isRead": false
}
```
- `id` (`String`): Unique notification ID.
- `title` (`String`): Alert title.
- `message` (`String`): Detailed alert message body.
- `timestamp` (`Long`): Millisecond Unix timestamp of alert creation.
- `isRead` (`Boolean`): Read/Unread inbox status flag.

#### E. Authentication Models (`LoginRequest` & `LoginResponse`)
```json
// Login / Register Request Body
{
  "serialNumber": "SN123456",
  "otp": "12345678"
}

// Login Response Body
{
  "token": "mock_jwt_token_SN123456"
}
```
- `serialNumber` (`String`): Registered device serial number.
- `otp` (`String`): 8-digit numeric one-time authorization PIN.
- `token` (`String`): JWT Bearer authentication token.

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
2. Send test notification via Firebase Console or cURL to the Symfony API.
3. The app receives and displays system alerts in real-time.
