# SmartQ Material 3 Healthcare Design System

## Overview
Complete Jetpack Compose Material 3 design system with heavy animations, minimal modern aesthetic, and healthcare-specific design patterns.

**Tech Stack:**
- Jetpack Compose (UI Framework)
- Material 3 (Design System)
- Lottie (Premium Animations)
- Dark Mode First

---

## Color Palette

### Primary Brand (Clinical Blue)
- **Light Mode Light:** `#0089C3`
- **Dark Mode Light:** `#89CEFF` 
- **Container:** `#001A29` (Dark) / `#C9E6FF` (Light)
- **Usage:** CTAs, headers, primary UI elements

### Secondary (Soft Slate)
- **Light:** `#3A4A5F` (Light Mode) / `#B7C8E1` (Dark Mode)
- **Usage:** Supporting actions, secondary UI

### Tertiary / Accent (Healthcare Red)
- **Light:** `#EB4141`
- **Dark:** `#FFB3AD`
- **Usage:** Alerts, error states, critical information

### Priority Status Colors
```
High:    #EB4141 (Red)
Medium:  #FF9500 (Orange)
Normal:  #89CEFF (Blue)
Low:     #4CAF50 (Green)
```

### Surfaces
- **Surface (Dark):** `#0B1326` - Deep clinical dark
- **Surface Container Low:** `#131B2E` - Layered depth
- **Surface Variant:** `#2D3449` - For cards/elevated surfaces
- **Text Primary:** `#DAE2FD` - High contrast white text
- **Text Secondary:** `#909097` - Muted secondary text

---

## Typography System

### Scale
| Level | Size | Weight | Use Case |
|-------|------|--------|----------|
| **displayLarge** | 57sp | Normal | Rarely used headlines |
| **headlineLarge** | 28sp | SemiBold | Key titles |
| **titleLarge** | 18sp | SemiBold | Screen titles (most common) |
| **bodyLarge** | 16sp | Normal | Main content |
| **labelLarge** | 14sp | SemiBold | Button text, UI labels |
| **bodySmall** | 12sp | Normal | Secondary info |
| **labelSmall** | 11sp | SemiBold | Minimal UI text |

### Usage Examples
```kotlin
// Screen title
Text("Admin Dashboard", style = MaterialTheme.typography.titleLarge)

// Button text
Button(/* */) {
    Text("Call Next", style = MaterialTheme.typography.labelLarge)
}

// Secondary info
Text("ID: P024", style = MaterialTheme.typography.bodySmall)
```

---

## Shape System (Minimal & Modern)

| Shape | Radius | Usage |
|-------|--------|-------|
| **extraSmall** | 4dp | Chips, minimal elements |
| **small** | 8dp | Buttons, pills |
| **medium** | 12dp | Cards, dialogs |
| **large** | 16dp | Large containers |

**Philosophy:** Minimal rounded corners → rectangular modern feel

---

## Animated Components

### 1. AnimatedPrimaryButton
```kotlin
AnimatedPrimaryButton(
    text = "Call Next",
    onClick = { /* handle click */ },
    isLoading = false,
    enabled = true,
)
```
**Animations:** Press scale (0.95x), loading pulse

### 2. AnimatedStatCard
```kotlin
AnimatedStatCard(
    label = "Waiting",
    value = "7",
    modifier = Modifier.weight(1f)
)
```
**Animations:** Hover elevation (2dp → 8dp), background transition

### 3. PriorityBadge
```kotlin
PriorityBadge(priority = "high")
```
**Animations:** Scale entry (0.3x → 1x), fade-in  
**Colors:** Contextual based on priority level

### 4. QueueItemCard
```kotlin
QueueItemCard(
    patientName = "John Doe",
    patientId = "P001",
    priority = "high",
    index = 0,
)
```
**Animations:** Sequential slide-in from left (400ms + 100ms per item), fade-in

### 5. PulsingDot
```kotlin
PulsingDot(size = 4.dp, color = MaterialTheme.colorScheme.onPrimary)
```
**Animations:** Three-dot pulsing loader (repeating)

### 6. ExpandableSection
```kotlin
ExpandableSection(
    title = "ML Ops Details",
    isExpanded = isExpanded,
    onExpandChange = { /* update state */ },
) {
    Text("Content here")
}
```
**Animations:** Height expand/shrink (expandVertically), fade in/out

---

## Admin Dashboard Compose Screen

### Features
- **Top App Bar:** Title, admin name, logout button
- **Stats Row:** 3 animated stat cards (Waiting, Completed, Avg Wait)
- **Action Buttons:** Pause/Resume, Call Next
- **Currently Serving:** Highlighted box with patient info
- **Queue Section:** List of patients with priority badges, staggered animations
- **ML Ops Health:** Status card with logs and refresh button

