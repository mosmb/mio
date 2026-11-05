#!/usr/bin/env python3
"""Loopback-only Task 20 provider fixture. Never connects to external services."""
import argparse
import json
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs

parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True)
args = parser.parse_args()
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
lock = threading.RLock()
state = {'progress': 8, 'status': 'CURRENT', 'total': 12, 'score': 7, 'tags': 'untouched', 'other': 3, 'failures': 0, 'delay': 0, 'updated': int(time.time())}
if (out / 'remote.json').exists():
    state = json.loads((out / 'remote.json').read_text())
requests = json.loads((out / 'requests.json').read_text()) if (out / 'requests.json').exists() else []

def save():
    (out / 'remote.json').write_text(json.dumps(state, indent=2))
    (out / 'requests.json').write_text(json.dumps(requests, indent=2))

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, value, code=200):
        raw = json.dumps(value).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        try:
            self.wfile.write(raw)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        if self.server.server_port == 18821:
            with lock:
                self.reply({'state': state, 'requests': requests})
            return
        if '/users/@me' in self.path:
            self.reply({'id': 42, 'name': 'Task 20 fixture'})
            return
        with lock:
            requests.append({'method': 'GET', 'path': self.path, 'at': time.time()})
            save()
            self.reply({'id': 101, 'num_episodes': state['total'], 'num_chapters': state['total'], 'num_volumes': state['total'], 'my_list_status': {'status': 'completed' if state['status'] == 'COMPLETED' else 'watching', 'num_episodes_watched': state['progress'], 'num_chapters_read': state['progress'], 'num_volumes_read': state['other'], 'is_rewatching': False, 'updated_at': datetime.fromtimestamp(state['updated'], timezone.utc).isoformat(), 'score': state['score'], 'tags': state['tags']}})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or '{}')
        if self.server.server_port == 18821:
            with lock:
                if body.pop('reset', False):
                    requests.clear()
                state.update(body)
                state['updated'] = int(time.time())
                save()
                self.reply(state)
            return
        if 'Viewer' in body.get('query', ''):
            self.reply({'data': {'Viewer': {'id': 42, 'name': 'Task 20 fixture'}}})
            return
        variables = body.get('variables', {})
        mutation = 'SaveMediaListEntry' in body.get('query', '')
        with lock:
            requests.append({'method': 'POST', 'mutation': mutation, 'variables': variables, 'at': time.time()})
            if mutation and state['failures']:
                state['failures'] -= 1
                save()
                self.reply({}, 503)
                return
            if mutation:
                state['progress'] = variables.get('progress', state['progress'])
                state['other'] = variables.get('progressVolumes', state['other'])
                state['status'] = variables.get('status', state['status'])
                state['updated'] = int(time.time())
            save()
            entry = {'id': 201, 'userId': 42, 'mediaId': 101, 'progress': state['progress'], 'progressVolumes': state['other'], 'status': state['status'], 'updatedAt': state['updated'], 'media': {'id': 101, 'episodes': state['total'], 'chapters': state['total'], 'volumes': state['total']}}
            delay = state['delay'] if mutation else 0
        time.sleep(delay)
        self.reply({'data': {'SaveMediaListEntry' if mutation else 'MediaList': entry}})

    def do_PATCH(self):
        body = parse_qs(self.rfile.read(int(self.headers.get('Content-Length', 0))).decode())
        with lock:
            requests.append({'method': 'PATCH', 'fields': body, 'at': time.time()})
            if state['failures']:
                state['failures'] -= 1
                save()
                self.reply({}, 503)
                return
            for key in ('num_watched_episodes', 'num_chapters_read'):
                if key in body:
                    state['progress'] = int(body[key][0])
            if 'num_volumes_read' in body:
                state['other'] = int(body['num_volumes_read'][0])
            if 'status' in body:
                state['status'] = 'COMPLETED' if body['status'][0] == 'completed' else 'CURRENT'
            state['updated'] = int(time.time())
            save()
            delay = state['delay']
        time.sleep(delay)
        self.reply({'status': 'watching'})

save()
control = ThreadingHTTPServer(('127.0.0.1', 18821), Handler)
threading.Thread(target=control.serve_forever, daemon=True).start()
ThreadingHTTPServer(('127.0.0.1', 18820), Handler).serve_forever()
