# Mio engineering conventions

This document records source-organization conventions that are too concrete for product or architecture specifications.

## Kotlin files

- A file is named after its primary public declaration.
- Public architectural roles ending in `Repository`, `UseCase`, `ViewModel`, `UiState`, `UiEffect`, or `UiMapper` each live in their matching file.
- A file contains at most one public architectural role. Small private helpers may remain with their sole consumer.
- A sealed type and its nested variants stay together. Independent public enums and reusable value wrappers use their own matching files.
- Do not create broad topic files such as `AccountSession.kt` when the file would combine state, repository contracts, values, and operations without an `AccountSession` declaration.
- Tests mirror the production concept they verify. A broad integration fixture may group private test helpers only when they serve one cohesive scenario.

These rules optimize navigation and ownership clarity. Exceptions require a concrete cohesion reason in the implementation plan's file/API ledger.
