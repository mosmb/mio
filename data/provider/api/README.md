# Canonical provider contract

The canonical provider models in this module are data-layer-only contracts. They describe only the normalized inputs and outputs needed for OAuth-style authentication, account identity, and a complete initial collection/media/release import. Retrofit services, DTOs, provider status strings, remote identifiers, pagination state, HTTP details, and provider-specific error mapping belong to the concrete provider modules.

Provider-specific evidence must be added by tasks 13 and 14 before either provider row is marked implemented.

| Concern | Canonical requirement | Unavailable/unknown behavior | AniList task | MyAnimeList task |
|---|---|---|---:|---:|
| OAuth launch/callback | authorization URL plus redacted transaction; canonical credentials | normalized failure; no credential | 13 ✓ | 14 ✓ |
| Identity | remote account ID, nonblank display name, optional avatar | missing required ID/name is terminal malformed/unsupported data | 13 ✓ | 14 ✓ |
| Complete import | all pages resolved before `CompleteProviderImport` | no partial snapshot and no absence-as-deletion | 13 ✓ | 14 ✓ |
| Media type/title | anime or manga plus nonblank canonical title | missing mandatory value is `UnsupportedMandatoryData` | 13 ✓ | 14 ✓ |
| Collection status | exact six-value normalized set (`CURRENT`, `PLANNED`, `PAUSED`, `COMPLETED`, `DROPPED`, `REPEATING`) | unknown mandatory status fails; never invent `CURRENT` | 13 ✓ | 14 ✓ |
| Progress | non-negative value and explicit episode/chapter/volume unit | unresolvable unit fails; never default to episode/chapter | 13 ✓ | 14 ✓ |
| Totals | positive episode/chapter/volume totals when known | `null`, never `0` or `-1` | 13 ✓ | 14 ✓ |
| Release confidence | `Trusted`, `Untrusted`, or `Unknown` | uncertain dates remain untrusted; absent remains unknown | 13 ✓ | 14 ✓ |
| Optional metadata | nullable artwork/format/synopsis and empty genres/relations/releases | omit honestly; never synthesize | 13 ✓ | 14 ✓ |
| Terminal errors | explicit `DO_NOT_RETRY` or `REAUTHENTICATE` normalized failure | no raw provider/HTTP error escapes | 13 ✓ | 14 ✓ |
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
- 2026-08-23 production-client follow-up: after an existing browser login, AniList returned `unsupported_grant_type` for Mio's registered client despite a request matching the documented implicit shape (`client_id`, exact registered callback, `response_type=token`, and state). The public documentation still recommends implicit grant for mobile clients, so treat this as an external registered-client configuration issue; do not ship a client secret or silently change the Android implementation to authorization code.
- Volume progress is imported only when the user has recorded volumes and no chapters; dual chapter-plus-volume tracking is not modeled in v0.
- Related media are not imported by the initial import; `relatedMediaIds` is always empty for AniList today.
- The release history is bounded by one nested `airingSchedule` page (at most 25 nodes) per media, so Mio never claims a complete release history for a title.
- Descriptions are imported verbatim and contain provider HTML that presentation must sanitize or render safely; this task does not sanitize it.
- `total` is cleared to `null` whenever the provider reports progress beyond the known total, rather than dropping the progress value.

## MyAnimeList mapping evidence

Recorded 2026-08-19. The provenance of each part differs, and the difference matters:

