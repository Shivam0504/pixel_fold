# Neon Dusk Launcher

A lightweight, fold-aware Android home launcher.

- Transparent home so your Neon Dusk wallpaper shows through
- Clock + date (cyan accent), pinned-apps panel
- Auto layout: cover screen = stacked, inner screen = two columns
- Swipe up: app drawer with instant search (Enter launches first match)
- Long-press any app: pin/unpin, app info, uninstall
- Survives folding/unfolding without restarting

## Build and install
1. Install Android Studio (free) and open this folder as a project. Let Gradle sync (needs internet).
2. On your Pixel: Settings -> About phone -> tap Build number 7 times, then Developer options -> USB debugging ON.
3. Plug in the phone, pick it in the device dropdown, press Run. Done.
   Or: Build -> Build APK(s), copy app-debug.apk to your phone, and open it (allow "install unknown apps").
4. Make it your launcher: Settings -> Apps -> Default apps -> Home app -> Neon Dusk.

Revert any time by choosing Pixel Launcher in the same setting.

## Notes
- Android widgets (KWGT etc.) are not hosted yet; this is the next feature to add.
- Default pins use Google app package names; pin others by long-pressing in the drawer.
