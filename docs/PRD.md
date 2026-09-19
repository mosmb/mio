# Mio — Product Requirements Document

**Status:** Product vision and v0 definition  
**Product:** Mio  
**Primary platform:** Android  
**Release posture:** v0 is a beta-stage product; closed or public distribution remains undecided

## MVP scope amendment — 2026-09-17

The first delivery milestone is the reduced MVP defined in [MVP scope and delivery](tasks/MVP.md). That ordered board and its remaining MVP tickets supersede the broader v0 scope below only for this milestone. Both providers, the daily progress loop, Library, Discover search/add through detail, correction/recovery, and You account/preferences remain required. Celebration polish, browsing shelves, rich personal statistics/history, duplicate status shortcuts, and the explicitly listed secondary features are deferred.

For MVP, You starts with account/preferences rather than recap; completion uses existing feedback rather than Achievement Stamp; Library uses latest-progress ordering; Discover has a search prompt instead of shelves/recent history. The original sections remain the post-MVP product direction, not additional MVP acceptance gates. Existing implemented behavior is not to be removed merely because it is now optional. Architecture, durable sync, security, and safe recovery are unchanged.

## 1. Product summary

Mio is a fast, focused anime and manga tracker for people who already maintain an AniList or MyAnimeList collection.

Mio turns a large, passive collection into a small, actionable daily queue. A user should be able to open the app, understand what is ready to watch or read next, update progress, and leave in under 30 seconds.

Mio is not intended to reproduce every database, community, discovery, or customization feature offered by existing tracking platforms. It is the user's daily companion for deciding what is next and recording progress with near-zero friction.

### Product promise

> Open Mio, know what is next, update progress, and leave satisfied in under 30 seconds.

## 2. Problem

Active anime and manga followers commonly track 15–50 current titles. AniList and MyAnimeList contain the necessary collection data, but frequent tracking still requires users to:

- Scan large lists.
- Remember which titles have released something new.
- Determine the next episode, chapter, or volume themselves.
- Open detail or editing screens.
- Perform several actions to record a simple progress change.

The result is unnecessary cognitive load around an activity that should take seconds.

## 3. Target user

The primary user:

- Tracks approximately 15–50 active anime and manga titles.
- Checks releases several times per week.
- Updates progress almost daily.
- Already has an AniList or MyAnimeList account and collection.
- Wants a focused daily queue rather than another comprehensive media database.
- Values speed, reliability, calmness, and polished interactions.
- Expects synchronization and offline behavior to work without ongoing attention.

Mio is initially optimized for this high-activity tracker. Casual users may still benefit, but their needs must not dilute the core workflow.

## 4. Job to be done

When I have many active anime and manga titles, help me identify the most relevant thing I can watch or read next and record my progress immediately, so I do not need to scan or maintain my full collection manually.

### Core session

1. The user opens Mio.
2. Today immediately presents the best next action and any meaningful new releases.
3. The user taps `+1` on an episode, chapter, or volume.
4. Mio commits the update immediately and provides Undo.
5. The user leaves, normally within 30 seconds.

## 5. Product principles

### 5.1 Action before exploration

Today is the default destination. Discovery is available but secondary. Mio must never optimize browsing time at the expense of the core update loop.

### 5.2 A small, honest queue

Mio surfaces a few understandable choices rather than an opaque or endless feed. It does not claim that a release is new or available unless the information is sufficiently trustworthy.

### 5.3 Immediate, reversible actions

Common actions commit with one tap. Undo protects the user from mistakes. Confirmation dialogs are reserved for destructive or genuinely ambiguous actions.

### 5.4 Invisible success, visible failure

Routine offline operation and synchronization should require no attention. Mio must never hide unresolved failure or risk silent data loss.

### 5.5 Immersion around a stable utility core

Artwork, color, motion, and haptics make Mio feel connected to entertainment. They never move, obscure, or delay essential controls.

### 5.6 No backlog guilt

Mio helps users choose; it does not pressure them to consume. It avoids streaks, overdue language, punitive badges, and productivity framing.

### 5.7 The user's interests are not ad inventory

Mio does not use sponsored ranking, paid queue quality, or advertising that compromises clarity or trust.

## 6. Information architecture

Mio uses four bottom-navigation destinations:

1. **Today** — the default, prioritized daily queue.
2. **Library** — the complete imported collection and its management tools.
3. **Discover** — lightweight title search and collection entry.
4. **You** — identity, personal recap, statistics, milestones, favorites, synchronization state, and preferences.

