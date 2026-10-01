# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before
acting. Writing them down also helps the human team agree on how we build.

## The app

Roam (`com.github.roamswent.roam`), a Kotlin/Android tourist-guide app built
on an **MVVM** architecture and based on the SwEnt bootcamp B3 solution.

- `model/` holds the data and repositories (Firestore, Location, ...).
- `ui/` holds the screens and their **ViewModels**.

## Architecture rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a
  repository implementation; they depend on repository interfaces. Firebase
  lives only in the `model/` repositories.
- Do not edit generated code.

## Definition of done

- The feature works and matches its acceptance criteria.
- **All new code comes with unit tests.**
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck`
  passes (formatting) before you submit.
- Features covered by instrumented tests (under `androidTest/`, e.g. the
  Firestore repository) must also pass `./gradlew
  connectedDebugAndroidTest`, with an Android emulator and the Firebase
  emulator (`firebase emulators:start`) running.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across
  unrelated files, split it.
- Read the failing tests carefully and iterate until `./gradlew check`
  passes.
- Stage only the files you changed; never `git add .` or `git add -A` (it
  can pull in local config like `local.properties` or
  `google-services.json`).
- Commit with a conventional-commit type, imperative subject of at most 50
  characters, description in lowercase (e.g. `feat: add photo capture`).
  Add a body wrapped at 72 characters when the subject is not enough.
- **Acknowledge your contributors** at the top of the file: credit the AI
  that wrote it with a `Co-authored-by` line. An AI agent is a contributor,
  so credit it.

## Your role

You provide the goal, the context, the acceptance criteria and the
permissions. The agent plans, acts and observes. **You review the diff, and
you own every line you submit.** "The agent wrote it" is not a defence.