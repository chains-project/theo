#!/usr/bin/env python3
"""
Reconcile actual scan outcomes from existing files — no re-running needed.

Cross-references:
  1. selected_packages.json  (all packages)
  2. checkpoint.json         (which were marked completed)
  3. reports/ directory      (which have actual report files)
  4. sensitive_api_usage.csv (which have real data vs all-False rows)
  5. version-history/        (which have version analysis reports)

Usage:
  python count_failures.py --output-dir /path/to/package-miner/outputs
"""

import argparse
import csv
import json
from pathlib import Path


def coord3(pkg):
    """3-part coordinate matching checkpoint.json format: groupId:artifactId:version"""
    return f"{pkg['groupId']}:{pkg['artifactId']}:{pkg['latestVersion']}"


def coord2(pkg):
    """2-part coordinate for version-history lookups: groupId:artifactId"""
    return f"{pkg['groupId']}:{pkg['artifactId']}"


def report_filename(pkg):
    """Report filename as written by PackageAnalyzer: groupId_artifactId_version-report.json"""
    return f"{pkg['groupId']}_{pkg['artifactId']}_{pkg['latestVersion']}-report.json"


def main():
    parser = argparse.ArgumentParser(description="Count actual scan failures from existing data.")
    parser.add_argument("--output-dir", "-o", required=True, help="Package-miner output directory")
    args = parser.parse_args()

    out = Path(args.output_dir)

    # 1. Load all selected packages
    pkg_file = out / "selected_packages.json"
    if not pkg_file.exists():
        print(f"ERROR: {pkg_file} not found")
        return
    with open(pkg_file) as f:
        all_packages = json.load(f)
    print(f"Total selected packages: {len(all_packages)}")

    # 2. Load checkpoint (plain list of "groupId:artifactId:version" strings)
    checkpoint_file = out / "checkpoint.json"
    completed = set()
    if checkpoint_file.exists():
        with open(checkpoint_file) as f:
            completed = set(json.load(f))
    print(f"Marked completed in checkpoint: {len(completed)}")

    not_started = []
    for pkg in all_packages:
        if coord3(pkg) not in completed:
            not_started.append(coord3(pkg))
    print(f"Never started / not in checkpoint: {len(not_started)}")

    # 3. Check which completed packages have actual report files
    reports_dir = out / "reports"
    has_report = set()
    empty_report = set()
    no_report = set()

    for pkg in all_packages:
        c = coord3(pkg)
        if c not in completed:
            continue
        report_file = reports_dir / report_filename(pkg)
        if report_file.exists() and report_file.stat().st_size > 0:
            has_report.add(c)
        elif report_file.exists():
            empty_report.add(c)
        else:
            no_report.add(c)

    print(f"\nAmong completed packages:")
    print(f"  Has non-empty report file:  {len(has_report)}")
    print(f"  Has empty report file (0b): {len(empty_report)}")
    print(f"  No report file at all:      {len(no_report)}")

    # 4. Cross-reference with CSV
    csv_file = out / "sensitive_api_usage.csv"
    csv_has_any_true = set()
    csv_all_false = set()
    if csv_file.exists():
        with open(csv_file) as f:
            reader = csv.reader(f)
            header = next(reader)
            api_cols_start = 3  # groupId, artifactId, version, then API columns
            for row in reader:
                c = f"{row[0]}:{row[1]}:{row[2]}"
                api_values = row[api_cols_start:]
                if any(v == "True" for v in api_values):
                    csv_has_any_true.add(c)
                else:
                    csv_all_false.add(c)
        print(f"\nIn CSV:")
        print(f"  Rows with at least one True: {len(csv_has_any_true)}")
        print(f"  Rows with all False:         {len(csv_all_false)}")

    # 5. Check version-history reports (keyed by groupId:artifactId, no version)
    vh_dir = out / "version-history"
    has_version_history = set()
    if vh_dir.exists():
        for f in vh_dir.glob("*-history.json"):
            stem = f.stem.replace("-history", "")
            parts = stem.split("_", 1)
            if len(parts) == 2:
                has_version_history.add(f"{parts[0]}:{parts[1]}")
    print(f"\nHas version-history file: {len(has_version_history)}")

    # 6. Final reconciliation
    true_failures = no_report | empty_report
    recovered = true_failures & csv_has_any_true
    still_failed = true_failures - csv_has_any_true

    # For version-history lookup, map 3-part coords to 2-part
    coord3_to_coord2 = {coord3(p): coord2(p) for p in all_packages}
    failed_but_has_vh = {c for c in still_failed if coord3_to_coord2.get(c, "") in has_version_history}
    failed_no_vh = still_failed - failed_but_has_vh

    print(f"\n{'='*60}")
    print(f"  RECONCILIATION SUMMARY")
    print(f"{'='*60}")
    print(f"  Total packages:                {len(all_packages)}")
    print(f"  Completed with report:         {len(has_report)}")
    print(f"  Completed, no report (failed): {len(true_failures)}")
    print(f"    - Already recovered (in CSV): {len(recovered)}")
    print(f"    - Still failed:               {len(still_failed)}")
    print(f"      - Has version-history data: {len(failed_but_has_vh)}")
    print(f"      - No data at all:           {len(failed_no_vh)}")
    print(f"  Not started:                   {len(not_started)}")
    print(f"{'='*60}")

    # 7. Dump lists for inspection
    dump_dir = out / "failure_audit"
    dump_dir.mkdir(exist_ok=True)

    for name, items in [
        ("still_failed", sorted(still_failed)),
        ("recovered_by_csv", sorted(recovered)),
        ("not_started", sorted(not_started)),
        ("failed_has_version_history", sorted(failed_but_has_vh)),
        ("failed_no_data", sorted(failed_no_vh)),
        ("all_false_in_csv", sorted(csv_all_false)),
    ]:
        path = dump_dir / f"{name}.txt"
        with open(path, "w") as f:
            f.write("\n".join(items) + "\n" if items else "")
        print(f"  Saved {path.name}: {len(items)} packages")

    # Disambiguate all-False CSV rows using report files
    legit_zero = csv_all_false & has_report
    silent_fail = csv_all_false & (no_report | empty_report)
    print(f"\nAll-False CSV rows breakdown:")
    print(f"  Legitimate zero (has report): {len(legit_zero)}")
    print(f"  Silent failure (no report):   {len(silent_fail)}")


if __name__ == "__main__":
    main()
