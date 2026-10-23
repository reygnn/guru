# CLAUDE.md (test source set)

Orientation for sessions touching Guru's tests. The concrete patterns live in
`java/com/github/reygnn/guru/TESTING_CONVENTIONS.kt`; this file says what exists, what is
deliberately left out, and where to start.

The baseline philosophy (JVM-only by default, pure logic outside Android-runtime classes)
comes from `~/.claude/CLAUDE.md`. Like Verso, Guru departs from the baseline stack on
purpose: no coroutines, no MockK — see "deliberately not".

---

## What exists

JVM unit tests (`library/src/test`, `./gradlew :library:testDebugUnitTest`, 83 tests):

```
GuruStoreTest       16  reports on real files: atomic write (.new, rename, dir fsync), newest
                        first, at most 8, a refused rename, ASCII names in any locale,
                        exits-seen, installation-id
GuruNumberTest      12  the number: kind with the dead-end bit, FNV-1a over the root cause's
                        type and first three frames, stable across messages and line numbers
ReportCodecTest     10  key=value round-trip (a hostile trace too); an unknown key from a newer
                        Guru skipped; missing field, bad value, bad escape or a cut-off file
                        is no report
GuruTextTest         9  cap and readCapped at their limit (surrogate pairs whole), the alert
                        lines, "+N more", the share body and subject
AcraJsonTest         7  the ACRA fields Acrarium requires, USER_CRASH_DATE's format, read back
                        with the real org.json
ExitSelectionTest    7  which exit records become reports: the first look only sets the
                        mark, oldest first, a Java crash the handler caught not twice, an ANR
                        beside it still reported, the mark moved even past a dropped crash
AcraPostTest         6  a real HTTP server on loopback: method, path, basic auth, body, 2xx
GuruProcessTest      6  building a report from a crash (trace capped) or an exit record; the
                        kind codes are fixed
GuruHandlerTest      5  one capture per process, a failing capture swallowed, the previous
                        handler always reached with the original throwable
GuruTextsTest        5  GuruTexts.English: the toast after sending, "+N more", both gestures
                        named
```

Shared: `GuruFixtures.kt` (`testEnv`, `report(...)`, `GuruLocaleRule`).

Not covered on the JVM yet (planned as Robolectric tests): `Guru.kt` — install twice, the
receiver only for `GuruSink.Share`, exit records via `ShadowActivityManager`, `showPending`,
the chooser and its answer — and `GuruScreen.kt` (tap and hold act once, TalkBack actions
carry `GuruTexts`, the alert closes with its activity). Until then: the device test in the
README.

---

## What this set deliberately does NOT have

- **No MockK, no mocks at all.** Android-bound collaborators are constructor functions
  (`GuruStore`'s `syncDir` / `rename` / `now`, the `log` channel, the host's `io`), so a
  test passes a lambda or a fake.
- **No coroutines**, so no `MainDispatcherRule` / `runTest`. Threading is the host's `io`;
  a test passes a manual queue and decides when it runs.
- **No mocked filesystem or connection.** Real files in a `TemporaryFolder`, a real
  `HttpServer` on loopback: the bytes on disk and on the wire are what is tested.

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