### Structure
```kotlin
AdminDashboardScreen(
    adminName = "Dr. Smith",
    onLogout = { /* handle logout */ },
    onCallNext = { /* handle call */ },
    onPauseToggle = { /* handle pause */ },
    onRefreshML = { /* handle ML refresh */ },
)
```

---

## Dark Mode Support

**Automatic:** System respects device dark mode setting  
**Manual Override Optional:**
```kotlin
SmartQTheme(darkTheme = true) {
    // Content
}
```

**Colors automatically adapt:**
- Text becomes lighter on dark backgrounds
- Surfaces become darker
- Accent colors adjust for contrast

---

## Animation Specifications

### Timing
- **Quick interactions:** 150ms (button press)
- **Standard transitions:** 300-400ms (card elevation, expansion)
- **Entrance animations:** 400ms + staggered delays
- **Loading loops:** 500-600ms per cycle

### Easing
- `Ease In/Out` for most transitions
- `Linear` for staggered list animations
- `Fast Out Slow In` for entrance animations

### Principles
1. **Motion Enhances:** Animations clarify interactions, not just decorative
2. **Responsive:** All animations respect system animations setting
3. **Purposeful:** Every animation serves a UX function
4. **Consistent:** Timing and easing consistent across all interactions

---

## Implementation Guide

### Step 1: Set Up Dependencies ✅
```gradle
// Already added in build.gradle:
// - Compose BOM
// - Material 3
// - Lottie Compose
```

### Step 2: Use the Theme
```kotlin
class ComposeAdminDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartQTheme {
                AdminDashboardScreen(/* params */)
            }
        }
    }
}
```

### Step 3: Build Screens with Components
```kotlin
@Composable
fun YourScreen() {
    Column {
        AnimatedPrimaryButton(text = "Action") { /* */ }
        AnimatedStatCard(label = "Count", value = "42")
        PriorityBadge(priority = "high")
    }
}
```

### Step 4: Lottie Animations
```kotlin
@Composable
fun LoadingScreen() {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.loading_spinner)
    )
    val progress by animateLottieCompositionAsState(composition)
    
    LottieAnimation(
        composition = composition,
        progress = progress,
        modifier = Modifier.size(100.dp),
    )
}
```

---

## Next Steps: Full App Redesign

### Phase 1: Core Screens (Priority)
1. ✅ Admin Dashboard (DONE)
2. Patient Queue (Next)
3. Doctor Dashboard
4. Authentication Screens

### Phase 2: Patient Screens
5. Patient Registration
6. Symptom Input (with animations)
7. Triage Results
8. Queue Position Tracker

### Phase 3: Navigation & Polish
9. Bottom Navigation with animated transitions
10. Shared Element Animations between screens
11. Gesture support (swipe, long-press)
12. Accessibility compliance (a11y)

---

## Customization

### Change Primary Color
Edit `Color.kt`:
```kotlin
val md_theme_dark_primary = Color(0xFF89CEFF) // Change this
```

### Adjust Animation Duration
Edit individual component files:
```kotlin
animationSpec = tween(durationMillis = 300) // Change duration
```

### Disable Animations (Accessibility)
```kotlin
// Respect user's animation preferences
val context = LocalContext.current
val areAnimationsEnabled = Settings.Global.getFloat(
    context.contentResolver,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    1f
) != 0f
```

---

## Files Created

| File | Purpose |
|------|---------|
| `Color.kt` | Healthcare color palette |
| `Type.kt` | Material 3 typography system |
| `Shape.kt` | Minimal rounded shapes |
| `Theme.kt` | Complete M3 theme with dark/light modes |
| `AnimatedComponents.kt` | Reusable animated components |
| `AdminDashboardScreen.kt` | Compose admin dashboard UI |
| `ComposeAdminDashboardActivity.kt` | Activity wrapper |
| `loading_spinner.json` | Lottie loading animation |
| `success_checkmark.json` | Lottie success animation |

---

## Resources

- [Material 3 Docs](https://m3.material.io/)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Lottie Android](https://github.com/airbnb/lottie-android)
- [Healthcare UX Patterns](https://healthcare.google/products/design-systems/)

---

## Questions?

This design system provides a foundation for a modern, healthcare-focused Android application with:
- ✅ Material 3 compliance
- ✅ Heavy animations (minimal but meaningful)
- ✅ Rectangular modern aesthetic  
- ✅ Dark mode first
- ✅ Accessibility considerations

**Next action:** Migrate remaining screens to Compose using these components!
