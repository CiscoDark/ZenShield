# Sentinal Rust Core Engine & Kotlin Bridge Architecture

This plan introduces a portable, zero-dependency **Rust Core Crate** alongside a **1:1 Rust-mirrored Kotlin Core Engine** and an interactive **Rust Core Engine Inspector** inside Sentinal, modeling the Focus State Machine, Schedule Evaluator, Notification Priority Classifier, and Call Bouncer Cooldown Matcher with strict algebraic data types (`enum`, `Result`, immutable transitions).

## User Review & Critical Decisions

> [!IMPORTANT]
> Please review the confirmed architectural scope below before we proceed to implementation:

- **Confirmed Decision 1 (Architecture)**: **Kotlin Android App + Portable Rust Core Crate** — Preserve full Android framework compatibility (`TileService`, `NotificationListenerService`, `CallScreeningService`, `Room`, and Jetpack Compose UI) while providing a complete, idiomatic Rust crate (`Cargo.toml` + modular Rust source engines + FFI/JNI C-ABI bridge contracts) and refactoring the active Kotlin runtime engine to execute the exact same deterministic state-machine transitions.
- **Confirmed Decision 2 (Core Engines Modeled in Rust)**:
  1. **Focus State Machine & Schedule Evaluation Engine**: Deterministic profile transitions (`Off`, `DoNotDisturb`, `Gaming`, `Work`, `Sleep`, `Cinema`), 5-second game launch confirmation state machine, per-session manual turn-off suppression, and overnight/same-day schedule window evaluation.
  2. **Notification Priority Classifier & Global Focus Rules Engine**: Zero-allocation keyword, package, and category classification (`SecurityAndUrgent`, `DirectMessages`, `WorkAndCalendar`, `SocialAndCommunity`, `NewsAndMedia`, `PromotionsAndDeals`) mapped to priority levels (`P1 Critical` through `P4 Low`) and enforcement actions (`AllowAll`, `MuteSilent`, `HideCompletely`).
  3. **Call Bouncer Cooldown & Repeat-Caller Matcher**: Trailing-9-digit E.164 phone number and VoIP handle normalization, 180-second (3-minute) repeat-caller emergency breakthrough state machine, 10-minute outgoing-call exemption guard, and 3-minute per-caller auto-reply cooldown matcher.

---

## 1. Overview & Core Concept

- **What It Does**: Extracts Sentinal's three mission-critical decision engines into a pure, deterministic Rust core library accompanied by a matching Kotlin algebraic state machine bridge and an interactive **Rust Core Engine & State Machine Diagnostics Card** in the System tab.
- **Target Audience / Persona**: Privacy-conscious Android power users, gamers, and systems engineers who want provably deterministic call/notification filtering and portable Rust core logic that can be compiled with `cargo test` / `cargo build` or inspected live on-device.
- **Key Value**: Eliminates race conditions and scattered mutable flags by modeling every Focus transition, schedule window boundary, notification classification, and repeat-caller check as an explicit input event producing an immutable state transition and side-effect command list.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Live Rust State Machine Execution**: Whenever a game launches, a schedule triggers, a Quick Settings tile is tapped, or an incoming call/notification arrives, Sentinal routes the event through the deterministic Rust-mirrored state machine engine (`SentinalRustCoreBridge`), returning an immutable `TransitionOutcome` (`NewState` + `Vec<EngineCommand>`).
  2. **Interactive Rust Core Diagnostics & Inspector (System Tab)**: Users can open the **System** tab to view the **Rust Core Engine Inspector**, run live deterministic test vectors against the 3 engines (State Machine, Notification Classifier, Call Bouncer Matcher), inspect the generated Rust `TransitionOutcome` / `CallDecision` / `RuleDecision` payloads in real time, and browse or copy the portable Rust crate modules.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Material 3 Tonal Precision with clean technical telemetry accents.
  - *Color Palette & Mood*: Uses `MaterialTheme.colorScheme.surfaceContainer`, `primaryContainer`, and `secondaryContainer` tonal surfaces paired with semantic status badges for `AllowAll`, `MuteSilent`, `HideCompletely`, and `Breakthrough`.
  - *Typography & Hierarchy*: Bold Material 3 display headers paired with `JetBrainsMonoFontFamily` for Rust enum variants, state transition signatures, and execution latency metrics.
  - *Component Styling & Layout*: Generous 16dp–20dp card padding, 28dp rounded tonal containers, interactive filter chips for switching between the 3 core engines, and 48dp minimum touch targets on all simulation and inspection buttons.
- **Interactive Feedback & Motion**: Spring-animated state transition cards, haptic feedback on live test vector execution, and instant visual diffing of `BeforeState -> Event -> AfterState + Commands`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Deterministic Event-Driven State Machine (`State + Event -> (State, Vec<SideEffect>)`)**
  - *Chosen Approach*: Model all three engines as pure functions without hidden global state or wall-clock side effects (`now_ms` is passed explicitly as an argument).
  - *Why*: Pure state machines guarantee that edge cases—such as turning off Gaming Mode from the notification bar while a game is in the foreground, or receiving a second call within 180 seconds—behave identically every time and can be unit-tested deterministically.
  - *Alternatives Considered*: Keeping ad-hoc imperative `if/else` mutations inside Android services, which makes cross-feature interactions harder to reason about and port.
