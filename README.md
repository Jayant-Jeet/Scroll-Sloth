# Scroll Sloth

Scroll Sloth is an Android-only, on-device app for counting short-form video sessions in Instagram, YouTube Shorts, and Facebook Reels. The current build includes onboarding, rule-driven reel detection, daily/weekly/monthly statistics with laziness and unproductivity scores (with a "why this score" breakdown), on-device commentary (Gemma or templates), and the debug **Rule Inspector** used to collect the real UI structure that detection rules are built from.

## Architecture overview

- `app`: the only Android application module, written in Kotlin with Jetpack Compose and Material 3.
- `com.reelsloth.AppContainer`: hand-written dependency container created by `ScrollSlothApplication` and shared by the activity and the accessibility service (one DataStore instance per process).
- `com.reelsloth.detect`: supported platforms, the detection-rules schema, loader and repository, `RulesUpdater`/`HttpRulesFetcher` (rules fetch), `AppStatuses` (per-app status), `RuleEngine` (node tree → `DetectorEvent`), the pure-Kotlin `ReelStateMachine` (dwell, dedupe, resets), `ReelDetector` (glue), `ReelAccessibilityService`, and the enabled-service check.
- `com.reelsloth.data`: Room entities and DAO, the Room database, `ReelEventWriter` (a Channel drained by one writer coroutine), Preferences DataStore settings (onboarding, Inspector mode, tracking pause, week start, scoring config), `CsvExporter`, and `UserData` (export and delete-all).
- `com.reelsloth.debug`: the Rule Inspector — node snapshot serialiser, event ring buffer, capture storage, the floating Capture overlay, capture sharing, and the captures screen.
- `com.reelsloth.stats`: `PeriodCalendar` (day/week/month ranges in local time), the pure `StatsCalculator` (sessions, late night, baseline), `StatsEngine` (Flows for today, this week, this month and previous periods, refreshed at local midnight), and `PeriodCharts` (week bars, month heat map, best/worst day, chart descriptions for screen readers).
- `com.reelsloth.score`: `ScoreConfig` and `ScoreBands`, the deterministic `ScoreEngine`, `ScoreExplainer` (the "why this score" lines), and `ScoreConfigInput` (Settings form validation).
- `com.reelsloth.ai`: on-device commentary — `CommentaryEngine`, `StatsFacts`, `PromptBuilder`, `NumberGuard`, `TemplateCommentaryEngine` (fallback), `GemmaCommentaryEngine` (guard + one retry + fallback) over a `LanguageModel` seam implemented by `LiteRtLanguageModel`, `ModelDownloader`/`ModelFiles` (download and SHA-256 marker), and `CommentaryRepository` (facts for completed periods, cache, catch-up).
- `com.reelsloth.ui`: onboarding, the Today/Week/Month screens, Settings, shared components (score card with "why this score", commentary card with source badge, per-app split, banners, empty states), and light/dark theme. See [Screens](#screens).
- `com.reelsloth.work`: `NightlySchedule` (next 00:30 local, DST-safe), `NightlyCommentaryWorker`, and `RulesUpdateWorker`.

Room stores only event timestamps, platform identifiers, and dwell durations, plus a commentary cache (period key, tone, text, source, retries, created time).

## Privacy statement

All app data is stored on the device. The only network requests are the optional one-time model download and the detection-rules fetch described below; both are plain downloads of public files and send no user data; the app includes no analytics or crash-reporting SDK. It never stores captions, usernames, video URLs, screen text, or reel content.

The accessibility service is limited in its XML configuration to exactly `com.instagram.android`, `com.google.android.youtube`, and `com.facebook.katana`, so Android never delivers events or window content from other apps. For detection, the service reads only structure from those windows: whether a reel screen is shown, the pager position, and (for Facebook) a hash of selected labels. That hash is kept in memory only, for at most the last 10 reels per app, and is never written to disk. Each counted reel is stored as a timestamp, a platform id, and a dwell time — nothing else. Rule Inspector captures contain structure only (see below); they stay in app-private storage until you choose to share them.

## Onboarding and accessibility access

Onboarding explains the permission and provides a shortcut to Android Accessibility settings. For sideloaded installations on Android 13+, the user may need to open **App info > menu > Allow restricted settings** before enabling the service.

## Rule Inspector

Third-party apps do not publish their UI structure, so detection rules must come from real captures rather than memory.

### Using it

1. Enable the Scroll Sloth accessibility service.
2. Open **Settings > Advanced** and turn on **Inspector mode**. A floating **Capture** button appears (drag it anywhere).
3. Open a supported app. Capture once while watching a reel (Instagram Reels, YouTube Shorts, Facebook Reels) and once on a non-reel screen such as the home feed.
4. Open **Settings > Advanced > Rule Inspector captures** to share captures as `.json` files (one or all) or delete them.

Turning Inspector mode off hides the button and clears the in-memory event log.

### Why an overlay button rather than a quick-settings tile

The button is a `TYPE_ACCESSIBILITY_OVERLAY` window owned by the accessibility service. It needs no extra permission (no "draw over other apps") and the target app stays in the foreground while you tap it. A quick-settings tile would need the notification shade open, which makes System UI the active window at the exact moment of capture, and would need an extra `TileService`.

The service also sets `flagRetrieveInteractiveWindows` (beyond the requested `flagReportViewIds`). Capture prefers the active window, but if tapping the overlay ever makes it the active window, the service falls back to the interactive window whose root belongs to a supported app. Window content of other packages is still never captured.

### Capture format (`schemaVersion` 1)

Files are named `capture-<platform>-<epochMs>.json`.

| Field | Meaning |
| --- | --- |
| `capturedAtEpochMs`, `capturedAtUptimeMs` | Capture time (wall clock and uptime, the latter matches event times) |
| `platform`, `app` | Platform and the app's `packageName`, `versionName`, `versionCode` — UI structure changes between app versions |
| `device` | `sdkInt` and screen size in pixels, to interpret `bounds` |
| `nodeCount`, `truncated` | Nodes captured; `truncated` is true if the 3000-node or 80-level depth cap was hit |
| `viewIds` | Sorted, distinct view IDs on screen — the quickest thing to diff |
| `recentEvents` | Last 30 events from supported apps: `type`, `packageName`, `className`, `scrollDeltaX/Y` (scroll events, Android 9+), `fromIndex`, `toIndex`, `itemCount` (absent when Android reports -1) |
| `root` | Node tree |

Each node has `path` (child indices from the root, e.g. `0.3.1`), `className`, `viewId`, `hasText`/`textLength`, `hasContentDescription`/`contentDescriptionLength`, `scrollable`, `bounds`, optional `collectionInfo`, `collectionItemInfo`, `rangeInfo`, `childCount`, and `children`. Actual text and content descriptions are never written — only whether they exist and their length.

### Reading which nodes differ

- Compare `viewIds` between a reel capture and a feed capture of the same app: IDs present only on the reel screen are candidate reel markers.
- For a finer diff, flatten each tree to `path viewId className scrollable` lines and diff them, e.g. with `jq`:

  ```shell
  jq -r '.root | recurse(.children[]?) | "\(.viewId // "-") \(.className) scrollable=\(.scrollable) \(.collectionInfo // "")"' capture-a.json > a.txt
  ```

- `recentEvents` shows which container scrolls (`TYPE_VIEW_SCROLLED` with `fromIndex`/`toIndex`) when you swipe between reels.

## How detection rules work

Detection is data-driven. A versioned `detection-rules.json` (`schemaVersion` 1, plus a `rulesVersion`) tells the app, for each supported package:

- `reelScreenMatchers`: nodes that must **all** be visible for the screen to count as a reel screen.
- `strategy`: `ITEM_CHANGED` (a new reel is a new pager position, read from collection/range info) or `KEY_CHANGED` (a new reel is a new hash of `keyFields`, e.g. `contentDescription`).
- `pagerMatcher`: the node that holds the pager or reel page.
- `keyFields` and optional `keyMatchers`: what to hash for `KEY_CHANGED`. `keyMatchers` restricts the hash to matching nodes so things that change during one reel (play/pause controls, like counts) do not look like a new reel.

A matcher can use `viewIdContains`, `classNameEquals`, `scrollable`, `minWidthFraction`, `minHeightFraction`, `minTopFraction` and `maxRightFraction` (fractions are of the window). Facebook obfuscates its view IDs, so its rule relies on class, size and position.

`minDwellMs` (2 s) and `maxDwellMs` (120 s) apply to all apps: a reel counts once after at least 2 s, when you move on or leave; loops are capped at 120 s; a reel seen again within 60 s is not counted twice.

### Bundled rules and calibration

The bundled `app/src/main/assets/detection-rules.json` contains only `"TODO_FROM_INSPECTOR"` values. Any rule containing a TODO, or a matcher with no conditions, is disabled, and Settings shows that app as **Needs calibration**. Nothing is counted for it.

Calibrated values are derived from Rule Inspector captures (`captures/<date>-<device>/`, currently one Samsung S24 Ultra on Android 16) and live in [`rules/detection-rules.json`](rules/detection-rules.json). The rules unit tests replay those captures, so a rule change that stops matching them fails the build. To use them, either:

- open **Settings > Advanced > Import detection rules** and pick the file, or
- with a debug build: `adb push rules/detection-rules.json /data/local/tmp/` then `adb shell "run-as com.reelsloth sh -c 'cat /data/local/tmp/detection-rules.json > files/detection-rules.json'"`.

The imported file overrides the bundled one until you choose **Use bundled rules**. Settings > Advanced also shows a live detector readout to check a rule while using an app. **Use bundled rules** also forgets downloaded rules.

### Rules updates

`RulesUpdateWorker` fetches `detection-rules.json` once every 7 days when a network is available, and **Settings > Detection rules > Check now** runs the same check on demand. The request is a plain HTTPS GET of a public file: no query parameters, identifiers or user data, no caching, 15 s timeouts, 256 KB limit. The downloaded file is validated like an imported one and is only kept if its `rulesVersion` is higher than the current rules. Settings shows the version, its source (bundled, imported or downloaded) and when the server last answered.

The address is a build setting and is **empty by default**, which turns the fetch off (no worker is scheduled and Settings says so). To enable it, publish [`rules/detection-rules.json`](rules/detection-rules.json) at an `https://` address and build with `./gradlew assembleDebug -PrulesUrl=https://example.com/detection-rules.json`.

## Statistics and scores

All numbers are computed on the device by plain Kotlin from the stored events. The language model will never compute or change them.

**Statistics** (`PeriodStats`), in the phone's time zone:
- A reel belongs to the local day it started on. Minutes = total dwell time / 60 000.
- Weeks start on Monday by default (Settings > Week starts on).
- A session is a run of reels where each starts less than 5 minutes after the previous one ended. A session that crosses midnight is split between the two days for daily figures, and counted whole in the week or month.
- Late night = reels started between 23:00 and 04:59.
- Baseline = median daily count over the 28 days before the day (or period), counting only days since your first recorded reel, with zero days included. It is empty until there are at least 7 such days.

**Scores** per day, with `clamp` limiting to 0..1 (defaults are editable in Settings > Advanced):

| Component | Formula | Weight |
|---|---|---|
| Volume | `clamp(reels / heavyDaily) * 100` (heavyDaily = 200) | 45% |
| Above your usual | `clamp(reels / baseline - 1) * 100`, 0 without a baseline | 15% |
| Late night | `clamp(lateShare / lateShareHeavy) * 100` (lateShareHeavy = 50%) | 20% |
| Longest session | `clamp(longestSession / sessionHeavy) * 100` (sessionHeavy = 100) | 20% |

Laziness is the weighted sum of these. Unproductivity = `clamp(minutes / (dailyMinutesBudget * 4)) * 100`, where dailyMinutesBudget = 30.

Week and month scores are the mean of the daily scores, and days with no reels count as 0. For the current week or month, only the days up to today are averaged. Scores are rounded half up, then banded as follows (the labels are editable):
0-19 Fully awake, 20-39 Casual scroller, 40-59 Sofa sloth, 60-79 Reel hibernator, 80-100 Fossilised.

## Screens

All screens use Material 3 with light and dark themes and are drawn with plain Compose (charts use `Canvas`, no charting library). Charts have a spoken description for screen readers, and touch targets are at least 48 dp.

- **Today:** today's count and minutes, yesterday for comparison, laziness and unproductivity cards (band label, meter and a "Why this score" expander listing each component), the per-app split, and yesterday's verdict with a **Gemma** or **Template** badge and the Gentle/Roast toggle. While Gemma downloads, the card says templates are used until it is ready.
- **Week:** seven bars of reels per day with today highlighted (future days are stubs), week total, average per tracked day, weekly scores and last week's verdict.
- **Month:** a calendar heat map (shade = quarters of `heavyDaily`, today outlined), month total, best and worst finished day (from 2 tracked days; ties go to the earlier day), monthly scores and last month's verdict.
- **Settings:** pause counting, accessibility status, per-app status (**Working**, **Needs calibration**, **Permission missing**; calibration is reported first because an uncalibrated app is never counted), commentary tone, week start, the on-device writer (download, progress, delete), detection rules (version, source, last check, **Check now**), your data (**Export CSV**, **Delete all data** with confirmation), and **Advanced** (score settings, write commentary now, Rule Inspector tools, rules import/reset, live detector).
- **Empty and blocked states:** a banner explains when nothing can be counted (permission off, counting paused, or no calibrated app), and empty days, weeks and months get a friendly note instead of zero-value scores.

**Pause counting** stops detection in the accessibility service immediately. A reel already on screen for at least the minimum dwell when you pause still counts, just as if you had left the app.

**Export CSV** writes `scroll-sloth-reels-<date>.csv` (`timestamp_ms,local_time,platform,dwell_ms`) to the app cache and opens the share sheet; nothing leaves the phone unless you pick a target. **Delete all data** removes every counted reel, all commentary, Inspector captures and old exports. Settings, detection rules and the downloaded model are kept.

## Commentary

Each period screen shows a short verdict on the last completed day, week or month ("Yesterday's verdict", etc.). The UI only reads a cache, so it never waits for a model.

- **Writer.** [LiteRT-LM](https://ai.google.dev/edge/litert-lm) 0.18.0 (MediaPipe LLM Inference is in maintenance mode) running **Gemma 4 E2B instruction-tuned** (`gemma-4-E2B-it-gpu.litertlm`, ~1.9 GB, Apache 2.0, not gated, so no token or accepted terms are needed). GPU backend only (this file has no CPU sections; if the GPU cannot load it, templates are used), FLOAT32 activations, greedy decoding (top-k 1, temperature 0.1), at most 60 output tokens, thinking off. The engine is loaded per job and closed straight after; a verdict takes a few seconds on a Galaxy S24 Ultra. With the default FLOAT16 activations that phone produced garbled words ("you watchedヒル closely"), matching [LiteRT-LM #3879](https://github.com/google-ai-edge/LiteRT-LM/issues/3879); FLOAT32 with a separate GPU cache directory fixes it.
- **Prompt.** `PromptBuilder` sends only a JSON block of facts (reels, minutes, band, late-night reels, previous-period reels, delta) and the rules: two sentences max, no medical or mental-health claims, no insults about the person's worth, the requested tone (Gentle or Roast), and only the numbers given.
- **NumberGuard.** Every number in the reply (digits, decimals, percentages, and number words up to twenty, plus tens) must be in an allow-list built from the facts and simple derived values (rounded percentage change, late-night share, hours from minutes). Empty text, text over 300 characters, banned phrases, and letters from non-Latin scripts (a sign of garbled output; emoji, accents and a renamed band label pass) are rejected. On failure the model gets one retry naming the rejected numbers; a second failure falls back to the template engine. The cache records `source` (GEMMA or TEMPLATE) and `retries`, and the card says "Written by Gemma on your phone" or "Template".
- **Templates.** `TemplateCommentaryEngine` has 5 to 8 deterministic templates per band and tone. It is always available and is used before the model is downloaded.
- **Nightly job.** `NightlyCommentaryWorker` runs at about 00:30 local time while charging and idle. It writes any missing verdict for the latest completed day, week and month, so the weekly verdict appears on the week-start day, the monthly one on the 1st, and missed nights catch up. A template verdict is rewritten once the model is ready, and every verdict is rewritten when the tone changes. It is a one-time job that re-arms itself for the next 00:30 (periodic work drifts under charging/idle constraints). Settings > Advanced > **Write commentary now** runs it immediately without constraints.
- **Download.** Settings > On-device writer. Uses Android `DownloadManager` (resumable, survives app restarts), Wi-Fi only unless "Allow mobile data" is on, into the app-specific external files dir (`Android/data/com.reelsloth/files/models`). The URL is pinned to a Hugging Face revision, and the file must match the pinned size and SHA-256. A marker file records the verified size and modification time; on every start the app checks the file against the marker and deletes and re-downloads it if they do not match. Needs the model size plus 200 MB free.

## Build and test

Run the requested verification from the project root:

```shell
./gradlew testDebugUnitTest assembleDebug
```

Compose UI tests (for example `TodayScreenTest`: no permission, no data, normal, model downloading) run under Robolectric as part of `testDebugUnitTest`, so CI needs no emulator.
