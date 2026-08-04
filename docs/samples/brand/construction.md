# Mio app icon construction

[`app-icon-reference.png`](app-icon-reference.png) records the approved brand direction. It is a concept board, **not** a production asset. Rebuild the mark as vector geometry so it remains crisp, flat, adaptive, and recognizable at launcher and notification sizes.

## Required construction

- Background: a uniform `#C8F53D` electric-lime field. No gradient, texture, glow, or vignette.
- Foreground: `#0B0D0F` near-black.
- Base form: a friendly lowercase `m` derived from Gabarito `700`, adjusted optically rather than used as an untouched font glyph.
- Distinctive gesture: the upper-right shoulder folds inward like a turned manga page. The fold is a negative-space cut in the silhouette, not a separate decorative object.
- Counterforms and joins must remain open at `24 px`. Simplify the fold before allowing it to disappear or fill in.
- Keep the complete foreground inside the adaptive-icon safe zone. Background color must cover the entire adaptive canvas.
- The monochrome icon uses the same one-color silhouette. Do not invent a different symbol for themed icons.
- The wordmark is lowercase `mio`. Reuse the constructed `m`; set or custom-adjust the `i` and `o` from Gabarito so their weight and spacing feel native to it.

## Optical targets

- The mark should read first as `m`, then reward a second look with the page fold.
- It should feel playful through its soft mass and small visual surprise, not through a mascot or extra decoration.
- At small size, preserve the outer silhouette and the fold. Fine internal detail is expendable.
- Center by perceived visual weight, accounting for the heavier left stem and the missing upper-right fold area; do not rely only on geometric centering.

Validate the vector at `1024 px`, `48 px`, and `24 px`, on both square preview and common adaptive masks. The `24 px` version must still read as Mio without the wordmark.

## Prohibited treatments

- no book, television, play, sparkle, character, eye, or speech-bubble symbol;
- no outline around the mark or the icon tile;
- no shadow, bevel, glass, translucency, or three-dimensional fold;
- no warm paper color, off-white field, violet brand gradient, or secondary accent;
- no uppercase `M` and no disconnected decorative page corner.

## Production deliverables

Create source vector files and derive Android assets for:

- adaptive icon background;
- adaptive icon foreground;
- monochrome themed icon;
- standalone app mark;
- horizontal lowercase wordmark.

The exported launcher assets must be generated from those vectors. Do not crop or rasterize the concept board into an icon.
