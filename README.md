# Guru Meditation

```
┌──────────────────────────────────────────────┐
│              Software Failure.               │
│     Press left mouse button to continue.     │
│      Guru Meditation #80000001.4A3F21C0      │
└──────────────────────────────────────────────┘
       double-tap: continue · hold: save · back: later   ← the host's words
```

A crash reporter for Android, vendored as source: no dependency, no permission, no
thread of its own. A crash is written to disk before the process dies; on the next
start the app shows the Amiga alert — black, red, the border blinking — and the user
decides: **double-tap** (left button) continues and discards, **hold** (right button)
saves the reports as ZIP files to `Download/Guru/` and offers them to share, **Back** leaves
them for the next start.
A single tap or a swipe does nothing: a finger laid on a dimmed screen to wake it must not
discard a report. Moving past the touch slop cancels a double-tap and a hold alike.

Platform APIs and the Kotlin stdlib only, minSdk 36 like the rest of the family.

## What it catches

| Kind | Code | Source | Trace |
|---|---|---|---|
| Java/Kotlin crash | `#80000001` | `Thread.setDefaultUncaughtExceptionHandler`, at the moment of the crash | full stack trace |
| ANR | `#80000002` | `ApplicationExitInfo`, on the next start | the system's thread dump |
| Native crash | `#80000003` | `ApplicationExitInfo`, on the next start | description only (the tombstone is protobuf) |

A Java crash shows up in `ApplicationExitInfo` too; it is dropped when the handler wrote
a report within 10 s of it. On the very first start with Guru the exit history is only
marked, not reported: it predates Guru.

**The number.** `#80000001` is the kind with the Amiga's dead-end bit. The second half
is FNV-1a over the root cause's type and its first three frames, without messages and
line numbers: the same bug keeps its number from build to build. For an ANR, the main
thread's first three frames.

## The handler's rules

- One capture per process. A second crash meanwhile goes straight on.
- Whatever the capture throws (OOM, full disk) is dropped. The real crash reaches the
  previous handler unchanged: the runtime's own, which kills the process. Guru never
  decides how the process dies.
- Nothing but building the report and writing one file: the app's version and the
  device were read at install, so no binder call happens while dying.

## On disk

`filesDir/guru/`: one `guru-<time>-<id>.txt` per report (`key=value`, readable with
`adb`), written atomically (`.new` + fsync → rename → fsync dir). At most 8, the newest.
A crash loop cannot fill the disk. Beside them: `installation-id` and `exits-seen`.

## Saving

A hold saves every report on screen as a ZIP of its own through `MediaStore.Downloads`, in
`Download/Guru/`: no permission, no manifest entry. One entry per ZIP, the report's ACRA
JSON, unchanged. The file is named after the app, its version and the crash time in local
time — `Verso-0.3.1-2026-10-24_14-03-12.zip`, the entry `….json`. A character a file name
should not carry becomes `_`; the name is cut to 40 chars and the version to 24, so it stays
under 255 bytes. MediaStore adds ` (1)` to a second file of the same second.

Each file shows only once complete and synced (`IS_PENDING`, `fsync`). Once it is saved, the
file is the user's and Guru removes its own copy, report by report. A report whose file
cannot be saved stays for the next start; the toast says how many of how many were saved. A
death between saving and removing repeats that one report as a second ZIP with the same
`REPORT_ID`, which the backend counts once.

Then Guru offers the saved files in the share chooser — `ACTION_SEND`, or
`ACTION_SEND_MULTIPLE` for several, read access for the chooser (its preview) and the app
picked, in a task of its own. Only the files' URIs travel, so there is no size limit. What
happens in the chooser no longer matters: picked, copied or cancelled, the files are in
`Download/Guru/` already. The save waits its turn on the host's `io`, so the chooser can come
a moment after the hold; if the activity is gone by then (rotation, language change), only
the toast comes.

**Privacy.** Every hold puts the reports into shared storage, even if the chooser is
cancelled: the full trace (exception messages can carry names, paths, other apps' package
names), an ANR's dump of every thread (up to 256 KB), the device's brand, model and Android
version, and the installation's lasting `INSTALLATION_ID`. The user sees only the number
before saving. The files outlive an uninstall; Guru never reads or removes them again, and
after a reinstall it could not. A file manager, a PC over USB, an app with access to all
files or a sync tool can read them, and every host with Guru shares the one `Download/Guru/`.

To get the reports into the backend, share the ZIPs to guru2acra, or run the backend's
`tools/import_share.py` on them.

## The backend contract

