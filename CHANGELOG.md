# Changelog

Every tag has an entry here, newest first. Versions follow SemVer; what counts as major,
minor or patch is in `CLAUDE.md`, "Versions and releases".

## Unreleased

- Moved out of Verso into its own repo, at the state of Verso's Guru patches 1 to 3
  (2026-10-23). Sources and the 83 JVM tests unchanged, except for two lint findings:
  `Setup` holds the `Application` as such (StaticFieldLeak), `GuruView` suppresses
  ViewConstructor with its reason.
- 25 Robolectric tests for `Guru.kt` and `GuruScreen` (108 in all).
- Fix: since Android 15 the share chooser also answers for "Copy" and "Edit", and that
  removed the reports. Now only an app picked (`ChooserResult` `SELECTED_COMPONENT`)
  removes them; the callback `PendingIntent` is mutable so the system's result arrives.
  5 more tests (113). Checked on the A17 (One UI, Android 16, the AOSP intentresolver
  chooser): "Copy" keeps the report, picking an app removes it; the previous build
  removed it on "Copy".
- CI: `./gradlew check` with JDK 21 on every push and pull request.
- TalkBack no longer reads doubled periods ("Software Failure.. Press").
- `sample/`: a tiny host for the device test — crash button, ANR button in debug builds,
  `GuruSink.Share`, its own `GuruTexts` with `values-de`.
