# Canonical provider contract

The canonical provider models in this module are data-layer-only contracts. They describe only the normalized inputs and outputs needed for OAuth-style authentication, account identity, and a complete initial collection/media/release import. Retrofit services, DTOs, provider status strings, remote identifiers, pagination state, HTTP details, and provider-specific error mapping belong to the concrete provider modules.

Provider-specific evidence must be added by tasks 13 and 14 before either provider row is marked implemented.

| Concern | Canonical requirement | Unavailable/unknown behavior | AniList task | MyAnimeList task |
|---|---|---|---:|---:|
| OAuth launch/callback | authorization URL plus redacted transaction; canonical credentials | normalized failure; no credential | 13 ✓ | 14 |
| Identity | remote account ID, nonblank display name, optional avatar | missing required ID/name is terminal malformed/unsupported data | 13 ✓ | 14 |
| Complete import | all pages resolved before `CompleteProviderImport` | no partial snapshot and no absence-as-deletion | 13 ✓ | 14 |
| Media type/title | anime or manga plus nonblank canonical title | missing mandatory value is `UnsupportedMandatoryData` | 13 ✓ | 14 |
| Collection status | exact six-value normalized set (`CURRENT`, `PLANNED`, `PAUSED`, `COMPLETED`, `DROPPED`, `REPEATING`) | unknown mandatory status fails; never invent `CURRENT` | 13 ✓ | 14 |
| Progress | non-negative value and explicit episode/chapter/volume unit | unresolvable unit fails; never default to episode/chapter | 13 ✓ | 14 |
| Totals | positive episode/chapter/volume totals when known | `null`, never `0` or `-1` | 13 ✓ | 14 |
| Release confidence | `Trusted`, `Untrusted`, or `Unknown` | uncertain dates remain untrusted; absent remains unknown | 13 ✓ | 14 |
| Optional metadata | nullable artwork/format/synopsis and empty genres/relations/releases | omit honestly; never synthesize | 13 ✓ | 14 |
| Terminal errors | explicit `DO_NOT_RETRY` or `REAUTHENTICATE` normalized failure | no raw provider/HTTP error escapes | 13 ✓ | 14 |
| Activity/favorites | no task-05 contract | unsupported and omitted | later slice | later slice |
| Discovery/mutations | no task-05 contract | unsupported and omitted | later slice | later slice |

`REPEATING` was added to the canonical `Collection status` set by task 13, with repository-owner approval recorded in `docs/tasks/13-connect-anilist-initial-import.md` section 0, to represent AniList's `MediaListStatus.REPEATING` (rewatching/rereading) honestly rather than collapsing it into `CURRENT`. Today eligibility, Library filter chips, and queue-eligibility rules do not yet surface it; that product decision belongs to tasks 17, 18, and 26.

The contract rules are:

- Provider-specific status strings, numeric IDs, pagination cursors, HTTP fields, DTOs, error codes, and capability flags never appear in the canonical column.
- `null`, empty, `UNKNOWN`, `Untrusted`, and `Unknown` are distinct honest absence states; they must not be converted into false data.
- A provider implementation may omit optional information without failing the import.
- It must fail the complete import when mandatory identity, media linkage, status, or progress-unit data cannot be normalized safely.
- Tasks 13 and 14 must extend this matrix with verified provider mappings and evidence links rather than changing the common meaning to fit one provider.

Neither provider is claimed to support an optional field until its provider task proves that support with official documentation and an HTTP contract fixture.

## AniList mapping evidence

Verified 2026-08-19 against `https://docs.anilist.co` and the live AniList GraphQL schema at `https://graphql.anilist.co` (introspection of `MediaListStatus`, `MediaFormat`, `MediaStatus`, `MediaListSort`, `Page.mediaList` arguments, and `Media.airingSchedule` arguments; a representative `MioMediaListPage` query was submitted and returned a schema-valid response). All enum constant names and query argument shapes matched `docs/tasks/13-connect-anilist-initial-import.md` section 5.3/5.10 exactly; no plan revision was required.

