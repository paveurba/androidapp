# Smart Home Android App

A modern, reactive Smart Home management application built with **Jetpack Compose**. This app allows users to monitor sensors, manage device schedules, control multi-channel relays, receive system notifications, and dynamically switch between backend API servers.

---

## 🚀 Key Features & Implementation Status

### 1. Dashboard (Sensors)
- **Real-time Monitoring**: View current temperature, humidity, battery levels, and link quality.
- **Thermostat Control**: Interactive circular dial to adjust the "Set" temperature (Range: 0°C to 30°C).
- **Unit Conversion**: Toggle between Celsius (°C) and Fahrenheit (°F) across the entire app.
- **Visual Alerts**: 
    - Highlights sensors requiring heating (Current < Set).
    - Red alerts for low battery (< 20%).
    - Orange alerts for stale data (> 1 hour).

### 2. Device Schedules
- **Switch Management**: Define active time windows for devices like Heaters, Boilers, and Garden Lights.
- **Precise Timing**: Set "From" and "Till" hours (0-23) using a simple slider interface.

### 3. Relay Controllers
- **Multi-Channel Control**: Support for modules with 1 to 16 independent switches.
- **Responsive Toggles**: Instant on/off switching with reactive UI updates.
- **Custom Labels**: Each switch within a relay can have its own unique name.

### 4. Notification Center (Alerts)
- **Inbox System**: System alerts are saved locally within the app.
- **Unread Badges**: Real-time badge count on the navigation bar.
- **Read/Unread States**: Visual distinction between new and viewed alerts.

### 5. Custom API & WebSocket Server Mode (Local / Offline Endpoint)
- **Dynamic Endpoint Switch**: Switch from the default `.env` API URL (`BuildConfig.API_BASE_URL`) to a custom local server endpoint (e.g. `http://192.168.1.100:8000/api/`) directly from the UI.
- **Custom & Auto-Derived WebSocket Endpoint**: The WebSocket URL automatically adapts to the selected server host (e.g., `ws://192.168.1.100:8080/?clientId=...`). Optionally, users can specify an explicit custom WebSocket URL (e.g., `ws://192.168.1.100:9090/`).
- **Accessible Everywhere**: Available via the settings icon on both the **Login Screen** and **Dashboard TopAppBar**.
- **Persistent Preferences**: API and WebSocket endpoint configurations are stored securely in `DataStore` preferences and saved across logouts and app restarts.
- **Automatic Interceptor & WebSocket Switch**: Dynamically rewrites OkHttp request URLs and automatically reconnects real-time WebSocket listeners whenever configuration changes.

### 6. Adaptive Tablet & Large Screen Optimizations
- **Master-Detail Split View**: On tablets (`>= 600.dp` screen width), the Sensors tab displays a side-by-side split screen—Sensor list on the left pane (360dp) and live interactive Thermostat controls on the right pane.
- **Adaptive Multi-Column Grids**: Relays and Device Schedules automatically format into 2 or 3 responsive grid columns on large screens instead of stretching cards excessively.
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
   - **Direct install via Gradle**:
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
2. **Firebase Configuration**:
   - Package name: `com.smarthome.lv`.
   - Ensure `app/google-services.json` matches this package name.
3. **Configure Base API URL**:
   - `.env` file in project root sets default base URL:
     ```env
     API_BASE_URL=http://10.0.2.2:8000/api/
     ```
4. **Build & Run**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📁 Project Structure

- `com.smarthome.data`: Data models, Repositories, `AuthPreferences`, and `SmartHomeFirebaseService`.
- `com.smarthome.data.network`: `ApiService`, `AuthInterceptor`, `DynamicBaseUrlInterceptor`, and `NetworkClient`.
- `com.smarthome.ui.components`: Custom components like `CustomApiServerDialog`.
- `com.smarthome.ui.dashboard`: Feature screens (Sensors, Schedules, Relays, Notifications).
- `com.smarthome.ui.auth`: `LoginScreen` with Custom API Server toggle.
- `com.smarthome.navigation`: Navigation host and routing logic.
- `com.smarthome.MainActivity`: Entry point and FCM initialization.

---

## 📡 Sending Push Notifications
1. Locate the **Device Token** in Logcat (filter by `FCM`).
2. Send test notification via Firebase Console or cURL.
3. The app receives and displays system alerts in real-time.