Anime and manga are mixed in Today. Library provides remembered `All`, `Anime`, and `Manga` filters. They must not become separate bottom-navigation destinations.

## 7. v0 requirements

### 7.1 Account connection and onboarding

- An AniList or MyAnimeList account is required.
- Both providers are supported in v0.
- A user selects and connects exactly one provider.
- The selected provider is the sole source of truth for collection state and progress.
- Mio imports the user's entire collection automatically.
- Mio does not require a separate app account, preference questionnaire, tutorial carousel, or manual collection setup.
- After initial synchronization, the user lands directly on Today.
- Guest and browse-only modes are out of scope.
- Provider-specific functionality must not weaken the common core experience.

### 7.2 Today

Today answers: **What deserves my attention now?**

It is not a copy of Library and must not expand into the user's complete collection.

#### Now

Today presents one prominent **Now** card:

- It represents one title, never an individual release feed.
- It shows the next released unit after the user's recorded progress.
- It uses the progress unit already tracked for that title by the connected provider.
- Confirmed v0 eligible statuses are Currently Watching and Currently Reading.
- Rewatching and Rereading remain TBD pending verification of AniList and MyAnimeList provider behavior.
- Paused, Planned, Dropped, and Completed titles are excluded.
- Titles with no released unit after the user's current progress are excluded.
- The default priority is the eligible title the user updated most recently.
- An explicitly unfinished or partially consumed unit is not modeled in v0.

The card communicates:

- Title and recognizable artwork.
- The next episode, chapter, or volume.
- How many released units are available when known.
- Current progress in context.
- A large, stable `+1` action.

#### New

Today may show up to three compact **New** title cards:

- A title qualifies as New when its earliest currently unconsumed unit has a trustworthy release time within the last seven days.
- If an older released unit is still waiting, the title is backlog and does not qualify as New merely because a later unit was released recently.
- This rule works from the first synchronization and does not require Mio to reconstruct whether the user was caught up in the past.
- One title appears at most once, even if multiple units have arrived.
- Multiple releases are aggregated into a badge or count.
- Items are ordered by release recency.
- A title already represented by Now is not duplicated in New.
- If release timing is unavailable or insufficiently trustworthy, the title may appear as Ready or Continue but must not be labeled New.

#### Remaining actionable titles

Today does not display or expand the full backlog. When additional titles are actionable, it shows a compact summary such as:

> 6 other titles are ready when you are

Tapping this opens Library with the `Ready` filter already applied.

#### First synchronized session

- Now is derived from the most recently updated eligible title.
- New uses the same trustworthy seven-day release rule as every later session.
- Mio must not use language such as “since your last visit” until it has established that baseline.

#### Caught-up state

An empty actionable queue must feel complete rather than abandoned. Today shows a completion receipt containing, when available:

- A clear “You're caught up” confirmation.
- A lightweight summary of recent progress.
- The next known release from the user's collection.
- Personal season artwork or a mosaic of recently active titles.
- A discreet route to Discover, without placing recommendations in the primary path.

The intended outcome is that the user feels satisfied and leaves immediately. The empty state must not be optimized to extend engagement.

### 7.3 Progress updates

- `+1` commits immediately without confirmation.
- The interface updates optimistically and offers Undo.
- Updates work while offline and synchronize automatically when connectivity returns.
- Routine synchronization does not interrupt the user.
- Today and Library both provide direct progress updates.
- Users can access an explicit progress editor for corrections or larger changes.
- Hidden gestures may accelerate an action but must never be the only way to perform it.
- Reaching a known final episode, chapter, or volume automatically changes the title to Completed.
- Automatic completion receives a short celebratory confirmation and remains undoable.
- Mio preserves whichever progress unit the user already uses for the title on AniList or MyAnimeList rather than imposing a global chapter-versus-volume choice.

### 7.4 Offline and conflict behavior