- **Confirmed against official documentation.** `https://myanimelist.net/apiconfig/references/authorization` was fetched and read directly. It is the authority for the authorization and token endpoint URLs, `response_type=code`, the 43–128 character `code_challenge`, the statement that "Currently, only the `plain` method is supported", the `authorization_code` and `refresh_token` request fields, the `token_type`/`expires_in`/`access_token`/`refresh_token` response keys, and the one-hour access-token and one-month refresh-token lifetimes.
- **Confirmed against the live API** using client-ID authentication (`X-MAL-CLIENT-ID`), which needs no user token and reads only the public catalogue. Method, so this is reproducible: `GET /v2/anime/{id}` and `/v2/manga/{id}` with the exact `fields` strings the implementation sends (minus `list_status`), plus `GET /v2/anime/ranking` over seven ranking types (**3,374 anime sampled**) and `/v2/manga/ranking` over nine (**4,369 manga sampled**), tallying every distinct `media_type` and `status`.

  | Checked | Result |
  |---|---|
  | anime node keys | `id, title, main_picture{large,medium}, synopsis, media_type, status, num_episodes, genres[{id,name}]` — exactly the DTO |
  | manga node keys | same plus `num_chapters, num_volumes` — exactly the DTO |
  | anime `status` | `finished_airing`, `currently_airing`, `not_yet_aired` — all three handled |
  | manga `status` | `finished`, `currently_publishing`, `on_hiatus`, `discontinued` — all four handled; `not_yet_published` unobserved in rankings but handled |
  | manga `media_type` | `manga`, `light_novel`, `manhwa`, `one_shot`, `doujinshi`, `manhua`, `novel` — all seven handled |
  | anime `media_type` | `tv`, `movie`, `ova`, `special`, `ona`, **`tv_special`**, `unknown`, **`pv`**, **`cm`**, `music` — see the defect below |
  | `paging` | carries `next`; `previous` absent on a first page, so both stay nullable |
  | `nsfw=true`, `limit=100`, flat comma-separated `fields` | all accepted (HTTP 200); an unrecognized `fields` entry is ignored rather than rejected, so the field string cannot hard-fail |

  **Defect found and fixed by this verification.** `tv_special`, `pv`, and `cm` appear in neither published specification. `tv_special` is not rare — roughly 5% of ranked anime — and was falling through to `OTHER` when `SPECIAL` is the honest canonical match, so it is now mapped to `SPECIAL`. `pv` and `cm` have no canonical equivalent and correctly remain `OTHER`; they are the reason the defensive fallback must stay. Covered by `MyAnimeListMediaListMapperTest.everyAnimeMediaTypeMapsToItsFormat`.

- **Confirmed against a live authenticated account.** The repository owner completed the OAuth flow on 2026-08-19 and supplied a user access token, used read-only. This closes what had been the residual risk.

  | Checked | Result |
  |---|---|
  | `data[]` element keys | `node`, `list_status` — exactly the DTO |
  | anime `list_status` keys from a **bare** `list_status` field | `status`, `num_episodes_watched`, `is_rewatching`, `updated_at`, `score` |
  | manga `list_status` keys from a **bare** `list_status` field | `status`, `num_chapters_read`, `num_volumes_read`, `is_rereading`, `updated_at`, `score` |
  | anime `list_status.status` | `watching`, `completed`, `on_hold`, `dropped`, `plan_to_watch` — **exactly the five mapped**, verified by the endpoint's `status` filter accepting each (HTTP 200) while rejecting the manga values and a bogus control (HTTP 400) |
  | manga `list_status.status` | `reading`, `completed`, `on_hold`, `dropped`, `plan_to_read` — **exactly the five mapped**, verified the same way |
  | `is_rewatching` / `is_rereading` | JSON booleans |
  | `updated_at` | ISO-8601 with a numeric offset (`NNNN-NN-NNTNN:NN:NN+NN:NN`), which `kotlin.time.Instant.parse` accepts |
  | empty collection | `data: []` with `paging: {}`, so the loop terminates and yields a valid empty import |
  | `/users/@me` | `id` (int) and `name` (non-blank) present, alongside `birthday`, `joined_at`, `location` |
  | token response | keys exactly `access_token`, `expires_in`, `refresh_token`, `token_type`; a refresh token **is** issued; `state` **is** echoed on the redirect and matched |

  The bare-`list_status` result is what retires the plan's `fields`-syntax risk: no nested `{...}` sub-selection is needed to obtain the status, the progress counters, or the rewatching flag. `score` is also returned; the DTOs ignore it under `ignoreUnknownKeys = true`, which this response exercises.

  **Documentation discrepancy:** the official authorization page states an access-token lifetime of "one hour", but the live token response returned `expires_in: 2678400` (31 days). Mio derives `expiresAt` from the response rather than from the documented figure, so behavior is unaffected.

- **Not exercised live, though the values they key off are now confirmed:** no list entry had `is_rewatching`/`is_rereading` set, so the `REPEATING` branch ran only under unit test; and no manga entry had volumes-but-no-chapters read, so the `VOLUME` progress branch likewise. Both remain covered by `MyAnimeListMediaListMapperTest`.
- **Not exercised live:** the token exchange sends `redirect_uri` in both the authorization request and the token request. The verification flow omitted it from both — the other internally consistent pairing MyAnimeList accepts — so the send-in-both path the implementation uses is unproven against the live endpoint. Task 15 owns production OAuth wiring and should confirm it with the registered redirect URI.
- **Inconclusive:** `/users/@me` returned no `picture` key even when explicitly requested via `fields`. This cannot distinguish "MyAnimeList omits the field" from "this account has no avatar set". Either way `MyAnimeListIdentityDataSource` yields a null avatar and never a placeholder.

