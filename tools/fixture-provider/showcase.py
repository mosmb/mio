#!/usr/bin/env python3
"""Seed the fixture server with a fictional showcase collection and generated artwork.

Every title, synopsis and poster is invented, so screenshots never contain real
accounts or copyrighted artwork. Requires Pillow (`pip install pillow`).

Usage:
    python3 showcase.py --output build/showcase/server --art build/showcase/art
    python3 provider_server.py --output build/showcase/server --art build/showcase/art
"""

import argparse
import json
import math
import random
import time
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw

DAY = 24 * 60 * 60

PALETTES = [
    ("#1d2b53", "#ff6b6b", "#ffd93d"),
    ("#0b3d3a", "#c8f169", "#f4f1de"),
    ("#2d1e2f", "#f7b267", "#f25f5c"),
    ("#12355b", "#7cc6fe", "#f2f7f2"),
    ("#3a0ca3", "#f72585", "#4cc9f0"),
    ("#283618", "#dda15e", "#fefae0"),
    ("#1b1b1e", "#c8f169", "#a9e5ff"),
    ("#432818", "#ffb4a2", "#ffe5d9"),
    ("#14213d", "#fca311", "#e5e5e5"),
    ("#264653", "#e9c46a", "#f4a261"),
    ("#3d0066", "#ff9e00", "#ffd6ff"),
    ("#0d1b2a", "#e0e1dd", "#778da9"),
]

ANIME = [
    ("Starlight Courier", "A night-shift courier delivers letters between floating cities, one constellation at a time.", ["Adventure", "Fantasy"], 12, 7, 9, 0),
    ("Tidewater Academy", "Students at a seaside school learn to read the moods of the ocean before the great storm season.", ["Slice of Life", "Drama"], 24, 13, 15, 1),
    ("Paper Lantern Detective", "A soft-spoken archivist solves small mysteries in a town where every lantern remembers.", ["Mystery", "Supernatural"], 12, 4, 6, 0),
    ("Orbit Bakery", "Two friends run a bakery on a space station and try to perfect bread in zero gravity.", ["Comedy", "Sci-Fi"], 13, 10, 11, 2),
    ("Kite Runner Club", "A struggling high-school club aims for the national kite-flying championship.", ["Sports", "Comedy"], 12, 2, 3, 3),
]

MANGA = [
    ("Ink & Ember", "An apprentice calligrapher discovers her brush strokes can wake sleeping spirits.", ["Fantasy", "Drama"], 96, 11, 41),
    ("Moss Garden Diaries", "A retired gardener writes weekly letters to the plants in a forgotten courtyard.", ["Slice of Life"], 54, 7, 22),
    ("Glasswing", "A courier pilot races through a crystal canyon to deliver the last vaccine.", ["Action", "Sci-Fi"], 120, 14, 63),
]

DISCOVER = [
    ("Velvet Signal", ["Romance", "Music"], "ANIME"),
    ("Northbound Train", ["Drama"], "ANIME"),
    ("Clockwork Fox", ["Fantasy", "Adventure"], "ANIME"),
    ("Summer Static", ["Slice of Life"], "ANIME"),
    ("Hollow Crown", ["Action", "Fantasy"], "MANGA"),
    ("Rooftop Astronomy", ["Romance"], "MANGA"),
    ("Salt & Circuit", ["Sci-Fi"], "MANGA"),
]


