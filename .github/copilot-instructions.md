# Project: Scroll Sloth

Android app (Kotlin, Jetpack Compose) that counts how many short-form videos ("reels") the user
watches in Instagram, YouTube Shorts and Facebook Reels, shows daily / weekly / monthly counts,
and rates the user's laziness and unproductivity. Built for the DEV Hacktoberfest "Slow and Steady" challenge.

## Non-negotiable rules
- Android only. minSdk 26, targetSdk = latest stable. Kotlin only. Gradle Kotlin DSL, version catalog.
- Everything runs on the device. There is NO backend and NO analytics/crash-reporting SDK.
- Only two network calls are allowed: (1) a one-time Gemma model download, (2) a periodic fetch of
  `detection-rules.json`. Neither may send any user data. Do not add any other network code.
- Privacy: never store or log reel content (captions, usernames, URLs, text). Store only
  timestamp, platform id, dwell milliseconds. Reel identity keys are hashed and kept in memory only.
- The AccessibilityService must be limited by `packageNames` in its XML config to exactly:
  com.instagram.android, com.google.android.youtube, com.facebook.katana.
- Scores are computed by deterministic Kotlin code. The language model NEVER computes or changes a number.
  It only writes commentary, and a NumberGuard rejects any commentary containing a number not in the stats.
- Do NOT invent Android view IDs or UI structure of third-party apps. Detection rules live in JSON, and
  the values come from real device captures made with the in-app Rule Inspector. Use clearly marked
  placeholders until real captures exist.
- Before using any third-party API (MediaPipe LLM Inference / LiteRT, Room, WorkManager), read its
  current official documentation and use the current API. Do not guess method names.
- Prefer small, testable classes. Constructor injection by hand (no Hilt/Dagger). Coroutines + Flow.
- Every non-UI class gets unit tests. Keep the build green after every change.

## Package layout
com.halfbloodcoder.scrollsloth
  detect/    ReelAccessibilityService, RuleEngine, ReelStateMachine, DetectionRules (data classes)
  data/      Room: ReelEventEntity, ReelDao, AppDatabase, CommentaryEntity; SettingsStore (DataStore)
  stats/     StatsEngine, PeriodStats
  score/     ScoreEngine, ScoreConfig, ScoreBands
  ai/        CommentaryEngine (interface), GemmaCommentaryEngine, TemplateCommentaryEngine,
             NumberGuard, PromptBuilder, ModelDownloader
  work/      NightlyCommentaryWorker, RulesUpdateWorker
  ui/        Compose screens, theme, navigation
  debug/     RuleInspector, CalibrationScreen (debug + release, hidden under Settings > Advanced)

## Style
- Sentence case in UI text. Calm, slightly cheeky copy. Material 3, dark mode supported.
- No TODO-only stubs in merged code: either implement or leave a failing test that documents the gap.