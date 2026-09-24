# LifeBoard (Android)

A to-do list, calendar and journal app. Everything stays on your phone.

## Install on your phone

1. On your Android phone, open
   **https://github.com/Ajg720057/Chase/releases/tag/lifeboard-latest**
2. Tap **LifeBoard.apk** to download it, then open the download.
3. If Android asks, allow your browser to "install unknown apps", then tap **Install**.
4. Open LifeBoard and allow notifications so alarms can ring.

Every push to `android-app/` rebuilds the APK and replaces the file on that release.
New builds install over the old one and keep your data, because every build is
signed with the same key (`app/lifeboard.keystore`).

## Features

**Tasks**
- Priority levels (High / Medium / Low / None) shown as a colored stripe.
- Sort by **My order** (drag the ≡ handle), **Priority**, or **Due date**.
- Due date and time, with an optional **alarm** that rings even when the app is
  closed. The notification has **Done** and **Snooze 10 min** buttons.
- **Repeats** daily, on weekdays, weekly, monthly or yearly. Checking off a repeating
  task moves it to its next date and clears its subtasks.
- **Subtasks**, nested as deep as you like. Tap a subtask to give it its own date,
  alarm, priority or subtasks. Checking off a parent also checks off its subtasks.

**Calendar**
- Month view. Dots mark days with tasks (red if overdue) and journal entries, plus
  that day's mood.
- Upcoming repeats of repeating tasks appear on their future dates.
- Tap a day to see its tasks and journal entries, or add either for that day.
  Swipe left or right to change month.

**Journal**
- Entries for any date, with a title, text, a mood (😞 to 😄) and photos.
- Search across all entries.

**Home-screen widget**
- Long-press your home screen, choose **Widgets**, find **LifeBoard tasks** and drag it onto the screen.
  You can resize it.
- It lists your unfinished tasks. Tasks with dates come first, soonest at the top, with overdue ones
  in red. Tasks without dates follow, sorted by priority.
- Tick a checkbox to complete a task. A repeating task moves to its next date instead.
- Tap a task to open it in the app.
- **+** opens a quick popup to add a task with a priority. **Date, alarm, subtasks…** opens the full editor.
- The ✎ button opens a quick journal popup for today with a mood and text. **Add photos, title…** opens
  the full entry.

**PlanBoard link**
- Plan to-dos from the PlanBoard app can be sent here. They show up as tasks with
  "From plan: …" in their notes.
- Checking one off here checks it off in PlanBoard too, and the other way round.
- Only PlanBoard (identified by its signing key) can add or read tasks this way.

**Backup**
- Open the ⋮ menu on any tab and choose **Back up to file…** to save everything
  (tasks, journal and photos) as a single `.zip` file. Save it to Google Drive or
  Downloads.
- Use **Restore from backup…** on a new phone. Restoring replaces all current data.

## Building locally

This project needs JDK 17 and the Android SDK (API 35). Build it with:

```
cd android-app
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Tech stack: Kotlin, Jetpack Compose (Material 3), Room, AlarmManager, Coil, and
[Reorderable](https://github.com/Calvin-LL/Reorderable).
