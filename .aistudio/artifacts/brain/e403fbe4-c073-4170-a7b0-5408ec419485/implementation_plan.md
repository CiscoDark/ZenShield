# Sentinal — Zero-Floor Timer Deactivation, Multi-Surface Auto-Reply Toggle & 3-Minute Cooldown

This update guarantees that timed Focus Shield sessions immediately deactivate at `00:00` without ever ticking into negative numbers, introduces one-tap **Declined Call Auto-Reply ON/OFF** controls across the Command Center, Live Notification Shade, and Rules tab, and sets the per-caller auto-reply cooldown to **3 minutes (180 seconds)**.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions were confirmed during interactive clarification and govern this implementation:

- **Confirmed Decision 1 — Auto-Reply Toggle Placement**: The Declined Call Auto-Reply ON/OFF toggle will be accessible and synchronized in real time across **all three surfaces**: the **Shield Command Center** (Hero card & Live HUD preview), the **Live Notification Shade** (interactive action pill), and the **Rules Tab** (`Declined Call Auto-Reply` card).
- **Confirmed Decision 2 — 3-Minute Cooldown Scope**: The **3-minute (`180,000 ms`) cooldown** applies strictly to **declined call auto-replies** per caller, preventing repeated auto-reply spam while still declining incoming calls according to the active Focus Shield profile.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Guaranteed `00:00` Auto-Deactivation & Non-Negative Timer Clamp**: Eliminates negative countdown displays (`-00:01 left`) in both the Android notification shade and in-app UI by clamping all remaining-time calculations to a strict zero floor (`>= 0L`), replacing unbounded SystemUI `Chronometer` negative ticking with clamped live formatting + `setTimeoutAfter(remainingMs)`, and enforcing immediate `FocusProfile.OFF` deactivation the instant any timer reaches `00:00`.
  2. **Synchronized Auto-Reply ON/OFF Control**: Gives users explicit control over whether declined calls trigger an automated reply message or are declined completely silently, toggleable with one tap from the Command Center, the Live Notification Shade, or the Rules tab.
  3. **3-Minute Auto-Reply Cooldown**: Reduces the per-caller auto-reply cooldown from 30 minutes to **3 minutes (`180,000 ms`)**, matching the 3-minute repeat-caller emergency window.
- **Target Audience / Persona**: Mobile gamers, deep-work professionals, and privacy-conscious users who want predictable timed focus sessions and instant control over whether callers receive automated text replies.
- **Key Value**: Timed sessions end cleanly on the exact second without negative timer artifacts, and users can switch between "Decline + Auto-Reply" and "Silent Call Bouncer" from anywhere.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Timed Focus Expiration (`00:00` Auto-Shutoff)**:
     - When a user starts a timed Focus session (via preset chips `45m`, `1h`, `2h`, the `5m–8h` custom slider, or `+15m`/`-15m` pills), the live countdown ticks down second-by-second.
     - If the user taps `-15m` when 15 minutes or less remain, or when the countdown naturally reaches `00:00`, Focus Shield immediately transitions to `FocusProfile.OFF`, triggers the `FOCUS_DEACTIVATED` spring haptic waveform, restores the system interruption filter, and removes the Live Notification from the shade—never displaying `-00:01`.
  2. **Toggling Auto-Reply Across Surfaces**:
     - **Command Center**: Inside the Hero Status Header Card (and the Live Notification HUD Preview card), an expressive pill row lets the user toggle **Call Auto-Reply (`ON` / `OFF`)** with bouncy corner morphing and tactile spring haptics.
     - **Live Notification Shade**: The expanded Live Notification HUD includes a dedicated **`Reply: ON` / `Reply: OFF`** pill button (`ACTION_TOGGLE_AUTO_REPLY`) that toggles auto-reply in-place and updates the shade subtitle immediately (`"Call Auto-Reply ON (3m Cooldown)"` vs. `"Silent Call Bouncer • Auto-Reply OFF"`).
     - **Rules Tab**: The `Declined Call Auto-Reply` card displays the master switch, custom message editor, and updated **3-Minute Cooldown** badge.
  3. **3-Minute Per-Caller Auto-Reply Cooldown**:
     - When `autoReplyEnabled` is `true` and an incoming call is declined, Sentinal checks if an auto-reply was sent to that caller within the last **3 minutes (`180,000 ms`)**. If not, it dispatches the profile auto-reply and records the timestamp; subsequent declined calls from the same caller within 3 minutes are declined without sending duplicate replies.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Software-Clamped Live Timer vs. Unbounded System `Chronometer` CountDown**
  - *Chosen Approach*: Clamp all countdown values with `maxOf(0L, expiryTime - now)` and render formatted non-negative `MM:SS` / `HH:MM:SS` strings in the 1-second Foreground Service ticker alongside `NotificationCompat.Builder.setTimeoutAfter(remainingMs)` and multi-layer expiration guards.
  - *Why*: Android's `RemoteViews.setChronometerCountDown(true)` continues ticking into `-00:01`, `-00:02` on the SystemUI thread if a background coroutine is delayed by even 500ms or if `chronometerBase` is passed. Explicit zero-clamped formatting paired with `setTimeoutAfter` guarantees the user never sees a negative number and the shade notification self-terminates at zero.
