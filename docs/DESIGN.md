# Mio Design Specification

**Status:** Normative visual and interaction contract

**Applies to:** Android v0

**Product requirements:** [`PRD.md`](./PRD.md)

**Implementation architecture:** [`ARCHITECTURE.md`](./ARCHITECTURE.md)
**Reference viewport:** 393 × 852 dp Android phone

## MVP design amendment — 2026-09-17

For the reduced milestone, the PRD MVP amendment supersedes requirements below for deferred features and exhaustive validation. Apply existing tokens, components, ordinary semantic labels/selected states, touch targets, and system reduced-motion support to shipped screens. Preserve existing accessibility behavior.

MVP may use existing completion feedback without Achievement Stamp, a search prompt without Discover shelves/recent searches, and a You account/preferences screen without personal statistics/history. Additional sort modes, custom motion/haptic overrides, and animated theme crossfades are deferred. Detail remains the sole required Add/Change Status surface.

Keep architecture-required previews and inspect primary light/dark portrait screens and keyboard access on a representative emulator. Sections 15.3 and 16 do not require a full configuration cross-product for MVP. Dedicated screen-reader, enlarged-text, landscape, narrow-width, high-contrast, and frame/gesture permutation audits are deferred, not recorded as passed. The complete design remains the follow-up target; this amendment grants no architecture or data-safety exception.

## 1. Purpose and authority

This document defines how Mio MUST look, move, respond, and adapt. It is written so that a human or coding agent can implement any v0 screen without inventing a second design language.

The terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative:

- **MUST / MUST NOT**: mandatory. A violation is an implementation defect.
- **SHOULD / SHOULD NOT**: the default. A deviation requires a documented reason.
- **MAY**: optional within all other constraints.

When requirements conflict, use this order:

1. The current explicit user instruction.
2. `PRD.md` for product behavior and scope.
3. This document for visual, interaction, motion, and accessibility behavior.
4. `ARCHITECTURE.md` for implementation ownership and technical boundaries.
5. Existing code or prototypes that comply with the sources above.

The prototypes under `prototypes/` are reference implementations, not independent sources of truth. Where a prototype differs from this document, this document wins.

## 2. Design north star

Mio is a **playful, artwork-led utility** for anime and manga tracking. It should feel fast and dependable during routine updates, then briefly delightful when progress becomes an achievement.

The desired emotional sequence is:

```text
Open -> understand immediately -> update confidently -> feel a small reward -> leave satisfied
```

### 2.1 Character

Mio MUST feel:

- Playful, not childish.
- Content-led, not decorative.
- Fast, direct, and forgiving.
- Recognizable through Gabarito, electric lime, rounded geometry, and artwork.
- Calm during routine use and expressive only at meaningful moments.

### 2.2 Anti-goals

Mio MUST NOT use:

- Warm paper, cream, parchment, grain, halftone, or faux print textures.
- Austere gallery or MUBI-like editorial minimalism.
- Violet as a brand color.
- Glassmorphism, liquid glass, translucent navigation, or decorative blur.
- Decorative gradients on controls or structural surfaces.
- Generic AI-generated anime illustrations or decorative “Japanese” motifs.
- Mascots, sakura, torii, manga speed lines, or Japanese characters as superficial branding.
- Autoplay video, sound, streaks, overdue language, guilt, or engagement traps.
- Confetti. Completion uses the defined Achievement Stamp instead.

A dark-to-transparent scrim over artwork is allowed only when needed for legibility. Artwork may contain its native colors and gradients.

## 3. Platform and layout foundation

### 3.1 Target and adaptation

- The reference composition MUST be validated at **393 × 852 dp**.
- Layouts MUST remain usable from 320 dp phone width upward.
- Mio MUST render edge-to-edge and consume status, navigation, gesture, cutout, and IME insets correctly.
- Phone layouts use one column. Tablets MAY widen content, but MUST NOT introduce a different information architecture in v0.
- Root-tab content MUST preserve its scroll position and navigation stack when switching tabs.

### 3.2 Grid and spacing

Use a 4 dp base grid. Prefer these tokens:

| Token | Value | Use |
|---|---:|---|
| `space.1` | 4 dp | Icon/text micro-gap |
| `space.2` | 8 dp | Tight internal gap |
| `space.3` | 12 dp | Related controls |
| `space.4` | 16 dp | Screen gutter, card padding |
| `space.5` | 20 dp | Large card internal gap |
| `space.6` | 24 dp | Section separation |
| `space.8` | 32 dp | Major separation |
| `space.12` | 48 dp | Large breathing space |

Rules:

- Standard horizontal screen gutter: **16 dp**.
- Root screen top content padding after system insets: **24 dp**.
- Minimum space between unrelated sections: **24 dp**.
- Root scrolling content bottom padding: bottom navigation height + system inset + **28 dp**.
- Text and controls that describe the same item MUST remain visually grouped.

### 3.3 Shape scale

| Token | Value | Use |
|---|---:|---|
| `radius.xs` | 10 dp | Compact actions |
| `radius.sm` | 12 dp | Chips, small controls |
| `radius.md` | 16 dp | Progress controls, rows, snackbars |
| `radius.lg` | 24 dp | Major cards and sheets |
| `radius.nav` | 22 dp | Bottom navigation container |
| `radius.full` | 999 dp | Pills only |

