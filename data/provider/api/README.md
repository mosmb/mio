# Canonical provider contract

The canonical provider models in this module are data-layer-only contracts. They describe only the normalized inputs and outputs needed for OAuth-style authentication, account identity, and a complete initial collection/media/release import. Retrofit services, DTOs, provider status strings, remote identifiers, pagination state, HTTP details, and provider-specific error mapping belong to the concrete provider modules.

Provider-specific evidence must be added by tasks 13 and 14 before either provider row is marked implemented.

| Concern | Canonical requirement | Unavailable/unknown behavior | AniList task | MyAnimeList task |
|---|---|---|---:|---:|
| OAuth launch/callback | authorization URL plus redacted transaction; canonical credentials | normalized failure; no credential | 13 | 14 |
| Identity | remote account ID, nonblank display name, optional avatar | missing required ID/name is terminal malformed/unsupported data | 13 | 14 |
| Complete import | all pages resolved before `CompleteProviderImport` | no partial snapshot and no absence-as-deletion | 13 | 14 |
| Media type/title | anime or manga plus nonblank canonical title | missing mandatory value is `UnsupportedMandatoryData` | 13 | 14 |
| Collection status | exact five-value normalized set | unknown mandatory status fails; never invent `CURRENT` | 13 | 14 |
| Progress | non-negative value and explicit episode/chapter/volume unit | unresolvable unit fails; never default to episode/chapter | 13 | 14 |
| Totals | positive episode/chapter/volume totals when known | `null`, never `0` or `-1` | 13 | 14 |
| Release confidence | `Trusted`, `Untrusted`, or `Unknown` | uncertain dates remain untrusted; absent remains unknown | 13 | 14 |
| Optional metadata | nullable artwork/format/synopsis and empty genres/relations/releases | omit honestly; never synthesize | 13 | 14 |
| Terminal errors | explicit `DO_NOT_RETRY` or `REAUTHENTICATE` normalized failure | no raw provider/HTTP error escapes | 13 | 14 |
| Activity/favorites | no task-05 contract | unsupported and omitted | later slice | later slice |
| Discovery/mutations | no task-05 contract | unsupported and omitted | later slice | later slice |

The contract rules are:

- Provider-specific status strings, numeric IDs, pagination cursors, HTTP fields, DTOs, error codes, and capability flags never appear in the canonical column.
- `null`, empty, `UNKNOWN`, `Untrusted`, and `Unknown` are distinct honest absence states; they must not be converted into false data.
- A provider implementation may omit optional information without failing the import.
- It must fail the complete import when mandatory identity, media linkage, status, or progress-unit data cannot be normalized safely.
- Tasks 13 and 14 must extend this matrix with verified provider mappings and evidence links rather than changing the common meaning to fit one provider.

Neither provider is claimed to support an optional field until its provider task proves that support with official documentation and an HTTP contract fixture.
