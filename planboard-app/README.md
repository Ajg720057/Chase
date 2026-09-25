# PlanBoard (Android)

Plan your days, weeks and months, then build on those plans: break them into smaller
plans, track progress, and send their to-dos to LifeBoard. Everything stays on your phone.

## Install on your phone

1. On your Android phone, open
   **https://github.com/Ajg720057/Chase/releases/tag/planboard-latest**
2. Tap **PlanBoard.apk** to download it, then open the download.
3. If Android asks, allow your browser to "install unknown apps", then tap **Install**.

Every push to `planboard-app/` rebuilds the APK and replaces the file on that release.
New builds install over the old one and keep your data, because every build is signed
with the same key (`app/planboard.keystore`).

To link to-dos to LifeBoard, also install the latest LifeBoard build
(**https://github.com/Ajg720057/Chase/releases/tag/lifeboard-latest**). LifeBoard builds
from before the PlanBoard link can't receive to-dos.

## Features

**Day, Week and Month tabs**
- **Day**: that day's plans (sorted by time), to-dos due that day, and the week and
  month plans it belongs to.
- **Week**: plans for the week, then each day of the week with its plans. Tap a day
  to open it, or **+** to plan it.
- **Month**: a calendar with a dot per plan on each day (orange means in progress,
  green means done), the month's plans, the week plans in that month, and the
  selected day's plans.
- Swipe left/right or use the arrows to move between days, weeks or months. The
  **Today** button jumps back to today.
- Colored stripes tell plans apart: purple for month plans, blue for week plans, teal for day plans.

**Plans**
- A title, details, and a date. Switch a plan between Day / Week / Month any time.
- Day plans can have a start time and, if there's a set finish, an optional end time.
  PlanBoard shows how long it lasts (e.g. "1 hr 30 min"); an end earlier than the start
  means it runs past midnight.
- **Status**: Planned → In progress → Done. Tap the circle on any plan card to move it along.
- **Break it down**: add week or day plans under a month plan, day plans under a week plan, or
  steps under a day plan. Sub-plans show on their own dates too, marked "Part of …".
- **Progress log**: add dated updates to a plan as you go.
- **Duplicate to…** copies a plan (with its sub-plans and to-dos) to another date.
- **Repeat…** makes copies daily, weekly or monthly, up to 52 times.

**To-dos and LifeBoard**
- Each plan has its own to-do list. To-dos can have a due date and time; the Day tab
  lists every to-do due that day.
- Turn on **Send to-dos to LifeBoard** on a plan. Its open to-dos, and any you add later, then
  appear in LifeBoard with "From plan: …" in their notes.
- Or send a single to-do with its link button.
- A to-do checked off in either app is checked off in both. Renames and deletions made in
  LifeBoard are picked up when you return to PlanBoard. Deleting a to-do (or its plan) in
  PlanBoard also removes it from LifeBoard.
- On a linked to-do, the link button can **Open in LifeBoard** or **Stop syncing**.

**PDF itinerary**
- Open the ⋮ menu on any tab and choose **Export itinerary (PDF)…**. Or, on a plan, choose
  **Export as PDF…** from its ⋮ menu.
- Pick a **Day**, **Week** or **Month** (use the arrows to move to other ones), or **Custom**
  for any range up to a year.
- Choose what to include: to-dos, plan details, progress log, finished plans, and days with
  nothing planned.
- The PDF lists the month goals and week plans for those dates, then goes day by day with
  start–end times and durations, details and to-do checkboxes. Save it anywhere (Downloads, Google Drive…), then
  open or share it straight away.

**Backup**
- Open the ⋮ menu on any tab and choose **Back up to file…** to save everything as a
  `.json` file. Save it to Google Drive or Downloads.
- Use **Restore from backup…** on a new phone. Restoring replaces all current data.

## How the LifeBoard link works

LifeBoard has a task provider (`content://com.chase.lifeboard.tasks/tasks`) that only
answers apps signed with PlanBoard's key. It checks the caller's signing certificate
against a pinned SHA-256 fingerprint, so no other app on the phone can read or add
tasks. PlanBoard uses it to create, update, check off and delete the tasks it made.

## Building locally

This project needs JDK 17 and the Android SDK (API 35). Build it with:

```
cd planboard-app
./gradlew testReleaseUnitTest assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Tech stack: Kotlin, Jetpack Compose (Material 3), Room.
