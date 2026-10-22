# Sentinal — Material 3 Expressive Design Upgrade Plan

## 1. Visual & Interaction Philosophy
We will upgrade **Sentinal** to a full **Material 3 Expressive** interface that pairs tactile shape morphing, bouncy spring physics, and high-contrast editorial typography with profile-reactive color palettes:

- **Dynamic Shape Morphing**: Interactive cards, Bento profile tiles, duration chips, and primary action buttons dynamically morph their corner radii and proportions based on selection and press states (transitioning smoothly between sculpted squircles, asymmetric expressive containers, and full pills).
- **Bouncy Spring Motion System**: All state transitions—including profile switching, shield activation, telemetry counter updates, and navigation tab changes—are driven by expressive spring physics (`dampingRatio = Spring.DampingRatioLowBouncy` and `Spring.DampingRatioMediumBouncy`) instead of linear tween curves.
- **Editorial Typography & Scannable Hierarchy**: Oversized `Plus Jakarta Sans` display headers and high-contrast `JetBrains Mono` live telemetry readouts create clear focal points on the Hero Shield Console and Weekly Analytics views.
- **Profile-Reactive Aura & Surface Tinting**: Tonal surface containers, glowing borders, and multi-stop radial/linear gradients adapt smoothly to the active Focus profile (**Gaming**, **Do Not Disturb**, **Work**, **Sleep**, or **Standby**), while respecting Material You dynamic color and AMOLED Pure Black modes.

---

## 2. Architecture & Expressive Component Flow

```text
+-------------------------------------------------------------------------+
|                    Material 3 Expressive Theme Engine                   |
|  - ExpressiveShapes token scale (sculpted squircles <-> pill radii)     |
|  - ExpressiveMotion spring specs (bouncy spatial & color transitions)   |
|  - Profile-Reactive Tonal Palettes (Gaming / DND / Work / Sleep)        |
+------------------------------------+------------------------------------+
                                     |
         +---------------------------+---------------------------+
         |                           |                           |
         v                           v                           v
+-----------------------+   +-----------------------+   +-----------------------+
| Expressive Hero &     |   | Morphing Bento Grid   |   | Floating Expressive   |
| Telemetry Cards       |   | & Control Pills       |   | Pill Toolbar          |
| - Animated corner     |   | - 2x2 profile tiles   |   | - Elevated capsule    |
|   morphing on active  |   |   morph from 26dp     |   |   docked above nav    |
|   shield state        |   |   squircle to 42dp    |   |   bar insets          |
| - Breathing radial    |   |   expressive pill     |   | - Expanding spring    |
|   aura & spring       |   | - Spring scale &      |   |   pill indicator with |
|   toggle button       |   |   border luminance    |   |   icon + active label |
+-----------------------+   +-----------------------+   +-----------------------+
```

---

## 3. Core Capabilities & UI Upgrades

### A. Material 3 Expressive Theme & Motion Tokens
- **Expressive Shape Scale**: Define an explicit M3 Expressive `Shapes` hierarchy (`extraSmall` through `extraLarge`) alongside reusable animated corner-morphing helpers that interpolate corner radii on press and selection.
- **Expressive Spring Presets**: Centralize bouncy spatial springs (for scale, width expansion, and corner radius morphing) and smooth tonal springs (for container colors, glowing borders, and background gradients).
- **Editorial Type Scale Refinements**: Enhance display, headline, and monospace telemetry styles with tighter display tracking and bold weight contrast for instant scannability.

### B. Expressive Hero Shield Console & Bento Profile Selector
- **Morphing Hero Status Card**:
  - Morphs its outer silhouette and inner status badge geometry when switching between **Standby** and **Active Shield** states.
  - Features an animated multi-stop aura backdrop, oversized live countdown/elapsed readout, expressive pill metric badges, and a spring-loaded primary action button that morphs shape on activation.
- **Shape-Morphing 2x2 Bento Profile Grid**:
  - Each Focus Profile tile (**Gaming**, **DND**, **Work**, **Sleep**) dynamically morphs its corner radius, scale, and tonal elevation when selected—transforming from a structured squircle card into a sculpted expressive pill container with an illuminated accent border and animated status indicator.
- **Expressive Duration & Simulation Pills**:
  - Timer preset chips (`15m`, `30m`, `45m`, `1h`, `2h`, `∞`) and simulation triggers morph from compact rounded rectangles into expanded capsules with bouncy spring feedback when selected.

### C. Floating Expressive Pill Toolbar (Bottom Navigation)
- **Elevated Capsule Container**:
  - Replace the traditional full-width docked bottom bar with a **Floating Expressive Pill Toolbar** suspended above the system navigation bar (`WindowInsets.navigationBars`), styled with high-contrast tonal surface elevation, subtle glass/border highlights, and a full capsule silhouette.
- **Morphing Active Tab Indicator**:
  - Selecting a workspace tab (**Shield**, **Analytics**, **Rules**, **System**) triggers a bouncy spring expansion where the active tab morphs into a vibrant pill housing both the filled icon and label, while inactive tabs remain compact icon targets with full 48dp accessibility touch bounds.

### D. Expressive Analytics, Rules & System Cards
- **Weekly Shield Analytics & Telemetry**: Upgrade the Weekly Shield Activity chart card, Live Notification HUD Preview card, and Game Detection card with expressive container headers, spring-animated filter pills, and sculpted metric containers.
- **Rules & System Cards**: Refresh the Call Auto-Reply, Smart Automation, and Permission Diagnostics cards with expressive toggle rows, morphing status chips, and cohesive M3 Expressive surface hierarchy.