- **Decision 2: Multi-Surface Auto-Reply State Synchronization**
  - *Chosen Approach*: Bind the Command Center quick-toggle, Live Notification `ACTION_TOGGLE_AUTO_REPLY` PendingIntent, and Rules tab switch to `FocusStateManager.setAutoReplyEnabled(enabled)` / `toggleAutoReply()`.
  - *Why*: Ensures a single source of truth (`StateFlow<FocusSessionState>`) that immediately refreshes both the Jetpack Compose UI and the pinned RemoteViews notification.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        FocusStateManager (Singleton)                       │
│  • expiresAtMillis + Zero-Floor Expiration Guard (checkAndExpireIfNeeded)  │
│  • autoReplyEnabled: Boolean  +  toggleAutoReply()                         │
│  • AUTO_REPLY_COOLDOWN_MS = 180_000L (3 Minutes)                           │
└───────────────┬───────────────────────────────┬────────────────────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────────────┐ ┌──────────────────────────────────────┐
│    Jetpack Compose UI Surfaces    │ │      Background Services & Shade     │
│  • HeroStatusHeaderCard           │ │  • ZenShieldForegroundService        │
│    - Non-negative rolling timer   │ │    - 1s clamped timer + timeoutAfter │
│    - Auto-Reply Quick Toggle Pill │ │    - ACTION_TOGGLE_AUTO_REPLY pill   │
│  • LiveNotificationHudPreviewCard │ │  • NotificationGuardService          │
│    - Reply: ON/OFF pill button    │ │    - Checks checkAndExpireIfNeeded() │
│  • VoipAutoReplyCustomizationCard │ │    - Checks isAutoReplyEnabled()     │
│    - Master Switch & 3m Cooldown  │ │    - Enforces 3-min caller cooldown  │
└───────────────────────────────────┘ └──────────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - **Timer Expiration & Zero-Floor Clamp**:
    - `FocusStateManager.checkAndExpireIfNeeded()`: Checks if `activeProfile != FocusProfile.OFF && expiresAtMillis != null && System.currentTimeMillis() >= expiresAtMillis`; if true, immediately invokes `setProfile(FocusProfile.OFF)` and returns `true`.
    - `FocusStateManager.setProfile(profile, duration)`: When activating a profile while `duration == DurationOption.UNTIL_TURNED_OFF`, respects `duration.durationMinutes`; when `setCustomDurationMinutes(minutes)` is used or a timed `DurationOption` is selected, sets `expiresAtMillis` and starts the 500ms-precision expiration loop.
    - `FocusStateManager.adjustRemainingTime(context, deltaMinutes)`: If subtracting time (`-15m`) brings `currentExpiry + delta <= now`, immediately calls `setProfile(FocusProfile.OFF)` so the shield deactivates cleanly instead of clamping or going negative.
    - `ZenShieldForegroundService`: Formats remaining time with `maxOf(0L, expiryTime - now)` in `tv_expanded_duration` / `tv_collapsed_duration` when timed (hiding the unbounded countdown `Chronometer` so SystemUI can never render `-00:01`), sets `builder.setTimeoutAfter(remainingMs)`, and calls `setProfile(FocusProfile.OFF)` as soon as `remainingMs <= 0L`.
  - **Auto-Reply Toggle & 3-Minute Cooldown**:
    - `FocusStateManager.toggleAutoReply()`: Flips `autoReplyEnabled`, persists to `SharedPreferences`, plays `ExpressiveHaptics.playSpringTap(appContext)`, and triggers `ZenShieldForegroundService.update(appContext)`.
    - `FocusStateManager.shouldSendAutoReply(senderName)`: Returns `false` if `!isAutoReplyEnabled()`, `false` if `isRecentOutgoingCall(senderName)`, and checks `(now - lastTimestamp) > THREE_MINUTES_MS` (`180_000L`).
