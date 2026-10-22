🚀 Getting Started & Building
Prerequisites
Android Studio Ladybug (2024.2.1) or newer
JDK 17+
Android SDK 35 (minSdk = 26, targetSdk = 35)
Build & Run
Clone the repository and open the project root in Android Studio.
Build the debug APK using Gradle:
code
Bash
gradle :app:assembleDebug
Install on a physical device or emulator, then open the Command Center or System tab to grant the 5 one-tap system role checklists (Notification Listener, Call Screening Role, DND Policy Access, Usage Stats for Auto-Game, and Runtime Notifications).
Use the "Simulate Call & Msg" button on the Command Center at any time to preview real-time call rejection, auto-reply dispatch, and notification suppression in the Recent Intercepts Feed.
🧪 Running Unit & Robolectric Tests
Sentinal includes a comprehensive suite of local JVM and Robolectric tests (app/src/test/java/com/example/ExampleRobolectricTest.kt) covering:
Focus mode activation, cycling, and persistence
Zero-floor countdown expiry (checkAndExpireIfNeeded) and -15m boundary auto-deactivation
Declined-call Auto-Reply toggle synchronization across UI & Service broadcasts
3-minute (180,000 ms) per-caller auto-reply cooldown enforcement
3-minute Repeat Caller breakthrough and Zero-Tolerance Lockdown overrides
"URGENT" keyword breakthrough detection
30-second Auto-Game exit grace period state machine
