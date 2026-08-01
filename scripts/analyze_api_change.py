#!/usr/bin/env python3
"""
Analyze why a sensitive API access changed between two versions of a Maven package.

Given a library (groupId:artifactId), two versions, and a sensitive API,
this script gathers evidence from multiple sources to help classify the
nature of the change (feature addition, malicious update, vulnerability fix, etc.)

Data sources:
  1. Theo's static analysis reports (call paths)
  2. GitHub commits between version tags
  3. OSV.dev vulnerability database
  4. Maven Central POM metadata / changelogs

Usage:
  python analyze_api_change.py \
    --group-id com.fasterxml.jackson.core \
    --artifact-id jackson-databind \
    --from-version 2.14.0 \
    --to-version 2.15.0 \
    --sensitive-api "java.lang.reflect.Method.invoke" \
    --output-dir /path/to/package-miner/output

  # Or point directly to report files:
  python analyze_api_change.py \
    --from-report /path/to/v1/package-static-report.json \
    --to-report /path/to/v2/package-static-report.json \
    --sensitive-api "java.lang.reflect.Method.invoke"
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional



# Data classes


@dataclass
class CallPath:
    entry_point: str
    sensitive_api: str
    full_path: list[str]
    access_type: str  # DIRECT or INDIRECT
    dependencies: list[str] = field(default_factory=list)


@dataclass
class VersionCallPaths:
    version: str
    paths: list[CallPath]

    @property
    def entry_points(self):
        return {p.entry_point for p in self.paths}


@dataclass
class GitCommit:
    sha: str
    message: str
    author: str
    date: str
    files_changed: list[str] = field(default_factory=list)


@dataclass
class Vulnerability:
    id: str
    summary: str
    severity: str
    affected_versions: str
    references: list[str] = field(default_factory=list)


@dataclass
class ChangeAnalysis:
    group_id: str
    artifact_id: str
    from_version: str
    to_version: str
    sensitive_api: str
    change_type: str  # ADDED, REMOVED, MODIFIED, UNCHANGED
    from_paths: list[CallPath]
    to_paths: list[CallPath]
    added_entry_points: set
    removed_entry_points: set
    commits: list[GitCommit]
    vulnerabilities: list[Vulnerability]
    signals: dict = field(default_factory=dict)



# 1. Parse Theo static analysis reports


def parse_package_static_report(report_path: str, target_api: str) -> list[CallPath]:
    """Parse a package-static-report.json and extract call paths for the target API."""
    with open(report_path) as f:
        report = json.load(f)

    paths = []

    for access in report.get("directAccesses", []):
        if _api_matches(access.get("sensitiveAPI", ""), target_api):
            paths.append(CallPath(
                entry_point=access.get("entryPoint", ""),
                sensitive_api=access.get("sensitiveAPI", ""),
                full_path=access.get("fullPath", []),
                access_type="DIRECT",
            ))

    for access in report.get("indirectAccesses", []):
        if _api_matches(access.get("sensitiveAPI", ""), target_api):
            paths.append(CallPath(
                entry_point=access.get("entryPoint", ""),
                sensitive_api=access.get("sensitiveAPI", ""),
                full_path=access.get("fullPath", []),
                access_type="INDIRECT",
                dependencies=access.get("dependencies", []),
            ))

    return paths


def _api_matches(api_str: str, target: str) -> bool:
    """Flexible matching: 'java.lang.reflect.Method.invoke' matches target."""
    return target in api_str or api_str in target



# 2. GitHub commit analysis


GITHUB_API = "https://api.github.com"


def _github_headers():
    headers = {"Accept": "application/vnd.github.v3+json"}
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"token {token}"
    return headers


def _github_get(url: str) -> Optional[dict]:
    req = urllib.request.Request(url, headers=_github_headers())
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read())
    except Exception as e:
        print(f"  [warn] GitHub API error: {e}", file=sys.stderr)
        return None


def resolve_github_repo(group_id: str, artifact_id: str, output_dir: str = None) -> Optional[str]:
    """Try to find the GitHub repo URL for this package."""
    # 1. Check Theo's SCM extraction output
    if output_dir:
        scm_file = Path(output_dir) / "packages_with_github_scm.json"
        if scm_file.exists():
            with open(scm_file) as f:
                scm_data = json.load(f)
            coord = f"{group_id}:{artifact_id}"
            for entry in scm_data:
                if entry.get("coordinate", "") == coord or \
                   (entry.get("groupId") == group_id and entry.get("artifactId") == artifact_id):
                    url = entry.get("githubUrl") or entry.get("scmUrl", "")
                    if "github.com" in url:
                        return _normalize_github_url(url)

    # 2. Fetch POM from Maven Central and parse SCM tag
    pom_url = (
        f"https://repo1.maven.org/maven2/"
        f"{group_id.replace('.', '/')}/{artifact_id}/"
        f"maven-metadata.xml"
    )
    # We'll try the latest version's POM
    return _extract_github_from_maven_central(group_id, artifact_id)


def _extract_github_from_maven_central(group_id: str, artifact_id: str) -> Optional[str]:
    """Fetch the POM from Maven Central and extract the GitHub URL."""
    metadata_url = (
        f"https://repo1.maven.org/maven2/"
        f"{group_id.replace('.', '/')}/{artifact_id}/maven-metadata.xml"
    )
    try:
        req = urllib.request.Request(metadata_url)
        with urllib.request.urlopen(req, timeout=10) as resp:
            tree = ET.parse(resp)
        latest = tree.find(".//latest")
        if latest is None:
            latest = tree.find(".//release")
        if latest is None:
            return None
        version = latest.text

        pom_url = (
            f"https://repo1.maven.org/maven2/"
            f"{group_id.replace('.', '/')}/{artifact_id}/{version}/"
            f"{artifact_id}-{version}.pom"
        )
        req = urllib.request.Request(pom_url)
        with urllib.request.urlopen(req, timeout=10) as resp:
            pom_text = resp.read().decode("utf-8")

        # Strip namespaces for simpler parsing
        pom_text = re.sub(r'\sxmlns="[^"]+"', '', pom_text, count=1)
        root = ET.fromstring(pom_text)
        scm = root.find("scm")
        if scm is not None:
            for tag in ["url", "connection", "developerConnection"]:
                elem = scm.find(tag)
                if elem is not None and elem.text and "github.com" in elem.text:
                    return _normalize_github_url(elem.text)
    except Exception as e:
        print(f"  [warn] Maven Central POM fetch failed: {e}", file=sys.stderr)
    return None


def _normalize_github_url(raw: str) -> Optional[str]:
    m = re.search(r"github\.com[/:]([A-Za-z0-9_.\-]+)/([A-Za-z0-9_.\-]+?)(?:\.git)?(?:/.*)?$", raw)
    if m:
        return f"https://github.com/{m.group(1)}/{m.group(2)}"
    return None


def _guess_tag_names(version: str, owner: str, repo: str) -> list[str]:
    """Generate candidate tag names for a Maven version."""
    base = [
        f"v{version}", version,
        f"{repo}-{version}",
        f"release-{version}",
        f"rel/{version}",
    ]
    # Some projects use artifact-version pattern
    return base


def find_version_tag(owner: str, repo: str, version: str) -> Optional[str]:
    """Find the actual git tag for a given version."""
    candidates = _guess_tag_names(version, owner, repo)

    # Fetch tags and match
    page = 1
    all_tags = []
    while page <= 5:
        data = _github_get(f"{GITHUB_API}/repos/{owner}/{repo}/tags?per_page=100&page={page}")
        if not data:
            break
        all_tags.extend(data)
        if len(data) < 100:
            break
        page += 1
        time.sleep(0.5)

    tag_names = {t["name"] for t in all_tags}

    for candidate in candidates:
        if candidate in tag_names:
            return candidate

    # Fuzzy: find tags containing the version string
    for t in tag_names:
        if version in t:
            return t

    return None


def fetch_commits_between_tags(owner: str, repo: str, from_tag: str, to_tag: str,
                               max_commits: int = 100) -> list[GitCommit]:
    """Fetch commits between two tags via GitHub compare API."""
    url = f"{GITHUB_API}/repos/{owner}/{repo}/compare/{from_tag}...{to_tag}"
    data = _github_get(url)
    if not data or "commits" not in data:
        return []

    commits = []
    for c in data["commits"][:max_commits]:
        commit_info = c.get("commit", {})
        files = [f["filename"] for f in c.get("files", [])] if "files" in c else []
        commits.append(GitCommit(
            sha=c.get("sha", "")[:12],
            message=commit_info.get("message", "").split("\n")[0],
            author=commit_info.get("author", {}).get("name", ""),
            date=commit_info.get("author", {}).get("date", ""),
            files_changed=files,
        ))

    return commits


def fetch_commit_details(owner: str, repo: str, sha: str) -> Optional[dict]:
    """Fetch full commit details including file diffs."""
    return _github_get(f"{GITHUB_API}/repos/{owner}/{repo}/commits/{sha}")



# 3. Vulnerability database lookup (OSV.dev)


def query_osv(group_id: str, artifact_id: str,
              from_version: str, to_version: str) -> list[Vulnerability]:
    """Query OSV.dev for known vulnerabilities affecting this package version range."""
    vulns = []

    # Query for the from_version (vulnerabilities that existed before the update)
    payload = json.dumps({
        "package": {
            "name": f"{group_id}:{artifact_id}",
            "ecosystem": "Maven"
        },
        "version": from_version,
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://api.osv.dev/v1/query",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read())
    except Exception as e:
        print(f"  [warn] OSV query failed: {e}", file=sys.stderr)
        return vulns

    for v in data.get("vulns", []):
        severity = "UNKNOWN"
        for s in v.get("severity", []):
            if s.get("type") == "CVSS_V3":
                severity = s.get("score", "UNKNOWN")
                break

        affected_str = ""
        for aff in v.get("affected", []):
            for r in aff.get("ranges", []):
                events = r.get("events", [])
                introduced = next((e["introduced"] for e in events if "introduced" in e), "?")
                fixed = next((e["fixed"] for e in events if "fixed" in e), "unfixed")
                affected_str += f"[{introduced}, {fixed}) "

        refs = [r.get("url", "") for r in v.get("references", []) if r.get("url")]

        vulns.append(Vulnerability(
            id=v.get("id", ""),
            summary=v.get("summary", v.get("details", "")[:200]),
            severity=str(severity),
            affected_versions=affected_str.strip(),
            references=refs[:5],
        ))

    return vulns



# 4. Signal extraction and heuristic classification


SECURITY_KEYWORDS = {
    "cve", "vulnerability", "security", "exploit", "injection", "xss",
    "xxe", "ssrf", "rce", "deserialization", "bypass", "privilege",
    "escalation", "dos", "denial", "patch", "advisory", "ghsa",
}

FIX_KEYWORDS = {
    "fix", "fixed", "fixes", "bugfix", "hotfix", "patch", "resolve",
    "resolved", "repair", "correct", "correction",
}

FEATURE_KEYWORDS = {
    "add", "added", "feature", "implement", "support", "introduce",
    "new", "enable", "enhance", "enhancement", "improvement",
}

REFACTOR_KEYWORDS = {
    "refactor", "cleanup", "clean up", "simplify", "reorganize",
    "restructure", "rename", "extract", "move", "migrate", "deprecate",
    "remove unused", "dead code",
}

DEPENDENCY_KEYWORDS = {
    "upgrade", "update", "bump", "dependency", "dependencies",
    "transitive", "classpath",
}


def classify_commit_message(message: str) -> dict[str, float]:
    """Score a commit message against keyword categories. Returns category->score."""
    msg_lower = message.lower()
    tokens = set(re.findall(r'[a-z]+', msg_lower))
    scores = {}
    for name, keywords in [
        ("security_fix", SECURITY_KEYWORDS),
        ("bug_fix", FIX_KEYWORDS),
        ("feature", FEATURE_KEYWORDS),
        ("refactor", REFACTOR_KEYWORDS),
        ("dependency_update", DEPENDENCY_KEYWORDS),
    ]:
        hits = tokens & keywords
        scores[name] = len(hits) / max(len(keywords), 1)
    return scores


def extract_signals(analysis: ChangeAnalysis) -> dict:
    """Extract classification signals from all gathered evidence."""
    signals = {
        "has_known_cve": len(analysis.vulnerabilities) > 0,
        "cve_ids": [v.id for v in analysis.vulnerabilities],
        "change_type": analysis.change_type,
        "num_added_entry_points": len(analysis.added_entry_points),
        "num_removed_entry_points": len(analysis.removed_entry_points),
        "commit_count": len(analysis.commits),
    }

    # Aggregate commit classification
    category_scores = {
        "security_fix": 0.0, "bug_fix": 0.0, "feature": 0.0,
        "refactor": 0.0, "dependency_update": 0.0,
    }
    security_commits = []
    for commit in analysis.commits:
        scores = classify_commit_message(commit.message)
        for cat, score in scores.items():
            category_scores[cat] = max(category_scores[cat], score)
        if scores.get("security_fix", 0) > 0:
            security_commits.append(commit.sha)

    signals["category_scores"] = category_scores
    signals["security_related_commits"] = security_commits

    # Path-based signals
    if analysis.from_paths and analysis.to_paths:
        from_deps = {d for p in analysis.from_paths for d in p.dependencies}
        to_deps = {d for p in analysis.to_paths for d in p.dependencies}
        signals["dependency_chain_changed"] = from_deps != to_deps
        signals["new_deps_in_path"] = list(to_deps - from_deps)
        signals["removed_deps_from_path"] = list(from_deps - to_deps)

        from_direct = any(p.access_type == "DIRECT" for p in analysis.from_paths)
        to_direct = any(p.access_type == "DIRECT" for p in analysis.to_paths)
        signals["access_type_changed"] = from_direct != to_direct
        if from_direct and not to_direct:
            signals["access_type_shift"] = "DIRECT -> INDIRECT"
        elif not from_direct and to_direct:
            signals["access_type_shift"] = "INDIRECT -> DIRECT"

    # Heuristic classification
    signals["likely_nature"] = _classify_nature(signals)

    return signals


def _classify_nature(signals: dict) -> str:
    """Heuristic classification of the change nature. Returns a label with confidence."""
    scores = signals.get("category_scores", {})
    change = signals.get("change_type", "")

    # Strong signal: known CVE + API removed = vulnerability fix
    if signals.get("has_known_cve") and change == "REMOVED":
        return "VULNERABILITY_FIX (high confidence)"

    # Strong signal: known CVE + API access modified
    if signals.get("has_known_cve"):
        return "VULNERABILITY_FIX (medium confidence)"

    # Security keywords in commits + API removed
    if scores.get("security_fix", 0) > 0 and change == "REMOVED":
        return "SECURITY_HARDENING (medium confidence)"

    # API added + feature keywords dominant
    if change == "ADDED":
        if scores.get("feature", 0) > scores.get("security_fix", 0):
            return "FEATURE_ADDITION (medium confidence)"
        if scores.get("dependency_update", 0) > 0:
            return "DEPENDENCY_UPDATE_SIDE_EFFECT (medium confidence)"
        if signals.get("new_deps_in_path"):
            return "TRANSITIVE_DEPENDENCY_CHANGE (medium confidence)"
        return "NEW_API_ACCESS (low confidence — review manually)"

    # API removed + refactor keywords
    if change == "REMOVED" and scores.get("refactor", 0) > 0:
        return "REFACTORING (medium confidence)"

    # API removed with no other signals
    if change == "REMOVED":
        return "API_REMOVAL (low confidence — could be fix, refactor, or hardening)"

    # Modified paths
    if change == "MODIFIED":
        if signals.get("dependency_chain_changed"):
            return "DEPENDENCY_CHAIN_RESTRUCTURE (low confidence)"
        return "CALL_PATH_CHANGE (low confidence — review manually)"

    return "UNKNOWN (review manually)"



# 5. Report generation


def print_report(analysis: ChangeAnalysis):
    """Print a human-readable analysis report."""
    sep = "=" * 72
    print(sep)
    print(f"  SENSITIVE API CHANGE ANALYSIS")
    print(sep)
    print(f"  Package:       {analysis.group_id}:{analysis.artifact_id}")
    print(f"  Versions:      {analysis.from_version} -> {analysis.to_version}")
    print(f"  Sensitive API: {analysis.sensitive_api}")
    print(f"  Change Type:   {analysis.change_type}")
    print(sep)

    # Classification
    nature = analysis.signals.get("likely_nature", "UNKNOWN")
    print(f"\n  >> LIKELY NATURE: {nature}\n")

    # Call paths
    print("-" * 72)
    print(f"  CALL PATHS IN {analysis.from_version}")
    print("-" * 72)
    if analysis.from_paths:
        for i, p in enumerate(analysis.from_paths, 1):
            print(f"\n  Path {i} ({p.access_type}):")
            print(f"    Entry: {p.entry_point}")
            if p.full_path:
                for j, method in enumerate(p.full_path):
                    prefix = "    -> " if j > 0 else "       "
                    print(f"{prefix}{method}")
            if p.dependencies:
                print(f"    Via deps: {', '.join(p.dependencies)}")
    else:
        print("  (no paths found — API not accessed in this version)")

    print()
    print("-" * 72)
    print(f"  CALL PATHS IN {analysis.to_version}")
    print("-" * 72)
    if analysis.to_paths:
        for i, p in enumerate(analysis.to_paths, 1):
            print(f"\n  Path {i} ({p.access_type}):")
            print(f"    Entry: {p.entry_point}")
            if p.full_path:
                for j, method in enumerate(p.full_path):
                    prefix = "    -> " if j > 0 else "       "
                    print(f"{prefix}{method}")
            if p.dependencies:
                print(f"    Via deps: {', '.join(p.dependencies)}")
    else:
        print("  (no paths found — API not accessed in this version)")

    # Entry point diff
    if analysis.added_entry_points or analysis.removed_entry_points:
        print()
        print("-" * 72)
        print("  ENTRY POINT CHANGES")
        print("-" * 72)
        for ep in sorted(analysis.added_entry_points):
            print(f"  + {ep}")
        for ep in sorted(analysis.removed_entry_points):
            print(f"  - {ep}")

    # Vulnerabilities
    if analysis.vulnerabilities:
        print()
        print("-" * 72)
        print("  KNOWN VULNERABILITIES (from OSV.dev)")
        print("-" * 72)
        for v in analysis.vulnerabilities:
            print(f"\n  {v.id} (severity: {v.severity})")
            print(f"    {v.summary}")
            print(f"    Affected: {v.affected_versions}")
            for ref in v.references[:3]:
                print(f"    Ref: {ref}")

    # Relevant commits
    if analysis.commits:
        print()
        print("-" * 72)
        print(f"  COMMITS ({analysis.from_version} -> {analysis.to_version})")
        print("-" * 72)
        security_shas = set(analysis.signals.get("security_related_commits", []))
        for c in analysis.commits[:30]:
            flag = " [SECURITY]" if c.sha in security_shas else ""
            print(f"  {c.sha} {c.message[:80]}{flag}")

    # Signals summary
    print()
    print("-" * 72)
    print("  CLASSIFICATION SIGNALS")
    print("-" * 72)
    for key, val in analysis.signals.items():
        if key == "category_scores":
            print(f"  {key}:")
            for cat, score in val.items():
                bar = "#" * int(score * 20)
                print(f"    {cat:25s} {score:.2f} {bar}")
        else:
            print(f"  {key}: {val}")

    print()
    print(sep)


def save_report_json(analysis: ChangeAnalysis, output_path: str):
    """Save the analysis as a JSON file for programmatic consumption."""
    data = {
        "package": f"{analysis.group_id}:{analysis.artifact_id}",
        "from_version": analysis.from_version,
        "to_version": analysis.to_version,
        "sensitive_api": analysis.sensitive_api,
        "change_type": analysis.change_type,
        "likely_nature": analysis.signals.get("likely_nature", "UNKNOWN"),
        "from_paths": [
            {
                "entry_point": p.entry_point,
                "sensitive_api": p.sensitive_api,
                "full_path": p.full_path,
                "access_type": p.access_type,
                "dependencies": p.dependencies,
            }
            for p in analysis.from_paths
        ],
        "to_paths": [
            {
                "entry_point": p.entry_point,
                "sensitive_api": p.sensitive_api,
                "full_path": p.full_path,
                "access_type": p.access_type,
                "dependencies": p.dependencies,
            }
            for p in analysis.to_paths
        ],
        "added_entry_points": sorted(analysis.added_entry_points),
        "removed_entry_points": sorted(analysis.removed_entry_points),
        "vulnerabilities": [
            {"id": v.id, "summary": v.summary, "severity": v.severity,
             "affected_versions": v.affected_versions, "references": v.references}
            for v in analysis.vulnerabilities
        ],
        "commits": [
            {"sha": c.sha, "message": c.message, "author": c.author, "date": c.date}
            for c in analysis.commits
        ],
        "signals": {k: v if not isinstance(v, set) else sorted(v)
                    for k, v in analysis.signals.items()},
    }
    with open(output_path, "w") as f:
        json.dump(data, f, indent=2)
    print(f"\nJSON report saved to: {output_path}")



# 6. Batch mode: process all changes from a version-history JSON


def load_version_history(history_path: str) -> dict:
    """Load a Theo version-history JSON file."""
    with open(history_path) as f:
        return json.load(f)


def analyze_all_changes_in_history(history_path: str, report_dir: str = None,
                                   output_dir: str = None) -> list[ChangeAnalysis]:
    """Analyze all sensitive API changes in a version-history file.

    Args:
        history_path: Path to a *-history.json file from Theo's version-history/
        report_dir:   Directory containing per-version package-static-report.json files
                      (organized as report_dir/<version>/package-static-report.json)
        output_dir:   Theo's package-miner output directory (for SCM data lookup)
    """
    history = load_version_history(history_path)
    group_id = history["groupId"]
    artifact_id = history["artifactId"]
    results = []

    for change in history.get("changes", []):
        if not (change.get("addedDirect") or change.get("removedDirect") or
                change.get("addedIndirect") or change.get("removedIndirect")):
            continue

        from_v = change["fromVersion"]
        to_v = change["toVersion"]

        all_apis = set()
        all_apis.update(change.get("addedDirect", []))
        all_apis.update(change.get("removedDirect", []))
        all_apis.update(change.get("addedIndirect", []))
        all_apis.update(change.get("removedIndirect", []))

        for api in all_apis:
            change_type = _determine_change_type(api, change)

            from_paths = []
            to_paths = []
            if report_dir:
                from_report = Path(report_dir) / from_v / "package-static-report.json"
                to_report = Path(report_dir) / to_v / "package-static-report.json"
                if from_report.exists():
                    from_paths = parse_package_static_report(str(from_report), api)
                if to_report.exists():
                    to_paths = parse_package_static_report(str(to_report), api)

            from_eps = {p.entry_point for p in from_paths}
            to_eps = {p.entry_point for p in to_paths}

            analysis = ChangeAnalysis(
                group_id=group_id,
                artifact_id=artifact_id,
                from_version=from_v,
                to_version=to_v,
                sensitive_api=api,
                change_type=change_type,
                from_paths=from_paths,
                to_paths=to_paths,
                added_entry_points=to_eps - from_eps,
                removed_entry_points=from_eps - to_eps,
                commits=[],
                vulnerabilities=[],
            )
            analysis.signals = extract_signals(analysis)
            results.append(analysis)

    return results


def _determine_change_type(api: str, change: dict) -> str:
    added = api in change.get("addedDirect", []) or api in change.get("addedIndirect", [])
    removed = api in change.get("removedDirect", []) or api in change.get("removedIndirect", [])
    if added and removed:
        return "MODIFIED"
    elif added:
        return "ADDED"
    elif removed:
        return "REMOVED"
    return "UNCHANGED"



# Main


def main():
    parser = argparse.ArgumentParser(
        description="Analyze why a sensitive API access changed between library versions.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Analyze a specific API change with Theo output directory
  python %(prog)s \\
    --group-id com.fasterxml.jackson.core \\
    --artifact-id jackson-databind \\
    --from-version 2.14.0 --to-version 2.15.0 \\
    --sensitive-api "java.lang.reflect.Method.invoke" \\
    --output-dir ./output

  # Use direct report file paths
  python %(prog)s \\
    --from-report output/2.14.0/package-static-report.json \\
    --to-report output/2.15.0/package-static-report.json \\
    --sensitive-api "java.lang.reflect.Method.invoke"

  # Batch mode: analyze all changes in a version-history file
  python %(prog)s \\
    --history output/version-history/com.example_lib-history.json \\
    --report-dir output/versions/ \\
    --output-dir ./output

Environment:
  GITHUB_TOKEN   GitHub personal access token (recommended for higher rate limits)
        """,
    )

    parser.add_argument("--group-id", help="Maven groupId")
    parser.add_argument("--artifact-id", help="Maven artifactId")
    parser.add_argument("--from-version", help="Version before the change")
    parser.add_argument("--to-version", help="Version after the change")
    parser.add_argument("--sensitive-api", help="The sensitive API to investigate (e.g. java.lang.reflect.Method.invoke)")
    parser.add_argument("--from-report", help="Path to package-static-report.json for from-version")
    parser.add_argument("--to-report", help="Path to package-static-report.json for to-version")
    parser.add_argument("--output-dir", help="Theo package-miner output directory")
    parser.add_argument("--report-dir", help="Directory with per-version reports (<version>/package-static-report.json)")
    parser.add_argument("--history", help="Path to a *-history.json file for batch analysis")
    parser.add_argument("--json-output", help="Save results as JSON to this path")
    parser.add_argument("--skip-github", action="store_true", help="Skip GitHub commit fetching")
    parser.add_argument("--skip-osv", action="store_true", help="Skip OSV vulnerability lookup")

    args = parser.parse_args()

    # Batch mode
    if args.history:
        print(f"Batch mode: analyzing all changes in {args.history}")
        results = analyze_all_changes_in_history(
            args.history, args.report_dir, args.output_dir)
        for r in results:
            print_report(r)
        if args.json_output:
            all_data = []
            for r in results:
                all_data.append({
                    "from_version": r.from_version,
                    "to_version": r.to_version,
                    "sensitive_api": r.sensitive_api,
                    "change_type": r.change_type,
                    "likely_nature": r.signals.get("likely_nature", "UNKNOWN"),
                })
            with open(args.json_output, "w") as f:
                json.dump(all_data, f, indent=2)
            print(f"\nBatch summary saved to: {args.json_output}")
        return

    # Single analysis mode
    if not args.sensitive_api:
        parser.error("--sensitive-api is required")

    group_id = args.group_id or ""
    artifact_id = args.artifact_id or ""
    from_version = args.from_version or "unknown"
    to_version = args.to_version or "unknown"

    # Parse reports
    from_paths = []
    to_paths = []

    if args.from_report:
        print(f"Parsing from-version report: {args.from_report}")
        from_paths = parse_package_static_report(args.from_report, args.sensitive_api)
    elif args.report_dir and args.from_version:
        report = Path(args.report_dir) / args.from_version / "package-static-report.json"
        if report.exists():
            print(f"Parsing from-version report: {report}")
            from_paths = parse_package_static_report(str(report), args.sensitive_api)

    if args.to_report:
        print(f"Parsing to-version report: {args.to_report}")
        to_paths = parse_package_static_report(args.to_report, args.sensitive_api)
    elif args.report_dir and args.to_version:
        report = Path(args.report_dir) / args.to_version / "package-static-report.json"
        if report.exists():
            print(f"Parsing to-version report: {report}")
            to_paths = parse_package_static_report(str(report), args.sensitive_api)

    # Determine change type
    had_api = len(from_paths) > 0
    has_api = len(to_paths) > 0
    if had_api and has_api:
        change_type = "MODIFIED"
    elif has_api:
        change_type = "ADDED"
    elif had_api:
        change_type = "REMOVED"
    else:
        change_type = "UNCHANGED"

    from_eps = {p.entry_point for p in from_paths}
    to_eps = {p.entry_point for p in to_paths}

    # Fetch external signals
    commits = []
    vulns = []

    if not args.skip_osv and group_id and artifact_id:
        print(f"Querying OSV.dev for known vulnerabilities...")
        vulns = query_osv(group_id, artifact_id, from_version, to_version)
        print(f"  Found {len(vulns)} vulnerabilities affecting {from_version}")

    if not args.skip_github and group_id and artifact_id:
        print(f"Resolving GitHub repository...")
        github_url = resolve_github_repo(group_id, artifact_id, args.output_dir)
        if github_url:
            m = re.search(r"github\.com/([^/]+)/([^/]+)", github_url)
            if m:
                owner, repo = m.group(1), m.group(2)
                print(f"  Found: {github_url}")

                print(f"  Finding tags for {from_version} and {to_version}...")
                from_tag = find_version_tag(owner, repo, from_version)
                to_tag = find_version_tag(owner, repo, to_version)
                print(f"  Tags: {from_tag} -> {to_tag}")

                if from_tag and to_tag:
                    print(f"  Fetching commits between tags...")
                    commits = fetch_commits_between_tags(owner, repo, from_tag, to_tag)
                    print(f"  Found {len(commits)} commits")
        else:
            print("  Could not resolve GitHub repo (set GITHUB_TOKEN for better results)")

    analysis = ChangeAnalysis(
        group_id=group_id,
        artifact_id=artifact_id,
        from_version=from_version,
        to_version=to_version,
        sensitive_api=args.sensitive_api,
        change_type=change_type,
        from_paths=from_paths,
        to_paths=to_paths,
        added_entry_points=to_eps - from_eps,
        removed_entry_points=from_eps - to_eps,
        commits=commits,
        vulnerabilities=vulns,
    )
    analysis.signals = extract_signals(analysis)

    print_report(analysis)

    if args.json_output:
        save_report_json(analysis, args.json_output)


if __name__ == "__main__":
    main()
