# Changelog

All notable changes to L0 are documented here.

## [0.0.2] - 2026-10-04

### Added

- **Explicit reasoning control**: Per-model reasoning configuration in the composer. Capability is inferred from the model id — Gemini models receive a thinking budget, known reasoning models receive effort levels — and every other model is never sent reasoning parameters.
- **Web search progress and failure reporting**: Search now shows an explicit progress state and surfaces provider-side failure notices alongside the answer instead of failing silently.
- **Follow-up generation budget**: Follow-up suggestions get their own timeout, tuned so reasoning-capable models still produce suggestions instead of being cut off mid-thought.
- **Scroll-to-latest affordance** and follow-latest detection that respects overscroll, so reading history during a stream is no longer interrupted.
- **Horizontal scrolling for citations**, with refined light and dark colour schemes, splash background and brand colours.
- **Transparent top bar** and adjusted message-list padding.

### Fixed

- **Follow-up suggestions never appeared with reasoning models.** The follow-up call reuses the chat model but discards its reasoning tokens, so the 6-second ceiling expired before any answer text arrived and the result was discarded without trace. The ceiling is now 30 seconds.
- **Regenerating could destroy the previous answer.** If a stored attachment could not be read during regeneration, the failed path deleted the message row and all of its saved versions. The snapshotted version is now restored and the error reported.
- **Concurrent sends produced duplicate turns.** A fast double-tap on send passed the guard twice and left a permanent empty assistant message. A per-turn lock now admits one turn at a time, and conversation switches wait for the in-flight turn to finish persisting.
- **Send was disabled with only an attachment.** Attaching a photo and typing nothing left the send button permanently disabled, even though the repository always accepted it.
- **Edit-and-resend destroyed the draft.** Starting an edit overwrote the composer, and cancelling cleared it instead of restoring it.
- **Camera captures were lost on rotation.** The pending capture URI and file path were not saveable, so a configuration change silently discarded the photo.
- **First TTS utterance after a cold start was dropped**, leaving the reply silently unspoken. It is now queued until the engine finishes binding. Auto-play also no longer speaks over another screen.
- **Gemini responses could vanish.** A part carrying an explicit `"thought": false` was misread as reasoning, discarding the answer; safety blocks and `finishReason` values were reported as "Malformed response" instead of the real cause.
- **Over-long SSE frames discarded whole answers.** A single frame above 64 KB aborted the stream rather than resynchronising.
- **Semantic version comparison was wrong.** Build metadata was parsed into the patch component (`1.2.3+45` read as `1.2.0`), and pre-release identifiers compared as raw strings, so `rc.2` looked newer than `rc.10` and a downgrade could be offered.
- **Server errors were labelled "Gemini service error"** for every provider, including OpenAI-compatible endpoints.
- **Provider protocol could be mis-selected.** A provider whose *name* contained "gemini" was routed to the Gemini wire protocol regardless of its base URL. Selection is now driven by the URL only.
- **Sending an image with a missing file** produced a zero-byte data URL and an answer about nothing, instead of a readable error.
- **Out-of-memory crashes on large images.** Image downscaling only sampled while *both* dimensions exceeded the limit, so a 20000×1000 JPEG decoded at full size.
- **Crash risks**: a duplicate LazyColumn key in the model selector, a schemeless base URL escaping model listing, and unguarded `startActivity` calls for updates and sharing.
- **Provider errors were uninformative.** The parsed error detail was discarded, so every 400/404/422 showed "Something went wrong".
- **Update notifications were permanently suppressed** when `POST_NOTIFICATIONS` was denied — the version was still marked as notified. A failed check also suppressed the next check for 24 hours, and a 404 was reported as "up to date".
- **Search false positives**: a query containing the word "anomaly" was reported as rate-limited, and nested HTML block elements produced duplicated page text.
- **Draft and history regressions**: cancelling an edit blanked the composer, History flashed its empty state on every open, and a provider deletion failure left the row in place with no error.

### Security

- **Backups excluded explicitly.** `android:allowBackup="false"` does not stop device-to-device transfer on Android 12+, so `dataExtractionRules` and `fullBackupContent` now exclude every domain from both cloud backup and D2D transfer. Previously the full conversation database and the keystore-backed key store were copied to a new device.
- **Web-fetch hardening**: CGNAT (`100.64.0.0/10`), benchmarking (`198.18.0.0/15`) and multicast/reserved ranges added to the blocked set.
- **Keystore failures are no longer silent.** If the keystore is unavailable the provider save fails visibly instead of dropping the key the user just typed.
- **Debug signing credentials removed from the repository.** The tracked `debug.keystore` and its hardcoded passwords are gone; AGP's auto-generated debug keystore is used.

### Changed

- Version is derived from the release tag by the release workflow, so local `versionName`/`versionCode` only act as a fallback.
- Redundant `androidx.room.ktx` dependency removed, along with 20 unused string resources, 2 unused font-family definitions and dead DAO and manager members.
- Android Lint reduced to dependency-version suggestions only.

## [0.0.1] - 2026-10-02

### Initial Release

- **Streaming Conversations**: Token-level response streaming with cooperative cancellation (stop button), retry on error, message editing with resend, and draft staging.
- **Answer Regeneration & Versioning**: Re-generating an assistant response stores prior variations in local history with in-message version pagination.
- **Provider Support (BYOK)**:
  - Direct integration with Google Gemini via API key.
  - OpenAI-compatible chat completions endpoint support with model listing (`/models`) and connection test capabilities.
  - Quick-switch provider switcher in the top bar with saved profile management.
  - Local and self-hosted model support (e.g. Ollama, LM Studio) over private/loopback network addresses (`localhost`, `127.0.0.1`, `10.0.2.2`, `*.local`, `*.lan`, `*.home`, `*.internal`).
  - Collapsible reasoning/thinking display when providers return `reasoning_content`.
- **Follow-Up Suggestions**: Automatic, contextual follow-up chips after successful answers, with natural-language refusal and error detection. Tapping a suggestion populates the composer without auto-sending.
- **Web Search Grounding**: Optional DuckDuckGo web search integration per conversation with source citations listed alongside generated answers.
- **Multimodal Attachments**:
  - Image attachments via camera or photo picker with automatic downscaling (max dimension 1280px) and memory-safe compression.
  - Plain-text document attachments (Markdown, JSON, code up to 100 KB) inlined as context.
- **Voice Capabilities**: Voice speech recognition input via Android `SpeechRecognizer` and text-to-speech (TTS) playback with adjustable speed and per-message playback controls.
- **Local Conversation History**: Offline SQLite storage via Room database supporting conversation pinning, search, renaming, deletion, and transcript export.
- **Privacy & Security**:
  - API keys stored in `EncryptedSharedPreferences` backed by the Android KeyStore (`AES-256-GCM`) with `allowBackup="false"`.
  - Keys are never logged, never synced to third-party servers, and sent only to the configured provider endpoint.
  - Strict HTTPS by default, permitting cleartext HTTP exclusively for local development and private network ranges.
- **Appearance & Design**: Material Design 3 interface with dynamic theming (system, light, dark), font scaling, and custom minimal L0 geometric adaptive launcher icon.
- **Release Automation**: Automated GitHub Actions CI and release workflows for testing, linting, R8 minification, signature verification, and checksum generation.