Rounded geometry MUST be consistent. Do not assign arbitrary radii per screen.

### 3.4 Touch targets

- Every interactive target MUST be at least **48 × 48 dp**.
- The visible shape MAY be smaller only when the semantic hit target remains 48 × 48 dp.
- Adjacent targets MUST have at least 4 dp separation.
- Press feedback starts on touch-down. Dragging away cancels the commit; returning before release restores it.

## 4. Color and themes

### 4.1 Core palette

Electric lime is a sparse action and selection color, not a background theme.

| Token | Light | Dark | Meaning |
|---|---|---|---|
| `color.background` | `#FFFFFF` | `#0B0D0F` | Root background |
| `color.surface` | `#FFFFFF` | `#111418` | Cards, sheets, navigation |
| `color.surfaceSoft` | `#F2F5EF` | `#171B1E` | Quiet controls and skeletons |
| `color.surfaceRaised` | `#F7F9F5` | `#15191C` | Raised neutral controls |
| `color.text` | `#0B0D0F` | `#F4F8F2` | Primary text |
| `color.textMuted` | `#5B635B` | `#A6AFA5` | Secondary text |
| `color.accent` | `#C8F53D` | `#C8F53D` | Primary action and selection |
| `color.onAccent` | `#0B0D0F` | `#0B0D0F` | Content on lime |
| `color.border` | `#0B0D0F` at 12% | `#F4F8F2` at 14% | Standard border |
| `color.borderStrong` | `#0B0D0F` at 22% | `#F4F8F2` at 25% | High-contrast boundary |
| `color.scrim` | `#0B0D0F` at 42% | `#000000` at 52% | Modal background dim |
| `color.error` | `#B42318` | `#FFB4AB` | Actionable failure |
| `color.errorSurface` | `#FEE4E2` | `#3B1512` | Error container |

Rules:

- Light mode MUST use pure white as its dominant base.
- Dark mode MUST use neutral near-black, not pure OLED black, as its root background.
- Lime MUST occupy roughly 10% or less of a typical populated screen.
- Text on lime MUST be near-black. White text on lime is forbidden.
- Color MUST NOT be the only signal for selection, status, progress, completion, or error.
- Provider or artwork colors MUST remain inside imagery or a small provider identity mark.
- Cover-derived colors MAY affect an artwork scrim but MUST NOT recolor global surfaces or move controls.

### 4.2 Appearance behavior

- Default appearance: follow the Android system.
- Settings MUST provide **System**, **Light**, and **Dark**.
- Artwork is identical across themes.
- Theme changes use a **180 ms opacity crossfade**. No radial reveal or sweeping transition.
- Device-wide appearance and accessibility preferences survive account disconnection.

### 4.3 Elevation

Prefer borders and tonal separation over heavy shadows.

- Flat content: 0 dp elevation.
- Floating navigation, snackbar, and modal sheet: visually equivalent to 6–8 dp.
- Major artwork card: visually equivalent to 4 dp.
- Shadows MUST be soft and neutral; dark mode uses deeper opacity without gray halos.
- Do not stack multiple elevated cards inside one another.

## 5. Typography

### 5.1 Typeface

- Primary family: **Gabarito**.
- Required weights: 400, 500, 600, and 700.
- Fallback order: Noto Sans, Android system sans-serif.
- Gabarito provides Mio's recognizable, playful voice; do not substitute another display face per feature.

### 5.2 Type scale

| Style | Size / line | Weight | Tracking | Use |
|---|---|---:|---:|---|
| `display` | 36 / 40 sp | 700 | -0.04 em | Root screen title |
| `title` | 28 / 32 sp | 700 | -0.03 em | Detail and major state title |
| `section` | 22 / 28 sp | 700 | -0.025 em | Section heading |
| `cardTitle` | 17 / 22 sp | 600 | -0.015 em | Media title |
| `body` | 16 / 24 sp | 400 | 0 | Body and synopsis |
| `bodyStrong` | 16 / 22 sp | 600 | -0.01 em | Important value or action |
| `label` | 13 / 18 sp | 500 | 0 | Navigation and metadata |
| `caption` | 12 / 16 sp | 500 | 0.01 em | Supporting metadata |

Rules:

- Use sentence case. Avoid all caps except tiny provider-supplied abbreviations.
- Media titles may use two lines; truncate after two lines in cards and rows.
- Body copy SHOULD stay under approximately 45 characters per line on phones.
- Large numbers use tabular numerals when available.
- Text MUST follow Android font scaling. Do not clamp user-selected text size.
- At large font scales, reflow controls and grow containers rather than truncating actions.

## 6. Brand and iconography

### 6.1 App icon

The selected identity is the **page-fold `m`**:

- Android adaptive-icon background: flat `#C8F53D`.
- Foreground: a bold near-black lowercase `m` derived from Gabarito 700.
- The upper-right shoulder contains one small angular negative-space cut suggesting a page fold.
- The cut MUST remain visible at 24 px without making the glyph read as another letter.
- Foreground content MUST remain inside the Android adaptive-icon safe zone.
- The monochrome icon uses the same `m` silhouette without the lime field.
- No gradient, shadow, outline, literal open book, Japanese character, or illustration.

