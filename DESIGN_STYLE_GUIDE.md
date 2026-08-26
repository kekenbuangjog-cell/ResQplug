# ResQPlug — 8-Bit Design & Style Guide
### Android Edition · University of Cebu Banilad · Capstone 2026–2027

> **Strictly enforced.** Every rule in this document is derived directly from the
> existing Phaser web codebase and must be followed without exception in the
> Android implementation. When in doubt, make it look worse — blocky always wins.

---

## Table of Contents

1. [Aesthetic Philosophy](#1-aesthetic-philosophy)
2. [Color Palette](#2-color-palette)
3. [Typography](#3-typography)
4. [Canvas & Rendering Rules](#4-canvas--rendering-rules)
5. [Sprite Catalog & Pixel Dimensions](#5-sprite-catalog--pixel-dimensions)
6. [Animation System — 8 FPS Enforcement](#6-animation-system--8-fps-enforcement)
7. [Scene Structure & Progression](#7-scene-structure--progression)
8. [UI Chrome Components](#8-ui-chrome-components)
9. [Sound Design — Chiptune Rules](#9-sound-design--chiptune-rules)
10. [Philippine Cultural Design Elements](#10-philippine-cultural-design-elements)
11. [Android Implementation Guide](#11-android-implementation-guide)
12. [Quick Reference Checklist](#12-quick-reference-checklist)

---

## 1. Aesthetic Philosophy

ResQPlug looks like a **1988 home computer running an emergency broadcast.**
Every pixel is intentional. Every animation is stepped. Every sound is synthetic.

### Core Pillars

| Pillar | Rule |
|--------|------|
| **Pixel-perfect** | No anti-aliasing. No sub-pixel rendering. No fractional scaling. |
| **8 FPS** | All sprite and world animations tick at exactly 8 frames per second (125 ms per step). No exceptions. |
| **Terminal green** | Primary accent is `#00FF88` on near-black `#0A0A0A`. Think retro CRT monitor. |
| **Chiptune only** | All audio is synthesized via oscillators and noise buffers. No WAV samples. |
| **Philippine soul** | Characters, vehicles, buildings, and props are authentic to the Philippine setting. |

### Explicitly Forbidden

- ❌ Drop shadows via `Paint.setShadowLayer` on game objects
- ❌ Rounded corners on game sprites (UI buttons: 2–4 px max)
- ❌ Smooth easing tweens on world objects
- ❌ Any font other than the designated pixel font / monospace fallback
- ❌ Colors outside the defined palette
- ❌ Semi-transparent pixels on solid sprites (binary alpha only: 0 or 255)
- ❌ Photographic textures or gradients on sprites
- ❌ Hardware-accelerated bilinear/trilinear texture filtering

---

## 2. Color Palette

All colors are fixed. No Material Design theming. Reproduce hex values exactly.

### Primary UI Palette

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `COLOR_BG_BLACK` | `#0A0A0A` | 10, 10, 10 | Body background, overlays, scene backdrop |
| `COLOR_BG_NAVY` | `#1A1A2E` | 26, 26, 46 | Button fills, progress bar background |
| `COLOR_ACCENT_GREEN` | `#00FF88` | 0, 255, 136 | Primary interactive accent, all borders, progress fill |
| `COLOR_ACCENT_GREEN_ALT` | `#2ECC71` | 46, 204, 113 | Logo color, packet sprites, foliage highlight |
| `COLOR_TEXT_WHITE` | `#FFFFFF` | 255, 255, 255 | Primary text on dark backgrounds |
| `COLOR_TEXT_GRAY` | `#7F8C8D` | 127, 140, 141 | Sub-labels, metadata, captions |
| `COLOR_BORDER_DARK` | `#333333` | 51, 51, 51 | Game container border |

### Status / Alert Palette

| Token | Hex | Usage |
|-------|-----|-------|
| `COLOR_ALERT_RED` | `#E74C3C` | SOS messages, danger labels, responder helmet |
| `COLOR_ALERT_AMBER` | `#F39C12` | EVAC messages, siren beacon, warning glow |
| `COLOR_STATUS_OK` | `#2ECC71` | OK/status messages, LoRa active LED, trees |
| `COLOR_SIGNAL_CYAN` | `#00FFFF` | Electrical spark effects, RF indicator LED |
| `COLOR_PACKET` | `#00FF88` | Data packet sprites travelling the mesh |

### Scene / Environment Palette

| Token | Hex | Usage |
|-------|-----|-------|
| `COLOR_SKY_TOP` | `#64B5F6` | Peaceful morning sky gradient top |
| `COLOR_SKY_BOT` | `#E3F2FD` | Peaceful morning sky gradient bottom |
| `COLOR_STORM_TOP` | `#0D1117` | Typhoon/blackout sky gradient top |
| `COLOR_STORM_BOT` | `#2D3748` | Typhoon/blackout sky gradient bottom |
| `COLOR_MOUNTAIN_FAR` | `#4A6572` | Far mountain silhouette |
| `COLOR_MOUNTAIN_MID` | `#34495E` | Mid mountain silhouette |
| `COLOR_ROAD` | `#212F3D` | Asphalt road body |
| `COLOR_ROAD_MARKING` | `#F1C40F` | Yellow centre line |
| `COLOR_GROUND` | `#708090` | Ground tiles |
| `COLOR_GROUND_MUD` | `#8B4513` | Muddy ground tiles (storm scenes) |
| `COLOR_WATER` | `#85C1E9` | Water surface, ripples |
| `COLOR_RAIN` | `#74B9FF` | Raindrop sprites |

### Building / Prop Palette

| Token | Hex | Usage |
|-------|-----|-------|
| `COLOR_CONCRETE` | `#7F8C8D` | Apartment block facade |
| `COLOR_BRICK_BROWN` | `#795548` | Bahay kubo posts, arch pillars |
| `COLOR_WOOD_LIGHT` | `#8D6E63` | Timber siding, benches |
| `COLOR_WOOD_DARK` | `#5D4037` | Doors, tree trunks |
| `COLOR_TIN_SILVER` | `#BDC3C7` | Galvanized yero roofing, chrome |
| `COLOR_CLAY_TILE` | `#C0392B` | Spanish clay tile roofs |
| `COLOR_NEON_SIGN` | `#F1C40F` | Shop signboards, evacuation sign |
| `COLOR_DRRMC_ORANGE` | `#E67E22` | Command tent canopy, rescue boat |
| `COLOR_SKIN` | `#DEB887` | All character skin tone |

### Banderitas Flag Colors (strict order)

```
#E74C3C  #F1C40F  #3498DB  #2ECC71  #9B59B6  #E67E22  #00FF88
```

### Android colors.xml

```xml
<!-- res/values/colors.xml -->
<resources>
    <!-- UI -->
    <color name="bg_black">#FF0A0A0A</color>
    <color name="bg_navy">#FF1A1A2E</color>
    <color name="accent_green">#FF00FF88</color>
    <color name="accent_green_alt">#FF2ECC71</color>
    <color name="text_white">#FFFFFFFF</color>
    <color name="text_gray">#FF7F8C8D</color>
    <color name="border_dark">#FF333333</color>
    <!-- Status -->
    <color name="alert_red">#FFE74C3C</color>
    <color name="alert_amber">#FFF39C12</color>
    <color name="status_ok">#FF2ECC71</color>
    <color name="signal_cyan">#FF00FFFF</color>
    <color name="packet_green">#FF00FF88</color>
    <!-- Environment -->
    <color name="sky_top">#FF64B5F6</color>
    <color name="sky_bot">#FFE3F2FD</color>
    <color name="storm_top">#FF0D1117</color>
    <color name="storm_bot">#FF2D3748</color>
    <color name="road">#FF212F3D</color>
    <color name="road_marking">#FFF1C40F</color>
    <color name="ground">#FF708090</color>
    <color name="water">#FF85C1E9</color>
    <color name="rain">#FF74B9FF</color>
    <!-- Props -->
    <color name="clay_tile">#FFC0392B</color>
    <color name="tin_silver">#FFBDC3C7</color>
    <color name="drrmc_orange">#FFE67E22</color>
    <color name="skin">#FFDEB887</color>
    <color name="wood_light">#FF8D6E63</color>
    <color name="wood_dark">#FF5D4037</color>
    <color name="neon_sign">#FFF1C40F</color>
</resources>
```

---

## 3. Typography

### Font Selection

| Platform | Primary Font | Fallback |
|----------|-------------|---------|
| Web (existing) | `'Courier New', monospace` | System monospace |
| **Android** | **Press Start 2P** (bundled TTF) | `Typeface.MONOSPACE` |

**Download:** https://fonts.google.com/specimen/Press+Start+2P

Place at: `app/src/main/res/font/press_start_2p.ttf`

### Size Scale

| Role | Web (px) | Android (sp) | Canvas px at 1x density |
|------|---------|-------------|--------------------------|
| Logo / Title | 58 px | 22 sp | 88 px |
| Scene heading | 28 px | 14 sp | 56 px |
| Body / Dialogue | 13-15 px | 8 sp | 32 px |
| Label / Caption | 11-12 px | 7 sp | 28 px |
| Tiny / HUD | 9 px | 5 sp | 20 px |

### Text Paint (Kotlin)

```kotlin
val paint = Paint().apply {
    typeface       = ResourcesCompat.getFont(context, R.font.press_start_2p)
    isAntiAlias    = false        // MANDATORY
    isSubpixelText = false        // MANDATORY
    textSize       = 28f          // pixels for Canvas draws
    color          = Color.parseColor("#00FF88")
    textAlign      = Paint.Align.CENTER
}
```

### Typography Rules

| Property | Rule |
|----------|------|
| Case | ALL CAPS for buttons, labels, scene titles |
| Letter spacing | +0.1 to +0.2 em on headings |
| Italic | Allowed on taglines/sub-text only |
| Line height | 1.6x font size |
| Logo glow | Draw text twice — first pass 40% alpha same position, second pass full alpha |

---

## 4. Canvas & Rendering Rules

### Virtual Resolution

| Property | Value |
|----------|-------|
| Virtual width | **960 px** |
| Virtual height | **540 px** |
| Aspect ratio | 16:9 |
| Scaling mode | **Integer scale only** |
| Pillarbox/letterbox fill | `#0A0A0A` |

```kotlin
fun computeScale(deviceW: Int, deviceH: Int): Int {
    val scaleX = deviceW / 960
    val scaleY = deviceH / 540
    return maxOf(1, minOf(scaleX, scaleY))
}
```

### Pixel Rendering Rules

| Rule | Android Implementation |
|------|----------------------|
| No bilinear filtering | `paint.isFilterBitmap = false` |
| No anti-aliasing | `paint.isAntiAlias = false` |
| No dithering | `paint.isDither = false` |
| No hardware smoothing | `view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)` |
| Bitmap load — no prescaling | `BitmapFactory.Options().apply { inScaled = false }` |

### Drawing Layer Order (back to front)

```
Layer 0:  Sky background (gradient fill)
Layer 1:  Far mountain backdrop
Layer 2:  Far buildings (low alpha)
Layer 3:  Mid buildings / houses
Layer 4:  Ground tiles / road
Layer 5:  Background props (electric poles, trees)
Layer 6:  Characters (back row)
Layer 7:  Vehicles
Layer 8:  Characters (front row)
Layer 9:  Foreground props (signs, lamps)
Layer 10: Particle effects (rain, debris, petals)
Layer 11: UI overlays (speech bubbles, message bubbles)
Layer 12: Screen-space UI (progress bar, controls, HUD)
```

### Sprite Outline Rule

All solid sprites must have a **1 px black outline**:

```kotlin
paint.style       = Paint.Style.STROKE
paint.strokeWidth = 1f
paint.color       = Color.BLACK
canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
```

---

## 5. Sprite Catalog & Pixel Dimensions

### Characters (all 32 x 48 px)

| Key | Body Color | Hat / Head Detail |
|-----|-----------|------------------|
| `villager` | `#3498DB` blue shirt | `#2C3E50` standard hair |
| `responder` | `#FF5722` hi-vis vest + `#FFFF00` stripes | `#E74C3C` DRRMC red helmet |
| `elderly` | `#9B59B6` purple duster | `#BDC3C7` silver bun, `#F1C40F` glasses |
| `tech_volunteer` | `#1E88E5` UC blue hoodie | `#1E88E5` blue cap + glasses |
| `child` | `#F39C12` yellow shirt | `#2980B9` blue cap |
| `vendor` | `#F39C12` blouse + `#E74C3C` apron | `#E74C3C` red bandana |

**Character pixel grid:**
```
Rows  0- 3: Hat / hair
Rows  4-15: Head (#DEB887 skin) + face details
Rows 16-31: Body / outfit + arms
Rows 32-42: Legs (#2C3E50 dark pants)
Rows 43-47: Shoes (#5D4037 brown)
```

### Devices & Technology

| Key | W x H | Notes |
|-----|-------|-------|
| `resqplug` | 56 x 28 | USB-C + PCB + ESP32 + LoRa shield + antenna |
| `phone` | 24 x 40 | Smartphone, blue screen |
| `signal` | 64 x 64 | 4-ring LoRa icon, `#00FF88` |
| `packet` | 8 x 8 | Solid green data packet |
| `sos_msg` | 80 x 32 | Red bubble, white "SOS" |
| `evac_msg` | 80 x 32 | Amber bubble, white "EVAC" |
| `status_msg` | 80 x 32 | Green bubble, white "OK" |

**ResQPlug LED colors (left to right):** Power `#EF4444` · Mesh `#10B981` · RF `#06B6D4`

### Environment & Infrastructure

| Key | W x H | Notes |
|-----|-------|-------|
| `electric_pole` | 32 x 120 | Brown pole + transformer box |
| `cell_tower` | 40 x 160 | Red/white banded lattice |
| `watch_tower` | 48 x 96 | Timber stilt + relay cabin + solar panel |
| `lighthouse` | 32 x 70 | Red/white striped tower |
| `streetlamp` | 24 x 90 | Black iron pole + yellow lantern |
| `siren_pole` | 24 x 80 | Steel mast + dual megaphones |
| `evac_sign` | 48 x 40 | Yellow reflective, "EVAC >>>" |
| `mountain_bg` | 960 x 140 | Two-layer silhouette backdrop |
| `ground_tile` | 32 x 32 | Grey asphalt + crack detail |
| `ground_tile_mud` | 32 x 32 | Brown mud |
| `road_pavement` | 64 x 32 | Dark asphalt + yellow centre line |

### Buildings & Structures

| Key | W x H | Notes |
|-----|-------|-------|
| `building_apartment` | 72 x 130 | 4-floor concrete + fire escape |
| `building_townhouse` | 56 x 110 | 3-floor stucco + roll-up garage |
| `house_two_story` | 64 x 80 | Clay tile roof + varnished wood |
| `house_bungalow` | 48 x 52 | Green metal roof + jalousie |
| `bahay_kubo` | 80 x 70 | Nipa thatched roof + sawali walls |
| `sari_sari_store` | 96 x 80 | Tin roof + planks + candy jars |
| `panaderia` | 84 x 75 | Orange canopy + pandesal window |
| `welcome_arch` | 220 x 150 | Brick pillars + clay tile + sign |
| `tanod_outpost` | 72 x 60 | Blue tin roof + wooden bench |
| `command_tent` | 96 x 60 | Orange DRRMC tent + antenna |
| `rescue_boat` | 84 x 32 | Orange inflatable + "DRRMC RESCUE" |
| `relief_table` | 72 x 40 | Blue table + rice sacks + AID box |

### Vehicles

| Key | W x H | Notes |
|-----|-------|-------|
| `jeepney` | 110 x 44 | Red/yellow + PH flag stripes + horse ornament |
| `tricycle` | 64 x 38 | Red sidecar + stainless roof + 3 wheels |

### Flora & Nature

| Key | W x H | Notes |
|-----|-------|-------|
| `palm_tree` | 48 x 72 | Curved trunk + 5 fronds |
| `tree` | 32 x 48 | Triangular standard tree |
| `calamansi_tree` | 36 x 52 | Round foliage + yellow/green fruits |
| `aspin_dog` | 22 x 14 | Curled tan street dog |
| `flower_petal` | 8 x 8 | Bougainvillea pink petal |
| `water_foam` | 48 x 8 | White foam strip |
| `yero` / `yero_debris` | 32 x 16 | Galvanized corrugated sheet |
| `banderitas` | 160 x 24 | 7-color pennant flags on sagging string |
| `basketball_hoop` | 32 x 70 | Wooden pole + backboard + orange rim |

---

## 6. Animation System — 8 FPS Enforcement

> **The 8 FPS rule is absolute.** Every sprite animation and world object must
> advance exactly once per **125 ms.** Smooth easing is restricted to camera
> transitions and UI-only elements.

### The 125 ms Tick

```
8 frames per second = 1000 ms / 8 = 125 ms per frame step
```

### Android Game Loop

```kotlin
class GameSurface(context: Context) : SurfaceView(context), Runnable {

    private val FRAME_MS = 125L   // 8 FPS — DO NOT CHANGE

    override fun run() {
        while (running) {
            val t0 = System.currentTimeMillis()
            currentScene.update()   // advance all anims by 1 step
            drawFrame()
            val elapsed = System.currentTimeMillis() - t0
            if (elapsed < FRAME_MS) Thread.sleep(FRAME_MS - elapsed)
        }
    }
}
```

### Sprite Frame Stepping

```kotlin
data class AnimatedSprite(
    val frames: Array<Bitmap>,
    var currentFrame: Int = 0
) {
    fun tick() { currentFrame = (currentFrame + 1) % frames.size }
    val bitmap get() = frames[currentFrame]
}
```

### Animation Catalog — Stepped 8 FPS Specs

| Animation | Frames | Step Action per Tick |
|-----------|--------|--------------------|
| Palm tree sway | 4 | angle snaps: -3, -1, +1, +3 deg |
| Calamansi tree sway | 4 | angle snaps: -2.5, -0.8, +0.8, +2.5 deg |
| Aspin dog bob | 4 | scaleY snaps: 1.00, 1.04, 1.08, 1.04 |
| Banderitas flutter | 4 | y snaps: 0, +1, +3, +1 px; angle -1.5, 0, +1.5, 0 deg |
| Jeepney suspension | 4 | y snaps: 0, -1, 0, +1 px |
| Siren beacon blink | 2 | alpha: 0.9/0.2; scale: 1.0/1.5 |
| Signal rings pulse | 4 | alpha cycle: 1.0, 0.7, 0.4, 0.7 |
| Rain particles | every tick | y += 12 px, x -= 2 px; wrap at bottom |
| Flying debris spin | 4 | angle += 90 deg per tick |
| Flower petals drift | every tick | x += ~6 px, y += ~3 px, angle += 45 deg |
| Data packet hop | every tick | x advances along path segment |
| Smoke puffs | 4 | y -= 8 px; scale 1.0->2.2; alpha 0.7->0 |
| Water ripples | 4 | scale 1->4.5; alpha 0.8->0 |
| Character walk | 4 | alternating leg/arm positions |

### Correct vs Wrong Pattern

```kotlin
// CORRECT — stepped 8 FPS
fun tickPalmTree(sprite: PalmTreeSprite) {
    val angles = floatArrayOf(-3f, -1f, 1f, 3f)
    sprite.angle = angles[sprite.frame % 4]
    sprite.frame++
}

// WRONG — smooth animator on a game sprite (FORBIDDEN)
ValueAnimator.ofFloat(-3f, 3f).apply {
    duration = 2200
    interpolator = AccelerateDecelerateInterpolator()
    start()
}
```

### Permitted Smooth Transitions (UI Layer Only)

| Element | Duration | Type |
|---------|----------|------|
| Scene fade-in / fade-out | 1000 ms | Linear alpha overlay |
| Progress bar fill | 600 ms | Linear width |
| Start button pulse | 1800 ms | Sine scale 1.0 to 1.04 |
| Music volume crossfade | 700 ms | Linear gain ramp |
| Speech bubble appear | 200 ms | Linear alpha 0 to 1 |

---

## 7. Scene Structure & Progression

| # | Scene ID | Title | Music |
|---|----------|-------|-------|
| 1 | SCENE_PEACEFUL_MORNING | Peaceful Morning | calm |
| 2 | SCENE_TYPHOON_APPROACHES | Typhoon Approaches | tense |
| 3 | SCENE_COMM_BLACKOUT | Communication Blackout | tense |
| 4 | SCENE_RESQPLUG_INTRO | ResQPlug Introduced | hope |
| 5 | SCENE_BEFORE_AFTER | Before / After Split | hope |
| 6 | SCENE_MESH_NETWORK | Mesh Network Shown | hope |
| 7 | SCENE_PRIORITY_MESSAGING | Priority Messaging | hope |
| 8 | SCENE_RESCUE_COORD | Rescue Coordination | victory |
| 9 | SCENE_TECHNICAL_DIAGRAM | Technical Diagram | hope |
| 10 | SCENE_RESOLUTION | Resolution & Hope | victory |
| 11 | SCENE_CREDITS | Credits | victory |

### Scene Transition — 1-Second Black Fade

```kotlin
// 8 ticks x 125 ms = 1000 ms. Add 32 alpha per tick (32 x 8 = 256 = full black).
var fadeAlpha = 0
val fadePaint = Paint()

fun tickFadeOut(canvas: Canvas) {
    fadeAlpha = minOf(255, fadeAlpha + 32)
    fadePaint.color = Color.argb(fadeAlpha, 0, 0, 0)
    canvas.drawRect(0f, 0f, 960f, 540f, fadePaint)
    if (fadeAlpha >= 255) switchToNextScene()
}
```

### Progress Bar

- Container: 960 x 6 px, background `#1A1A2E`
- Fill: `#00FF88` to `#2ECC71` horizontal gradient
- Width: `(sceneIndex + 1) / 11 * 960` px
- Label: "Scene N of 11", 7 sp Press Start 2P, `#00FF88`, top-right

---

## 8. UI Chrome Components

### Start Screen Layout

```
+------------------------------------------+
|  (background: #0A0A0A full screen)        |
|                                           |
|         R E S Q P L U G                  |  22sp #2ECC71 + glow
|   Stay Connected When It Matters Most    |  10sp #FFFFFF italic
|  University of Cebu Banilad  Cap 2026    |  8sp  #7F8C8D
|                                           |
|         [ PLAY  CLICK TO START ]         |  button spec below
+------------------------------------------+
```

**Logo glow:** Draw text twice — first at 40% alpha (same XY), then full alpha on top.

### Button Specification

| Property | Start Button | Control Buttons |
|----------|-------------|-----------------|
| Background | `#1A1A2E` | `#1A1A2E` |
| Border | 2 px `#00FF88` | 2 px `#00FF88` |
| Border radius | 4 px | 2 px |
| Text color | `#00FF88` | `#00FF88` |
| Font size | 9 sp | 7 sp |
| Padding | 52 x 16 px | 18 x 8 px |
| Case | ALL CAPS | ALL CAPS |
| Press bg | `#00FF88` | `#00FF88` |
| Press text | `#1A1A2E` | `#1A1A2E` |
| Active scale | 0.95 snap (no tween) | 0.95 snap |
| Pulse (start only) | 1.8s sine 1.0 to 1.04 | none |

### Control Bar

```
[ PREV ]   [ REPLAY ]   [ PAUSE ]   [ NEXT ]
```
Margin top: 12 px. Gap between buttons: 10 px.

### Speech Bubble

- Background: `#1A1A2E`, border 1.5 px `#00FF88`
- Width: 220 px
- Text: Press Start 2P, 11 px, `#FFFFFF`
- Visible: 3000 ms then 200 ms fade-out
- Position: 50 px above character Y

### Message Priority Bubbles (80 x 32 px)

| Priority | Color | Label |
|----------|-------|-------|
| High (SOS) | `#E74C3C` | SOS |
| Medium (EVAC) | `#F39C12` | EVAC |
| Low (Status) | `#2ECC71` | OK |

All: white bold text 14 px, 20% white highlight strip on top 2 px.

---

## 9. Sound Design — Chiptune Rules

> **All audio must be procedurally synthesized.** No MP3. No OGG. No recorded samples.

### Android Audio Engine — PCM via AudioTrack

```kotlin
class ChiptuneEngine {
    private val SAMPLE_RATE = 44100

    fun generateSquare(freqHz: Double, durationMs: Int, volume: Float): ShortArray {
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val period  = (SAMPLE_RATE / freqHz).toInt()
        val amp     = (Short.MAX_VALUE * volume).toInt()
        return ShortArray(samples) { i ->
            if ((i % period) < period / 2) amp.toShort() else (-amp).toShort()
        }
    }

    fun generateSawtooth(freqHz: Double, durationMs: Int, volume: Float): ShortArray {
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val period  = (SAMPLE_RATE / freqHz).toInt()
        val amp     = (Short.MAX_VALUE * volume).toInt()
        return ShortArray(samples) { i ->
            val pos = i % period
            ((pos.toFloat() / period * 2 * amp) - amp).toInt().toShort()
        }
    }

    fun generateNoise(durationMs: Int, volume: Float): ShortArray {
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val amp     = (Short.MAX_VALUE * volume).toInt()
        return ShortArray(samples) { ((Math.random() * 2 - 1) * amp).toInt().toShort() }
    }
}
```

### Waveform Assignments

| Waveform | Used For |
|----------|---------|
| Square | Melody leads, UI clicks, SFX beeps, SOS Morse |
| Sawtooth | Tense/danger music, thunder, distress wail |
| Triangle | Bass lines, counter-melodies, soft pads |
| White noise | Rain/wind ambient, percussion snare, spark zap |

### ADSR Envelope Defaults

```
Attack  : 0.003-0.010 s  (linear ramp 0 to peak)
Decay   : 0.040-0.100 s  (ramp peak to sustain)
Sustain : 0.50-0.85      (fraction of peak amplitude)
Release : 0.040-0.120 s  (ramp sustain to 0)
```

### Music Tracks

| Track | Tempo | Mood | Waveforms |
|-------|-------|------|-----------|
| calm | 340 ms/beat | Peaceful pentatonic C major | Square + Square + Triangle |
| tense | 270 ms/beat | Chromatic descending minor | Sawtooth + Square + Square |
| hope | 310 ms/beat | Ascending G major | Square + Triangle + Triangle |
| victory | 370 ms/beat | Triumphant C major fanfare | Square + Square + Triangle |

**calm track — C pentatonic, 8 beats per bar:**

```
Beat:     0    1    2    3    4    5    6    7
Melody:   C5   E5   G5   A5   G5   E5   C5   —
Hz:       523  659  784  880  784  659  523
Harmony:  G4   C5   E5   —    E5   C5   G4   —
Hz:       392  523  659       659  523  392
Bass:     C3   —    G3   —    C3   —    G3   —
Hz:       131       196       131       196
```

### SFX Catalog

| SFX | Frequencies (Hz) | Wave | Duration | Trigger |
|-----|-----------------|------|---------|---------|
| playThunder | 80 to 40 sweep + noise | Sawtooth | 0.6s + 0.9s | Storm scenes |
| playSignalBeep | 880 to 1320 chirp | Square | 0.12s x2 | LoRa packet sent |
| playMessageSent | 523-659-784 arpeggio | Square | 0.1s x3 | Message transmitted |
| playSuccess | 523-659-784-1046-1318 | Square | 0.18s x5 | Scene success |
| playJeepneyHorn | 440 + 330 | Square | 0.18s + 0.22s | Jeepney passes |
| playDistress | 260 to 200 descending | Sawtooth | 0.35s + 0.4s | Distress signal |
| playUplifting | 523-587-659-784-880-1046 | Square | 0.22s x6 | Credits |
| playClick | 1400 blip | Square | 0.04s | Button press |
| playSpark | Noise + 2200 zap | Noise+Square | 0.06s | Connection established |
| playPowerUp | 220 to 880 sweep | Square | 0.4s | Dongle connects |
| playSOS | 880/660 Morse pattern | Square | 0.1s x9 | SOS display scene |

**SOS Morse pattern:**
```
Time (ms): 0   120  240  480  600  720  960  1080  1200
Freq (Hz): 880 880  880  660  660  660  880   880   880
Symbol:    .   .    .    -    -    -    .     .     .
            --- S ---    ---- O ----    --- S ---
```

### Ambient Sound Rules

- **Rain:** White noise looped (2s buffer) -> low-pass 450 Hz -> fade-in 1.5s, fade-out 0.8s
- **Wind:** White noise looped (2s buffer) -> bandpass 750 Hz Q=0.5 -> fade-in 2s, fade-out 0.8s

---

## 10. Philippine Cultural Design Elements

These assets are **required.** Reproduce with the exact visual details below.

### Bahay Kubo (80 x 70 px)
- 3 bamboo stilts `#795548`, 6 px wide
- Sawali walls `#D7CCC8` with vertical line texture `#A1887F`
- Nipa roof `#A1887F` pyramid, shadow `#8D6E63`
- Capiz windows `#ECEFF1` with `#8D6E63` grid frame
- Bamboo ladder on right side

### Sari-Sari Store (96 x 80 px)
- Corrugated tin canopy `#BDC3C7`
- Sign: `#F39C12` background, white "SARI-SARI STORE"
- Counter interior: `#1A1A2E`
- Candy jars: `#E74C3C`, `#F1C40F`, `#2ECC71`
- 5 chip bags: `#E74C3C` `#3498DB` `#F1C40F` `#9B59B6` `#E67E22`
- Coca-Cola banner: `#C0392B` on left side

### Jeepney (110 x 44 px)
- Body: `#C0392B` base, `#E74C3C` highlight
- Philippine flag stripe: `#0038A8` (blue) + `#FCD116` (gold)
- Chrome hood `#BDC3C7` + white horse figurine
- 4 windows `#AED6F1`
- Route sign `#F1C40F`, black "EXPRESS"
- Headlight `#F1C40F` / Taillight `#E74C3C`

### Welcome Arch (220 x 150 px)
- Brick pillars `#795548` with `#5D4037` mortar lines
- Clay tile roof `#C0392B` / `#E74C3C` two-tone
- Golden sign `#F1C40F`: "WELCOME / MABUHAY"
- 11 mini decorative flags below span

### Banderitas (160 x 24 px — 6 strips across scene)
- Sagging string `#2C3E50`, 1.2 px width
- 7 triangular pennants per strip in strict order:
  `#E74C3C` `#F1C40F` `#3498DB` `#2ECC71` `#9B59B6` `#E67E22` `#00FF88`

### DRRMC Branding
- All text: white `#FFFFFF`, bold, Press Start 2P
- Primary color: `#E67E22` orange
- Text on tent: "DRRMC HQ"
- Text on boat: "DRRMC RESCUE"
- Responder vest: `#FF5722` + `#FFFF00` safety stripes

---

## 11. Android Implementation Guide

### Recommended Stack

| Layer | Technology | Reason |
|-------|-----------|--------|
| Rendering | **Native SurfaceView + Canvas** | Zero dependencies, full pixel control |
| Game loop | Thread + SurfaceHolder.lockCanvas | Precise 125 ms tick |
| Sprites | Bitmap drawn in BootManager at startup | Mirrors Phaser BootScene exactly |
| Audio | AudioTrack (PCM synthesis) | Pure chiptune, no external files |
| Font | ResourcesCompat.getFont(R.font.press_start_2p) | Bundled TTF |
| UI chrome | View overlay on top of SurfaceView | Buttons + progress bar |

**Optional upgrade:** libGDX — mirrors Phaser's scene/sprite API most closely.
Use TextureFilter.Nearest for pixel-perfect rendering.

### Project Structure

```
app/
  src/main/
    java/com/resqplug/
      MainActivity.kt
      GameSurface.kt           (SurfaceView + 125 ms loop)
      BootManager.kt           (generates all Bitmap textures)
      scenes/
        SceneBase.kt
        Scene1PeacefulMorning.kt
        ... (11 scenes total)
      audio/
        ChiptuneEngine.kt
      ui/
        OverlayView.kt         (buttons + progress bar)
    res/
      font/
        press_start_2p.ttf
      values/
        colors.xml
        themes.xml
```

### AndroidManifest.xml

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="landscape"
    android:windowSoftInputMode="adjustNothing"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:theme="@style/Theme.ResQPlug.Fullscreen" />
```

```xml
<!-- res/values/themes.xml -->
<style name="Theme.ResQPlug.Fullscreen" parent="Theme.AppCompat.NoActionBar">
    <item name="android:windowFullscreen">true</item>
    <item name="android:windowKeepScreenOn">true</item>
    <item name="android:statusBarColor">#FF0A0A0A</item>
    <item name="android:navigationBarColor">#FF0A0A0A</item>
</style>
```

### SurfaceView Core (Kotlin)

```kotlin
class GameSurface(context: Context) : SurfaceView(context), Runnable {

    private val FRAME_MS  = 125L       // 8 FPS lock — do not change
    private val VIRTUAL_W = 960
    private val VIRTUAL_H = 540

    private val virtualBitmap = Bitmap.createBitmap(VIRTUAL_W, VIRTUAL_H,
                                    Bitmap.Config.ARGB_8888)
    private val virtualCanvas = Canvas(virtualBitmap)

    // Pixel-perfect paint — absolutely no smoothing
    private val scalePaint = Paint().apply {
        isAntiAlias    = false
        isFilterBitmap = false
        isDither       = false
    }

    override fun run() {
        while (running) {
            val t0 = System.currentTimeMillis()

            // 1. Advance all animations by exactly 1 step
            currentScene.update()

            // 2. Draw to virtual 960x540 canvas
            virtualCanvas.drawColor(Color.parseColor("#0A0A0A"))
            currentScene.draw(virtualCanvas)

            // 3. Integer-scale to device screen, centred, letterboxed
            val sh = holder.lockCanvas() ?: continue
            sh.drawColor(Color.parseColor("#0A0A0A"))
            val scale   = minOf(sh.width / VIRTUAL_W, sh.height / VIRTUAL_H)
            val offsetX = (sh.width  - VIRTUAL_W * scale) / 2f
            val offsetY = (sh.height - VIRTUAL_H * scale) / 2f
            sh.drawBitmap(
                virtualBitmap,
                RectF(0f, 0f, VIRTUAL_W.toFloat(), VIRTUAL_H.toFloat()),
                RectF(offsetX, offsetY,
                      offsetX + VIRTUAL_W * scale,
                      offsetY + VIRTUAL_H * scale),
                scalePaint
            )
            holder.unlockCanvasAndPost(sh)

            // 4. Sleep remainder of frame budget
            val elapsed = System.currentTimeMillis() - t0
            if (elapsed < FRAME_MS) Thread.sleep(FRAME_MS - elapsed)
        }
    }
}
```

### Bitmap Texture Generation (BootManager pattern)

```kotlin
object BootManager {

    fun generateAll(): Map<String, Bitmap> {
        val t = mutableMapOf<String, Bitmap>()
        t["ground_tile"] = drawGroundTile()
        t["packet"]      = solidRect(8,  8,  "#00FF88")
        t["raindrop"]    = solidRect(2,  8,  "#74B9FF")
        // ... generate all 40+ sprites using same pixel-grid approach
        return t
    }

    private fun drawGroundTile(): Bitmap {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val c   = Canvas(bmp)
        val p   = Paint().apply { isAntiAlias = false }

        // Fill
        p.color = Color.parseColor("#708090"); p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, 32f, 32f, p)

        // Crack detail
        p.color = Color.parseColor("#5A6876")
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f
        c.drawLine(0f, 16f, 10f, 16f, p)
        c.drawLine(20f, 8f,  32f, 8f,  p)
        c.drawLine(8f,  24f, 24f, 24f, p)

        // Mandatory 1px black outline
        p.color = Color.BLACK
        c.drawRect(0f, 0f, 32f, 32f, p)

        return bmp
    }

    private fun solidRect(w: Int, h: Int, hex: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.parseColor(hex))
        return bmp
    }
}
```

---

## 12. Quick Reference Checklist

Use before every build to confirm 8-bit compliance.

### Visual

- [ ] All colors match palette in Section 2 exactly (no approximations)
- [ ] `paint.isAntiAlias = false` on every game-layer Paint
- [ ] `paint.isFilterBitmap = false` on every bitmap scale operation
- [ ] All sprites have a 1 px black outline drawn on the Bitmap
- [ ] Integer-only scaling — no fractional scale values
- [ ] Letterbox / pillarbox fills with `#0A0A0A`
- [ ] No blur, drop shadows, or gradient fills on any sprite
- [ ] Press Start 2P font bundled and confirmed loading at runtime

### Animation

- [ ] Game loop ticks at exactly **125 ms** per frame (8 FPS)
- [ ] No `ValueAnimator` / `ObjectAnimator` / `Animation` used on game sprites
- [ ] No easing interpolators on any world object
- [ ] All sprite frame arrays contain 4 or fewer frames
- [ ] Smooth motion only on: scene fade, progress bar, start button pulse, music volume

### Audio

- [ ] All audio is PCM-synthesized — no external audio files
- [ ] Waveform roles respected: Square=melody, Sawtooth=danger, Triangle=bass, Noise=ambient
- [ ] Music crossfades use 700 ms linear volume ramp
- [ ] Rain and wind ambients stop with 800 ms fade-out

### Android Platform

- [ ] Screen orientation locked to landscape
- [ ] `windowFullscreen = true`
- [ ] `android:keepScreenOn` enabled
- [ ] Status bar and navigation bar color set to `#0A0A0A`
- [ ] `BitmapFactory.Options.inScaled = false` on all sprite loads
- [ ] Minimum API level: **26** (required for AudioTrack.Builder)
- [ ] All Canvas text drawn with Press Start 2P at integer sp sizes

---

*Last updated: 2026-08-26 · ResQPlug Capstone 2026-2027 · University of Cebu Banilad*
