# Smart Home Android App

A modern, reactive Smart Home management application built with **Jetpack Compose**. This app allows users to monitor sensors, manage device schedules, control multi-channel relays, and receive system notifications.

## 🚀 Current Implementation Status

The project is currently a fully functional prototype using reactive mock data.

### 1. Dashboard (Sensors)
- **Real-time Monitoring**: View current temperature, humidity, battery levels, and link quality.
- **Thermostat Control**: Interactive circular dial to adjust the "Set" temperature (Range: 0°C to 30°C).
- **Unit Conversion**: Toggle between Celsius (°C) and Fahrenheit (°F) across the entire app.
- **Visual Cues**: 
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
- **Inbox System**: All system alerts are saved locally within the app.
- **Unread Badges**: Real-time badge count on the navigation bar.
- **Read/Unread States**: Visual distinction between new and viewed alerts.

### 5. Technical Highlights
- **Reactive Architecture**: Built using `StateFlow` for immediate UI response to data changes.
- **Performance Optimized**: 
    - Stable keys for `LazyColumn` items to ensure smooth scrolling.
    - Optimized object allocation (e.g., pre-allocated Date formatters) to prevent UI freezing/jank.
- **Production-Ready Push Notifications**: 
    - Full **Firebase Cloud Messaging (FCM)** integration.
    - Configured `SmartHomeFirebaseService` for background alert handling.
    - Automatic device token registration logic.

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
   - The project is configured to use the package name `com.smarthome.lv`.
   - Ensure your `app/google-services.json` matches this package name.
3. **Open in Android Studio**:
   Select `File > Open` and navigate to the project root.
4. **Build the project**:
   Use the terminal in Android Studio or run:
   ```bash
   ./gradlew assembleDebug
   ```
5. **Run on Device/Emulator**:
   Click the **Run** button (Green Play Icon) in Android Studio.

---

## 📁 Project Structure

- `com.smarthome.data`: Data models, Repositories, and the `SmartHomeFirebaseService`.
- `com.smarthome.ui.dashboard`: Main feature screens (Sensors, Schedules, Relays, Notifications).
- `com.smarthome.navigation`: App routing and Tab navigation logic.
- `com.smarthome.MainActivity`: App entry point and FCM token retrieval.

---

## 📡 Sending Push Notifications
To send a real notification to the app:
1. Locate the **Device Token** in the Logcat (filter by `FCM`).
2. Use the Firebase Console or a cURL command to send a message to that token.
3. The app will receive and display the notification even if it's in the background.
