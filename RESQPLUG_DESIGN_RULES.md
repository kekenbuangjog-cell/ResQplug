# ResQPlug — Design Rules & Resource Guide
### Quick Reference for Developers · Capstone 2026–2027

> **This document is the single source of truth** for all visual and design rules
> in the ResQPlug Android application. Every rule is derived from the existing
> `res/` folder contents and the full `DESIGN_STYLE_GUIDE.md`.

---

## Table of Contents

1. [Color System](#1-color-system)
2. [Typography Rules](#2-typography-rules)
3. [Drawable Rules](#3-drawable-rules)
4. [Layout Rules](#4-layout-rules)
5. [Theme Rules](#5-theme-rules)
6. [String Resource Rules](#6-string-resource-rules)
7. [Animation Rules](#7-animation-rules)
8. [Forbidden Patterns](#8-forbidden-patterns)

---

## 1. Color System

**Source:** `res/values/colors.xml`

All colors are **fixed hex values**. No Material theming. No runtime color changes. Reproduce hex values exactly.

### Primary UI Palette

| Token | Hex | RGB | Usage Rule |
|-------|-----|-----|------------|
| `bg_black` | `#0A0A0A` | 10, 10, 10 | Body background, overlays, scene backdrop, letterbox fill |
| `bg_navy` | `#1A1A2E` | 26, 26, 46 | Button fills, progress bar backgrounds, panel fills |
| `accent_green` | `#00FF88` | 0, 255, 136 | Primary accent — borders, progress fills, interactive text, HUD |
| `accent_green_alt` | `#2ECC71` | 46, 204, 113 | Logo color, packet sprites, foliage highlight |
| `text_white` | `#FFFFFF` | 255, 255, 255 | Primary text on dark backgrounds |
| `text_gray` | `#7F8C8D` | 127, 140, 141 | Sub-labels, metadata, captions |
| `border_dark` | `#333333` | 51, 51, 51 | Panel borders, dividers |

### Status / Alert Palette

| Token | Hex | RGB | Usage Rule |
|-------|-----|-----|------------|
| `alert_red` | `#E74C3C` | 231, 76, 60 | SOS messages, danger labels, responder helmet ONLY |
| `alert_amber` | `#F39C12` | 243, 156, 18 | EVAC messages, warning glow, siren beacon ONLY |
| `status_ok` | `#2ECC71` | 46, 204, 113 | OK/status messages, LoRa active LED, trees ONLY |
| `signal_cyan` | `#00FFFF` | 0, 255, 255 | Electrical spark effects, RF indicator LED ONLY |
| `packet_green` | `#00FF88` | 0, 255, 136 | Data packet sprites travelling the mesh ONLY |

### Color Enforcement Rules

| Rule | Enforcement |
|------|-------------|
| **Exact hex only** | Never use `Color.RED`, `Color.GREEN` — always `Color.parseColor("#E74C3C")` |
| **No alpha blending** | Binary alpha only: 0 or 255 on solid sprites |
| **No gradients on game objects** | Gradients allowed ONLY on progress bar fill |
| **No approximation** | Use the exact hex, not "close enough" Material colors |
| **Android resource** | Always reference `@color/token_name` in XML, not hardcoded hex |

### How to Reference Colors

```xml
<!-- In XML layouts — ALWAYS use resource reference -->
android:textColor="@color/accent_green"
android:background="@color/bg_black"
```

```kotlin
// In Kotlin — use resource reference or exact parse
val color = ContextCompat.getColor(context, R.color.accent_green)
// OR
val color = Color.parseColor("#00FF88")
```

---

## 2. Typography Rules

**Source:** `res/values/strings.xml`, layout XMLs

### Font Selection

| Platform | Primary Font | Fallback |
|----------|-------------|---------|
| Web (existing) | `'Courier New', monospace` | System monospace |
| **Android (current)** | `monospace` | System monospace |
| **Android (target)** | **Press Start 2P** (bundled TTF) | `monospace` |

> **TODO:** Bundle `press_start_2p.ttf` at `app/src/main/res/font/press_start_2p.ttf`
> Download: https://fonts.google.com/specimen/Press+Start+2P

### Size Scale

| Role | Android (sp) | Canvas (px at 1x) | When to Use |
|------|-------------|-------------------|-------------|
| Tiny / HUD | 7 sp | 28 px | Scene progress, device ID, captions |
| Label | 8 sp | 32 px | Panel titles, node count, queue labels |
| Button | 9 sp | 36 px | Action buttons (SOS, EVAC, STATUS) |
| Status | 10 sp | 40 px | Searching/connecting status text |
| Header | 12 sp | 48 px | App title, header bar text |
| Logo | 22 sp | 88 px | Start screen title (future) |

### Case Rules

| Element | Required Case |
|---------|--------------|
| All button labels | ALL CAPS |
| All panel titles | ALL CAPS |
| All status messages | ALL CAPS |
| All node names | Title Case (e.g., "Node-A") |
| Device IDs | Uppercase (e.g., "RQP-SIM-1234") |

### Text Style Rules

| Property | Rule |
|----------|------|
| **Bold** | Allowed on headers, labels, buttons |
| **Italic** | ONLY on taglines/sub-text — NEVER on UI labels |
| **Letter spacing** | +0.1 em on headings |
| **Line height** | 1.6x font size |
| **Alignment** | Left-aligned for data, center-aligned for titles |

### How to Reference Fonts

```xml
<!-- In XML — current (monospace fallback) -->
android:fontFamily="monospace"

<!-- In XML — target (Press Start 2P) -->
android:fontFamily="@font/press_start_2p"
```

```kotlin
// In Kotlin
val typeface = ResourcesCompat.getFont(context, R.font.press_start_2p)
paint.typeface = typeface
paint.isAntiAlias = false  // MANDATORY
paint.isSubpixelText = false  // MANDATORY
```

---

## 3. Drawable Rules

**Source:** `res/drawable/`

### Current Drawables

```
res/drawable/
├── ic_launcher_background.xml     App icon background
├── ic_launcher_foreground.xml     App icon foreground
├── pixel_button_sos.xml           SOS button background (red)
├── pixel_button_evac.xml          EVAC button background (amber)
├── pixel_button_status.xml        STATUS button background (green)
├── pixel_status_box.xml           Panel border/background
├── progress_bar_retro.xml         Green progress bar
├── progress_bar_evac.xml          Amber progress bar
└── progress_bar_sos.xml           Red progress bar
```

### Naming Conventions

| Prefix | Use For | Examples |
|--------|---------|----------|
| `pixel_` | Retro-styled UI elements | `pixel_button_sos.xml`, `pixel_status_box.xml` |
| `progress_bar_` | Progress bar drawables | `progress_bar_retro.xml`, `progress_bar_sos.xml` |
| `ic_` | App icons | `ic_launcher_foreground.xml` |
| `bg_` | Background shapes | (future) |

### Button Drawable Rules

| Property | Value | Rule |
|----------|-------|------|
| Background fill | `#1A1A2E` (bg_navy) | All buttons use navy fill |
| Border width | 2dp | Consistent across all buttons |
| Border color | `#00FF88` (accent_green) | Green border on all buttons |
| Corner radius | 0dp (sharp edges) | Pixel-perfect — NO rounded corners |
| Pressed state fill | `#00FF88` (accent_green) | Invert on press |
| Pressed text color | `#1A1A2E` (bg_navy) | Invert on press |

### Panel Drawable Rules

| Property | Value | Rule |
|----------|-------|------|
| Background fill | `#1A1A2E` (bg_navy) | Navy fill for all panels |
| Border width | 2dp | Consistent border |
| Border color | `#333333` (border_dark) | Dark border for panels |
| Corner radius | 0dp | Sharp edges only |

### Progress Bar Rules

| Property | Value | Rule |
|----------|-------|------|
| Background | `#1A1A2E` (bg_navy) | Dark track |
| Fill color | `#00FF88` (accent_green) | Green for default/status |
| Fill color | `#E74C3C` (alert_red) | Red for SOS progress |
| Fill color | `#F39C12` (alert_amber) | Amber for EVAC progress |
| Height | 8dp | Consistent height |
| Corner radius | 0dp | Sharp pixel edges |

### How to Create New Drawables

```xml
<!-- pixel_button_template.xml -->
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/accent_green"/>
            <stroke android:width="2dp" android:color="@color/accent_green"/>
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/bg_navy"/>
            <stroke android:width="2dp" android:color="@color/accent_green"/>
        </shape>
    </item>
</selector>
```

---

## 4. Layout Rules

**Source:** `res/layout/`

### Current Layouts

| Layout | Activity | Background | Structure |
|--------|----------|------------|-----------|
| `activity_main.xml` | `MainActivity` | `@color/bg_black` | StarfieldView + center content |
| `activity_dashboard.xml` | `DashboardActivity` | `@color/bg_black` | StarfieldView + vertical panels |

### Layout Structure Rules

| Rule | Value |
|------|-------|
| Root background | ALWAYS `@color/bg_black` |
| Panel background | ALWAYS `@drawable/pixel_status_box` |
| Orientation | Vertical primary, horizontal for rows |
| Padding | 12dp consistent |
| Panel spacing | 8dp spacers between panels |
| Button spacing | 12dp between buttons |

### Panel Hierarchy

```
FrameLayout (root)
├── StarfieldView (full screen background)
├── LinearLayout (main content, vertical)
│   ├── Header Bar
│   ├── Panel: Mesh Network
│   ├── Panel: Message Queue
│   ├── Action Buttons (horizontal)
│   └── Bottom Bar: Scene Progress
└── FadeOverlay (transition overlay)
```

### Button Layout Rules

| Property | Value |
|----------|-------|
| Width | `wrap_content` |
| Height | `wrap_content` |
| Padding horizontal | 18dp–24dp |
| Padding vertical | 10dp |
| Text size | 9sp |
| Text style | bold |
| Text color | `@color/text_white` |
| Background | `@drawable/pixel_button_*` |

---

## 5. Theme Rules

**Source:** `res/values/themes.xml`

### Required Theme Properties

```xml
<style name="Base.Theme.ResQplug" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="android:windowFullscreen">true</item>
    <item name="android:statusBarColor">@color/bg_black</item>
    <item name="android:navigationBarColor">@color/bg_black</item>
</style>
```

### AndroidManifest.xml Requirements

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="landscape"          <!-- MANDATORY -->
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:theme="@style/Theme.ResQplug" />
```

### Theme Enforcement Rules

| Rule | Value | Reason |
|------|-------|--------|
| Fullscreen | `true` | Immersive retro experience |
| Status bar color | `#0A0A0A` | Matches game background |
| Navigation bar color | `#0A0A0A` | Matches game background |
| Keep screen on | `true` | Prevent sleep during emergency use |
| Orientation | `landscape` | 16:9 virtual resolution (960x540) |
| No action bar | `true` | Full screen real estate for game |

---

## 6. String Resource Rules

**Source:** `res/values/strings.xml`

### Naming Conventions

| Prefix | Use For | Examples |
|--------|---------|----------|
| `app_` | App-wide strings | `app_name` |
| `splash_` | Splash screen strings | `splash_searching`, `splash_connected` |
| `dash_` | Dashboard strings | `dash_mesh_network`, `dash_btn_sos` |
| `scene_` | Scene-specific strings (future) | `scene_1_title`, `scene_2_title` |

### Current Strings

```xml
<!-- Splash -->
splash_searching    "[ SEARCHING FOR RESQPLUG DEVICE ]"
splash_connecting   "[ CONNECTING TO RESQPLUG DEVICE ]"
splash_connected    "[ RESQPLUG CONNECTED : MESH READY ]"
splash_device_id    "DEVICE ID: %1$s"

<!-- Dashboard -->
dash_title          "RESQPLUG"
dash_mesh_network   "MESH NETWORK"
dash_nodes_active   "%1$d NODES ACTIVE"
dash_signal         "SIGNAL"
dash_message_queue  "MESSAGE QUEUE"
dash_sos            "SOS"
dash_evac           "EVAC"
dash_status         "STATUS"
dash_btn_sos        "[ SOS ]"
dash_btn_evac       "[ EVAC ]"
dash_btn_status     "[ STATUS ]"
dash_sent           "SENT"
dash_scene_progress "SCENE %1$d of 11"
```

### String Rules

| Rule | Enforcement |
|------|-------------|
| **ALL CAPS** | All UI-facing text must be uppercase |
| **No hardcoded strings** | Always use `@string/` resource references |
| **Format strings** | Use `%1$s`, `%1$d` for dynamic values |
| **Brackets** | Wrap button text in `[ ]` for retro look |
| **No trailing spaces** | Clean string values only |

---

## 7. Animation Rules

**Source:** `DESIGN_STYLE_GUIDE.md`, `MainActivity.kt`

### The 125ms Tick (8 FPS)

```
8 frames per second = 1000 ms / 8 = 125 ms per frame step
```

### Current Animations in Code

| Animation | Location | Tick Duration | Pattern |
|-----------|----------|---------------|---------|
| Searching dots | `MainActivity.kt:220` | 250ms (2 ticks) | 4-frame stepped |
| Emoticon blink | `MainActivity.kt:240` | 500ms (4 ticks) | 2-frame on/off |
| Starfield twinkle | `MainActivity.kt:251` | 125ms (1 tick) | Continuous drift |
| Connection dots | `MainActivity.kt:165` | 250ms (2 ticks) | 4-frame stepped |
| Fade to dashboard | `MainActivity.kt:203` | 125ms (1 tick) | 8-step alpha ramp |
| Dashboard fade in | `DashboardActivity.kt:103` | 125ms (1 tick) | 8-step alpha ramp |

### Animation Enforcement Rules

| Rule | Value | Reason |
|------|-------|--------|
| **Game loop tick** | 125ms fixed | 8 FPS retro feel |
| **Sprite animations** | 4 frames max | Pixel-art constraint |
| **Frame stepping** | `currentFrame = (currentFrame + 1) % frames.size` | No easing |
| **Alpha transitions** | Step by 32 per tick (255/8) | 1-second fade |
| **Dot animations** | Step every 2 ticks (250ms) | Readable speed |

### Permitted Smooth Transitions (UI Layer Only)

| Element | Duration | Type |
|---------|----------|------|
| Scene fade-in/fade-out | 1000ms | Linear alpha overlay |
| Progress bar fill | 600ms | Linear width |
| Start button pulse | 1800ms | Sine scale 1.0→1.04 |
| Music volume crossfade | 700ms | Linear gain ramp |
| Speech bubble appear | 200ms | Linear alpha 0→1 |

### Forbidden Animation Patterns

```kotlin
// FORBIDDEN — smooth animator on game sprite
ValueAnimator.ofFloat(-3f, 3f).apply {
    duration = 2200
    interpolator = AccelerateDecelerateInterpolator()
    start()
}

// CORRECT — stepped 8 FPS
val angles = floatArrayOf(-3f, -1f, 1f, 3f)
sprite.angle = angles[sprite.frame % 4]
sprite.frame++
```

---

## 8. Forbidden Patterns

### Quick Reference Table

| Category | FORBIDDEN | ALLOWED |
|----------|-----------|---------|
| **Colors** | Gradients on sprites | Gradients on progress bars only |
| **Colors** | Colors outside palette | Exact hex from `colors.xml` |
| **Colors** | `Color.RED`, `Color.GREEN` | `Color.parseColor("#E74C3C")` |
| **Typography** | Italic on UI labels | Italic on taglines only |
| **Typography** | Lowercase labels | ALL CAPS for all UI text |
| **Typography** | Text shadows | No shadows on any text |
| **Shapes** | Rounded corners > 4px | Sharp edges (0px) preferred |
| **Shapes** | Drop shadows | No shadows on any element |
| **Shapes** | Gradient fills on panels | Solid fills only |
| **Animation** | `ValueAnimator` on sprites | Stepped frame arrays |
| **Animation** | `ObjectAnimator` on game objects | Manual tick-based updates |
| **Animation** | `AccelerateDecelerateInterpolator` | Linear or stepped only |
| **Animation** | Smooth easing on world objects | Only on UI layer elements |
| **Rendering** | `paint.isAntiAlias = true` | `paint.isAntiAlias = false` |
| **Rendering** | `paint.isFilterBitmap = true` | `paint.isFilterBitmap = false` |
| **Rendering** | `paint.isDither = true` | `paint.isDither = false` |
| **Rendering** | Bilinear filtering | Nearest-neighbor only |
| **Audio** | WAV/MP3/OGG files | PCM-synthesized chiptune only |
| **Layout** | Hardcoded strings | Always `@string/` references |
| **Layout** | Hardcoded colors in XML | Always `@color/` references |

### Common Mistakes to Avoid

| Mistake | Correct Approach |
|---------|-----------------|
| Using `Color.parseColor("#FF0000")` for red | Use `@color/alert_red` (`#E74C3C`) |
| Setting `android:fontFamily="sans-serif"` | Use `monospace` or `@font/press_start_2p` |
| Adding `android:elevation="4dp"` | Remove — no shadows |
| Using `android:radius="8dp"` on buttons | Use `android:radius="0dp"` |
| Creating animation with `ObjectAnimator` | Use `delay(frameDurationMs)` loop |
| Hardcoding `"SEARCHING..."` in layout | Use `@string/splash_searching` |
| Using `android:background="#1A1A2E"` | Use `@color/bg_navy` |

---

## Appendix: File Locations

| Resource | Path |
|----------|------|
| Colors | `app/src/main/res/values/colors.xml` |
| Strings | `app/src/main/res/values/strings.xml` |
| Themes | `app/src/main/res/values/themes.xml` |
| Drawables | `app/src/main/res/drawable/` |
| Layouts | `app/src/main/res/layout/` |
| USB Filter | `app/src/main/res/xml/device_filter.xml` |
| Full Style Guide | `DESIGN_STYLE_GUIDE.md` (root) |
| This Document | `RESQPLUG_DESIGN_RULES.md` (root) |

---

*Last updated: 2026-08-28 · ResQPlug Capstone 2026-2027 · University of Cebu Banilad*