The wordmark is lowercase **`mio`** in Gabarito 700, with the same fold detail applied only to the `m`. Use near-black on light backgrounds and off-white on dark backgrounds.

The Android system splash uses the adaptive icon centered on the current theme background and exits directly into the current root state. Do not add a second custom splash, logo loop, or blocking brand animation.

### 6.2 Functional icons

- Base family: **Material Symbols Rounded** or project vector assets faithfully matching it.
- Default icons use rounded geometric outlines at medium optical weight.
- Selected navigation icons use their filled variant.
- Standard visual size: 20–24 dp inside a 48 dp target.
- Strokes and filled variants MUST share the same optical size.
- Custom icons are allowed only for a genuinely Mio-specific concept.
- Do not mix Lucide, sharp Material, emoji, and rounded Material styles.

## 7. Root navigation and common chrome

### 7.1 Bottom navigation

Root destinations are **Today**, **Library**, **Discover**, and **You** in that order.

Container:

- Opaque floating capsule.
- 12 dp from the left, right, and bottom safe area.
- Minimum height: 68 dp.
- Padding: 6 dp; gap: 4 dp.
- Radius: 22 dp.
- Light: white surface with standard border and soft elevation.
- Dark: `#111418` with standard dark border and soft elevation.
- No blur, translucency, or liquid/glass deformation.

Destinations:

- Four equal-width targets, each at least 48 × 54 dp.
- Icon above label; labels are always visible.
- Selected target uses a solid lime rounded rectangle with 16 dp radius.
- Selected icon is filled; inactive icons are outlined and muted.
- The selection surface MAY slide between equal tab bounds using the shared navigation spring, but MUST remain a solid geometric pill.
- Root navigation never auto-hides while scrolling.

Root navigation is visible on the four root screens and their in-tab search/browse states. It is hidden on media detail, progress editor, conflict resolution, OAuth, and other nested focused tasks.

### 7.2 Top bars

- Root screens use a large left-aligned 36 sp title and optional 48 dp actions on the right.
- Nested screens use a 56 dp opaque toolbar with Back at start and at most two actions at end.
- Back is always the leftmost action.
- Do not center long media titles in a toolbar.
- The media-detail toolbar begins visually over artwork, then becomes an opaque surface during Sheet Rise.

## 8. Component contracts

### 8.1 Buttons

**Primary button**

- Height: 52–56 dp.
- Lime fill, near-black label/icon, 16 dp radius.
- Label uses `bodyStrong`.
- Full-width when it is the only primary action in onboarding or an empty state.

**Secondary button**

- White/dark neutral surface, standard border, primary text.
- Same height and radius as its paired primary button.

**Icon button**

- 48 × 48 dp target.
- 20–24 dp rounded icon.
- Neutral pressed surface; lime only when the action is primary or selected.

### 8.2 Progress stepper

`+1` and `−1` MUST use stable, same-size controls:

- Each control: **52 × 52 dp**, 16 dp radius.
- `+1`: lime fill with near-black plus.
- `−1`: neutral surface with a one-pixel-equivalent border and near-black/off-white minus.
- The visible symbols are simple rounded strokes; do not place `+1` text inside the button.
- Accessible labels name the unit and result, for example “Increase to episode 9” and “Decrease to chapter 41.”
- At zero, `−1` remains in place but is disabled. It does not disappear.
- Eligible Library rows stack `−1` over `+1` at the trailing edge with an 8 dp gap.
- A detail tracking dock places them horizontally because horizontal space is available.
- Only Current and supported Repeating entries expose the fast stepper. Other statuses display status without inactive progress controls.

### 8.3 Progress display

- Always show a textual value such as “Episode 8 of 12” or “Chapter 42”.
- A 4 dp rounded progress bar MAY supplement the text.
- Progress bars use lime fill on a neutral track.
- Unknown totals use textual current progress without a misleading percentage bar.
- Anime, chapter, and volume units use the provider-normalized unit already associated with the entry.

### 8.4 Filter pills

- Minimum hit height: 48 dp; visible pill height MAY be 40 dp.
- Selected: lime fill, near-black label, 999 dp radius.
- Unselected: neutral surface with standard border.
- Media filters are always ordered **All**, **Anime**, **Manga**.
- Filter selection MUST include text, not color alone.

### 8.5 Artwork

- Posters use the provider artwork at approximately 2:3 without stretching.
- Use center crop unless a known focal point is available.
- Major cards use full-bleed artwork with a lower legibility scrim.
- Do not place a colored filter over poster art.
- Placeholder: `surfaceSoft` with the page-fold `m` glyph at low contrast.
- Image loading failure preserves the exact artwork bounds to prevent layout shift.

### 8.6 Snackbar and Undo

- One floating snackbar at a time; never stack snackbars.
- 16 dp horizontal margin.
- Bottom: 92 dp above the viewport edge when bottom navigation is visible; 12 dp + safe inset when it is hidden.
- Minimum height: 56 dp; 16 dp leading padding; 16 dp radius.
- Opaque surface, standard border, soft elevation.
- Copy describes the committed result; the trailing **Undo** action uses a compact lime button with a 48 dp target.
- Undo is available immediately, including while completion motion is active.
- A new reversible action replaces the previous snackbar and refers to the latest transaction.
- Screen readers receive a polite announcement containing the result and the availability of Undo.

