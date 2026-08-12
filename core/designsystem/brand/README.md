# Mio brand vectors

These are the source-of-truth, one-color vectors for the Mio mark and lowercase
wordmark. The silhouette is a simplified optical construction based on the
Gabarito 700 lowercase `m` from Gabarito version 1.000 at Google Fonts commit
`cd21f74a2a47fa24d59d2c1e075c88e9ba4cb36c`. The bundled variable font and OFL
license are checksum-pinned in `THIRD_PARTY_NOTICES.md`.

The launcher uses a 108 × 108 normalized adaptive canvas. The centered 66 × 66
safe zone is `[21, 21]` through `[87, 87]`; the mark's target visual bounds are
`[24, 30]` through `[84, 78]` (60 × 48), with a zero-unit geometric offset and
an optical upward/leftward bias of at most 1 dp left to the launcher consumer.
The angular upper-right fold cut is approximately 9 × 9 normalized units and
is represented as an even-odd negative-space cut in the same path.

`mio-mark.svg`, `mio_launcher_foreground.xml`, and the monochrome launcher
layer share the same normalized path. `mio-wordmark.svg` combines that folded
`m` with Gabarito-like lowercase `i` and `o`; the fold is present only on `m`.
All paths are flat, tintable, and contain no gradients, alpha shading, strokes,
filters, shadows, or secondary brand object.

Export/inspection viewports are 1024 px, 48 px, and 24 px, and the adaptive
launcher is checked under circle, squircle, rounded-square, and square masks.
The Android VectorDrawable path data was converted from these SVG path `d`
values by direct one-color path transcription with `apply_patch`; there is no
raster intermediary or runtime conversion step.