- Today displays saved information immediately and silently checks the connected provider whenever Mio enters the foreground.
- Provider-side collection and progress changes normally appear during that foreground refresh.
- Refreshing never blocks Today or its progress actions.
- Mio must not reorder or replace a card while the user is actively interacting with it.
- Offline operation has no banner, warning, or special interaction state. The saved queue and progress actions continue to behave normally.
- Routine refresh, pending synchronization, reconnection, and successful delivery are invisible.
- Mio notifies the user only when an update is rejected, authentication fails, a conflict requires input, or another genuine error prevents safe completion.
- Cached release information that no longer satisfies the seven-day New rule must not continue to appear as New merely because Mio is offline.
- The local interaction must feel identical online and offline.
- Pending operations synchronize automatically after reconnection.
- Mio never silently moves progress backward.
- For routine increment conflicts, Mio preserves the highest credible progress observed locally or remotely.
- A Completed state takes precedence over an older in-progress state.
- Explicit manual corrections are distinguished from routine increments.
- If a correction conflicts with a newer external change and cannot be reconciled safely, Mio asks the user which value is correct.
- Authentication failures, rejected updates, and unresolved conflicts are visible and actionable.
- The user can inspect a lightweight synchronization state from You, but routine success does not require monitoring.

### 7.5 Library

Library answers: **What am I tracking?**

- It imports and represents the user's entire provider collection.
- It defaults to Currently Watching and Currently Reading.
- Status filter chips include Current, Planned, Paused, Completed, and Dropped.
- Rewatching and Rereading remain TBD pending verification of AniList and MyAnimeList provider behavior.
- Media filters are `All`, `Anime`, and `Manga`; the last selection is remembered.
- A `Ready` filter shows Current titles with a released unit available after current progress.
- Current titles are ordered by the user's latest progress update by default.
- Each eligible row or card exposes a fast `+1` control.
- Users can open a title, edit exact progress, or change collection status.
- Library is the sole complete inventory. Today only summarizes and prioritizes it.

### 7.6 Discover

Discover remains deliberately bounded in v0. It supports four jobs:

1. Search for an anime or manga.
2. Browse a small, understandable set of current titles.
3. View essential title information.
4. Add the title to the collection or change its status.

The screen is search-first:

- A prominent search field is the first content control.
- Visible `All`, `Anime`, and `Manga` filters apply to search results and browsing shelves.
- A horizontal **Trending now** shelf and a horizontal **This season** shelf provide lightweight browsing without turning Discover into an endless feed.
- Each shelf shows a small number of artwork-led title cards. `See all` opens a finite two-column grid for that shelf.
- Tapping artwork opens the title detail screen.
- An untracked title exposes an add action that opens a compact, single-select collection-status sheet. Mio never silently adds a title to Planning.
- A tracked title displays its current collection status instead of an add action.

Essential title information may include artwork, title, media format, release status, summary, genres, episode/chapter/volume totals when known, and the user's collection state.

Trending and seasonal shelves use trustworthy provider metadata and ordering. They are not personalized recommendations and must remain visually secondary to search. Personalized recommendations, editorial collections, reviews, social features, and advanced filters are deferred.

### 7.7 You

You is the fourth bottom-navigation tab and Mio's personal reflection layer. In v0 it contains:

- Connected-provider identity.
- A prominent **Your season so far** recap card.
- Detailed personal statistics.
- The user's latest completion milestone.
- A **Favorites right now** artwork row.
- Activity history.
- Synchronization state and any actionable failure.
- Appearance and accessibility preferences.
- Account disconnection and sign out.

#### Your season so far

The recap card combines personal statistics with artwork from the user's own collection:

- It shows Completed titles, recorded Episodes, and recorded Chapters as three equal summary values.
- Its artwork strip uses a stable rotating sample of titles from the user's Current and Completed collection entries.
- The artwork is decorative and never changes the meaning or position of the statistics.
- The heading is expressive rather than a promise of reconstructed annual history. Unless the connected provider supplies a trustworthy time range, the values represent available cumulative collection progress.

#### Detailed statistics

The statistics section may include:

- Tracked Anime and Manga totals.
- A compact recent-activity chart.
- An `Anime` and `Manga` segmented view.
- Top genres derived from the metadata of the user's tracked titles.

Collection totals and genres are derived from the connected provider. Recent-activity charts use only activity Mio has observed locally since installation unless the provider supplies equivalent trustworthy history. Mio must not imply that locally incomplete activity is a complete historical record.

#### Milestone, favorites, and history

- **Latest milestone** shows the most recent trustworthy completion, with title artwork and completion date when available.
- **Favorites right now** shows a small artwork row sourced from provider favorites when available. If provider favorites are unavailable, Mio uses top-rated or recently active collection titles and does not claim that the user explicitly favorited them.
- Activity history combines trustworthy provider activity with locally recorded Mio actions. Missing provider history is omitted rather than reconstructed.

