# Workout Log (Android)

A simple workout logger: pick an exercise, answer a couple of questions, and save.

- **Log** — ~55 built-in exercises (bench, squat, curls, pull-ups, plank, running, cycling…) grouped by
  muscle group, with search, a "Recent" list and custom exercises. The log screen asks what matters for
  that exercise:
  - Weights: how many sets, then reps × weight (lb) per set — set 1 fills in the rest.
  - Bodyweight: sets × reps. Timed holds: sets × seconds.
  - Cardio: how long, how far (miles), with live pace.
  - Everything is pre-filled from last time, so repeating a workout is one tap.
- **History** — every entry grouped by day; tap to edit, trash icon to delete.
- **Calendar** — month view with workout days highlighted, current/longest streak and days-per-week chart.
- **Progress** — per-exercise charts and headlines like "12% more weight" or "8% faster", comparing
  your first session to your latest, plus vs. last session and your personal best. Saving a new best
  shows a "New personal record!" message.

Data is stored on the phone only (`workouts.json` in the app's private storage).

## Install on your phone

Every push that changes `workout-app/` builds the app in GitHub Actions and publishes it as the
**workout-app-latest** release. On your phone, open the repo's *Releases* page, download
`WorkoutLog.apk`, and open it. Android will ask you to allow installs from your browser the first time.

Newer builds install over older ones and keep your data, because every build is signed with the same
key (`app/debug.keystore`, a throwaway key made for this project — not suitable for a Play Store release).

## Build locally

Open `workout-app/` in Android Studio, or run `./gradlew assembleRelease` with the Android SDK installed.
Unit tests: `./gradlew testReleaseUnitTest`.