| MyAnimeList field | Normalization rule | Absence behavior |
|---|---|---|
| `list_status.status` (anime) | `watching`→`CURRENT`, `plan_to_watch`→`PLANNED`, `on_hold`→`PAUSED`, `completed`→`COMPLETED`, `dropped`→`DROPPED` | null or any other value with `is_rewatching == false` is `UnsupportedMandatoryData`; if `is_rewatching == true`, result is `REPEATING` regardless |
| `list_status.status` (manga) | `reading`→`CURRENT`, `plan_to_read`→`PLANNED`, `on_hold`→`PAUSED`, `completed`→`COMPLETED`, `dropped`→`DROPPED` | null or any other value with `is_rereading == false` is `UnsupportedMandatoryData`; if `is_rereading == true`, result is `REPEATING` regardless |
| `list_status.is_rewatching` / `is_rereading` | when `true`, override the `status` mapping to produce `REPEATING` | false or absent defaults to examining `status`; a rewatching/rereading entry with an unknown status still yields `REPEATING` |
| `num_episodes_watched` (anime) | taken as `EPISODE` progress; absent defaults to `0` | missing is treated as `0`; a negative value is `MalformedResponse` |
| `num_chapters_read` / `num_volumes_read` (manga) | `VOLUME` progress from `num_volumes_read` only when it is `> 0` **and** `num_chapters_read` is `0` or absent; `CHAPTER` progress from `num_chapters_read` in every other case | absent is treated as `0`; a negative value in either field is `MalformedResponse` |
| `num_episodes`, `num_chapters`, `num_volumes` | taken as the entry's `total` only when `> 0` | absent, `0`, or negative all become `null`, never `0` and never `-1`; when the provider's `progress` exceeds a known total, the total is cleared to `null` rather than the progress being dropped |
| `updated_at` | parsed as `Instant.parse(value)` | null, blank, or unparsable is `UnsupportedMandatoryData`; never the Unix epoch or the current time |
| `media_type` (anime) | `tv`→`TV`, `movie`→`MOVIE`, `ova`→`OVA`, `ona`→`ONA`, `special` **and `tv_special`**→`SPECIAL`, `music`→`MUSIC`; any other non-blank value (observed: `pv`, `cm`)→`OTHER` | `null`, blank, or `unknown`→`null` |
| `media_type` (manga) | `manga`→`MANGA`, `novel` or `light_novel`→`NOVEL`, `one_shot`→`ONE_SHOT`; any other non-blank value→`OTHER` | `null`, blank, or `unknown`→`null` |
| `status` (anime) | `finished_airing`→`FINISHED`, `currently_airing`→`RELEASING`, `not_yet_aired`→`UPCOMING` | null or any other value→`UNKNOWN` |
| `status` (manga) | `finished`→`FINISHED`, `currently_publishing`→`RELEASING`, `not_yet_published`→`UPCOMING`, `on_hiatus`→`HIATUS`, `discontinued`→`CANCELLED` | null or any other value→`UNKNOWN` |
| `synopsis` | imported verbatim | blank→`null`; MyAnimeList synopses are plaintext without provider markup |
| `main_picture` | first non-blank of `large`, `medium` for the poster | blank/absent→`null`, never a placeholder |
| `genres` | non-null, non-blank, de-duplicated, original order preserved | absent→empty list |
| `banner` | not available from MyAnimeList | always `null` for every media |
| `releases` | not available from MyAnimeList (no per-episode airing schedule is exposed) | always `emptyList()` for every media |
| `relatedMediaIds` | not requested by the initial import | always `emptyList()` for every media; task 16 owns related-title import |

## MyAnimeList documented limitations