def poster(path, seed, size=(460, 650)):
    rng = random.Random(seed)
    base, accent, light = (ImageColor.getrgb(c) for c in PALETTES[seed % len(PALETTES)])
    w, h = size
    image = Image.new("RGB", size, base)
    draw = ImageDraw.Draw(image)
    for y in range(h):
        t = y / h
        draw.line([(0, y), (w, y)], fill=tuple(int(b + (a - b) * t * 0.35) for a, b in zip(accent, base)))
    for _ in range(rng.randint(18, 36)):
        x, y, s = rng.randint(0, w), rng.randint(0, int(h * 0.45)), rng.randint(2, 5)
        draw.ellipse([x, y, x + s, y + s], fill=light)
    cx, cy, r = rng.randint(w // 3, 2 * w // 3), rng.randint(h // 4, int(h * 0.42)), rng.randint(w // 6, w // 3)
    draw.ellipse([cx - r - 14, cy - r - 14, cx + r + 14, cy + r + 14], outline=light, width=3)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=accent)
    layers = rng.randint(3, 4)
    for i in range(layers):
        y0 = h * (0.58 + i * 0.1)
        amp, freq, phase = rng.randint(12, 34), rng.uniform(0.008, 0.02) * (460 / w), rng.uniform(0, 6.3)
        points = [(x, y0 + amp * math.sin(x * freq + phase)) for x in range(0, w + 5, 5)]
        t = (i + 1) / layers
        color = tuple(int(l + (b - l) * t) for l, b in zip(light, base))
        draw.polygon(points + [(w, h), (0, h)], fill=color)
    image.save(path, optimize=True)


def banner(path, seed):
    poster(path, seed, size=(1200, 420))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--art", required=True)
    args = parser.parse_args()
    output, art = Path(args.output), Path(args.art)
    output.mkdir(parents=True, exist_ok=True)
    art.mkdir(parents=True, exist_ok=True)
    now = time.time()
    seed = 0
    anime, manga = [], []

    for index, (title, synopsis, genres, episodes, progress, aired, age) in enumerate(ANIME):
        seed += 1
        poster(art / f"a{index}.png", seed)
        banner(art / f"a{index}-banner.png", seed)
        anime.append({
            "id": 1000 + index, "entry_id": 5000 + index, "title": title, "description": synopsis,
            "genres": genres, "score": 70 + seed * 2, "cover": f"a{index}.png", "banner": f"a{index}-banner.png",
            "progress": progress, "episodes": episodes, "updated_at": int(now - (index + 1) * 3600),
            "release_at": int(now - age * DAY - 600), "release_episode": aired,
        })
    for index, (title, synopsis, genres, chapters, volumes, read) in enumerate(MANGA):
        seed += 1
        poster(art / f"m{index}.png", seed)
        banner(art / f"m{index}-banner.png", seed)
        manga.append({
            "id": 2000 + index, "entry_id": 6000 + index, "title": title, "description": synopsis,
            "genres": genres, "score": 72 + seed, "cover": f"m{index}.png", "banner": f"m{index}-banner.png",
            "chapters": chapters, "volumes": volumes, "chapters_read": read, "volumes_read": 0,
            "updated_at": int(now - (index + 2) * 5400),
        })
    for index, (title, genres, media_type) in enumerate(DISCOVER):
        seed += 1
        poster(art / f"d{index}.png", seed)
        entry = {
            "id": 3000 + index, "entry_id": 7000 + index, "title": title, "tracked": False,
            "description": f"{title} is a fictional title created for Mio's showcase.", "genres": genres,
            "score": 68 + seed, "cover": f"d{index}.png", "updated_at": int(now - DAY),
        }
        if media_type == "ANIME":
            entry.update({"episodes": 12, "progress": 0, "release_at": int(now + 3 * DAY), "release_episode": 1})
            anime.append(entry)
        else:
            entry.update({"chapters": 40, "volumes": 4, "chapters_read": 0, "volumes_read": 0})
            manga.append(entry)

    state = {
        "account": {"id": 42, "name": "Mio Showcase"},
        "anime": anime,
        "manga": manga,
        "page_size": 20,
        "fail_next_collection_page": None,
        "fail_next_identity": None,
        "fail_next_mutation": None,
        "hold_next_collection_page": False,
        "delay_next_collection_seconds": 0,
        "delay_next_mutation_seconds": 0,
        "requests": [],
        "effects": [],
        "held": {},
    }
    (output / "remote.json").write_text(json.dumps(state, indent=2, sort_keys=True))
    print(f"Wrote {output / 'remote.json'} and {len(list(art.glob('*.png')))} artwork files")


if __name__ == "__main__":
    main()
