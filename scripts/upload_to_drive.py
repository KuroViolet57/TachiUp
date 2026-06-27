#!/usr/bin/env python3
"""Upload a build artifact to Google Drive.

Layout: <root>/TachiUpi/<iteration>/<file>

Credentials are read from environment variables so they are never committed:
  GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET, GDRIVE_REFRESH_TOKEN

Usage:
  python3 upload_to_drive.py <file> <iteration> [main_folder]
"""
import os
import sys
import json
import requests

CA = "/root/.ccr/ca-bundle.crt"
MAIN_FOLDER_DEFAULT = "TachiUpi"
FOLDER_MIME = "application/vnd.google-apps.folder"


def session():
    s = requests.Session()
    if os.path.exists(CA):
        s.verify = CA
    return s


def access_token(s):
    r = s.post(
        "https://oauth2.googleapis.com/token",
        data={
            "client_id": os.environ["GDRIVE_CLIENT_ID"],
            "client_secret": os.environ["GDRIVE_CLIENT_SECRET"],
            "refresh_token": os.environ["GDRIVE_REFRESH_TOKEN"],
            "grant_type": "refresh_token",
        },
        timeout=60,
    )
    r.raise_for_status()
    return r.json()["access_token"]


def find_folder(s, headers, name, parent):
    q = (
        f"name = '{name}' and mimeType = '{FOLDER_MIME}' "
        f"and '{parent}' in parents and trashed = false"
    )
    r = s.get(
        "https://www.googleapis.com/drive/v3/files",
        headers=headers,
        params={"q": q, "fields": "files(id,name)", "spaces": "drive"},
        timeout=60,
    )
    r.raise_for_status()
    files = r.json().get("files", [])
    return files[0]["id"] if files else None


def ensure_folder(s, headers, name, parent):
    existing = find_folder(s, headers, name, parent)
    if existing:
        return existing
    body = {"name": name, "mimeType": FOLDER_MIME, "parents": [parent]}
    r = s.post(
        "https://www.googleapis.com/drive/v3/files",
        headers=headers,
        json=body,
        params={"fields": "id"},
        timeout=60,
    )
    r.raise_for_status()
    return r.json()["id"]


def upload(s, headers, path, parent):
    name = os.path.basename(path)
    meta = {"name": name, "parents": [parent]}
    with open(path, "rb") as fh:
        data = fh.read()
    files = {
        "metadata": ("metadata", json.dumps(meta), "application/json; charset=UTF-8"),
        "file": (name, data, "application/vnd.android.package-archive"),
    }
    r = s.post(
        "https://www.googleapis.com/upload/drive/v3/files",
        headers=headers,
        params={"uploadType": "multipart", "fields": "id,name,webViewLink,size"},
        files=files,
        timeout=300,
    )
    r.raise_for_status()
    return r.json()


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    path = sys.argv[1]
    iteration = sys.argv[2]
    main_folder = sys.argv[3] if len(sys.argv) > 3 else MAIN_FOLDER_DEFAULT

    s = session()
    token = access_token(s)
    headers = {"Authorization": f"Bearer {token}"}

    main_id = ensure_folder(s, headers, main_folder, "root")
    iter_id = ensure_folder(s, headers, iteration, main_id)
    result = upload(s, headers, path, iter_id)

    print(json.dumps({
        "main_folder": main_folder,
        "main_folder_id": main_id,
        "iteration_folder": iteration,
        "iteration_folder_id": iter_id,
        "uploaded": result,
    }, indent=2))


if __name__ == "__main__":
    main()
