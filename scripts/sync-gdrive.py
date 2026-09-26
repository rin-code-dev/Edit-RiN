#!/usr/bin/env python3
"""
Edit:RiN Google Drive Append-Only Sync Script
--------------------------------------------
- Synchronizes ~/Documents/EditRiNデータ/ to Google Drive (rin55225@gmail.com).
- Append-Only policy: UPLOADS and UPDATES only.
- NEVER deletes any file on Google Drive even if deleted locally.
"""

import os
import sys
import subprocess
from pathlib import Path

ACCOUNT = "rin55225@gmail.com"
LOCAL_ROOT = Path.home() / "Documents" / "EditRiNデータ"

def run_cmd(cmd):
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.returncode, res.stdout.strip(), res.stderr.strip()

def ensure_mounted():
    code, stdout, _ = run_cmd(["gio", "mount", "-l"])
    if f"google-drive://{ACCOUNT}/" not in stdout:
        run_cmd(["gio", "mount", f"google-drive://{ACCOUNT}/"])

def get_drive_root():
    ensure_mounted()
    code, stdout, _ = run_cmd(["gio", "list", f"google-drive://{ACCOUNT}/"])
    if code != 0:
        raise RuntimeError(f"Failed to access Google Drive for {ACCOUNT}")
    for item in stdout.splitlines():
        item = item.strip()
        if item and not item.startswith("GVfs"):
            # This is the My Drive root ID
            return f"google-drive://{ACCOUNT}/{item}"
    raise RuntimeError("Could not find My Drive root directory ID")

def get_children(parent_uri):
    code, stdout, _ = run_cmd(["gio", "list", parent_uri])
    if code != 0 or not stdout:
        return {}
    children = {}
    for item_id in stdout.splitlines():
        item_id = item_id.strip()
        if not item_id:
            continue
        child_uri = f"{parent_uri}/{item_id}"
        c_code, c_out, _ = run_cmd(["gio", "info", "-a", "standard::display-name,standard::type,standard::size", child_uri])
        if c_code == 0:
            name, item_type, size = "", "", 0
            for line in c_out.splitlines():
                line = line.strip()
                if "standard::display-name:" in line:
                    name = line.split("standard::display-name:", 1)[1].strip()
                elif "standard::type:" in line:
                    item_type = line.split("standard::type:", 1)[1].strip()
                elif "standard::size:" in line:
                    try:
                        size = int(line.split("standard::size:", 1)[1].strip())
                    except ValueError:
                        size = 0
            if name:
                children[name] = {"id": item_id, "uri": child_uri, "type": item_type, "size": size}
    return children

def get_or_create_remote_dir(parent_uri, dir_name):
    children = get_children(parent_uri)
    if dir_name in children and children[dir_name]["type"] in ["2", "directory"]:
        return children[dir_name]["uri"]
    # Create directory
    target_uri = f"{parent_uri}/{dir_name}"
    run_cmd(["gio", "mkdir", target_uri])
    # Re-fetch children to get newly created ID
    new_children = get_children(parent_uri)
    if dir_name in new_children:
        return new_children[dir_name]["uri"]
    return target_uri

def sync_folder(local_path, remote_uri, rel_prefix=""):
    uploaded = 0
    updated = 0
    skipped = 0

    remote_children = get_children(remote_uri)

    for entry in sorted(os.scandir(local_path), key=lambda e: e.name):
        rel_item = f"{rel_prefix}/{entry.name}" if rel_prefix else entry.name
        if entry.is_dir():
            sub_remote_uri = get_or_create_remote_dir(remote_uri, entry.name)
            u, up, s = sync_folder(entry.path, sub_remote_uri, rel_item)
            uploaded += u
            updated += up
            skipped += s
        elif entry.is_file():
            local_size = entry.stat().st_size
            if entry.name in remote_children:
                rem_info = remote_children[entry.name]
                if rem_info["size"] == local_size:
                    skipped += 1
                    continue
                else:
                    print(f"  [UPDATE] {rel_item} ({local_size} bytes)")
                    dest_uri = f"{remote_uri}/{entry.name}"
                    code, _, err = run_cmd(["gio", "copy", entry.path, dest_uri])
                    if code == 0:
                        updated += 1
                    else:
                        print(f"    Failed: {err}", file=sys.stderr)
            else:
                print(f"  [UPLOAD] {rel_item} ({local_size} bytes)")
                dest_uri = f"{remote_uri}/{entry.name}"
                code, _, err = run_cmd(["gio", "copy", entry.path, dest_uri])
                if code == 0:
                    uploaded += 1
                else:
                    print(f"    Failed: {err}", file=sys.stderr)

    return uploaded, updated, skipped

def main():
    if not LOCAL_ROOT.exists():
        LOCAL_ROOT.mkdir(parents=True, exist_ok=True)
        for sub in ["デバッグ", "リリース", "プロジェクト"]:
            (LOCAL_ROOT / sub).mkdir(exist_ok=True)

    print(f"==> Google Drive Append-Only Sync: {ACCOUNT}")
    print(f"    Local:  {LOCAL_ROOT}")
    
    try:
        drive_root_uri = get_drive_root()
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    # Find or create EditRiNデータ
    editrin_remote_uri = get_or_create_remote_dir(drive_root_uri, "EditRiNデータ")
    print(f"    Remote: EditRiNデータ")
    print("------------------------------------------------------------")

    u, up, s = sync_folder(LOCAL_ROOT, editrin_remote_uri)

    print("------------------------------------------------------------")
    print(f"==> Complete: {u} uploaded, {up} updated, {s} up-to-date (0 deleted - Protected)")

if __name__ == "__main__":
    main()