The You tab has no Mio-specific achievements, badges, streaks, follower counts, or social feed. All persistent personal content comes from the active AniList or MyAnimeList account or is derived locally on the device; Mio requires no separate account or application backend.

### 7.8 Visual and interaction character

Mio combines calm utility with content-led immersion.

#### Living Spotlight

- The Now title visually owns Today through artwork and an atmospheric derived palette.
- Effects remain subtle and preserve text contrast.
- Changing the title changes the mood without changing control placement.

#### Progress journey

- Now visualizes the user's current place in a title, not merely a raw number.
- Tapping `+1` advances the visual state with concise motion and haptic feedback.
- Anime and manga may receive expressions appropriate to episodes, chapters, or volume spines while preserving the same interaction model.

#### Completion moments

- Completing a title creates a brief, satisfying visual and haptic response.
- The underlying update commits before celebration begins.
- Undo is immediately available.
- Feedback is interruptible and never blocks navigation.

#### Guardrails

- Core controls stay in stable positions.
- Artwork never compromises legibility.
- Motion is short, optional, and compatible with reduced-motion preferences.
- No autoplay video, forced sound, blocking intro sequence, or mandatory gesture-only control.
- Repeated sessions become calmer; special treatments appear only when context warrants them.

### 7.9 Meaning of “ready”

In v0, Ready means that a Current title's next episode, chapter, or volume appears to have been released according to trustworthy available information and follows the user's recorded progress.

Mio does not promise that the content is available through a particular streaming or reading service, subscription, language, edition, or region. Streaming-service aggregation and in-app playback/reading are out of scope.

### 7.10 Accessibility and adaptability

Accessibility is part of the fast core experience rather than later polish.

- Essential actions have clear labels, generous touch targets, and screen-reader descriptions.
- Color is never the only way progress, status, release confidence, or failure is communicated.
- Text supports platform font scaling without hiding progress controls.
- Living Spotlight artwork preserves sufficient contrast in every derived palette.
- Reduced-motion preferences replace movement with concise static feedback.
- Light, dark, and system appearance preferences are supported without changing information hierarchy.
- Haptics enhance feedback but are never required to understand whether an update succeeded.

## 8. Success criteria

### Primary outcome

Users trust Today enough to act on its top suggestion, update progress, and leave without navigating through the full Library.

### Provisional v0 metrics

- At least 60% of progress-update sessions complete directly from Today without opening Library.
- Median time from app open to committed progress update is under 30 seconds.
- The top Now suggestion is acted on in at least 40% of sessions containing a progress update.
- Retained users open Mio on at least three days per week.

### Guardrail signals

- Undo and manual correction rates remain low enough to demonstrate that one-tap actions are safe and clear.
- Users rarely need Library to compensate for an irrelevant or incomplete Today queue.
- Unresolved synchronization failures remain exceptional and visible.
- Caught-up sessions remain short rather than being artificially extended through recommendations.

These thresholds are initial hypotheses. Beta data should establish baselines before they become durable targets.

## 9. Roadmap

Version labels describe product maturity, not necessarily distribution channel. v0 may be closed or public, but it remains a beta-stage validation release.

### v0 — Validate the daily loop

Must prove that users trust Today and can complete routine progress updates in under 30 seconds.

Includes:

- Android phone experience.
- Sign-in with either AniList or MyAnimeList, using exactly one provider.
- Full collection import.
- Today with Now, meaningful New releases, remaining-item summary, and caught-up state.
- One-tap progress, Undo, automatic completion, and explicit correction.
- Reliable offline behavior and synchronization conflict handling.
- Complete Library with status, media, and Ready filters.
- Search-first Discover with bounded Trending now and This season shelves, title details, and collection management.
- You with a personal recap, detailed statistics, latest milestone, favorites, activity history, synchronization state, and preferences.
- Living Spotlight, progress journey, and completion feedback.
- Basic accessibility and appearance preferences.

### v1 — Strengthen retention and control

Candidate scope, subject to v0 evidence:

- Release notifications as a push extension of Today.
- Immediate notifications for explicitly prioritized titles.
- Optional daily release digest.
- Notifications only when relevant: caught-up or prioritized titles, not ordinary backlog accumulation.
- In-app `Prioritize` preference, distinct from provider favorites.
- Better queue explanations and user controls when trust data shows they are needed.
- Personalized recommendations in Discover.
- Android widgets or system surfaces for viewing Now and recording progress quickly.
- Continued improvement to release confidence, particularly for manga.