### 8.7 Bottom sheets

All sheets are opaque and use 24 dp top corners.

- Scrim: `color.scrim`.
- Drag handle: 32 × 4 dp, centered, muted.
- Header: left-aligned `section` text.
- Direct manipulation follows the finger 1:1 after a 10 dp gesture threshold.
- On release, velocity determines dismissal or return. The sheet continues from its live position and velocity.
- Back and scrim tap dismiss unless doing so would discard an unresolved destructive choice.
- Selection sheets apply immediately; forms use explicit Save.

**Set status sheet**

- Subtitle identifies the title.
- Canonical order: Current, Planning, Paused, Completed, Dropped, Repeating when supported.
- Statuses render as 52–56 dp full-width rows.
- Left: rounded status icon. Middle: adaptive status label. Right: single-select control.
- Current status uses a small lime check and slightly stronger label. The full row stays neutral.
- No Save button. Selecting applies, dismisses, then reveals feedback on the underlying screen.
- Selecting Completed triggers the Achievement Stamp after dismissal.

**Library filter sheet**

- Uses the same row treatment and single-selection behavior.
- Canonical order: All statuses, Ready, Current, Planning, Paused, Completed, Dropped, Repeating.
- `Ready` is a computed view, not a provider collection status.
- `Repeating` is shown only when supported by the normalized provider capability defined by the product/domain contract.

**Sort sheet**

- Uses the same full-width row and small-check selection treatment.
- Applies immediately and dismisses; no Save button.
- Only includes sort modes supported by the current screen's normalized data.

### 8.8 Media row

- Minimum height: 136 dp.
- Poster: 84 × 120 dp, 12 dp radius.
- Gap from poster to content: 16 dp.
- Content: two-line title, status with icon, progress text, optional 4 dp progress bar.
- Trailing region: progress stepper for eligible entries, otherwise a status indicator or overflow action.
- Row tap opens detail; stepper taps MUST NOT trigger row navigation.
- Use a quiet divider between rows or 12 dp vertical whitespace, not a raised card around every item.

### 8.9 Poster card

- Standard Discover shelf width: 124–132 dp.
- Poster ratio: approximately 2:3, 16 dp radius.
- Title: up to two lines beneath artwork.
- Secondary metadata: one line.
- Add/status control MUST be visible without opening overflow.

### 8.10 Playful empty state

Empty states use a small, screen-specific authored vector illustration:

- 96–140 dp focal size.
- Flat near-black, lime, white, and cool-gray shapes.
- Motifs MAY use abstract cover stacks, panel frames, bookmarks, or progress marks.
- No warm textures, faux manga art, characters, mascots, or generated anime imagery.
- One clear title, one concise explanatory sentence, and at most one primary action.
- Filtered-empty states offer **Clear filters** rather than Discover.

### 8.11 Loading and error states

Loading:

- Render layout-matched neutral skeletons so content does not jump.
- A narrow low-opacity lime highlight MAY sweep the skeleton at 1,200 ms while loading.
- Reduced motion uses static skeletons.
- Use a spinner only inside a button or compact action whose operation has no meaningful skeleton.

Errors:

- Keep cached content visible whenever safe.
- Place the error beside the affected content or action with direct copy and **Retry**.
- Use an icon and text in addition to red.
- Do not show an offline banner. Routine offline use and pending sync remain visually identical to online use.

## 9. Motion and haptics

### 9.1 Motion principles

- Feedback begins on touch-down.
- Gesture-driven motion MUST be interruptible, use the live on-screen value, and inherit release velocity.
- Reversals follow the same spatial path.
- User input is never locked while an animation finishes.
- Animate transform and opacity where possible.
- Routine actions receive concise feedback; particles and expressive travel are reserved for completion.
- No sound.

### 9.2 Motion tokens

| Interaction | Specification |
|---|---|
| Press down | 80 ms to scale 0.96; no overshoot |
| Press release | Critically damped spring, response 0.28 s, damping 1.0 |
| Root-tab selection | Shared pill spring, response 0.30 s, damping 0.9; icon fill crossfade 120 ms |
| Nested navigation | 220 ms fade plus 16 dp directional offset; symmetric return |
| Bottom sheet | Response 0.30 s, damping 0.8, inherited drag velocity |
| Scrim | 180 ms opacity |
| Small state crossfade | 140–180 ms ease-out |
| Theme change | 180 ms opacity crossfade |

Equivalent Compose spring parameters MAY be used; the observable response and damping are authoritative.

### 9.3 Routine `+1` / `−1`

On touch-down, the button scales to 0.94–0.96. On commit:

1. Button returns with the standard spring.
2. Numeric progress rolls vertically by one step over 180 ms: up for `+1`, down for `−1`.
3. Progress fill advances or retreats over 220 ms.
4. A light haptic tick fires on the committed value change.
5. Snackbar appears with Undo.

No particles, glow, bounce loop, or full-card movement occurs during ordinary progress.

### 9.4 Achievement Stamp

Reaching a known final unit or explicitly setting Completed is an achievement.

Sequence:

