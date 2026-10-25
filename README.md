# Quartz Launcher

Phase 8: widgets and weather, on phase 7 icons and badges, phase 6 app drawer and search, phase 5 dock styling, the phase 4 wallpaper engine, phase 3 folders and gestures, phase 2 layout engine and phase 1 settings.

## Phase 8: what's new

- **Widgets:** long-press an empty spot on the home screen and tap **Widgets**. Four to pick from, each on a glass panel that matches your dock (glass, gradient, pattern and edge light):
  - **Clock:** the time, big, with the date under it in your widget color.
  - **Flip clock:** two cards that really flip over as the minutes change, with the full date underneath.
  - **Flip clock and weather:** the flip clock with today's weather under it; the weather icon spills over the panel's edge.
  - **Weather:** a slim bar with where you are, what it's like, the temperature, and today's high over its low.
- **Moving and resizing:** hold a widget and drag it (an outline shows where it'll land; icons in the way move to the nearest free spots). Hold and let go for its menu: **Resize** (drag the edge handles; they snap to the grid), **Change style**, **Widget settings**, **Remove**. Icons slide around widgets when you rearrange, and widgets travel with their page when pages move.
- **Weather:** from Open-Meteo (free, no account, no ads or tracking), only while a weather widget is on your home screen, about every half hour. Choose a city by name, or use this phone's location (coarse, rounded to about 1 km; Android asks first). °F by default, °C in settings. Animated Meteocons icons, with day and night versions.
- **Settings › Appearance › Widgets:** a live preview, widget color, 24-hour time, motion on or off (flip and icon animation), where the weather is for, units, and when it last updated.
- **Widgets from your apps:** the picker's "From your apps" section lists every app with home screen widgets (Calendar, Gmail, music players…), with each widget's preview and size in cells. Android asks once per app whether Quartz may show its widgets, and the app's own setup runs if it has one. Hold one to drag it; hold and let go for **Resize** (only the ways the app allows, down to its smallest size), **Its settings** (when the app offers them), **Glass behind it** (a panel like Quartz's widgets), or **Remove**. Widgets update while the home screen is showing. List widgets (like a calendar agenda) scroll as usual.
- **Setup** now offers one widget for your home page; the recommended setup adds a flip clock.
- On the unfolded screen turned upright (and sideways grids), widgets sit across the top of the page above your icons.
- Credits: Meteocons by Bas Milius (MIT), Outfit typeface (SIL OFL), Lottie by Airbnb (Apache 2.0), weather data by Open-Meteo.com (CC BY 4.0). Licences are in `app/src/main/assets/licenses`.

## Phase 7: what's new

- **Icon packs:** any pack made for Nova, Lawnchair and similar launchers. Use one everywhere, or turn off "Match everywhere" to pick one each for the home screen, dock and app drawer. Apps a pack doesn't cover get the pack's own frame when it has one.
- **Shapes:** app's own, circle, squircle, rounded square, square, teardrop, hexagon. "Use the shape on every icon" cuts pack icons too.
- **Style:** default, or minimal (one color: Android 13 monochrome icons where apps have them, a grey version otherwise).
- **Background:** none, light, dark, or a color you pick, as a plate behind each icon.
- **Badges** (Appearance › Notification badges): a dot or a count (99+) that changes color at two steps you choose. Folders add up their apps. Needs notification access, which you turn on yourself; Quartz only counts notifications per app and never reads them. Off by default.
- Icons are drawn in the background and kept, so the home screen never waits; the app's own icon shows until its styled one is ready.
- **Apply:** icon style changes show in the preview straight away and reach your icons when you tap **Apply** (or **Undo** to drop them). Apply draws every icon in the new style behind a "Styling your icons" popup, then the home screen switches over in one go. Leaving the screen keeps your changes; setup's icons step applies on Next. Icon sizes and badges still apply instantly.

## Phase 6: what's new

- **Drawer styles:** full screen, floating box, side list or bottom popup, with a Size slider (per screen). The dock stays showing while the drawer is open; turn off "Keep the dock showing" to cover it.
- **Tabs:** My tabs (name, icon, and apps you choose; Quartz suggests apps from what's on the phone and you approve each one) or Automatic (Android's own categories). Names down a sidebar on wide screens, an icon rail with the tab's name as a heading on narrow ones. The tabs sit on the side away from the dock.
- **Apps:** grid, list or swipeable pages. Columns fit the space on their own, or set your own number. A to Z or newest first, with optional letter or month headings.
- **Search** at the top of the drawer: apps always, Android settings pages (Wi‑Fi, Bluetooth, battery…), and contacts if you turn it on (Android asks first; read on the phone only). The Search gesture opens the drawer ready to type.
- **Setup** has a drawer style and tabs step. Profiles carry your tabs.
- **Later:** a custom drag-to-arrange order. File search isn't offered, because Android would have to let Quartz see all your files.

## Phase 5: what's new

- **Dock background** is a simple on/off; one **Roundness** slider (all the way left is square). Old Square/Rounded/None choices carry over.
- **Shapes:** straight, slanted, pointed, wave, or fade (solid at the screen edge, clear toward the middle).
- **Floating or flush:** flush runs to the screen edge like a taskbar, with only the inner corners rounded; icons stay clear of the system bars.
- **Fill:** the shared glass color or a gradient to a second color, plus an optional pattern (including the quartz ones) cut into the glass.
- **Edge:** none, hairline, or a soft or bright rim of light that's brightest at the top-left and bottom-right corners.
- **Padding** around the dock icons.
- **Swap handle** (Settings › Dock): a small pill on the dock's inner edge flips it to the other side, on every screen. "Swap dock side" also works as a gesture.