The JSON in the ZIP is what the family's own ACRA backend
([`reygnn/acra-backend`](https://github.com/reygnn/acra-backend), Flask + MariaDB) takes.
Checked against its source (2026-10-23):

- Endpoint `POST <server>/report`, basic auth with the **reporter** user. That user may
  only report; the ProGuard-mapping routes take a separate admin user that never goes
  into an APK.
- Required (missing → 400): `PACKAGE_NAME` and `STACK_TRACE`. It also reads
  `APP_VERSION_CODE` (for the ProGuard mapping), `APP_VERSION_NAME`, `ANDROID_VERSION`,
  `PHONE_MODEL` and `BRAND`; anything longer than its column is clipped, never refused.
- Guru reports — crash, ANR and native crash alike — are grouped by `PACKAGE_NAME`, the
  exception class from the trace's first line and `CUSTOM_DATA.GURU_MEDITATION`
  (Guru's number: the root cause and three frames, stable from build to build). That is
  why the number is in the JSON, and why ANRs and native crashes start
  `ApplicationNotResponding: …` / `NativeCrash: …`. Reports without the number (the ACRA
  library) are grouped by the `at` frames in the trace's first three lines.
- A known bug always gets 200. A resend with the same `REPORT_ID` (the 200 got lost)
  is answered 200 and not counted again.

The other fields (`REPORT_ID`, `INSTALLATION_ID`, `USER_CRASH_DATE` in
`yyyy-MM-dd'T'HH:mm:ss.SSSXXX`) are what a stock ACRA server such as Acrarium requires.
Guru keeps writing them, so a report also posts to one of those.

## Texts

The Amiga lines are a quote and stay English. Everything else a user reads or hears
comes from the host: the hint under the box, the two TalkBack actions, "+N more", and
the toast. These are passed as `GuruTexts` from the app's resources:

```kotlin
internal class AppGuruTexts(private val context: Context) : GuruTexts {
    override val hint get() = context.getString(R.string.guru_hint)
    override val actionContinue get() = context.getString(R.string.guru_action_continue)
    override val actionSave get() = context.getString(R.string.guru_action_save)
    override fun more(count: Int) = context.resources.getQuantityString(R.plurals.guru_more, count, count)
    override fun saved(saved: Int, total: Int) = when { … }
}
```

They are read each time the screen is built or a toast is shown, so a language change in
between is picked up. Without `texts`: `GuruTexts.English`. Every member has a default (the
English one), so a host overrides only what it translates. A translated hint that is
longer shrinks to the width, like the alert lines.

## Release builds

R8 renames classes and strips line numbers: every trace becomes `a.b.c(Unknown
Source)`, and the Guru number changes with every build. In `proguard-rules.pro`:

```
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
```

Shrinking and optimisation stay on. Alternatively, keep `mapping.txt` per release and
run traces through `retrace`; Acrarium can do that itself with an uploaded mapping.

## Installing

Guru is vendored as source with `git subtree`, pinned to a tag — never as a library
(everything stays `internal`) and never from `main`:

```
git subtree add --prefix=guru https://github.com/reygnn/guru v2.0.0 --squash
```

The host compiles `guru/library/src/main/kotlin` as part of its app module (an extra
Kotlin source directory) and adds the same directory to detekt's `source`. Only the `.kt`
files arrive: Guru has no manifest and no resources. The host brings its own words as
`GuruTexts`.

An update is a pull of the next tag; `guru/` is never edited in the host. A fix goes
upstream first, gets a tag, and comes back with:

```
git subtree pull --prefix=guru https://github.com/reygnn/guru <tag> --squash
```

No `consumer-rules.pro`: the host decides about `-dontobfuscate` in its own ProGuard file
(see "Release builds").

Everything is `internal`. The host has two lines:

```kotlin
// Application.onCreate — io: the app's own single IO thread; texts: its own words
Guru.install(this, io = io::execute, texts = AppGuruTexts(this))

// first Activity.onCreate — onUi: the app's own way back to the main thread
Guru.showPending(this, ::onUi)
```

`io` must run tasks in submission order (the exit look comes before the screen). The
dialog dismisses itself when its activity is destroyed.

## Tests

`library/src/test/java`: 139 tests, JUnit 4, no mocks, each mutation-checked.

- **85 plain JVM tests.** Real files in a `TemporaryFolder`, the ZIP read back with
  `ZipInputStream`, and the real desktop `org.json` for parsing the JSON back (the library
  Acrarium reads it with).
- **54 Robolectric tests** for what the JVM alone cannot reach:
  - `Guru.install`: one handler, no receiver.
  - Exit records via `ShadowActivityManager`, an unreadable ANR dump too.
  - `showPending`: tap, Back, a destroyed activity.
  - Saving through a fake MediaStore provider: one ZIP per report in `Download/Guru`,
    hidden until complete, removed once saved; a refused, failed or throwing save keeps
    that report; the chooser offers the files with read access in a task of its own, and
    one that cannot start costs nothing.
  - `GuruScreen`: continue and save act once, TalkBack labels, closing with the activity.
  - Touch: a double-tap continues, a still hold saves; a single tap or a swipe does nothing.

**On the device**, before every tag, with `sample/` — a tiny host that takes Guru as
source, like Verso. Its debug build has an ANR button and keeps the plain application
id, so install it with
`ANDROID_SERIAL=R5GL51D5VHZ ./gradlew :sample:installDebug`:

1. `adb shell am crash com.github.reygnn.guru.sample`, start the app: the alert shows
   `#80000001`.
2. Hold: the toast names `Download/Guru`, the chooser offers one ZIP and shows its name
   (the chooser's own read access). Cancel it, restart:
   no alert; the file is in `Download/Guru/` (Files app), named
   `Guru_Sample-<version>-<time>.zip` (`Guru-Beispiel-…` in German), the JSON inside.
3. Crash twice, start, hold: two ZIPs offered together; pick guru2acra (or mail), it
   receives both.
4. Press "ANR" and touch the screen until the system kills the app, restart:
   `#80000002`, the thread dump in the report.
5. TalkBack in German: the hint and both actions are read in German.

## Verified

`./gradlew check` in this repo: the unit tests, lint (warnings as errors, no baseline)
and detekt (Verso's rules, no baseline), with Kotlin warnings as errors for main and test
sources, for `library/` and `sample/`. CI runs it on every push and pull request
(`.github/workflows/check.yml`). The build mirrors Verso's versions and rules, so what passes here compiles in a
host that pulls it.

## License

GPL-3.0, see `LICENSE`.
