# Tapedeck

**Your tape deck for the road.** Tapedeck saves YouTube videos as audio (or video) on your phone and plays
them offline: with the screen off, through the car stereo, Bluetooth or Android Auto, and always from where
you stopped.

Share a video from the YouTube app → Tapedeck → *Download*. That's it.

## Features

**Downloading**
- Share any YouTube link (or one of the [other sites yt-dlp supports](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)) straight into Tapedeck, or paste it
- Audio as **M4A** (YouTube's own AAC, no re-encoding), **MP3** (for car stereos that play USB sticks) or the original stream; *data saver* quality at ~64 kbit/s for talks and podcasts
- Video in 360p–1080p, preferring H.264 so every phone plays it smoothly
- Whole **playlists** in one go
- A live progress bar with speed and time left, a queue with 1–3 parallel downloads, and a notification
- **Wi-Fi only** mode: downloads wait for Wi-Fi and continue by themselves, picking up partial files
- Optional **SponsorBlock**: cuts sponsor segments, self-promotion and "subscribe" reminders out of downloads
- Readable error messages (unavailable, private, age-restricted, bot check, no space, …)

**yt-dlp that keeps working**
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) with its own Python, FFmpeg and QuickJS (the JavaScript runtime yt-dlp needs for YouTube since 2025.11), via [youtubedl-android](https://github.com/yausername/youtubedl-android)
- Checked for updates on GitHub whenever you open Tapedeck (at most once an hour), never in the background; nightly channel by default (YouTube fixes land there first)
- When a download fails in a way an update may fix, Tapedeck retries it once, and fetches the newest yt-dlp first if the app is open

**Listening in the car**
- Plays with the screen off and survives the app being closed
- Lock screen, headset and steering-wheel buttons; **−10 s / +30 s** in the media notification
- Remembers the position of every download; resumes a few seconds earlier so you don't lose the thread
- The car's play button resumes the last download even after Android closed the app
- **Pauses for navigation announcements** instead of talking over them (optional)
- Keeps playing the next download in the library's order (sort *oldest first* for a lecture series)
- Speed 0.75×–2×, *skip silence*, chapters
- **Android Auto** lists *Continue*, *New* and *All downloads*
- Full-screen video when parked

**Keeping the phone tidy**
- Library with search, filters (audio / video / new) and sorting
- Delete with undo, *keep* to protect a download, *delete everything heard*
- **Automatic clean-up** after 1–90 days, counted from the download or from the moment you heard it to the end
- Save a file to `Download/Tapedeck` (e.g. for a USB stick) or share it

## Install

Tapedeck is not on Google Play: Play does not allow apps that download from YouTube (see
*Device and Network Abuse* in the Play policy). Build it yourself, or install an APK from the
[releases](https://github.com/DevEmperor/Tapedeck/releases) and pick the one for your phone's ABI
(`arm64-v8a` for almost every phone of the last years).

Android Auto shows apps that were not installed from Play only after turning on *Unknown sources* in
Android Auto's developer settings.

## Build

Requires JDK 21 and the Android SDK (compileSdk 37).

```bash
./gradlew :app:assembleDebug -Ptapedeck.abis=arm64-v8a   # one ABI, for your phone
./gradlew :app:assembleRelease                           # arm64-v8a, armeabi-v7a, x86_64
./gradlew :app:testDebugUnitTest
```

Release builds are signed with the key from an untracked `keystore.properties` (`storeFile`,
`storePassword`, `keyAlias`, `keyPassword`), or with the debug key if it is missing.

## Privacy

Tapedeck has no accounts, analytics or ads. It connects only to:
- YouTube (or the site you shared), through yt-dlp, to download what you asked for, and YouTube's oEmbed endpoint for the title and picture in the download sheet
- the GitHub API, to check for and download yt-dlp updates
- the SponsorBlock API, only if *Skip sponsor segments* is on (yt-dlp sends a short hash prefix of the video id)

Downloads stay in the app's own folder on your phone until you or the clean-up delete them.

## Please note

Only download what you may keep, for example your own videos, freely licensed content, or a private copy
where your law allows it. YouTube's terms of service restrict downloading.

## License

Tapedeck is free software under the [GNU General Public License v3.0](LICENSE) or later.
It bundles yt-dlp (Unlicense), youtubedl-android (GPL-3.0), Python (PSF), FFmpeg (GPL), QuickJS (MIT),
AndroidX Media3, Coil and OkHttp (Apache-2.0).

Contact: contact@devemperor.net
