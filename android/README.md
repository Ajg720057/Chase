# Look Up

An Android app that keeps track of how long you spend in social media apps. Once a
sitting reaches 10 minutes, it shows a motivational quote on top of the app.

## What it measures

Only **which app is open and for how long**, using Android's built-in usage statistics.
It never reads what's on screen, what you type or who you talk to. The app doesn't
even request internet permission, so nothing can leave your phone.

## How it behaves

- A *sitting* is continuous time in any tracked app. Switching between Instagram and
  TikTok counts as one sitting. Being away from all of them for 3 minutes ends it.
- When a sitting reaches the limit (10 minutes by default, adjustable from 5 to 60), a
  full-screen quote card appears with two choices:
  - **I'm putting it down**: closes the card and goes to your home screen.
  - **5 more minutes**: closes the card, and another one appears in 5 minutes if you're
    still scrolling. Back works the same way.
- If "Display over other apps" isn't granted, the quote arrives as a notification.
- The home screen shows today's total time and how many nudges you've had.
- Monitoring restarts by itself after a reboot.

The tracked apps by default are Instagram, Threads, TikTok, Facebook, Snapchat, X,
Reddit, YouTube, Pinterest, LinkedIn, Tumblr, Bluesky and BeReal. Tap **Choose apps** to
change the list.

## Install

1. Open the repo's **Actions** tab, then the latest **Build Android app** run, and
   download the `look-up-apk` artifact. It's a zip containing `app-debug.apk`.
2. Copy the APK to your phone and open it. Allow installs from that source when Android
   asks.
3. Open Look Up and grant the three permissions it lists, then switch **Monitoring on**.
   Tap **Preview a quote** to see what the nudge looks like.

To build it yourself instead, run `./gradlew assembleDebug` in this folder. That needs
the Android SDK (Android Studio installs it).

## Note for Samsung, Xiaomi and similar phones

Some manufacturers kill background apps aggressively. If the nudges stop appearing, go
to Settings → Apps → Look Up → Battery and set it to **Unrestricted**.
