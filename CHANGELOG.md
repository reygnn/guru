# Changelog

Every tag has an entry here, newest first. Versions follow SemVer; what counts as major,
minor or patch is in `CLAUDE.md`, "Versions and releases".

## Unreleased

- Fix: a share cut at its limit removed every report once an app was picked, the ones cut
  off too. A share now carries whole reports only and removes exactly those; the rest stay
  for the next share. A first report too big alone still goes, cut.
- Fix: a share too large for the Binder (the text travels about three times in the chooser's
  start) threw out of `startActivity` and killed the host — for a launcher, on every hold.
  The limit is now 100 K chars instead of 200 K, and a start the system refuses keeps the
  reports and shows the "no app" toast.
- Fix: an ANR dump that threw while being read stopped the exit look before the mark moved,
  so the same exits came back as new reports on every start. Such an ANR is now reported
  without its dump.
- 6 tests (129 in all).

- Docs: the backend contract as deployed on 2026-10-23 — every Guru report grouped by
  package, class and Guru number; a `REPORT_ID` counted once; reporter and admin users
  separate; shared reports importable with the backend's `tools/import_share.py`.

## 1.0.1 — 2026-10-23

Checked on the A17: single tap, swipe and slow drag do nothing; still hold opens the
chooser; double-tap continues.

- Fix: any touch ending on the alert counted as a tap — a swipe, or a finger laid on a dimmed
  screen to wake it, discarded the reports, and a longer touch opened the share chooser.
  Continue now takes a **double-tap**; a hold sends only if the finger stays within the touch
  slop; a single tap or a swipe does nothing. TalkBack is unchanged (its own double-tap and
  long press act through the click actions).
- The default hint reads "double-tap: continue · hold: send · back: later". **Hosts with their
  own `GuruTexts` must update their hint** (the sample: "doppeltippen: weiter · …").
- 10 touch tests with real MotionEvents (123 in all).

## 1.0.0 — 2026-10-23

First release. Device test on the A17 (Samsung SM-A176B, One UI, Android 16) complete:
crash, ANR, share cancelled / app picked / Copy, and TalkBack in German.

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
