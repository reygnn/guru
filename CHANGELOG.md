# Changelog

Every tag has an entry here, newest first. Versions follow SemVer; what counts as major,
minor or patch is in `CLAUDE.md`, "Versions and releases".

## Unreleased

- Moved out of Verso into its own repo, at the state of Verso's Guru patches 1 to 3
  (2026-10-23). Sources and the 83 JVM tests unchanged, except for two lint findings:
  `Setup` holds the `Application` as such (StaticFieldLeak), `GuruView` suppresses
  ViewConstructor with its reason.