## Phase 4: what's new

- **Quartz wallpaper:** Quartz draws the wallpaper itself. Settings › Wallpaper has gradients (linear, radial, and four corners plus a middle color), solid colors, your own photos, patterns (dots, grid, stripes, checks, waves) and a Darken slider. Presets to start from. The old test pattern is still there as "Test".
- **Your photos stay on the phone:** Android's photo picker shares only the photo you choose; Quartz keeps a scaled copy in its private storage. Position it with Across, Up and down, and Zoom.
- **Folding phones:** Shared · cropped (the cover shows its half of the unfolded wallpaper), Shared · stretched (fitted to each screen), or Independent (a different wallpaper per screen).
- **Frosted glass:** the dock, app drawer and folders blur the wallpaper behind them. The blurred copy is made once per change and shared, so it costs almost nothing, and a new wallpaper engine after a fold draws instantly.
- **Readable on light backgrounds:** labels, folder text, the drawer header and the status bar turn dark on light glass and light wallpapers.
- **See-through slider:** now 0–100%, with 50% in the middle.
- **Setup:** a wallpaper step after the dock style. Profiles carry the wallpaper settings (not the photo).
- **After updating:** the wallpaper service was renamed, so your phone falls back to its own wallpaper once. Open Settings › Wallpaper and tap **Use Quartz wallpaper**.

## Phase 3: what's new

- **Folders:** drag an app onto another and hold for a moment (the target lights up), then let go. Drop more apps onto a folder the same way. Works on the home screen and in the dock. New folders are named from the apps' shared category when Android knows it.
- **Folder icons:** plain folder, folder with a badge (16 to choose from), or a 2×2 / 3×3 preview of the apps inside.
- **Opening:** full screen, floating box, pop-up above or below the folder (grid or list, with a grid size), or an expanding row (sideways or up and down). On the unfolded Fold, folders open within their own half.
- **Inside a folder:** tap the name to rename it; the ⋯ menu has Rename, Badge and Remove folder. Long-press an app and drag it out onto the home screen or dock. A folder left with one app turns back into that app.
- **Gestures:** swipe past the first or last page, pinch, and two-finger swipe down now work, along with swipe up/down and double-tap.
- **Quartz Lock:** "Lock screen" gestures work once Quartz Lock is turned on in Accessibility settings (you're asked the first time). It can only lock the screen.
- **Profiles** now carry folders too.

## Phase 2

The layout engine: dock on any edge, per-device grids, grid flip on rotation, positions kept on grid changes, rotation lock, layout in profiles.

## Phase 2: what's new

- **Dock on any edge:** bottom, top, left, right or none, per screen. Full length or fit to icons; side docks sit top, middle or bottom. Long docks scroll. On the Fold, a side dock stays on the outer edge.
- **No dock + apps button:** the button sits on the home screen.
- **Per-device grids:** phones and the cover screen have their own grid; tablets have their own single grid (no more doubled phone layout). Up to 10 × 10.
- **Grids flip when the screen turns:** a tablet held upright (or a phone allowed to rotate) flips the grid, iPad style, keeping every app in order.
- **Changing the grid keeps your apps in place:** each app keeps its row and column; only apps that no longer fit move.
- **Unfolded:** pages are always one strip (the old Paired mode is gone, its apps moved onto your pages). With carry-over off, the unfolded screen shows one page at a time in its own grid.
- **Rotation:** the phone and cover screen stay upright unless you allow rotation in Settings › Home screen.
- **Profiles include your layout:** export/import moves settings and home screen between devices. Missing apps leave empty spots.
- **Uninstalled apps** free their spots instead of leaving invisible gaps.

## Phase 1: what's new

- **Setup wizard** on first launch: privacy promise, set as default home, then Recommended / Walk me through it / Import. Function first (dock, grid, fold carry-over, drawer), then form (dock style, color and glass, icons, widgets), with a live preview of this device on every question.
- **Settings** (long-press an empty spot → Quartz settings): Home screen, Dock, App drawer, Appearance, Gestures, Privacy & backup, About, Restart setup.
- **Synced vs per-screen settings:** on a foldable, tap the cover or unfolded screen at the top to choose which one you're editing. Per-screen settings show a link chip: "Synced with the other screen" or "Only this screen".
- **Gesture map:** each gesture holds one action. Picking a taken gesture for the app drawer asks before replacing it.
- **Profiles:** export and import settings as a file (Privacy & backup).
- **Wired into the home screen now:** grid, pages, cover side, page mode, dock color/see-through/shape/roundness, app names, icon size, drawer columns and color, the apps button, swipe up/down and double-tap actions.
- **Saved for later phases:** dock position on other edges, drawer styles, icon shapes, badges, widgets, rotation lock, blur behind the dock.

## Phase 0

The fold test build underneath: its only job was to prove fold behavior on real devices.

## What it does

- Can be set as the default home app.
- **Cover screen / phone:** one grid (default 4×6) plus a dock.
- **Unfolded / tablet:** the same grid doubled horizontally (4×6 → 8×6). The cover layout lands on the left or right half, split at the hinge position the phone reports.
- Long-press an icon and drag it to any slot, including across halves and into the dock. Drag to **Remove from home** at the top to take it off (it stays installed).
- Several home pages (default 3). Swipe left/right; while dragging, hold an icon at the screen edge to flip pages.
- Unfolded page modes (test panel): **Paired** (each cover page has its own inner half) or **Side by side** (unfolding shows two cover pages at once, like an open book).
- Rotated unfolded: the apps reflow into one grid sized for the tall screen, iPad style. Rotate back and everything is where it was.
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