Notifications must be opt-in, controllable globally and per title, and conservative when release information is uncertain.

### v2 and later — Expand without diluting the core

Potential directions:

- iOS experience when product maturity justifies expansion.
- Broader shared product foundations prepared for multiplatform delivery.
- Deeper personal insights, themes, and customization.
- Upcoming-release calendar and richer planning views.
- More sophisticated recommendations that remain clearly separated from Today.
- Additional provider migration or account-management tools without introducing simultaneous competing sources of truth.

Later scope must continue to protect the under-30-second daily loop.

## 10. Monetization principles

- v0 is free and ad-free.
- Core tracking, synchronization, offline updates, Today ranking, and queue trust must remain ungated.
- Mio does not accept sponsored placement in Today.
- The long-term business model remains undecided.
- Potential paid value may include deeper personalization, richer statistics, themes, widgets, or advanced notification controls.
- A paying user must never receive more accurate core tracking than a free user.

## 11. Privacy and measurement principles

- Mio uses collection and activity data only to provide the tracking experience and improve the product.
- User data is not sold or used to create sponsored placement.
- Product analytics should be limited to the behaviors needed to evaluate the stated success criteria and diagnose reliability.
- Mio should measure whether a queue action occurred, how long the flow took, and whether correction or failure followed; it does not need invasive observation of unrelated user behavior.
- Users can disconnect their provider and remove Mio-specific preferences or personalization data.
- Disconnecting Mio never deletes or rewrites the user's provider collection unless the user explicitly performs a collection action beforehand.

## 12. Explicit non-goals

Mio is not building the following in v0:

- Social feeds, comments, reviews, or messaging.
- Anime streaming or manga reading.
- Streaming-service or regional availability aggregation.
- News or editorial publishing.
- Encyclopaedic character, staff, studio, and franchise pages.
- Advanced custom lists and power-user list management.
- Multiple simultaneous providers or cross-provider synchronization.
- Gamified streak pressure, badges, or backlog guilt.
- Desktop, web, tablet-specific, or iOS experiences.
- AI-generated recommendations or chat.
- Ads, sponsored ranking, or paid queue accuracy.
- Partial-episode or partial-chapter progress.

AniList and MyAnimeList remain available for deeper database, community, and power-user functionality.

## 13. Risks and product assumptions

### Release-data trust

Mio's differentiation depends on identifying what is ready. Release information, especially for manga, may be incomplete or ambiguous. The product must prefer omission or careful language over false certainty.

### Provider parity

AniList and MyAnimeList may expose different concepts or data quality. Mio promises parity for its core jobs, not identical exposure of every provider-specific feature.

### Queue relevance

Latest user activity is understandable and deterministic, but may not always match intent. v0 should measure when users ignore Now or immediately open Library. Manual Prioritize controls should only be accelerated if evidence supports them.

### First-session quality

Mio has no history of its own on first connection. It must derive a useful initial queue without making unsupported claims about what is new since the user last visited.

### Optimistic-update trust

One-tap updates are valuable only when corrections are easy and synchronization is dependable. Undo, conflict safety, and visible exceptional failure are product-critical rather than secondary polish.

### Scope pressure

Discovery, social, statistics, calendars, and customization can easily turn Mio into another comprehensive tracker. Every addition must be evaluated against the daily product promise.

## 14. Open decisions

The following decisions intentionally remain open:

- Whether v0 distribution is closed beta or public beta.
- Exact beta cohort size and duration.
- Final notification scope and defaults for v1.
- Future monetization model.
- Timing and product threshold for beginning iOS work.
- Metric targets after real beta baselines are available.

## 15. Product constraint note

v0 targets Android. iOS is a future product target. The current implementation direction is Kotlin with readiness for a possible Kotlin Multiplatform evolution, but architecture and technology choices are outside this PRD.

## 16. Decision test for future features

Before adding a feature, ask:

1. Does it help users know what is next, record progress faster, or trust Mio more?
2. Can it remain out of the way during a 30-second session?
3. Does it preserve Today as an actionable queue rather than a content feed?
4. Does it avoid duplicating functionality better handled by AniList or MyAnimeList?
5. Can success be observed through user behavior rather than time spent in the app?

If the answer is no, the feature should be delayed, moved to a secondary destination, or rejected.
