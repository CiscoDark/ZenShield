# Sentinal — Offline Android Focus Shield & Call Bouncer

**Sentinal** (`com.aistudio.focuszenshield.sentinal`) is a privacy-first, **100% offline** Android Focus Shield, Cellular & VoIP Call Bouncer, Automatic Game Mode Detector, and Heads-Up Notification Suppressor built with **Kotlin** and **Jetpack Compose (Material 3 Expressive)**.

Every rule evaluation, call intercept, notification suppression, and auto-reply runs strictly on-device with **zero network permissions**, **zero cloud dependencies**, and **zero telemetry uploads**.

---

## ✨ Key Features

### 1. Multi-Profile Focus Engine
Switch instantaneously between five specialized states from the **Command Center**, **Live Notification Shade HUD**, or **Android Quick Settings Tile**:
- **Do Not Disturb (`DO_NOT_DISTURB`)**: Blocks cellular and VoIP calls while silencing distracting heads-up notifications.
- **Gaming Focus (`GAMING_FOCUS`)**: Suppresses heads-up popups, banners, and incoming calls for uninterrupted frame rates and full-screen immersion.
- **Work Focus (`WORK_FOCUS`)**: Silences social & entertainment clutter while keeping emergency breakthrough pathways active.
- **Sleep Shield (`SLEEP_SHIELD`)**: Full overnight silence with customizable emergency breakthrough rules.
- **Standby (`OFF`)**: Disarms all interception and restores normal system notification behavior.

### 2. Cellular & VoIP Call Bouncer
- **Cellular Call Screening (`ZenCallScreeningService`)**: Uses Android's `CallScreeningService` API to silently reject, disallow call composer attachments, and suppress heads-up call notifications while logging blocked call telemetry locally.
- **Outgoing Call Protection (`ZenCallRedirectionService`)**: Uses `CallRedirectionService` to record active outgoing calls and place them in a temporary immunity window so active user-initiated calls are never interrupted.
- **VoIP Call Auto-Decline (`NotificationGuardService`)**: Detects incoming full-screen and heads-up VoIP calls from **WhatsApp**, **Telegram**, **Discord**, **Messenger**, **Instagram**, **Signal**, **Viber**, and **Line**, and invokes their decline/hang-up `Notification.Action` automatically.
- **Zero-Tolerance Call Lockdown**: Optional override (`declineAllCallsRegardless`) that declines 100% of incoming cellular and VoIP calls without exception, bypassing VIP star lists and repeat-caller breakthroughs.

### 3. Smart Declined-Call Auto-Reply (3-Minute Cooldown)
- **Strictly Scoped to Declined Incoming Calls**: Auto-replies are dispatched **only** when an incoming cellular or VoIP call is actively declined by Sentinal—never for regular chat messages or outgoing calls.
- **Synchronized 3-Way Toggle (`Auto-Reply: ON / OFF`)**: Toggle auto-reply immediately from:
  1. **Command Center** (Hero Status Card & Interactive Shade Preview)
  2. **Live Notification Shade HUD** (`Auto-Reply: ON/OFF` action button)
  3. **Rules Tab** (Declined Call Auto-Reply customization card)
- **3-Minute Per-Caller Cooldown (`180,000 ms`)**: Prevents reply loops when a caller repeatedly rings within a 3-minute window.
- **Customizable Per-Profile Templates**: Tailor distinct auto-reply messages for Do Not Disturb, Gaming Focus, Work Focus, and Sleep Shield.

### 4. Emergency Breakthrough Safeguards
- **3-Minute Repeat Caller Breakthrough**: Allows a second call from the same number within 3 minutes to ring through (unless Zero-Tolerance Lockdown is active).
- **"URGENT" Keyword Breakthrough**: Scans incoming notification text for emergency keywords (`URGENT`, `EMERGENCY`, `911`, `SOS`, `CRITICAL`) and lets matching messages bypass suppression.
- **Starred / VIP Contact Allowlist**: Optionally permit starred system contacts to bypass call blocking.

### 5. Zero-Floor Countdown Timer & Auto-Shutoff
- **Expressive Timer Presets & Scrubber**: Set timed focus sessions (`15m`, `30m`, `45m`, `1h`, `2h`, `4h`, `8h`, `Until I turn it off`, or custom slider minutes).
- **Strict `00:00` Auto-Deactivation**: A high-precision 1-second ticker monitors timed sessions and automatically transitions Sentinal to `OFF` the instant `00:00` is reached—never counting into negative overtime.
- **Live Time Adjusters (`-15m` / `+15m` / `+30m`)**: Add or subtract time on the fly from the app or notification shade; tapping `-15m` when `<= 15m` remain immediately ends the session cleanly.

### 6. Automatic Game Detection & 30-Second Grace Window
- **Foreground Game Scanner (`GameDetectorService`)**: Detects foreground games using `UsageStatsManager` (`ApplicationInfo.CATEGORY_GAME`, popular competitive titles, plus any user-selected custom apps).
- **Auto-Engage & Animated Heads-Up Banner**: Automatically activates **Gaming Focus** on game launch and displays a top-anchored spring-animated banner (`GamingModePopupManager`).
- **30-Second Exit Grace Period**: Switching briefly away from a game starts a 30-second grace countdown rather than dropping protection immediately; returning to the game cancels the countdown seamlessly.

### 7. Material 3 Expressive UI & Tactile Physics
- **Spring Physics (`ExpressiveMotion`)**: Bouncy spatial morphing (`dampingRatio = 0.6f..0.68f`), press-squish corner radius morphing, rolling odometer counters, and directional tab transitions.
- **Synchronized Multi-Step Waveforms (`ExpressiveHaptics`)**: Custom `VibrationEffect.createWaveform()` patterns paired with shield power-up, mode cycling, time adjustments, and test intercepts.
- **Live Animated Shield Emblem**: Canvas-drawn radar sweep, breathing sonar pulse rings, and animated status geometry.
- **Adaptive Layout Support**: Responsive layout supporting `Compact`, `Medium`, and `Expanded` window size classes.

