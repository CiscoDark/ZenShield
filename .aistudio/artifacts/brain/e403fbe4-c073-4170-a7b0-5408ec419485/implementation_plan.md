# Phase 13: Sentinel Resizable Material You Home Screen Widget (2x1 Minimum)

## Overview
Add a resizable **2x1 Material You Home Screen Widget** to **Sentinel** (`com.zenshield.focus`) so users can monitor their active focus profile at a glance and toggle focus mode on or off directly from their home screen, plus a live in-app widget preview and one-tap **Pin to Home Screen** launcher action.

---

## 1. Widget Metadata & Drawable Resources
* **`/app/src/main/res/xml/sentinel_widget_info.xml`**
  * Configure `appwidget-provider` with:
    * `android:minWidth="140dp"` and `android:minHeight="70dp"` (fits a compact 2x1 cell grid).
    * `android:targetCellWidth="2"` and `android:targetCellHeight="1"` for Android 12+ launchers.
    * `android:resizeMode="horizontal|vertical"` so users can resize from a 2x1 pill up to larger grids.
    * `android:initialLayout="@layout/widget_sentinel"` and `android:widgetCategory="home_screen"`.
* **Custom Material You RemoteViews Drawables (`/app/src/main/res/drawable/`)**
  * `bg_widget_sentinel_inactive.xml` & `bg_widget_sentinel_active.xml`: Deep slate Material You card surfaces (`#131722` inactive / `#162238` active with subtle accent stroke and `24dp` rounded corners).
  * `bg_widget_toggle_pill_off.xml` & `bg_widget_toggle_pill_on.xml`: Sleek pill toggle button backgrounds (`#232A3B` when inactive, vibrant Material You teal/indigo accent `#14B8A6` / `#38BDF8` when engaged).
  * `ic_widget_shield_active.xml` & `ic_widget_shield_inactive.xml`: Crisp vector shield status icons for `RemoteViews`.

---

## 2. Widget Layout XML (`/app/src/main/res/layout/widget_sentinel.xml`)
* Build a dark Material You `RemoteViews`-compatible layout (`RelativeLayout` / `LinearLayout`):
  * **Left Status Touch Zone (`@+id/widget_left_container`):**
    * Tapping opens `MainActivity` directly to inspect or customize Sentinel.
    * Displays the circular shield badge (`@+id/widget_status_icon`), primary title (`@+id/widget_title_text`: `"Sentinel Active"` or `"Sentinel Inactive"`), and active profile/rule subtitle (`@+id/widget_subtitle_text`: e.g., `"Do Not Disturb • Bouncer On"` or `"Tap toggle to engage DND"`).
  * **Right Toggle Button Touch Zone (`@+id/widget_toggle_button`):**
    * Tapping fires `com.zenshield.focus.ACTION_TOGGLE_WIDGET` via `PendingIntent.getBroadcast(...)`.
    * Displays the current state label (`"ON"` / `"OFF"`) with dynamic pill background and high-contrast text.

---

## 3. AppWidgetProvider & Real-Time State Sync
* **`/app/src/main/java/com/example/widgets/SentinelWidgetProvider.kt`**
  * Implement `SentinelWidgetProvider : AppWidgetProvider()`:
    * Handle both `AppWidgetManager.ACTION_APPWIDGET_UPDATE` and `ACTION_TOGGLE_WIDGET = "com.zenshield.focus.ACTION_TOGGLE_WIDGET"`.
    * When `ACTION_TOGGLE_WIDGET` is received:
      * Read current profile via `FocusStateManager.getActiveProfile(context)`.
      * If `activeProfile != FocusProfile.OFF`, turn Sentinel **OFF** (`FocusProfile.OFF`).
      * If `activeProfile == FocusProfile.OFF`, immediately activate **Do Not Disturb** (`FocusProfile.DO_NOT_DISTURB`) and start `FocusForegroundService`.
    * Update all active widget instances via `updateAllWidgets(context)` with dynamic titles, active profile name, shield icon, card surface, and toggle pill styling.
* **`/app/src/main/java/com/example/core/FocusStateManager.kt`**
  * Add `FocusStateManager.getActiveProfile(context: Context): FocusProfile` to the companion object.
  * Automatically call `SentinelWidgetProvider.updateAllWidgets(appContext)` whenever `persistState(...)` or `setProfile(...)` runs so profile changes from the app, Quick Settings tile, notification shade, Work Schedule, or Auto-Game detector immediately reflect on the home screen widget.

---

## 4. Manifest Registration & In-App Live Preview / Pin Card
* **`/app/src/main/AndroidManifest.xml`**
  * Register `.widgets.SentinelWidgetProvider` with `android:exported="true"`, `<action android:name="android.appwidget.action.APPWIDGET_UPDATE" />`, `<action android:name="com.zenshield.focus.ACTION_TOGGLE_WIDGET" />`, and `@xml/sentinel_widget_info`.
* **In-App Live Widget Preview & Pin Action (`FocusDashboardScreen.kt`)**
  * Add a compact **Home Screen Widget** card on the Command tab showing a live interactive replica of the 2x1 Material You widget (where the right button toggles focus and the **Pin to Home Screen** button calls `AppWidgetManager.requestPinAppWidget(...)` on Android 8.0+).
