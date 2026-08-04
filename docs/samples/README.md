# Mio approved visual samples

This folder is the visual companion to [`../DESIGN.md`](../DESIGN.md). It gives implementers a fast, concrete reading of Mio's approved composition and character. It is deliberately small: every image here represents a direction that was explicitly selected during design review.

## Authority

Use the references in this order:

1. [`../PRD.md`](../PRD.md) defines product behavior and scope.
2. [`../DESIGN.md`](../DESIGN.md) is the normative UI specification: tokens, dimensions, component contracts, states, motion, accessibility, and responsive behavior.
3. This folder communicates visual hierarchy, density, artwork treatment, and personality.
4. The interactive prototypes under [`../../prototypes/`](../../prototypes/) are the motion references named in `DESIGN.md` section 17.

If an image conflicts with `DESIGN.md`, **`DESIGN.md` wins**. Never infer a product feature from generated placeholder copy.

## How to read the images

- These are generated concept references, not production screenshots or reusable UI assets.
- They are intentionally preserved as the exact approved outputs. Phone frames, hardware, status bars, invented titles, poster art, and minor rendering artifacts are illustrative.
- Implement the app inside the platform safe area. The canonical design viewport is `393 × 852 dp`; images may have a different raster size.
- Recreate layouts with real components and provider data. Do not trace text, icons, posters, or chrome out of a PNG.
- Gabarito, the flat color tokens, exact dimensions, icon family, elevation, and accessibility behavior come from `DESIGN.md`, not from pixel sampling these images.
- A sample can show only one state. Implement all empty, loading, error, offline, large-text, dark-theme, and reduced-motion states required by `DESIGN.md` and the PRD.

## Sample map

| Sample | Establishes | Known deviations and limits |
| --- | --- | --- |
| [`brand/app-icon-reference.png`](brand/app-icon-reference.png) | The approved page-fold lowercase `m`, lime field, near-black mark, wordmark relationship, and small-size character. | Concept board only. The generated lime has slight shading and its geometry is approximate. Build the flat vector described in [`brand/construction.md`](brand/construction.md); never ship this PNG as the launcher icon. |
| [`today/default.png`](today/default.png) | Today hierarchy: artwork-led **Now** card, clear current progress, paired decrement/increment actions, compact **New** rows, and persistent root navigation. | Button labels and some container outlines are illustrative. Use the exact progress-stepper contract in `DESIGN.md` sections 8.2 and 10.2. |
| [`today/caught-up.png`](today/caught-up.png) | The approved **Personal Mosaic Receipt** for the caught-up state: collection artwork makes the success personal without turning it into a second achievement ceremony. | The receipt should be shorter, with a smaller check and shallower mosaic, as specified in `DESIGN.md` section 10.2. No confetti or stamp motion here. |
| [`library/default.png`](library/default.png) | Approved dense Library rows, prominent artwork, readable status/progress, rounded-outline media icons, filled selected navigation icon, and rounded-square `−1` / `+1` controls. | The image omits the status-filter trigger. Exact row, poster, button, and spacing dimensions come from `DESIGN.md` sections 8.2 and 10.3. Fictional content is illustrative. |
| [`library/status-sheet.png`](library/status-sheet.png) | The single-select opaque status sheet: dimmed context, one full-width row per status, and a small lime check for the current selection. | Use the left-aligned header, `52–56 dp` rows, provider-normalized labels, and sheet behavior in `DESIGN.md` sections 8.4, 10.3, and 11. |
| [`discover/default.png`](discover/default.png) | Search-first Discover, media-type pills, finite horizontal shelves, poster-led browsing, restrained metadata, and actions that remain subordinate to artwork. | Generated titles and art are placeholders. The `+` glyph does not replace the specified `48 dp` Add/status control or its status sheet. Follow `DESIGN.md` section 10.4. |
| [`media-detail/default.png`](media-detail/default.png) | The approved detail composition: shallow artwork banner, overlapping poster, strong title/meta grouping, restrained genres and score, progress, synopsis, and related media. | The persistent tracking dock is outside this frame. Implement it and its Sheet Rise behavior from `DESIGN.md` sections 9.5 and 10.5. |
| [`you/top.png`](you/top.png) | The top of You: playful identity, **Your season so far** near-black mosaic card, collection-derived artwork, Completed/Episodes/Chapters totals, and the start of stats. | Decorative doodles and fictional artwork are illustrative. Use provider-derived user data only and the hierarchy in `DESIGN.md` section 10.7. |
| [`you/scrolled.png`](you/scrolled.png) | The continuation of the same You screen: detailed weekly stats, latest milestone, favorites right now, history, and settings. | This is a scrolled state, not a second route. It intentionally omits the hero above. Join it into the single vertical screen specified in `DESIGN.md` section 10.7. |

## Motion authority

Static PNGs communicate composition, not timing. Use these approved interactive references:

- [`../../prototypes/celebration/`](../../prototypes/celebration/) for Achievement Stamp, Undo, Replay, cancellation, and reduced motion.
- [`../../prototypes/scroll_effect/`](../../prototypes/scroll_effect/) for the Media Detail Sheet Rise.
- [`../../prototypes/onboarding/`](../../prototypes/onboarding/) for Mosaic Assembly; omit its prototype-only intermediate completion screen.
- [`../../prototypes/typeface-comparison/`](../../prototypes/typeface-comparison/) for Gabarito and the baseline light/dark visual character.

## Adding a future sample

Add a file only after its direction is explicitly approved. Use a screen or component folder, a state-based filename such as `empty.png` or `status-sheet.png`, and update the table above with:

- what the image makes visually normative;
- every visible generator artifact or intentional deviation;
- the controlling `DESIGN.md` section.

Do not add rejected alternatives, broad inspiration boards, or near-duplicates. That would make the handoff less deterministic.