- **Decision 2: Dual Portable Rust Crate + Active Kotlin Algebraic Mirror**
  - *Chosen Approach*: Ship the complete, compilable Rust crate (`Cargo.toml` + Rust modules + unit tests + JNI C-ABI exports) in the project alongside an active Kotlin mirror (`SentinalRustCoreBridge`) that executes the exact same algebraic types at runtime in the cloud Android build container.
  - *Why*: Google Play policy prohibits dynamic loading of external `.so` binaries at runtime, and the cloud Android build environment uses the standard Gradle/Kotlin toolchain without requiring an external NDK cross-compile step. This gives you a 100% authentic, portable Rust crate for `cargo` builds plus immediate live execution in the Android emulator.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                   ANDROID FRAMEWORK & JETPACK COMPOSE UI                  │
│  ┌─────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │ MainActivity & Tabs │  │ Quick Settings Tiles │  │ Rust Core        │  │
│  │ (Shield / System)   │  │ (Gaming & Sleep)     │  │ Inspector Card   │  │
│  └──────────┬──────────┘  └──────────┬───────────┘  └────────┬─────────┘  │
└─────────────┼────────────────────────┼───────────────────────┼────────────┘
              │                        │                       │
              ▼                        ▼                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                FOCUS STATE MANAGER & ANDROID SERVICES LAYER               │
│   GameDetectorService • NotificationGuardService •ZenShieldForegroundSvc  │
└──────────────────────────────────────┬────────────────────────────────────┘
                                       │
                                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│            SENTINAL RUST CORE ENGINE (PORTABLE CRATE & BRIDGE)            │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ 1. Focus State Machine & Schedule Engine (focus_fsm.rs)             │  │
│  │    • FocusEvent::{GameObserved, ConfirmPromptExpired, ManualToggle, │  │
│  │      ScheduleTick} -> TransitionOutcome { state, commands }         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ 2. Notification Classifier & Rules Engine (notif_classifier.rs)     │  │
│  │    • NotificationPayload -> ClassificationResult { category,        │  │
│  │      priority: P1..P4, action: AllowAll | MuteSilent | Hide }       │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ 3. Call Bouncer & Cooldown Matcher (call_bouncer.rs)                │  │
│  │    • E.164 Trailing-9 Normalizer • 180s Repeat Breakthrough         │  │
│  │    • 600s Outgoing Call Guard • 180s Auto-Reply Cooldown            │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **Engine 1 — Focus State Machine (`FocusEngineState` & `FocusEvent`)**:
  - Tracks `active_profile`, `active_game_pkg`, `declined_game_pkg_for_session`, `manually_dismissed_whilst_in_game`, `pending_prompt_countdown`, and schedule configuration (`WorkScheduleWindow`, `SleepScheduleWindow`).
  - Emits explicit `EngineCommand` values (`StartForegroundShield`, `StopForegroundShield`, `ShowGameConfirmPrompt`, `DismissGameConfirmPrompt`, `SyncQuickSettingsTiles`, `LogCompletedSessionToRoom`).
- **Engine 2 — Notification Priority Classifier (`NotificationInput` -> `ClassificationOutcome`)**:
  - Evaluates OTP/2FA regexes, urgent keywords (`URGENT`, `EMERGENCY`, `911`, security codes), package categories (messaging, work/calendar, social, news, shopping), user category overrides, and priority-to-action rules (`P1 Critical` -> `AllowAll`, `P2 High` -> `MuteSilent`, `P3/P4` -> `HideCompletely`).
- **Engine 3 — Call Bouncer & Cooldown Matcher (`CallEvaluationInput` -> `CallBounceDecision`)**:
  - Normalizes caller identifiers to a canonical lookup key (trailing 9 digits for phone numbers, lowercase trimmed handle for VoIP).
  - Evaluates in strict priority order: `DeclineAllLockdown` -> `StarredContactBypass` -> `RepeatCallerWithin180sBreakthrough` -> `BounceAndEvaluateAutoReply(cooldown_180s, outgoing_guard_600s)`.

### Interactive Component & State Mapping

- **Rust Core Engine Inspector Card (System Tab)**:
  - **Engine Selector Chips**: Switch between `Focus FSM`, `Notification Classifier`, and `Call Bouncer`.
  - **Interactive Test Vector Runner**: One-tap buttons to fire real events into the engine (e.g., `Simulate Game Launch + Manual Shade Turn-Off`, `Test 2FA OTP vs Promo Alert`, `Test 2nd Call Within 180s Repeat Breakthrough`) and view the exact Rust struct output and execution time in microseconds.
  - **Rust Source & Crate Viewer**: Expandable code viewer displaying the portable Rust module implementation for the selected engine.