1. Commit the data update and expose Undo before visual celebration.
2. Complete the ordinary progress bar and label transition over 220 ms.
3. At approximately 120 ms, a compact lime **Completed** stamp begins near the `+1` source.
4. The stamp travels to the title/status area over 380 ms, with a maximum 1.05 scale overshoot and no more than 6 degrees rotation.
5. At landing, fire a medium confirmation haptic and emit **4–6** small lime/white geometric particles.
6. Particles disperse for 350–400 ms and disappear. The completed stamp remains as state.
7. Total expressive motion SHOULD finish within 900 ms.

Requirements:

- The effect is interruptible.
- Undo during any frame cancels animations, timers, frames, and transient particles immediately, then restores the previous progress and status.
- Navigation remains available throughout.
- Repeated completion after Undo may replay the effect.
- Do not show a persistent Mio achievement or badge elsewhere.

Reduced motion: crossfade the progress label and static Completed stamp over 160 ms; retain confirmation haptic if enabled; omit travel, scale, rotation, and particles.

### 9.5 Detail Sheet Rise

The selected detail-scroll behavior is **Sheet Rise**:

- The artwork banner is temporarily sticky at the top.
- The solid detail surface, with 24 dp top corners, rises over the anchored artwork during roughly 120 dp of scroll travel.
- The poster moves upward no more than 20 dp; the introductory copy moves no more than 8 dp.
- A compact title crossfades into the opaque toolbar as the surface approaches it.
- There is no blur, glass, cover-color toolbar, or large parallax displacement.
- Scrolling upward reverses the same path exactly.

Reduced motion: artwork is not sticky; the layout scrolls normally and the toolbar title uses a short opacity crossfade.

### 9.6 Onboarding Mosaic Assembly

After provider authentication and while initial collection content becomes available:

- Imported cover placeholders begin as a compact overlapping stack.
- Covers travel into a stable 2-row mosaic with small 24 ms staggering within each row and approximately 220 ms between rows.
- Each cover flight lasts about 480 ms with a restrained ease-out and no elastic bounce.
- Assembly completes within approximately 1.4 seconds.
- The mosaic compresses into the artwork region of the real Today screen.
- The user lands directly on Today. **Library imported** appears only as a nonblocking snackbar/toast; there is no intermediate success screen or “Open Today” button.

Reduced motion: crossfade the complete mosaic and Today over 180 ms.

### 9.7 Reduced motion and haptics

- Respect the platform reduced-motion setting and Mio's explicit preference.
- Replace large translation, sticky movement, parallax, rotation, and particles with static state plus 140–180 ms crossfades.
- Do not remove feedback that communicates success or state.
- Haptics are optional reinforcement and MUST NOT carry information alone.
- Provide a Mio setting to disable nonessential haptics.
- Use light feedback for routine progress, medium confirmation for completion, and distinct error feedback only for a rejected action.

## 10. Screen specifications

### 10.1 Onboarding

**Purpose:** Connect exactly one existing provider with no separate Mio account or tutorial.

#### Welcome state

Layout, top to bottom:

1. Quiet lowercase `mio` wordmark.
2. Title: **Your anime and manga, ready when you are.**
3. Short explanation that the selected provider remains the source of truth.
4. Full-width **Continue with AniList** button.
5. Full-width **Continue with MyAnimeList** button.
6. Small privacy/account note.

Rules:

- Pure white or neutral near-black background according to theme.
- No illustration, benefit carousel, preference questionnaire, guest mode, or Skip.
- Provider marks MAY use their official colors inside a 24 dp icon only; button structure remains Mio-neutral.
- OAuth is a nested system/browser task. Returning authentication errors appear inline above the provider buttons with Retry.

#### Initial import

- Keep the same visual shell and show imported covers through Mosaic Assembly.
- If import takes longer, keep the mosaic placeholders visible and show concise status text such as “Bringing in your library”.
- Never show a blocking percentage unless the provider exposes trustworthy total progress.
- On success, land directly on real Today and show nonblocking “Library imported”.

### 10.2 Today

**Purpose:** Answer “What deserves my attention now?” and support a progress update in seconds.

#### Populated state

Order:

1. Large **Today** title.
2. **Now** section with one major artwork card.
3. Optional **New** section with at most three compact action rows.
4. Optional remaining-ready summary.
5. Root bottom navigation.

#### Now card

- Height target: 320–344 dp; reference 332 dp.
- Full-bleed artwork with 24 dp radius.
- Lower scrim protects white text.
- Fixed lower content positions: title, next unit, known ready-unit count when available, current progress, `−1`, `+1`.
- `+1` is the dominant lime action; `−1` is neutral but always exposed when valid.
- Card tap outside controls opens media detail.
- No carousel, page dots, auto-rotation, or hidden swipe requirement.
- A title change changes artwork but MUST NOT change control geometry.

#### New rows

- Maximum three rows.
- Compact poster, title, release/progress context, `−1`, and `+1` where eligible.
- When multiple newly released units are waiting, show one aggregate count on the title row rather than duplicate rows.
- `+1` is lime; `−1` is smaller in visual emphasis but retains the same accessible footprint.
- Row tap opens media detail.
- No “See all,” backlog counter, horizontal carousel, or duplicate of Now.

#### Remaining ready summary