| AniList field | Normalization rule | Absence behavior |
|---|---|---|
| `MediaListEntry.status` | `CURRENT`→`CURRENT`, `PLANNING`→`PLANNED`, `PAUSED`→`PAUSED`, `COMPLETED`→`COMPLETED`, `DROPPED`→`DROPPED`, `REPEATING`→`REPEATING` | null or any other value is `UnsupportedMandatoryData` |
| `MediaListEntry.progress` / `progressVolumes` | anime uses `progress` as `EPISODE`; manga uses `progress` as `CHAPTER` unless `progressVolumes > 0` and `progress == 0`, in which case it uses `progressVolumes` as `VOLUME` | missing progress is treated as `0`; a negative value is `MalformedResponse` |
| `Media.episodes` / `chapters` / `volumes` | taken as the entry's `total` only when `> 0` | `null`, never `0` or `-1`; when the provider's `progress` exceeds a known total, the total is cleared to `null` rather than the progress being dropped |
| `MediaListEntry.updatedAt` / `createdAt` | `updatedAt` when `> 0`, else `createdAt` when `> 0`, converted with `Instant.fromEpochSeconds` | both absent or non-positive is `UnsupportedMandatoryData`; never the Unix epoch or the current time |
| `Media.format` | `TV`/`TV_SHORT`→`TV`; the other eight named AniList formats map to their identical canonical constants; any other non-null value→`OTHER` | `null` stays `null` (AniList reported no format) |
| `Media.status` | `FINISHED`, `RELEASING`, `NOT_YET_RELEASED`→`UPCOMING`, `CANCELLED`, `HIATUS` map directly | null or any other value→`UNKNOWN` |
| `Media.description` | imported verbatim | blank→`null`; AniList descriptions contain provider HTML, which presentation (task 16) must handle |
| `Media.coverImage` / `bannerImage` | first non-blank of `extraLarge`, `large`, `medium` for the poster; `bannerImage` directly for the banner | blank/absent→`null`, never a placeholder |
| `Media.genres` | non-null, non-blank, de-duplicated, original order preserved | absent→empty list |
| `Media` relations | not requested by the initial import | `relatedMediaIds` is always `emptyList()`; task 16 owns related-title import |
| `Media.airingSchedule.nodes` | anime only; each node with `episode > 0` and `airingAt > 0` becomes a `Trusted` release at `Instant.fromEpochSeconds(airingAt)`; duplicate `(unit, number)` pairs keep the first | manga produces no releases; the connection is capped at one page of at most 25 nodes, so Mio never claims a complete release history |

## AniList documented limitations

- No refresh token is issued under the implicit grant, so `refresh` always resolves to a `REAUTHENTICATE` failure (`ProviderFailure.AuthenticationRequired`).
- `token_type` is not documented on any AniList auth page; only `access_token` is promised in the redirect fragment. Mio uses AniList's value when present, falls back to `Bearer` when absent (RFC 6749 section 4.2.2 requires the field, and `Bearer` is the only scheme AniList's authenticated-request guide documents), and rejects a present-but-blank value as a malformed callback.
- Mio always sends an OAuth `state` value and rejects a mismatch on the redirect, but AniList is not documented to echo `state` back in the redirect fragment.
- Volume progress is imported only when the user has recorded volumes and no chapters; dual chapter-plus-volume tracking is not modeled in v0.
- Related media are not imported by the initial import; `relatedMediaIds` is always empty for AniList today.
- The release history is bounded by one nested `airingSchedule` page (at most 25 nodes) per media, so Mio never claims a complete release history for a title.
- Descriptions are imported verbatim and contain provider HTML that presentation must sanitize or render safely; this task does not sanitize it.
- `total` is cleared to `null` whenever the provider reports progress beyond the known total, rather than dropping the progress value.
