#!/usr/bin/env python3
"""Loopback-only AniList/MyAnimeList fixture server. Never connects to external services.

See README.md in this directory for usage."""

import argparse
import json
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlsplit

SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60


def timestamp(seconds):
    return int(seconds)


def iso_timestamp(seconds):
    return datetime.fromtimestamp(seconds, timezone.utc).isoformat().replace("+00:00", "Z")


def default_state():
    now = time.time()
    return {
        "account": {"id": 42, "name": "Task 21 fictional account"},
        "anime": [
            {
                "id": 101,
                "entry_id": 501,
                "title": "Task 21 Now Fixture",
                "progress": 8,
                "episodes": 12,
                "updated_at": timestamp(now),
                "release_at": timestamp(now + 60),
                "release_episode": 9,
            },
            {
                "id": 102,
                "entry_id": 502,
                "title": "Task 21 New Fixture",
                "progress": 4,
                "episodes": 24,
                "updated_at": timestamp(now - 3600),
                "release_at": timestamp(now - SEVEN_DAYS_SECONDS + 120),
                "release_episode": 5,
            },
            {
                "id": 103,
                "entry_id": 503,
                "title": "Task 21 Later Fixture",
                "progress": 2,
                "episodes": 12,
                "updated_at": timestamp(now - 7200),
                "release_at": timestamp(now - SEVEN_DAYS_SECONDS - 60),
                "release_episode": 3,
            },
        ],
        "manga": [
            {
                "id": 202,
                "entry_id": 602,
                "title": "Task 21 Chapter Fixture",
                "chapters": 60,
                "volumes": 5,
                "chapters_read": 20,
                "volumes_read": 0,
                "updated_at": timestamp(now - 1800),
            },
            {
                "id": 303,
                "entry_id": 603,
                "title": "Task 21 Volume Fixture",
                "chapters": 100,
                "volumes": 7,
                "chapters_read": 0,
                "volumes_read": 2,
                "updated_at": timestamp(now - 2400),
            },
        ],
        "page_size": 2,
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


parser = argparse.ArgumentParser()
parser.add_argument("--output", required=True)
parser.add_argument("--art", help="Optional directory of artwork served at /art/<file>")
args = parser.parse_args()
output = Path(args.output).expanduser().resolve()
output.mkdir(parents=True, exist_ok=True)
lock = threading.RLock()
state_path = output / "remote.json"
log_path = output / "requests.json"
state = json.loads(state_path.read_text()) if state_path.exists() else default_state()
requests = json.loads(log_path.read_text()) if log_path.exists() else []
held_events = {}
request_sequence = max((item.get("sequence", 0) for item in requests), default=0)


def persist():
    state_path.write_text(json.dumps(state, indent=2, sort_keys=True))
    log_path.write_text(json.dumps(requests, indent=2, sort_keys=True))


def new_request_id():
    global request_sequence
    request_sequence += 1
    return f"task21-{request_sequence:06d}"


def page_entries(media_type):
    return state["anime"] if media_type == "ANIME" else state["manga"]


def art_url(name):
    return f"http://127.0.0.1:18820/art/{name}" if name else None


def mal_picture(entry):
    url = art_url(entry.get("cover"))
    return {"medium": url, "large": url} if url else None


def anilist_entry(entry, media_type):
    common_media = {
        "id": entry["id"],
        "type": media_type,
        "format": "TV" if media_type == "ANIME" else "MANGA",
        "status": entry.get("publication_status", "RELEASING" if media_type == "ANIME" else "FINISHED"),
        "episodes": entry.get("episodes"),
        "chapters": entry.get("chapters"),
        "volumes": entry.get("volumes"),
        "description": entry.get("description", "Fictional Task 21 fixture item"),
        "bannerImage": art_url(entry.get("banner")),
        "genres": entry.get("genres", ["Fixture"]),
        "seasonYear": 2026,
        "averageScore": entry.get("score", 75),
        "title": {"userPreferred": entry["title"], "romaji": entry["title"], "english": None, "native": None},
        "coverImage": {"extraLarge": art_url(entry.get("cover")), "large": None, "medium": None},
        "airingSchedule": {"nodes": []},
    }
    if media_type == "ANIME":
        common_media["airingSchedule"] = {
            "nodes": [{"episode": entry["release_episode"], "airingAt": entry["release_at"]}],
        }
    return {
        "id": entry["entry_id"],
        "status": entry.get("status", "CURRENT"),
        "progress": entry.get("progress", entry.get("chapters_read", 0)),
        "progressVolumes": entry.get("volumes_read", 0),
        "updatedAt": entry["updated_at"],
        "createdAt": entry["updated_at"],
        "media": common_media,
    }


def mal_anime_entry(entry):
    return {
        "node": {
            "id": entry["id"],
            "title": entry["title"],
            "main_picture": mal_picture(entry),
            "synopsis": entry.get("description", "Fictional Task 21 fixture item"),
            "media_type": "tv",
            "status": "currently_airing",
            "num_episodes": entry["episodes"],
            "mean": 7.5,
            "start_date": "2026-01-01",
            "start_season": {"year": 2026},
            "genres": [{"id": index + 1, "name": name} for index, name in enumerate(entry.get("genres", []))],
        },
        "list_status": {
            "status": entry.get("status", "watching"),
            "num_episodes_watched": entry.get("progress", 0),
            "is_rewatching": False,
            "updated_at": iso_timestamp(entry["updated_at"]),
        },
    }


def mal_manga_entry(entry):
    return {
        "node": {
            "id": entry["id"],
            "title": entry["title"],
            "main_picture": mal_picture(entry),
            "synopsis": entry.get("description", "Fictional Task 21 fixture item"),
            "media_type": "manga",
            "status": "currently_publishing",
            "num_chapters": entry["chapters"],
            "num_volumes": entry["volumes"],
            "mean": 7.5,
            "start_date": "2026-01-01",
            "start_season": {"year": 2026},
            "genres": [{"id": index + 1, "name": name} for index, name in enumerate(entry.get("genres", []))],
        },
        "list_status": {
            "status": entry.get("status", "reading"),
            "num_chapters_read": entry.get("chapters_read", 0),
            "num_volumes_read": entry.get("volumes_read", 0),
            "is_rereading": False,
            "updated_at": iso_timestamp(entry["updated_at"]),
        },
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "MioTask21Fixture/1"

    def log_message(self, *_):
        pass

    def reply(self, value, status=200):
        raw = json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        try:
            self.wfile.write(raw)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def read_json(self):
        return json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")

    def control(self, operation, detail=None, effects=None):
        with lock:
            request_id = new_request_id()
            requests.append(
                {
                    "id": request_id,
                    "sequence": request_sequence,
                    "method": self.command,
                    "path": self.path,
                    "operation": operation,
                    "detail": detail or {},
                    "effects": effects or [],
                    "recorded_at": time.time(),
                },
            )
            persist()
            return request_id

    def hold_if_requested(self, request_id):
        with lock:
            should_hold = state.get("hold_next_collection_page", False)
            if should_hold:
                state["hold_next_collection_page"] = False
                held_events[request_id] = threading.Event()
                state.setdefault("held", {})[request_id] = {"since": time.time()}
                persist()
                event = held_events[request_id]
            else:
                event = None
        if event is not None:
            event.wait(timeout=300)
            with lock:
                state.setdefault("held", {}).pop(request_id, None)
                held_events.pop(request_id, None)
                persist()

    # Discover acceptance uses the same catalog and production provider adapters.
    def search_page(self, provider, query, media_type, offset, limit):
        self.control("search", {"provider": provider, "query": query, "type": media_type, "offset": offset, "limit": limit})
        with lock:
            fail = state.get("fail_next_search", False)
            state["fail_next_search"] = False
            delay = float(state.pop("delay_next_search_seconds", 0))
            entries = [entry for entry in page_entries(media_type) if query.lower() in entry["title"].lower()]
            size = min(limit, max(1, int(state.get("search_page_size", limit))))
            page = entries[offset:offset + size]
            has_next = offset + len(page) < len(entries)
            persist()
        if delay:
            time.sleep(min(delay, 10))
        if fail:
            self.reply({"errors": [{"status": 503}], "message": "fixture search failure"}, 503)
            return None
        return page, has_next

    def do_GET(self):
        parsed = urlsplit(self.path)
        if self.server.server_port == 18821:
            with lock:
                if parsed.path == "/snapshot":
                    self.reply({"state": state, "requests": requests})
                elif parsed.path == "/health":
                    self.reply({"ok": True, "bind": "127.0.0.1"})
                elif parsed.path == "/release-all":
                    for event in list(held_events.values()):
                        event.set()
                    self.reply({"released": len(held_events)})
                else:
                    self.reply({"error": "unknown control path"}, 404)
            return

        if parsed.path.startswith("/art/") and args.art:
            art_file = (Path(args.art).expanduser().resolve() / Path(parsed.path).name)
            if art_file.is_file():
                raw = art_file.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)
            else:
                self.reply({"error": "unknown artwork"}, 404)
            return

        if parsed.path in ("/anime/ranking", "/manga/ranking") or parsed.path.startswith("/anime/season/"):
            params = parse_qs(parsed.query)
            media_type = "MANGA" if parsed.path.startswith("/manga") else "ANIME"
            result = self.search_page("myanimelist", "", media_type, 0, int(params.get("limit", ["40"])[0]))
            if result is None:
                return
            page, _ = result
            mapper = mal_anime_entry if media_type == "ANIME" else mal_manga_entry
            self.reply({"data": [{"node": mapper(entry)["node"]} for entry in page], "paging": {}})
            return

        if parsed.path in ("/anime", "/manga"):
            params = parse_qs(parsed.query)
            query = params.get("q", [""])[0]
            limit = int(params.get("limit", ["20"])[0])
            offset = int(params.get("offset", ["0"])[0])
            media_type = "ANIME" if parsed.path == "/anime" else "MANGA"
            result = self.search_page("myanimelist", query, media_type, offset, limit)
            if result is None:
                return
            page, has_next = result
            mapper = mal_anime_entry if media_type == "ANIME" else mal_manga_entry
            next_url = None
            if has_next:
                next_url = "http://127.0.0.1:18820" + parsed.path + "?" + urlencode({"q": query, "limit": limit, "offset": offset + len(page)})
            self.reply({"data": [{"node": mapper(entry)["node"]} for entry in page], "paging": {"next": next_url}})
            return

        if parsed.path == "/users/@me":
            self.control("identity", {"provider": "myanimelist"})
            with lock:
                should_fail = state.get("fail_next_identity") in ("myanimelist", "all")
                if should_fail:
                    state["fail_next_identity"] = None
                    persist()
            if should_fail:
                self.reply({"message": "fixture authentication required"}, 401)
                return
            account = dict(state["account"])
            if self.headers.get("Authorization", "").endswith("task21-wrong-identity-token"):
                account["id"] = 43
                account["name"] = "Task 21 mismatched fictional account"
            self.reply({"id": account["id"], "name": account["name"], "picture": None})
            return

        if parsed.path in ("/users/@me/animelist", "/users/@me/mangalist"):
            is_anime = parsed.path.endswith("animelist")
            collection = [e for e in (state["anime"] if is_anime else state["manga"]) if e.get("tracked", True)]
            params = parse_qs(parsed.query)
            offset = int(params.get("offset", ["0"])[0])
            page_size = max(1, int(state.get("page_size", 2)))
            page = collection[offset : offset + page_size]
            next_offset = offset + len(page)
            has_next = next_offset < len(collection)
            request_id = self.control(
                "collection_page",
                {"provider": "myanimelist", "list": "anime" if is_anime else "manga", "offset": offset, "limit": int(params.get("limit", ["0"])[0])},
            )
            self.hold_if_requested(request_id)
            delay = float(state.get("delay_next_collection_seconds", 0))
            with lock:
                state["delay_next_collection_seconds"] = 0
            if delay > 0:
                time.sleep(min(delay, 30))
            with lock:
                fail = state.get("fail_next_collection_page")
                should_fail = fail in ("myanimelist", "all") or (
                    isinstance(fail, dict)
                    and fail.get("provider") == "myanimelist"
                    and fail.get("list") == ("anime" if is_anime else "manga")
                    and fail.get("offset") == offset
                )
                if should_fail:
                    state["fail_next_collection_page"] = None
                    persist()
                    self.reply({"message": "fixture unavailable"}, 503)
                    return
            mapped = [mal_anime_entry(item) if is_anime else mal_manga_entry(item) for item in page]
            self.reply({"data": mapped, "paging": {"next": f"fixture-offset-{next_offset}" if has_next else None, "previous": None}})
            return

        if parsed.path.startswith("/anime/") or parsed.path.startswith("/manga/"):
            request_id = self.control("entry_read", {"provider": "myanimelist", "path": parsed.path})
            is_anime = parsed.path.startswith("/anime/")
            raw_id = parsed.path.rsplit("/", 1)[-1]
            source = state["anime"] if is_anime else state["manga"]
            entry = next((item for item in source if str(item["id"]) == raw_id), None)
            if entry is None:
                self.reply({"message": "not found"}, 404)
                return
            fields = parse_qs(parsed.query).get("fields", [""])[0]
            if "synopsis" in fields:
                mapper = mal_anime_entry if is_anime else mal_manga_entry
                self.reply(mapper(entry)["node"])
                return
            if not entry.get("tracked", True):
                self.reply({"id": entry["id"], "num_episodes": entry.get("episodes"), "num_chapters": entry.get("chapters"), "num_volumes": entry.get("volumes"), "my_list_status": None})
                return
            if is_anime:
                self.reply({"id": entry["id"], "num_episodes": entry["episodes"], "my_list_status": {"status": entry.get("status", "watching"), "num_episodes_watched": entry.get("progress", 0), "is_rewatching": False, "updated_at": iso_timestamp(entry["updated_at"])}})
            else:
                self.reply({"id": entry["id"], "num_chapters": entry["chapters"], "num_volumes": entry["volumes"], "my_list_status": {"status": entry.get("status", "reading"), "num_chapters_read": entry.get("chapters_read", 0), "num_volumes_read": entry.get("volumes_read", 0), "is_rereading": False, "updated_at": iso_timestamp(entry["updated_at"])}})
            return

        self.reply({"message": "not found"}, 404)

    def do_POST(self):
        global request_sequence
        if self.server.server_port == 18821:
            body = self.read_json()
            with lock:
                if self.path == "/configure":
                    changes = {}
                    for key in ("search_page_size", "fail_next_search", "delay_next_search_seconds", "page_size", "fail_next_collection_page", "fail_next_identity", "fail_next_mutation", "hold_next_collection_page", "delay_next_collection_seconds", "delay_next_mutation_seconds"):
                        if key in body:
                            state[key] = body[key]
                            changes[key] = body[key]
                    for entry_type in ("anime", "manga"):
                        if entry_type in body:
                            state[entry_type] = body[entry_type]
                            changes[entry_type] = {
                                "ids": [entry.get("id") for entry in body[entry_type]],
                                "fields": sorted({key for entry in body[entry_type] for key in entry if key != "id"}),
                            }
                    if "account" in body:
                        account = body["account"]
                        state["account"] = {
                            "id": int(account.get("id", state["account"]["id"])),
                            "name": str(account.get("name", state["account"]["name"])),
                        }
                        changes["account"] = ["id", "name"]
                    request_id = new_request_id()
                    requests.append({"id": request_id, "sequence": request_sequence, "method": "CONTROL", "path": "/configure", "operation": "configure", "detail": changes, "effects": list(changes), "recorded_at": time.time()})
                    persist()
                    self.reply({"id": request_id, "state": state})
                    return
                if self.path == "/reset":
                    for event in list(held_events.values()):
                        event.set()
                    held_events.clear()
                    state.clear()
                    state.update(default_state())
                    requests.clear()
                    request_sequence = 0
                    persist()
                    self.reply({"reset": True, "provider": "loopback-fixture"})
                    return
                if self.path == "/release":
                    request_id = body.get("request_id")
                    event = held_events.get(request_id)
                    if event is None:
                        self.reply({"error": "request is not held", "held": list(held_events)}, 404)
                    else:
                        event.set()
                        self.reply({"released": request_id})
                    return
                if self.path == "/clear-requests":
                    requests.clear()
                    state["effects"] = []
                    persist()
                    self.reply({"cleared": True})
                    return
            self.reply({"error": "unknown control path"}, 404)
            return

        if urlsplit(self.path).path == "/token":
            fields = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode())
            self.control(
                "token_exchange",
                {
                    "grant_type": fields.get("grant_type", [""])[0],
                    "field_names": sorted(fields),
                },
            )
            self.reply(
                {
                    "access_token": "task21-myanimelist-access",
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "refresh_token": "task21-myanimelist-refresh",
                },
            )
            return

        try:
            body = self.read_json()
        except (json.JSONDecodeError, UnicodeDecodeError):
            self.reply({"errors": [{"status": 400}]}, 400)
            return

        query = body.get("query", "")
        variables = body.get("variables", {})
        if "MioMediaSearch" in query or "MioMediaBrowse" in query:
            media_type = variables.get("type", "ANIME")
            limit = int(variables.get("perPage", 20))
            size = min(limit, max(1, int(state.get("search_page_size", limit))))
            offset = (int(variables.get("page", 1)) - 1) * size
            result = self.search_page("anilist", (variables.get("search") or ""), media_type, offset, limit)
            if result is None:
                return
            page, has_next = result
            media = [anilist_entry(entry, media_type)["media"] for entry in page]
            if "MioMediaBrowse" in query and variables.get("status"):
                media = [node for node in media if node["status"] == variables["status"]]
            for node in media:
                node.pop("airingSchedule", None)
            self.reply({"data": {"Page": {"pageInfo": {"hasNextPage": has_next}, "media": media}}})
            return

        if "MioMediaDetails" in query:
            media_id = int(variables.get("id", 0))
            for media_type in ("ANIME", "MANGA"):
                entry = next((entry for entry in page_entries(media_type) if entry["id"] == media_id), None)
                if entry is not None:
                    self.control("media_detail", {"provider": "anilist", "id": media_id})
                    node = anilist_entry(entry, media_type)["media"]
                    node["relations"] = {"nodes": []}
                    self.reply({"data": {"Media": node}})
                    return
            self.reply({"data": {"Media": None}})
            return

        if "MioViewer" in query:
            self.control("identity", {"provider": "anilist", "operation": "Viewer"})
            with lock:
                should_fail = state.get("fail_next_identity") in ("anilist", "all")
                if should_fail:
                    state["fail_next_identity"] = None
                    persist()
            if should_fail:
                self.reply({"data": None, "errors": [{"status": 401}]})
                return
            account = dict(state["account"])
            if self.headers.get("Authorization", "").endswith("task21-wrong-identity-token"):
                account["id"] = 43
                account["name"] = "Task 21 mismatched fictional account"
            self.reply({"data": {"Viewer": {"id": account["id"], "name": account["name"], "avatar": {"large": None, "medium": None}}}, "errors": None})
            return

        if "MioMediaListPage" in query:
            media_type = variables.get("type", "ANIME")
            page_number = int(variables.get("page", 1))
            entries = [e for e in page_entries(media_type) if e.get("tracked", True)]
            page_size = max(1, int(state.get("page_size", 2)))
            start = (page_number - 1) * page_size
            page = entries[start : start + page_size]
            has_next = start + len(page) < len(entries)
            request_id = self.control(
                "collection_page",
                {"provider": "anilist", "type": media_type, "page": page_number, "perPage": variables.get("perPage"), "userId": variables.get("userId")},
            )
            self.hold_if_requested(request_id)
            delay = float(state.get("delay_next_collection_seconds", 0))
            with lock:
                state["delay_next_collection_seconds"] = 0
                fail = state.get("fail_next_collection_page")
                should_fail = fail in ("anilist", "all") or (isinstance(fail, dict) and fail.get("provider") == "anilist" and fail.get("type") == media_type and fail.get("page") == page_number)
                if should_fail:
                    state["fail_next_collection_page"] = None
                    persist()
                    self.reply({"data": None, "errors": [{"status": 503}]})
                    return
            if delay > 0:
                time.sleep(min(delay, 30))
            self.reply({"data": {"Page": {"pageInfo": {"currentPage": page_number, "hasNextPage": has_next}, "mediaList": [anilist_entry(item, media_type) for item in page]}}, "errors": None})
            return

        if "MioSyncEntry" in query or "MioFindSyncEntry" in query:
            entry_id = int(variables.get("id", 0))
            entry = next((item for items in (state["anime"], state["manga"]) for item in items if item.get("tracked", True) and (("MioFindSyncEntry" in query and item["id"] == int(variables.get("mediaId", 0))) or ("MioFindSyncEntry" not in query and item["entry_id"] == entry_id))), None)
            self.control("entry_read", {"provider": "anilist", "entry_id": entry_id})
            if entry is None:
                self.reply({"data": {"MediaList": None}, "errors": None})
                return
            self.reply({"data": {"MediaList": {"id": entry["entry_id"], "userId": state["account"]["id"], "mediaId": entry["id"], "status": entry.get("status", "CURRENT"), "progress": entry.get("progress", entry.get("chapters_read", 0)), "progressVolumes": entry.get("volumes_read", 0), "updatedAt": entry["updated_at"], "media": {"id": entry["id"], "episodes": entry.get("episodes"), "chapters": entry.get("chapters"), "volumes": entry.get("volumes")}}}, "errors": None})
            return

        if "MioSaveMediaListEntry" in query:
            entry_id = variables.get("id")
            media_id = variables.get("mediaId")
            entry = next(
                (
                    item
                    for items in (state["anime"], state["manga"])
                    for item in items
                    if (entry_id is not None and item["entry_id"] == int(entry_id))
                    or (entry_id is None and media_id is not None and item["id"] == int(media_id))
                ),
                None,
            )
            if entry is None:
                self.reply({"data": {"SaveMediaListEntry": None}, "errors": None})
                return
            entry_id = entry["entry_id"]
            with lock:
                rejection_status = state.get("fail_next_mutation")
                if rejection_status is not None:
                    state["fail_next_mutation"] = None
                    persist()
            if rejection_status is not None:
                self.control(
                    "mutation_rejected",
                    {
                        "provider": "anilist",
                        "entry_id": entry_id,
                        "status": int(rejection_status),
                        "target_progress": variables.get("progress"),
                        "target_status": variables.get("status"),
                    },
                )
                self.reply({"data": {"SaveMediaListEntry": None}, "errors": [{"status": int(rejection_status)}]})
                return
            entry["tracked"] = True
            changed = {}
            for variable, field in (("progress", "progress"), ("progressVolumes", "volumes_read"), ("status", "status")):
                if variables.get(variable) is not None:
                    target = "chapters_read" if field == "progress" and "chapters_read" in entry else field
                    entry[target] = variables[variable]
                    changed[variable] = variables[variable]
            entry["updated_at"] = timestamp(time.time())
            request_id = self.control("mutation", {"provider": "anilist", "entry_id": entry_id, "changed_fields": changed}, [f"entry:{entry_id}"])
            with lock:
                delay = float(state.get("delay_next_mutation_seconds", 0))
                state["delay_next_mutation_seconds"] = 0
                persist()
            if delay > 0:
                time.sleep(min(delay, 30))
            self.reply({"data": {"SaveMediaListEntry": {"id": entry["entry_id"], "userId": state["account"]["id"], "mediaId": entry["id"], "status": entry.get("status", "CURRENT"), "progress": entry.get("progress", entry.get("chapters_read", 0)), "progressVolumes": entry.get("volumes_read", 0), "updatedAt": entry["updated_at"], "media": {"id": entry["id"], "episodes": entry.get("episodes"), "chapters": entry.get("chapters"), "volumes": entry.get("volumes")}}}, "errors": None})
            return

        if "MioDeleteMediaListEntry" in query:
            entry_id = int(variables.get("id", 0))
            with lock:
                rejection_status = state.get("fail_next_mutation")
                if rejection_status is not None:
                    state["fail_next_mutation"] = None
                    persist()
            if rejection_status is not None:
                self.control(
                    "mutation_rejected",
                    {"provider": "anilist", "entry_id": entry_id, "status": int(rejection_status)},
                )
                self.reply({"data": {"DeleteMediaListEntry": None}, "errors": [{"status": int(rejection_status)}]})
                return
            removed = False
            for collection in (state["anime"], state["manga"]):
                for item in collection:
                    if item["entry_id"] == entry_id:
                        removed = item.get("tracked", True)
                        item["tracked"] = False
            self.control("mutation", {"provider": "anilist", "entry_id": entry_id, "changed_fields": {"removed": True}}, [f"entry:{entry_id}"])
            persist()
            self.reply({"data": {"DeleteMediaListEntry": {"deleted": removed}}, "errors": None})
            return

        self.control("unknown_provider_operation", {"provider": "anilist"})
        self.reply({"data": None, "errors": [{"status": 400}]}, 400)

    def do_DELETE(self):
        parts = urlsplit(self.path).path.split("/")
        collection = state["anime"] if parts[1] == "anime" else state["manga"]
        entry = next((e for e in collection if str(e["id"]) == parts[2]), None)
        with lock:
            failure = state.get("fail_next_mutation")
            state["fail_next_mutation"] = None
        if failure:
            self.reply({"message": "fixture rejected removal"}, int(failure))
            return
        if entry: entry["tracked"] = False
        self.control("mutation", {"provider": "myanimelist", "entry_id": parts[2], "changed_fields": {"removed": True}})
        persist()
        self.reply({})

    def do_PATCH(self):
        if self.server.server_port != 18820:
            self.reply({"error": "unsupported control method"}, 405)
            return
        params = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode())
        parsed = urlsplit(self.path)
        is_anime = parsed.path.startswith("/anime/")
        raw_id = parsed.path.split("/")[2] if len(parsed.path.split("/")) > 2 else ""
        source = state["anime"] if is_anime else state["manga"]
        entry = next((item for item in source if str(item["id"]) == raw_id), None)
        if entry is None:
            self.reply({"message": "not found"}, 404)
            return
        with lock:
            rejection_status = state.get("fail_next_mutation")
            if rejection_status is not None:
                state["fail_next_mutation"] = None
                persist()
        if rejection_status is not None:
            self.control(
                "mutation_rejected",
                {
                    "provider": "myanimelist",
                    "entry_id": raw_id,
                    "status": int(rejection_status),
                    "target_progress": params.get("num_watched_episodes", [None])[0],
                    "target_status": params.get("status", [None])[0],
                },
            )
            self.reply({"message": "fixture rejected mutation"}, int(rejection_status))
            return
        entry["tracked"] = True
        changed = {}
        for field, target in (("num_watched_episodes", "progress"), ("num_chapters_read", "chapters_read"), ("num_volumes_read", "volumes_read"), ("status", "status")):
            if field in params:
                value = params[field][0]
                entry[target] = int(value) if field != "status" else value
                changed[field] = entry[target]
        entry["updated_at"] = timestamp(time.time())
        self.control("mutation", {"provider": "myanimelist", "entry_id": raw_id, "changed_fields": changed}, [f"entry:{raw_id}"])
        with lock:
            delay = float(state.get("delay_next_mutation_seconds", 0))
            state["delay_next_mutation_seconds"] = 0
            persist()
        if delay > 0:
            time.sleep(min(delay, 30))
        self.reply({"my_list_status": {"status": entry.get("status", "watching" if is_anime else "reading")}})


persist()
control_server = ThreadingHTTPServer(("127.0.0.1", 18821), Handler)
threading.Thread(target=control_server.serve_forever, name="task21-control", daemon=True).start()
provider_server = ThreadingHTTPServer(("127.0.0.1", 18820), Handler)
try:
    provider_server.serve_forever()
finally:
    for event in list(held_events.values()):
        event.set()
    provider_server.server_close()
    control_server.shutdown()
    control_server.server_close()
