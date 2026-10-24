# Quartz Launcher — Phase 0 (fold test build)

A bare-bones test launcher. It isn't pretty on purpose: its only job is to prove the fold behavior on real devices before Phase 1 design work starts.

## What it does

- Can be set as the default home app.
- **Cover screen / phone:** one grid (default 4×6) plus a dock.
- **Unfolded / tablet:** the same grid doubled horizontally (4×6 → 8×6). The cover layout lands on the left or right half, split at the hinge position the phone reports.
- Long-press an icon and drag it to any slot, including across halves and into the dock.
- Swipe up for a simple app drawer. Long-press an app there and drag it to a spot on the home screen.
- Long-press empty space for the **test panel**: what the device reports, cover side, grid size, test wallpaper, default home, copy report, event log.
- A **test live wallpaper** that labels which screen, size, and display it is drawing on.

## Build

Open the folder in Android Studio, let Gradle sync, and run on a device. Same Gradle, Kotlin, and Compose versions as Quartz Gallery.

For speed testing (scrolling, animations), use the **release** variant: View > Tool Windows > Build Variants, then set **:app** to **release** (sync Gradle first if only debug shows). Debug builds of Compose are noticeably slower. Both variants are signed with the debug key, so they install over each other.

## Test checklist

Do these on each device, then use **Copy report to clipboard** in the test panel and paste it into our chat.

### Every device (Fold 8, Pixel 10 Pro, Tab A11+, Alexa Tablet 12 Pro)

1. Install, open Quartz Launcher from the app drawer, open the test panel, tap **Set as default home**. Press Home: does Quartz come up?
2. Do the icons and labels load? Is tapping an app instant?
3. Drag an icon to another slot and into the dock. Does it land where you dropped it?
4. Swipe up: does the drawer open? Does the swipe-home gesture from an app feel normal, or janky?
5. Tap **Set Quartz test wallpaper**. Does the live wallpaper picker open and apply it?

### Fold 8 only

6. Folded: the hint/caption should say cover screen. Unfold: the cover layout should sit on the chosen half, captioned "Cover layout", with "Inner half" on the other side.
7. Fold and unfold several times. Smooth, or a flash/blank frame? Check **Last relayout** in the panel.
8. Switch **Left half / Right half** in the panel. Does the cover layout move?
9. Start dragging an icon, then fold or unfold mid-drag. The icon should go back to its spot without breaking anything.
10. Wallpaper, **Shared · cropped** mode: folded should show "LEFT HALF" (or "RIGHT HALF"); unfolded should show both halves with a dashed hinge line in the middle. Try **Shared · stretched** and **Independent** too.
11. Look at the wallpaper's info box on each screen. Is it one wallpaper engine redrawing at a new size, or a different engine number per screen?
12. Rotate the unfolded phone to landscape. Does the layout still make sense?

### Things to note

- Any crash: what you were doing right before.
- Whether Samsung asks to keep One UI Home, or resets the default home after a restart.
- Anything that feels slow.