---

## 🏗️ Architecture & Project Structure

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/example/
│   ├── MainActivity.kt                  # M3 Expressive UI: Command Center, Rules, Auto-Game, System tabs
│   ├── core/
│   │   ├── FocusMode.kt                 # Enum definitions, colors, descriptions, default reply templates
│   │   ├── FocusStateManager.kt         # Single source of truth (StateFlow + SharedPreferences + cooldowns)
│   │   └── GamingModePopupManager.kt    # In-app & system overlay animated heads-up popup banner
│   ├── services/
│   │   ├── ZenShieldForegroundService.kt      # Pinned dark-glass Live Notification HUD & 1s zero-floor timer
│   │   ├── ZenCallScreeningService.kt         # Android CallScreeningService for cellular call rejection
│   │   ├── ZenCallRedirectionService.kt       # Outgoing call tracker & temporary caller immunity
│   │   ├── NotificationGuardService.kt        # NotificationListenerService for VoIP calls & heads-up suppression
│   │   ├── GameDetectorService.kt             # Foreground app monitor with 30s exit grace period
│   │   └── FocusQuickSettingsTileService.kt   # Android Quick Settings Tile (one-tap cycle)
│   └── ui/theme/
│       ├── Color.kt                     # Deep Obsidian & Cyber-Emerald M3 Expressive palette
│       ├── Theme.kt                     # Dynamic / Expressive dark color scheme
│       ├── Type.kt                      # Bundled Plus Jakarta Sans & JetBrains Mono typography
│       ├── ExpressiveMotion.kt          # Spring specs, press-squish modifier, rolling odometer digits
│       └── ExpressiveHaptics.kt         # Custom VibrationEffect tactile waveforms
└── res/
    ├── drawable/                        # Custom dark-glass RemoteViews backgrounds & adaptive icon vectors
    ├── font/                            # Local bundled .ttf fonts (Plus Jakarta Sans, JetBrains Mono)
    ├── layout/
    │   ├── notification_live_focus.xml          # Collapsed dark-glass Live Notification layout
    │   └── notification_live_focus_expanded.xml # Expanded HUD with telemetry, -15m/+15m & Auto-Reply toggle
    └── values/
        └── strings.xml
```

---

## 🔐 System Roles & Permissions Used

Sentinal requests only the Android permissions and system roles required to enforce local call and notification shielding:

| Permission / System Role | Purpose |
| :--- | :--- |
| **`ROLE_CALL_SCREENING`** (`ZenCallScreeningService`) | Silently rejects incoming cellular calls during active Focus modes without ringing. |
| **`ROLE_CALL_REDIRECTION`** (`ZenCallRedirectionService`) | Detects user-initiated outgoing calls so return calls from that number aren't blocked. |
| **`BIND_NOTIFICATION_LISTENER_SERVICE`** (`NotificationGuardService`) | Intercepts heads-up notifications, detects VoIP ringing actions, and sends inline declined-call auto-replies. |
| **`ACCESS_NOTIFICATION_POLICY`** (`NotificationManager`) | Synchronizes system Do Not Disturb interruption filters (`INTERRUPTION_filter_PRIORITY` / `NONE`). |
| **`PACKAGE_USAGE_STATS`** (`UsageStatsManager`) | Detects when a game or custom-monitored app enters the foreground for Auto-Gaming Mode. |
| **`POST_NOTIFICATIONS` & `FOREGROUND_SERVICE_SPECIAL_USE`** | Displays the persistent Live Focus HUD in the notification shade. |
| **`READ_CONTACTS` / `READ_CALL_LOG`** *(Optional)* | Verifies whether an incoming caller is a Starred VIP contact. |

> **Privacy Guarantee**: Sentinal does **not** request `android.permission.INTERNET`. No call logs, contact names, or notification contents ever leave the device.

---

## 🚀 Getting Started & Building

### Prerequisites
- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 17+**
- **Android SDK 35** (`minSdk = 26`, `targetSdk = 35`)

### Build & Run
1. Clone the repository and open the project root in Android Studio.
2. Build the debug APK using Gradle:
   ```bash
   gradle :app:assembleDebug
   ```
3. Install on a physical device or emulator, then open the **Command Center** or **System** tab to grant the 5 one-tap system role checklists (Notification Listener, Call Screening Role, DND Policy Access, Usage Stats for Auto-Game, and Runtime Notifications).
4. Use the **"Simulate Call & Msg"** button on the Command Center at any time to preview real-time call rejection, auto-reply dispatch, and notification suppression in the **Recent Intercepts Feed**.

---

## 🧪 Running Unit & Robolectric Tests

Sentinal includes a comprehensive suite of local JVM and Robolectric tests (`app/src/test/java/com/example/ExampleRobolectricTest.kt`) covering:
- Focus mode activation, cycling, and persistence
- Zero-floor countdown expiry (`checkAndExpireIfNeeded`) and `-15m` boundary auto-deactivation
- Declined-call Auto-Reply toggle synchronization across UI & Service broadcasts
- 3-minute (`180,000 ms`) per-caller auto-reply cooldown enforcement
- 3-minute Repeat Caller breakthrough and Zero-Tolerance Lockdown overrides
- `"URGENT"` keyword breakthrough detection
- 30-second Auto-Game exit grace period state machine

Run the full test suite with:
```bash
gradle :app:testDebugUnitTest
```
