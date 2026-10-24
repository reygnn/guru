# CLAUDE.md (test source set)

Orientation for sessions touching Guru's tests. The concrete patterns live in
`java/com/github/reygnn/guru/TESTING_CONVENTIONS.kt`; this file says what exists, what is
deliberately left out, and where to start.

The baseline philosophy (JVM-only by default, pure logic outside Android-runtime classes)
comes from `~/.claude/CLAUDE.md`. Like Verso, Guru departs from the baseline stack on
purpose: no coroutines, no MockK — see "deliberately not".

---

## What exists

Unit tests (`library/src/test`, `./gradlew :library:testDebugUnitTest`, 124 tests):

```
GuruStoreTest       16  reports on real files: atomic write (.new, rename, dir fsync), newest
                        first, at most 8, a refused rename, ASCII names in any locale,
                        exits-seen, installation-id
GuruNumberTest      12  the number: kind with the dead-end bit, FNV-1a over the root cause's
                        type and first three frames, stable across messages and line numbers
ReportCodecTest     10  key=value round-trip (a hostile trace too); an unknown key from a newer
                        Guru skipped; missing field, bad value, bad escape or a cut-off file
                        is no report
GuruTextTest         8  cap and readCapped at their limit (surrogate pairs whole), the alert
                        lines, "+N more", the share's subject
AcraJsonTest         7  the ACRA fields Acrarium requires, USER_CRASH_DATE's format, read back
                        with the real org.json
ExitSelectionTest    7  which exit records become reports: the first look only sets the
                        mark, oldest first, a Java crash the handler caught not twice, an ANR
                        beside it still reported, the mark moved even past a dropped crash
GuruDownloadsTest    6  the ZIP's name (app, version, local crash time; unsafe characters to
                        `_`, empty parts left out, ASCII digits in any locale) and its one
                        entry, the JSON byte for byte
GuruProcessTest      6  building a report from a crash (trace capped) or an exit record; the
                        kind codes are fixed
GuruTextsTest        6  GuruTexts.English: the toast after saving, "+N more", both gestures
                        named; a host's texts that override nothing are English
GuruHandlerTest      5  one capture per process, a failing capture swallowed, the previous
                        handler always reached with the original throwable
GuruSaveTest        11  Robolectric + FakeMediaStore: the save waits for io; one ZIP per report
                        in Download/Guru (name, MIME, IS_PENDING cleared), the ACRA JSON
                        inside, removed once saved; no label names by package; a refused
                        insert or a failed write keeps the report and leaves no file; only
                        the saved go; the chooser offers one file (SEND) or several
                        (SEND_MULTIPLE) with read access; one that cannot start, or an
                        activity gone before it, costs nothing
GuruInstallTest      8  Robolectric: a second install keeps one handler; no receiver; exit
                        records (ShadowActivityManager) — the look waits for io, the first
                        start only marks, an ANR with its dump, a caught Java crash not
                        twice, old exits and other reasons stay out, an unreadable dump still
                        a report and the mark moved
GuruShowPendingTest  7  Robolectric: nothing pending, nothing shown; the alert after the exit
                        look and on the host's main; number and "+N more"; a tap removes after
                        the io step and only what was on screen; Back keeps all; a destroyed
                        activity gets no alert
GuruScreenTest       5  Robolectric: continue and save each act once; TalkBack's action labels
                        and description from GuruTexts; the alert closes with its activity
GuruTouchTest       10  Robolectric, real MotionEvents: a double-tap continues, a still hold
                        saves; a single tap, two slow taps, a swipe, a slide past the slop and
                        a sliding second tap do nothing; TalkBack's click and long click act
```

`sample/src/test`: `SampleGuruTextsTest` (2) — the sample's `GuruTexts` from `values` and
`values-de`.

Shared: `GuruFixtures.kt` (`testEnv`, `report(...)`, `GuruLocaleRule`) and, for the
Robolectric tests, `GuruHostRule.kt` (the Application, `io` as a manual FIFO, the store,
the default handler restored afterwards) and `FakeMediaStore.kt` (a provider under the
`media` authority: rows in memory, bytes in real files, a refused insert or write).

Left to the device test (README): the real MediaStore and chooser, a real process death,
the blink.

---

## What this set deliberately does NOT have

- **No MockK, no mocks at all.** Android-bound collaborators are constructor functions
  (`GuruStore`'s `syncDir` / `rename` / `now`, the `log` channel, the host's `io`), so a
  test passes a lambda or a fake.
- **No coroutines**, so no `MainDispatcherRule` / `runTest`. Threading is the host's `io`;
  a test passes a manual queue and decides when it runs.
- **No mocked filesystem.** Real files in a `TemporaryFolder`, the ZIP's real bytes: what
  lands on disk is what is tested.

---

## Rules

- **Test names in backticks contain no character Windows forbids in file names**
  (`"`, `:`, `?`, `*`, `<`, `>`, `|`, `\`, `/`). The test class files are named after
  them, and under `allWarningsAsErrors` that breaks the build — Verso already failed on
  exactly this once.
- **Mutation check every fix:** revert the fix, watch the new test fail, restore.
- **Anything setting the default locale** holds a `GuruLocaleRule` (all three defaults).
- **The desktop org.json is not Android's.** It pins the format Acrarium reads (Acrarium
  uses it too); behaviour that differs on Android would need a device.