- No per-episode release schedule is available, so `releases` is always empty and a MyAnimeList title can never be labeled New (PRD 7.2 requires exactly this degradation).
- No banner artwork is available, so `banner` is always `null`.
- Anime and manga are numbered in separate namespaces, so every remote media ID is prefixed `anime/` or `manga/` before it reaches the canonical contract to ensure global uniqueness within one import.
- MyAnimeList exposes no separate list-entry identifier, so the namespaced remote media ID is reused as the entry ID; `ImportedIdentityFactory` hashes entry and media IDs under different kind strings, so the local Mio IDs still differ.
- Rewatching and rereading are reported as `REPEATING`, overriding the underlying status; this replaces the status rather than preserving it as a separate flag.
- Volume progress is imported only when the user records volumes and no chapters; dual chapter-plus-volume tracking is not modeled in v0.
- Related media are not imported by the initial import; `relatedMediaIds` is always empty for MyAnimeList.
- The list order is not documented as stable, so Mio de-duplicates by remote media ID rather than relying on it; a title added mid-import may be missed until the next refresh.
- `paging.next` is read only as a boolean "more exists" signal; its URL is never followed to avoid letting MyAnimeList redirect Mio's authenticated request at an arbitrary host.
- The refresh token is dropped when a refresh response omits it, forcing re-authentication rather than reusing a possibly-rotated value.

## Task 16 media-detail enrichment contract

Task 16 adds one provider-neutral `ProviderMediaDetailDataSource` request that returns a validated `ProviderMediaDetails` snapshot: one requested root and its direct related titles. The snapshot requires a matching provider, a positive root identity, unique related IDs, and exact ordered agreement between `root.relatedMediaIds` and the returned related list. Implementations copy the related list before exposing it, and malformed optional related nodes are omitted rather than invented.

| Provider | Detail request | Optional metadata | Relation order and defensive behavior |
|---|---|---|---|
| AniList | One validated GraphQL `Media(id: $id)` query with existing media fields, `seasonYear`, `averageScore`, releases, and direct `relations.nodes` essentials | positive `seasonYear` and scores in `1..100`; invalid values become `null`; root anime releases use valid positive episode/time pairs | GraphQL relation order is preserved; duplicate/self/invalid related nodes are dropped; a malformed mandatory root or response-ID mismatch fails |
| MyAnimeList | One fixed `GET anime/{id}` or `GET manga/{id}` request, selected only from `anime/<positive-int>` or `manga/<positive-int>`, with exact nested `related_anime{...}` and `related_manga{...}` fields | `mean` is converted to a nearest-integer percentage after multiplying by 10; year uses positive `start_season.year`, then a four-digit `start_date` fallback; invalid values become `null` | Anime relations precede manga relations and each provider array keeps its order; duplicate/self/malformed optional nodes are dropped; a malformed mandatory root or response-ID mismatch fails |

The initial-import field sets now include only year and score. Relations remain absent from initial imports; the detail request is the sole enrichment path and never performs an N+1 request per related title. These fields are optional honest data: unknown, zero, out-of-range, or malformed values remain `null`, not fabricated defaults.

The one-request shapes and nested relation fields were verified against the live AniList GraphQL schema and MyAnimeList detail endpoints during the task-13/task-14 planning checks (2026-08-19). The contract fixtures and provider tests in Task 16 preserve that evidence locally. Live limitations remain: provider relation completeness is not guaranteed, MyAnimeList has no banner or release schedule, and provider detail failures are normalized by the concrete provider before the capability layer writes any Room data.

## Task 20 delivery boundary

Task 20 adds `ProviderSyncDataSource` as the provider-neutral boundary for delivering an existing
collection entry. Every read and mutation receives the persisted progress unit and must return a
complete, normalized `ProviderCollectionEntry` before the sync layer can acknowledge the operation.
Progress writes are absolute targets for exactly one unit; the other provider counter and unrelated
list fields are not sent.

AniList reads the authenticated `MediaList` entry and updates it by entry ID with
`SaveMediaListEntry`, using `progress`, `progressVolumes`, or `status` as appropriate. MyAnimeList
reads `my_list_status` and uses form-encoded `PATCH` requests; anime progress is sent as
`num_watched_episodes` and read back as `num_episodes_watched`, while manga uses
`num_chapters_read` or `num_volumes_read`. Neither implementation recreates a missing entry.

The concrete sources perform a read-before-write and an authenticated read-back. Provider APIs do
not expose a server-side compare-and-set for this v0 boundary, so that read/write sequence cannot
eliminate a deletion race; the sync layer treats mismatched identity, unit, status, or malformed
success payloads as non-acknowledgements. Retrofit contract tests cover serialized fields,
fixed-unit zero values, GraphQL HTTP-200 errors, terminal HTTP categories, identity validation, and
preservation of unrelated fields. The tests use fictional MockWebServer payloads and do not claim
live-provider mutation evidence.
