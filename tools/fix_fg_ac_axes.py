#!/usr/bin/env python3
"""Fix the axis orientation of AC3D (.ac) models converted from X-Plane OBJ.

Problem
-------
The osm2xp FlightGear scenery uses 3D building models.  The original X-Plane
OBJ8 models are authored Y-up ("positive Y points up", per the OBJ8 spec), which
is also the native AC3D / FlightGear convention.  However models converted with
ModelConverterX end up Z-up (the model lies on its side in FlightGear).

This script "finishes the import" by applying the missing rotation of -90 deg
about the X axis to every AC3D vertex:

    (x, y, z)  ->  (x, z, -y)

Running the script twice would rotate the model again and break it, so each
converted file gets a marker comment line (see below) and already-converted
files are skipped.

Usage
-----
    python fix_fg_ac_axes.py [root_dir] [--force] [--dry-run] [--verbose]

    root_dir  directory scanned recursively for *.ac files
              (default: osm2xp_additions/flightgear)
    --force   re-apply the transform even if the marker is present
    --dry-run report what would change without writing files
    --verbose print a line per processed file
"""
import argparse
import os
import re
import sys

MARKER = "* osm2xp_fg_axis_fixed (X-Plane Y-up -> AC3D Y-up)"
MARKER_TOKEN = "osm2xp_fg_axis_fixed"

VERTEX_RE = re.compile(r"^\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)\s+(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)\s+(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)\s*$")


def has_marker(text):
    return MARKER_TOKEN in text


def rotate_point(x, y, z):
    """Rotation of -90 deg about the X axis: Z-up -> Y-up."""
    return x, z, -y


def format_number(value):
    return "%.6f" % value


def transform_vertex_line(line):
    m = VERTEX_RE.match(line)
    if not m:
        return None
    x, y, z = (float(v) for v in m.groups())
    nx, ny, nz = rotate_point(x, y, z)
    indent = line[: len(line) - len(line.lstrip())]
    return "%s%s %s %s" % (indent, format_number(nx), format_number(ny), format_number(nz))


def rotate_matrix(r):
    """Left-multiply a 3x3 row-major rotation matrix (rot line) by the fix rotation."""
    # M = [[1,0,0],[0,0,1],[0,-1,0]] row-major; result = M * r (row-major product)
    r00, r01, r02, r10, r11, r12, r20, r21, r22 = r
    return [r00, r01, r02,
            r20, r21, r22,
            -r10, -r11, -r12]


def process_file(path, force=False, verbose=False):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        text = f.read()

    if has_marker(text) and not force:
        return "skipped (marker present)"

    lines = text.splitlines()
    out = []
    pending_verts = 0          # number of vertex lines still to read inside numvert block
    pending_data = 0           # number of raw characters to consume after a 'data' line
    changed = False

    i = 0
    while i < len(lines):
        line = lines[i]

        if pending_verts > 0:
            new_line = transform_vertex_line(line)
            if new_line is not None:
                out.append(new_line)
                changed = changed or (new_line != line)
            else:
                out.append(line)
            pending_verts -= 1
            i += 1
            continue

        if pending_data > 0:
            out.append(line)
            if len(line) >= pending_data:
                pending_data = 0
            else:
                pending_data -= len(line)
            i += 1
            continue

        stripped = line.strip()
        m = re.match(r"^numvert\s+(\d+)", stripped, re.IGNORECASE)
        if m:
            pending_verts = int(m.group(1))
            out.append(line)
            i += 1
            continue

        if stripped.startswith("data "):
            # 'data N' is followed by N raw characters, possibly spanning lines
            try:
                n = int(stripped.split()[1])
            except (IndexError, ValueError):
                n = 0
            rest = line[len(stripped):]
            consumed = len(rest)
            if n > consumed:
                pending_data = n - consumed
            out.append(line)
            i += 1
            continue

        m = re.match(r"^loc\s+", stripped, re.IGNORECASE)
        if m:
            parts = line.split()
            if len(parts) >= 4:
                try:
                    x, y, z = float(parts[1]), float(parts[2]), float(parts[3])
                except ValueError:
                    out.append(line)
                    i += 1
                    continue
                nx, ny, nz = rotate_point(x, y, z)
                out.append(" ".join([parts[0], format_number(nx), format_number(ny), format_number(nz)]))
                changed = True
                i += 1
                continue

        m = re.match(r"^rot\s+", stripped, re.IGNORECASE)
        if m:
            parts = line.split()
            if len(parts) >= 10:
                try:
                    r = [float(p) for p in parts[1:10]]
                except ValueError:
                    out.append(line)
                    i += 1
                    continue
                nr = rotate_matrix(r)
                out.append(" ".join([parts[0]] + [format_number(v) for v in nr] + parts[10:]))
                changed = True
                i += 1
                continue

        out.append(line)
        i += 1

    if not changed:
        return "no change"

    new_text = "\n".join(out)
    if MARKER not in new_text:
        lines_out = new_text.splitlines()
        lines_out.insert(1, MARKER)
        new_text = "\n".join(lines_out)
    new_text += "\n"

    if verbose:
        print("converted: %s" % path)
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_text)
    return "converted"


def main():
    parser = argparse.ArgumentParser(description="Fix Y/Z axis of AC3D models converted from X-Plane OBJ")
    parser.add_argument("root", nargs="?", default=None, help="directory to scan recursively (default: osm2xp_additions/flightgear)")
    parser.add_argument("--force", action="store_true", help="re-apply transform even if marker present")
    parser.add_argument("--dry-run", action="store_true", help="only report what would change")
    parser.add_argument("--verbose", action="store_true", help="print a line per file")
    args = parser.parse_args()

    if args.root is None:
        args.root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "osm2xp_additions", "flightgear")
    root = os.path.abspath(args.root)
    if not os.path.isdir(root):
        print("error: directory not found: %s" % root, file=sys.stderr)
        return 1

    stats = {"converted": 0, "skipped": 0, "no_change": 0, "error": 0}
    for dirpath, _dirnames, filenames in os.walk(root):
        for name in sorted(filenames):
            if not name.lower().endswith(".ac"):
                continue
            path = os.path.join(dirpath, name)
            if args.dry_run:
                with open(path, "r", encoding="utf-8", errors="replace") as f:
                    text = f.read()
                if has_marker(text) and not args.force:
                    stats["skipped"] += 1
                else:
                    stats["converted"] += 1
                    print("would convert: %s" % path)
                continue
            try:
                result = process_file(path, force=args.force, verbose=args.verbose)
                if result == "converted":
                    stats["converted"] += 1
                elif result == "skipped (marker present)":
                    stats["skipped"] += 1
                elif result == "no change":
                    stats["no_change"] += 1
                else:
                    stats["converted"] += 1
            except Exception as exc:  # noqa: BLE001
                stats["error"] += 1
                print("error: %s: %s" % (path, exc), file=sys.stderr)

    print("done. converted=%d skipped=%d no_change=%d error=%d" % (
        stats["converted"], stats["skipped"], stats["no_change"], stats["error"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())