- One quiet outlined row such as “6 other titles are ready when you are”.
- Tapping opens Library with Ready selected.
- Copy MUST NOT imply lateness or obligation.

#### Caught-up state

Replace Now/New with a **Personal Mosaic Receipt**:

- Near-black card, approximately 240–264 dp tall, 24 dp radius. In dark mode use `#111418` plus a standard border so the receipt remains distinct from the root background.
- Small lime check, not an oversized productivity checkmark.
- Title: **You’re caught up**.
- One lightweight recent-progress summary.
- A shallow 5–6-cover mosaic from recently active or completed entries.
- Below the receipt, show the next known release row when trustworthy.
- End with a quiet text/secondary action: **Explore Discover**.
- Do not add recommendations, streaks, badges, achievements, or an endless feed.
- When a completion creates the caught-up state, the Achievement Stamp finishes first, the Now content crossfades into the receipt over 180 ms, and Undo remains available. Undo reverses to the previous Today state.

### 10.3 Library

**Purpose:** Display and manage the complete provider collection.

#### Default layout

1. Large **Library** title.
2. Search and Sort 48 dp icon actions.
3. Media pills: All, Anime, Manga.
4. One compact Status filter control showing the current choice and opening the Library filter sheet.
5. Vertically scrolling compact media rows.
6. Root bottom navigation.

Behavior:

- Default status is Current; remember the account-scoped media filter and supported sort/filter choices.
- Default ordering is recently updated.
- Sort sheet options: Recently updated, Title A–Z, Progress, Score, and Date added when the normalized data supports them.
- Search expands inline into a full-width field, receives focus, and preserves active filters.
- Clear search restores the exact prior list position when practical.
- Do not offer a grid/list toggle in v0.
- Completed, Planning, Paused, and Dropped rows do not show meaningless `+1`/`−1` controls.

#### Library states

- **Collection empty:** playful abstract cover-stack illustration, “Your library is ready for its first title,” and Explore Discover.
- **Filtered empty:** smaller illustration, explain the selected filter, and Clear filters.
- **Loading:** layout-matched rows; cached rows remain visible during refresh.
- **Sync failure:** keep the collection visible and show a local actionable error row or snackbar.

### 10.4 Discover

**Purpose:** Search, browse a bounded current selection, inspect a title, and add/change collection status.

#### Default state

1. Large **Discover** title.
2. Prominent 56 dp search field as the first content control.
3. Media pills: All, Anime, Manga.
4. **Trending now** horizontal artwork shelf with See all.
5. **This season** horizontal artwork shelf with See all.
6. Root bottom navigation.

Rules:

- Search has more visual priority than shelves.
- Shelves show a small, finite number of poster cards and never auto-scroll.
- See all opens a finite two-column poster grid within the Discover stack.
- No hero carousel, editorial collection, social proof, review feed, or endless personalized feed.

#### Focused search

- Search remains at the top with keyboard focus.
- Before text entry, show recent searches and a compact Trending now fallback; never present a blank screen.
- Results use a two-column poster grid.
- Each untracked result exposes a rounded Add action that opens Set status. Never silently add to Planning.
- Tracked results show the current adaptive status instead of Add.
- No-results state uses the Discover empty illustration and suggests changing the query or media filter.

### 10.5 Media detail

**Purpose:** Provide essential context and collection/progress management without becoming a database encyclopedia.

#### Structure

1. Artwork banner occupying approximately 30% of the reference viewport, target 240–270 dp.
2. Back and overflow actions over the banner.
3. 112 × 160 dp poster overlapping the solid detail surface by approximately 32 dp.
4. Title, format/year/status metadata, score, and compact genre pills.
5. User collection status and progress.
6. Synopsis with a four-line collapsed state and Read more when needed.
7. Essential totals and related titles.
8. Persistent opaque tracking dock above the bottom safe area.

The root bottom navigation is hidden. Scrolling uses Sheet Rise.

#### Tracking dock

- Height: 68 dp; 12 dp side/bottom margin; 22 dp radius.
- Opaque light/dark surface with border and soft elevation.
- Untracked: one full-width lime **Add to library** action. Tap opens Set status.
- Tracked and eligible: status/progress context plus horizontal `−1` and `+1`.
- Tracked but not eligible: current status plus **Change status**.
- Exact corrections are available from overflow or progress context and open Progress Editor.

### 10.6 Progress editor

**Purpose:** Correct progress or make larger deliberate changes without repeated tapping.

- Nested full-screen destination with opaque toolbar and no root navigation.
- Top media context uses a 56 × 80 dp poster, title, and current status.
- Use numeric text fields/steppers, never a slider.
- Show the provider-normalized unit and known maximum.
- Manga MAY show separate chapter and volume fields only when both exist in normalized state.
- Primary action: **Save progress**. Secondary: Cancel/back.
- Save is a full-width bottom action above system/IME insets and remains reachable when the keyboard is open.
- Validate inline before Save; do not wait for a failed submit to explain an invalid maximum.
- Saving a known final unit follows the same automatic Completed behavior and Achievement Stamp after returning to the source screen.

#### Conflict resolution

If a manual correction cannot be reconciled safely:

- Explain the conflict in plain language.
- Show two full-width selection cards: the device value and provider value, each with unit, timestamp when trustworthy, and source label.
- Require explicit selection and **Use this value**.
- Do not preselect a destructive rollback.
- Never silently move progress backward.

### 10.7 You

**Purpose:** Reflect the user's collection and activity without creating a Mio social or achievement layer.

Order:

1. Large **You** title and compact connected-provider identity.
2. **Your season so far** recap.
3. Detailed statistics.
4. **Latest milestone**.
5. **Favorites right now**.
6. Activity history.
7. Synchronization and preferences.

#### Your season so far

- Near-black major card with 24 dp radius. In dark mode use `#111418` plus a standard border.
- Stable heading and three equal headline totals: Completed, Episodes, Chapters.
- Shallow strip of artwork from the user's Current and Completed collection.
- Artwork is a stable rotating sample and never changes the positions or meaning of the totals.
- Do not imply a reconstructed seasonal time range when the provider does not supply one.

#### Statistics

- Use a segmented Anime/Manga control.
- Show tracked totals and top genres as compact ranked rows or bars.
- Recent activity uses a restrained lime line/bar chart on a neutral surface.
- Chart title MUST state the trustworthy range, for example “Activity recorded by Mio · last 8 weeks”.
- Charts require textual summaries for accessibility.

#### Milestone, favorites, and history

- Latest milestone: one artwork-led completion card with title and trustworthy date.
- Favorites right now: horizontal artwork row. If favorites are unavailable, label the fallback honestly, for example “Recently active,” rather than “Favorites”.
- History: compact chronological rows from trustworthy provider or local activity.
- No achievement gallery, timeline of ongoing titles, streak, follower count, or social feed.
- Every persistent personal card MUST be backed by the connected provider or trustworthy on-device observations; the design MUST NOT assume a Mio application backend.

#### Preferences and account

- Group rows under Appearance, Motion & haptics, Synchronization, and Account.
- Appearance opens System/Light/Dark single selection.
- Motion follows the system by default and MAY offer a stricter Reduce motion override.
- Show only actionable sync failure prominently; routine pending/success states remain quiet.
- Disconnect/sign out is a destructive text action at the end. Confirm because it removes local account-owned data, and name the connected provider in the dialog.

## 11. Status language and provider adaptation

Presentation is provider-neutral. Do not expose provider enum names.

| Canonical concept | Anime label | Manga label | Mixed/filter label |
|---|---|---|---|
| Current | Watching | Reading | Current |
| Planning | Plan to Watch | Plan to Read | Planning |
| Paused | Paused | Paused | Paused |
| Completed | Completed | Completed | Completed |
| Dropped | Dropped | Dropped | Dropped |
| Repeating | Rewatching | Rereading | Repeating |

Rules:

- `All` and `Ready` are filters, not collection statuses.
- The Set status sheet uses media-specific labels.
- Library's mixed-media filter uses canonical mixed labels.
- If a normalized capability is unavailable, omit or disable the unsupported action according to the product/domain decision; never display a provider error code.
- Status is always communicated with text and a rounded icon, not color alone.

## 12. Offline, sync, and feedback behavior

- Cached content renders immediately on launch and foreground.
- Routine refresh MUST NOT introduce a blocking loading screen.
- Offline operation has no banner, badge, color change, or disabled progress action.
- Optimistic progress looks identical online and offline.
- Routine pending, reconnection, and successful delivery remain invisible.
- A rejected update restores or presents the safe effective state, uses error feedback, and offers Retry or Resolve.
- Authentication failure identifies the provider and offers Reconnect.
- A refresh MUST NOT replace or reorder a card while the user is touching it or while its progress/Undo interaction is active.
- Errors are visible but do not erase cached content.

## 13. Accessibility

### 13.1 Semantics

- Every icon-only action requires a localized content description.
- Media rows expose title, media type, status, progress, and readiness as a coherent semantic group.
- `+1` and `−1` expose distinct custom actions and the resulting value.
- Selected tabs, filters, statuses, and theme options expose selected state programmatically.
- Decorative artwork and particles are hidden from accessibility services.
- Progress announcements are polite; completion may use an assertive confirmation only when it does not interrupt critical speech.

### 13.2 Contrast and color

- Normal text MUST meet WCAG 4.5:1; large text and essential icons MUST meet at least 3:1.
- Test text over every artwork instance, not only a placeholder image.
- If an artwork scrim cannot guarantee contrast, move the text to an opaque surface.
- High-contrast mode strengthens borders and avoids muted-on-muted combinations.

### 13.3 Large text and reflow

- At large font scales, major cards grow vertically.
- Library row actions MAY move to a bottom action row while retaining order and 48 dp targets.
- Bottom navigation labels remain visible; the container MAY grow to 76 dp.
- Do not truncate button labels that perform destructive, authentication, or conflict actions.
- Landscape and keyboard states MUST keep the focused control visible.

### 13.4 Motion and input

- All visible swipe/drag actions have a tap alternative.
- Hidden gestures may accelerate but never gate functionality.
- Reduced-motion equivalents are defined in section 9.
- Haptics may be disabled without losing confirmation.

## 14. Content voice

Mio copy is concise, warm, and nonjudgmental.

Preferred language:

