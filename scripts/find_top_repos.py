#!/usr/bin/env python3
"""
Find the top 420 GitHub repositories that depend on the most packages
from a given list of sensitive-API packages.

Usage:
    python3 scripts/find_top_repos.py <sensitive_packages.json> [--output top_repos.json] [--top 420] [--max-pages 50]

Input JSON format (same as selected_packages.json):
    [{"groupId": "org.apache.poi", "artifactId": "poi", "latestVersion": "5.2.0", "dependentReposCount": 75000}, ...]

Output JSON:
    [{"repo": "owner/name", "dependency_count": 5, "dependencies": ["org.apache.poi:poi", ...]}, ...]
"""

import argparse
import json
import sys
import time
import urllib.request
import urllib.error


API_BASE = "https://repos.ecosyste.ms/api/v1/usage/maven"
PER_PAGE = 100
RATE_LIMIT_SLEEP = 1.0  # seconds between API calls to stay under 5000/hr


def fetch_dependent_repos(package_coord, max_pages):
    """Fetch all GitHub repo full_names that depend on a given maven coordinate."""
    repos = set()
    page = 1
    while page <= max_pages:
        url = f"{API_BASE}/{package_coord}/dependencies?per_page={PER_PAGE}&page={page}&mailto=tulipgamage@gmail.com"
        try:
            req = urllib.request.Request(url, headers={"Accept": "application/json"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            print(f"  HTTP {e.code} for {package_coord} page {page}, stopping.", file=sys.stderr)
            break
        except Exception as e:
            print(f"  Error fetching {package_coord} page {page}: {e}", file=sys.stderr)
            break

        if not data:
            break

        for entry in data:
            repo = entry.get("repository")
            if repo and repo.get("full_name"):
                repos.add(repo["full_name"])

        if len(data) < PER_PAGE:
            break

        page += 1
        time.sleep(RATE_LIMIT_SLEEP)

    return repos


def main():
    parser = argparse.ArgumentParser(description="Find top N repos using the most sensitive-API packages")
    parser.add_argument("input_file", help="JSON file with sensitive-API packages (selected_packages.json format)")
    parser.add_argument("--output", "-o", default="top_repos.json", help="Output JSON file (default: top_repos.json)")
    parser.add_argument("--top", "-n", type=int, default=420, help="Number of top repos to select (default: 420)")
    parser.add_argument("--max-pages", type=int, default=50,
                        help="Max pages to fetch per package (default: 50, i.e. 5000 repos per package)")
    args = parser.parse_args()

    with open(args.input_file) as f:
        packages = json.load(f)

    coordinates = [f"{p['groupId']}:{p['artifactId']}" for p in packages]
    print(f"Loaded {len(coordinates)} packages with sensitive APIs.", file=sys.stderr)

    # repo_name -> set of package coordinates it depends on
    repo_deps = {}

    for i, coord in enumerate(coordinates, 1):
        print(f"[{i}/{len(coordinates)}] Fetching dependent repos for {coord}...", file=sys.stderr)
        repos = fetch_dependent_repos(coord, args.max_pages)
        print(f"  Found {len(repos)} repos.", file=sys.stderr)

        for repo_name in repos:
            if repo_name not in repo_deps:
                repo_deps[repo_name] = set()
            repo_deps[repo_name].add(coord)

        time.sleep(RATE_LIMIT_SLEEP)

    print(f"\nTotal unique repos found: {len(repo_deps)}", file=sys.stderr)

    # Rank by number of sensitive-API dependencies (descending), break ties by repo name
    ranked = sorted(repo_deps.items(), key=lambda x: (-len(x[1]), x[0]))

    top = ranked[:args.top]

    result = [
        {
            "repo": repo_name,
            "dependency_count": len(deps),
            "dependencies": sorted(deps)
        }
        for repo_name, deps in top
    ]

    with open(args.output, "w") as f:
        json.dump(result, f, indent=2)

    print(f"\nWrote top {len(result)} repos to {args.output}", file=sys.stderr)

    if result:
        print(f"  Max dependencies: {result[0]['dependency_count']}", file=sys.stderr)
        print(f"  Min dependencies: {result[-1]['dependency_count']}", file=sys.stderr)


if __name__ == "__main__":
    main()
