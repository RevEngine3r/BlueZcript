# STEP 1: Integrate Theme Wrapper in MainActivity

## Objective
Wrap the `setContent` block in `MainActivity.kt` with `MyApplicationTheme` composable to enable automatic dark/light mode based on system settings.

## Changes Required

### File: `android-app/app/src/main/java/r/iot/bluezcript/MainActivity.kt`

1. Import the theme:
   ```kotlin
   import r.iot.bluezcript.ui.theme.MyApplicationTheme
   ```

2. Wrap `setContent` block:
   ```kotlin
   setContent {
       MyApplicationTheme {
           MainScreen(
               status = status,
               isPaired = isPaired,
               hasBluetoothPermissions = hasBluetoothPermissions,
               detectedMac = detectedMacAddress,
               onScanQR = { ... },
               onTrigger = { ... },
               onReset = { ... }
           )
       }
   }
   ```

## Testing
1. Build and run the app
2. Switch system theme (Settings → Display → Dark theme)
3. Verify app theme switches automatically
4. Test on Android 12+ for dynamic colors
5. Verify all UI elements (buttons, text, surfaces) adapt correctly

## Acceptance Criteria
- [ ] App automatically switches between light and dark theme
- [ ] Theme follows system preferences
- [ ] All UI elements properly styled in both modes
- [ ] No visual glitches or color inconsistencies