- “Ready when you are”
- “You’re caught up”
- “Next release”
- “6 other titles are ready when you are”
- “Library imported”
- “Couldn’t update progress. Retry”

Avoid:

- “Overdue,” “falling behind,” “streak lost,” or “clear your backlog.”
- Inflated celebration copy for a routine episode.
- Claims about streaming availability, regional access, or complete history without trustworthy data.
- Generic actions such as “Submit” when a specific action exists.

## 15. Compose design-system implementation

### 15.1 Ownership

`:core:designsystem` SHOULD define:

- `MioTheme`, color schemes, typography, spacing, shapes, elevations, and motion tokens.
- `MioButton`, `MioIconButton`, `MioFilterPill`, `MioBottomNavigation`, `MioTopBar`.
- `MioBottomSheet`, `MioStatusRow`, `MioSnackbar`, `MioProgressBar`, `MioSkeleton`, `MioEmptyState`.
- Brand vector assets, common rounded icons, preview surfaces, and accessibility defaults.

`:core:ui` SHOULD define reusable product-aware pieces with at least two real consumers:

- `MioArtwork`, `MioPoster`, image placeholder/error behavior.
- `MioProgressStepper`.
- `MioMediaRow` and `MioPosterCard` only if at least two features genuinely share the same contract.

Feature-specific components remain within their feature, including:

- Today: `NowCard`, `NewMediaRow`, `CaughtUpReceipt`.
- Discover: shelf and search-result compositions.
- You: recap, statistics, milestone, favorite, and history compositions.
- Media: detail header and tracking dock.

### 15.2 State boundaries

- Design-system components receive UI-ready values and callbacks, never domain/provider objects.
- Progress controls receive a normalized unit label, current value, optional maximum, enabled states, and callbacks.
- Status/filter sheets receive an ordered list of UI models so capability differences do not enter the design system.
- Motion completion/cancellation must be tied to the relevant UI state and coroutine/animation lifecycle.
- Stable item keys are mandatory for media rows, shelves, and grids.

### 15.3 Preview matrix

Every screen and meaningful shared component MUST include previews for the relevant combinations:

- Light and dark.
- Reference 393 × 852 phone.
- Font scale 1.0 and a large-text case.
- Populated, loading, empty, and actionable failure.
- Online-effective and pending-offline state when behavior differs internally, while confirming visuals remain identical.
- Reduced motion for Achievement Stamp, Sheet Rise, and Mosaic Assembly.

## 16. Validation checklist

An implementation is not design-complete until all applicable checks pass.

### Global

- [ ] 393 × 852 reference screenshot matches the hierarchy and dimensions in this document.
- [ ] 320 dp width remains usable without clipped actions.
- [ ] Light mode is pure white; dark mode is neutral near-black.
- [ ] Lime is sparse and always uses near-black foreground content.
- [ ] Gabarito loads at the required weights with Noto Sans/system fallback.
- [ ] Root bottom navigation is opaque, persistent, equally divided, and label-visible.
- [ ] Nested screens hide root navigation and provide an obvious Back action.
- [ ] All targets are at least 48 × 48 dp.

### Interaction

- [ ] Press feedback begins on touch-down.
- [ ] `+1` and `−1` remain stable rounded squares; `−1` disables rather than disappears at zero.
- [ ] Undo works during ordinary motion and every frame of completion motion.
- [ ] Sheets track the drag 1:1, inherit velocity, and can be interrupted/reversed.
- [ ] Switching tabs preserves each tab's stack and scroll state.
- [ ] Routine offline behavior is visually indistinguishable from online behavior.

### Motion

- [ ] Ordinary progress uses only press, number roll, bar movement, and light haptic.
- [ ] Completion uses Achievement Stamp with no more than six particles and completes within 900 ms.
- [ ] Detail uses Sheet Rise with an opaque surface and reversible scroll mapping.
- [ ] Onboarding uses Mosaic Assembly and lands directly on Today.
- [ ] Reduced motion removes travel, parallax, rotation, and particles while preserving state feedback.
- [ ] No animation blocks navigation or input.

### Accessibility

- [ ] Screen-reader labels state media type, status, progress, selection, and action result.
- [ ] Text over artwork passes contrast or moves to an opaque surface.
- [ ] Large text reflows instead of hiding actions.
- [ ] Color and haptics are never the sole signal.
- [ ] Keyboard/IME, system insets, Back, and gesture alternatives work correctly.

## 17. Approved references

Use these prototypes to understand the chosen feel while implementing the normative rules above:

- `docs/samples/README.md` — curated static visual references for approved screen hierarchy, density, artwork treatment, and branding. Its deviation notes are part of the handoff; this document remains normative.
- `prototypes/typeface-comparison/` — Gabarito and the core light/dark visual baseline.
- `prototypes/celebration/` — Achievement Stamp, Undo, Replay, and reduced-motion behavior.
- `prototypes/scroll_effect/` — Sheet Rise detail behavior.
- `prototypes/onboarding/` — Mosaic Assembly. Ignore its prototype-only intermediate “Library imported / Open Today” screen; production lands directly on Today as specified here.

Static generated mockups are concept references only. They MUST NOT be treated as pixel-accurate assets, copied artwork, or a substitute for the tokens and component contracts in this document.
