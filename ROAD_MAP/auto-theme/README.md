# Auto Dark/Light Theme

## Overview
Implement automatic dark/light mode switching based on system theme preferences in the Android app.

## Current State
- Theme infrastructure already exists (`MyApplicationTheme` in `ui/theme/Theme.kt`)
- Theme includes `isSystemInDarkTheme()` support
- Dynamic color support for Android 12+
- `MainActivity` currently does not wrap UI with theme

## Goal
Wrap the `MainScreen` composable in `MainActivity.kt` with `MyApplicationTheme` to enable automatic theme switching.

## Steps
1. **STEP 1**: Integrate Theme Wrapper in MainActivity

## Benefits
- Seamless dark/light mode switching
- Better user experience matching system preferences
- Proper Material 3 theming throughout the app