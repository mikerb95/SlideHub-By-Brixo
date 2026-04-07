#!/usr/bin/env python3
"""Fetch and summarize Render build/runtime logs for SlideHub services.

Modes:
  latest-builds   (default) Most recent deploy per service with build log analysis.
  failed-deploys  Only failed deploys in the last --minutes window.
  runtime-errors  Scan runtime app logs for error patterns.

Usage:
  RENDER_API_KEY=xxx RENDER_OWNER_ID=xxx ./scripts/render-log-analyzer.py
  ./scripts/render-log-analyzer.py --mode latest-builds
  ./scripts/render-log-analyzer.py --mode failed-deploys --minutes 120
  ./scripts/render-log-analyzer.py --services slidehub-ui slidehub-ai
  ./scripts/render-log-analyzer.py --mode runtime-errors --minutes 30
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path
from typing import Any

ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_RENDER_YAML = ROOT_DIR / "render.yaml"
OUTPUT_DIR = ROOT_DIR / "target" / "render-logs"
LOCAL_ENV_FILE = ROOT_DIR / ".env"

# ── Error detection patterns ──────────────────────────────────────────────────

ERROR_PATTERNS = [
    re.compile(r"\b(exception|error|fatal|panic|stacktrace|caused by|failed)\b", re.IGNORECASE),
    re.compile(
        r"\b(5\d\d|bad gateway|service unavailable|gateway timeout|connection refused|timeout)\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(beancreationexception|unsatisfieddependencyexception|sqlsyntaxerrorexception|constraintviolationexception)\b",
        re.IGNORECASE,
    ),
]

UUID_RE = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.IGNORECASE)
ISO_RE = re.compile(r"\b\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\b")
NUM_RE = re.compile(r"\b\d+\b")
WS_RE = re.compile(r"\s+")

BUILD_HINT_RE = re.compile(
    r"\b(failed|exited with|exit code|cannot|unable to|not found|missing|denied|killed|oom|timeout|timed out|compile|error)\b",
    re.IGNORECASE,
)

CAUSE_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (
        re.compile(
            r"\b(entitymanagerfactory|hibernate sessionfactory|beancreationexception|schema-validation"
            r"|relation .* does not exist|table .* doesn't exist)\b",
            re.IGNORECASE,
        ),
        "database_schema_or_jpa_startup_failure",
    ),
    (re.compile(r"\b(outofmemory|killed|oom|memory limit)\b", re.IGNORECASE), "memory_limit"),
    (
        re.compile(r"\b(module not found|cannot find symbol|classnotfound|no such file)\b", re.IGNORECASE),
        "missing_dependency_or_file",
    ),
    (
        re.compile(r"\b(authentication failed|permission denied|access denied|forbidden|unauthorized)\b", re.IGNORECASE),
        "permissions_or_auth",
    ),
    (
        re.compile(r"\b(environment variable|missing env|not set|undefined variable)\b", re.IGNORECASE),
        "missing_environment_variable",
    ),
    (
        re.compile(r"\b(compilation failed|build failed|maven|gradle|npm err|failed to compile)\b", re.IGNORECASE),
        "build_or_compile_failure",
    ),
    (
        re.compile(r"\b(bind|port|address already in use|failed to start|startup)\b", re.IGNORECASE),
        "startup_or_port_failure",
    ),
    (re.compile(r"\b(timeout|timed out|deadline exceeded)\b", re.IGNORECASE), "timeout"),
]

STATUS_ICON: dict[str, str] = {
    "live": "✓",
    "build_failed": "✗",
    "update_failed": "✗",
    "pre_deploy_failed": "✗",
    "deactivated": "○",
    "suspended": "○",
    "canceled": "○",
    "build_in_progress": "⟳",
    "update_in_progress": "⟳",
    "pre_deploy_in_progress": "⟳",
    "created": "⟳",
}


# ── CLI ───────────────────────────────────────────────────────────────────────


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Analyze Render build and runtime logs for SlideHub services.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  ./scripts/render-log-analyzer.py\n"
            "  ./scripts/render-log-analyzer.py --mode failed-deploys --minutes 180\n"
            "  ./scripts/render-log-analyzer.py --services slidehub-ui --mode runtime-errors\n"
            "  ./scripts/render-log-analyzer.py --no-color > report.txt\n"
        ),
    )
    parser.add_argument(
        "--mode",
        choices=["latest-builds", "failed-deploys", "runtime-errors"],
        default="latest-builds",
        help=(
            "Analysis mode (default: latest-builds). "
            "'latest-builds': most recent deploy per service + build log analysis. "
            "'failed-deploys': failed deploys in the last --minutes window. "
            "'runtime-errors': scan runtime app logs for error patterns."
        ),
    )
    parser.add_argument(
        "--minutes",
        type=int,
        default=120,
        help="Time window in minutes for log/deploy queries (default: 120).",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=500,
        help="Max log lines fetched per service (default: 500).",
    )
    parser.add_argument(
        "--services",
        nargs="*",
        help="Service names or srv-xxx IDs. Default: all services in render.yaml.",
    )
    parser.add_argument(
        "--render-yaml",
        default=str(DEFAULT_RENDER_YAML),
        help="Path to render.yaml (default: repo root).",
    )
    parser.add_argument(
        "--owner-id",
        default=os.getenv("RENDER_OWNER_ID", ""),
        help="Render workspace owner ID (or set RENDER_OWNER_ID env var).",
    )
    parser.add_argument(
        "--output-dir",
        default=str(OUTPUT_DIR),
        help="Directory for JSON artifacts (default: target/render-logs).",
    )
    parser.add_argument(
        "--no-color",
        action="store_true",
        help="Disable ANSI color output.",
    )
    # Legacy compatibility (hidden)
    parser.add_argument("--deploy-only", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--log-type", choices=["app", "request", "build"], help=argparse.SUPPRESS)
    parser.add_argument("--deploy-status", nargs="*", help=argparse.SUPPRESS)
    return parser.parse_args()


# ── Env / YAML ────────────────────────────────────────────────────────────────


def load_env_file(env_path: Path) -> None:
    """Load KEY=VALUE pairs from .env, skipping keys already in the environment."""
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if not key or key in os.environ:
            continue
        if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
            value = value[1:-1]
        os.environ[key] = value


def read_render_service_names(render_yaml_path: Path) -> list[str]:
    """Parse service names from render.yaml without requiring a YAML library."""
    if not render_yaml_path.exists():
        raise FileNotFoundError(f"render.yaml not found: {render_yaml_path}")
    names: list[str] = []
    in_services_block = False
    expecting_name = False
    for raw_line in render_yaml_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        # Detect top-level 'services:' key
        if line == "services:":
            in_services_block = True
            continue
        # Any new top-level key ends the services block
        if in_services_block and not raw_line.startswith(" ") and not raw_line.startswith("\t") and line.endswith(":"):
            in_services_block = False
            continue
        if not in_services_block:
            continue
        if line.startswith("- type:"):
            expecting_name = True
            continue
        if expecting_name and line.startswith("name:"):
            _, value = line.split(":", 1)
            names.append(value.strip())
            expecting_name = False
    return names


# ── HTTP helpers ──────────────────────────────────────────────────────────────


def http_get_json(url: str, api_key: str) -> Any:
    req = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "Authorization": f"Bearer {api_key}"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
        raise RuntimeError(f"Render API HTTP {exc.code} — {url}\n  {body[:400]}") from exc


def render_timestamp(value: dt.datetime) -> str:
    """Return RFC-3339 / ISO-8601 UTC timestamp accepted by the Render API."""
    return value.astimezone(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


# ── Service resolution ────────────────────────────────────────────────────────


def unwrap_service_entry(entry: Any) -> dict[str, Any]:
    """Render API often wraps objects: {cursor, service: {...}}. Unwrap the inner dict."""
    if not isinstance(entry, dict):
        return {}
    for key in ("service", "owner", "resource"):
        if isinstance(entry.get(key), dict):
            return entry[key]
    return entry


def list_services(api_key: str, name: str | None = None) -> list[dict[str, Any]]:
    query: dict[str, str] = {"limit": "100"}
    if name:
        query["name"] = name
    url = "https://api.render.com/v1/services?" + urllib.parse.urlencode(query)
    data = http_get_json(url, api_key)
    if isinstance(data, list):
        return [unwrap_service_entry(item) for item in data]
    if isinstance(data, dict):
        # Handle paginated responses with various envelope keys
        for key in ("items", "results", "services", "data"):
            items = data.get(key)
            if isinstance(items, list):
                return [unwrap_service_entry(item) for item in items]
    return []


def resolve_service(api_key: str, service_ref: str) -> dict[str, Any]:
    """Resolve a service name or srv-xxx ID to a full service dict."""
    if service_ref.startswith("srv-"):
        return {"id": service_ref, "name": service_ref}
    services = list_services(api_key, service_ref)
    exact = [svc for svc in services if svc.get("name") == service_ref]
    if exact:
        return exact[0]
    if services:
        return services[0]
    raise RuntimeError(f"Service not found in Render API: '{service_ref}'")


# ── Log fetching ──────────────────────────────────────────────────────────────


def parse_render_time(timestamp: str | None) -> dt.datetime | None:
    if not timestamp:
        return None
    text = str(timestamp).strip()
    if not text:
        return None
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    try:
        parsed = dt.datetime.fromisoformat(text)
    except ValueError:
        return None
    return parsed.replace(tzinfo=dt.timezone.utc) if parsed.tzinfo is None else parsed.astimezone(dt.timezone.utc)


def fetch_logs_between(
    api_key: str,
    owner_id: str,
    resource_id: str,
    start: dt.datetime,
    end: dt.datetime,
    limit: int = 500,
    log_type: str = "build",
) -> list[dict[str, Any]]:
    """Fetch logs for a specific time window and log type."""
    query: list[tuple[str, str]] = [
        ("ownerId", owner_id),
        ("startTime", render_timestamp(start)),
        ("endTime", render_timestamp(end)),
        ("direction", "forward"),
        ("limit", str(limit)),
        ("type", log_type),
        ("resource", resource_id),
    ]
    url = "https://api.render.com/v1/logs?" + urllib.parse.urlencode(query)
    payload = http_get_json(url, api_key)
    if isinstance(payload, dict) and isinstance(payload.get("logs"), list):
        return payload["logs"]
    if isinstance(payload, list):
        return payload
    return []


def fetch_logs_window(
    api_key: str,
    owner_id: str,
    resource_ids: list[str],
    minutes: int,
    limit: int,
    log_type: str | None,
) -> list[dict[str, Any]]:
    """Fetch logs over a rolling time window (for runtime-errors mode)."""
    end = dt.datetime.now(dt.timezone.utc)
    start = end - dt.timedelta(minutes=minutes)
    query: list[tuple[str, str]] = [
        ("ownerId", owner_id),
        ("startTime", render_timestamp(start)),
        ("endTime", render_timestamp(end)),
        ("direction", "backward"),
        ("limit", str(limit)),
    ]
    if log_type:
        query.append(("type", log_type))
    for rid in resource_ids:
        query.append(("resource", rid))
    url = "https://api.render.com/v1/logs?" + urllib.parse.urlencode(query)
    payload = http_get_json(url, api_key)
    if isinstance(payload, dict) and isinstance(payload.get("logs"), list):
        return payload["logs"]
    if isinstance(payload, list):
        return payload
    return []


# ── Deploy fetching ───────────────────────────────────────────────────────────


def get_latest_deploy(api_key: str, service_id: str) -> dict[str, Any] | None:
    """Return the single most recent deploy for a service (any status)."""
    url = f"https://api.render.com/v1/services/{service_id}/deploys?limit=1"
    payload = http_get_json(url, api_key)
    if isinstance(payload, list) and payload:
        item = payload[0]
        if isinstance(item, dict):
            return item.get("deploy", item)
    return None


def list_deploys_in_window(
    api_key: str,
    service_id: str,
    minutes: int,
    statuses: list[str],
) -> list[dict[str, Any]]:
    """List deploys in the given time window filtered by status."""
    end = dt.datetime.now(dt.timezone.utc)
    start = end - dt.timedelta(minutes=minutes)
    # Render API accepts repeated 'status' params: status=x&status=y
    query: list[tuple[str, str]] = [
        ("limit", "100"),
        ("createdAfter", render_timestamp(start)),
    ]
    for status in statuses:
        query.append(("status", status))
    url = f"https://api.render.com/v1/services/{service_id}/deploys?" + urllib.parse.urlencode(query)
    payload = http_get_json(url, api_key)
    if isinstance(payload, list):
        return [item.get("deploy", item) if isinstance(item, dict) else {} for item in payload]
    return []


# ── Log analysis ──────────────────────────────────────────────────────────────


def is_error_log(message: str) -> bool:
    return any(p.search(message) for p in ERROR_PATTERNS)


def normalize_signature(message: str) -> str:
    first = message.splitlines()[0] if message else ""
    sig = ISO_RE.sub("<ts>", first)
    sig = UUID_RE.sub("<uuid>", sig)
    sig = NUM_RE.sub("<n>", sig)
    return WS_RE.sub(" ", sig).strip()[:320]


def collect_error_snippets(log_items: list[dict[str, Any]], limit: int = 8) -> list[str]:
    seen: set[str] = set()
    snippets: list[str] = []
    for item in log_items:
        raw = str(item.get("message", "")).strip()
        first = raw.splitlines()[0].strip() if raw else ""
        if not first or not is_error_log(first):
            continue
        sig = normalize_signature(first)
        if sig in seen:
            continue
        seen.add(sig)
        snippets.append(first[:700])
        if len(snippets) >= limit:
            break
    return snippets


def collect_build_context(log_items: list[dict[str, Any]], limit: int = 10) -> list[str]:
    hint_lines = [
        str(item.get("message", "")).splitlines()[0].strip()[:700]
        for item in log_items
        if BUILD_HINT_RE.search(str(item.get("message", "")))
    ]
    if hint_lines:
        return hint_lines[:limit]
    # fallback: last N lines of whatever was logged
    all_lines = [
        str(item.get("message", "")).splitlines()[0].strip()[:700]
        for item in log_items
        if str(item.get("message", "")).strip()
    ]
    return all_lines[-limit:]


def classify_cause(messages: list[str]) -> str:
    for msg in messages:
        for pattern, label in CAUSE_PATTERNS:
            if pattern.search(msg):
                return label
    return "unknown"


def summarize_log_items(service_name: str, log_items: list[dict[str, Any]]) -> dict[str, Any]:
    """Summarize runtime log items for runtime-errors mode."""
    matches = []
    signatures: Counter[str] = Counter()
    latest_by_sig: dict[str, dict[str, Any]] = {}
    for item in log_items:
        message = str(item.get("message", ""))
        if message and is_error_log(message):
            sig = normalize_signature(message)
            signatures[sig] += 1
            latest_by_sig[sig] = item
            matches.append(item)
    return {
        "service": service_name,
        "error_count": len(matches),
        "top_errors": [
            {"signature": sig, "count": cnt, "latest": latest_by_sig[sig]}
            for sig, cnt in signatures.most_common(10)
        ],
        "sample_logs": matches[:20],
    }


# ── Mode: latest-builds ───────────────────────────────────────────────────────


def analyze_latest_build(
    api_key: str,
    owner_id: str,
    service_id: str,
    service_name: str,
    limit: int,
) -> dict[str, Any]:
    """Fetch the most recent deploy and analyze its build logs."""
    deploy = get_latest_deploy(api_key, service_id)
    if not deploy:
        return {
            "service": service_name,
            "resourceId": service_id,
            "status": "no_deploys",
            "deployId": None,
            "commit": None,
            "createdAt": None,
            "logLines": 0,
            "errorSnippets": [],
            "buildContext": [],
            "suspectedCause": "unknown",
        }

    status = str(deploy.get("status", "unknown"))
    deploy_id = str(deploy.get("id", "")).strip()
    commit = deploy.get("commit") if isinstance(deploy.get("commit"), dict) else {}
    started_at = parse_render_time(deploy.get("startedAt") or deploy.get("createdAt"))
    finished_at = parse_render_time(deploy.get("finishedAt"))

    log_items: list[dict[str, Any]] = []
    if owner_id and started_at:
        buf = dt.timedelta(minutes=3)
        start = started_at - buf
        end = (finished_at or dt.datetime.now(dt.timezone.utc)) + buf
        # Try build logs first, then fall back to app logs
        for ltype in ("build", "app"):
            try:
                log_items = fetch_logs_between(api_key, owner_id, service_id, start, end, limit=limit, log_type=ltype)
            except Exception:
                log_items = []
            if log_items:
                break

    errors = collect_error_snippets(log_items, limit=8)
    context = collect_build_context(log_items, limit=10)
    return {
        "service": service_name,
        "resourceId": service_id,
        "status": status,
        "deployId": deploy_id,
        "commit": commit.get("message", ""),
        "commitId": commit.get("id", ""),
        "createdAt": deploy.get("createdAt"),
        "finishedAt": deploy.get("finishedAt"),
        "logLines": len(log_items),
        "errorSnippets": errors,
        "buildContext": context,
        "suspectedCause": classify_cause(errors + context),
    }


# ── Mode: failed-deploys ──────────────────────────────────────────────────────


def analyze_failed_deploys(
    api_key: str,
    owner_id: str,
    service_id: str,
    service_name: str,
    minutes: int,
    statuses: list[str],
    limit: int,
) -> dict[str, Any]:
    """List failed deploys in the time window and fetch their build logs."""
    deploys = list_deploys_in_window(api_key, service_id, minutes, statuses)
    status_set = set(statuses)
    failed = []
    for deploy in deploys:
        if not isinstance(deploy, dict):
            continue
        status = str(deploy.get("status", "")).strip()
        if status not in status_set:
            continue
        commit = deploy.get("commit") if isinstance(deploy.get("commit"), dict) else {}
        deploy_id = str(deploy.get("id", "")).strip()
        started_at = parse_render_time(deploy.get("startedAt") or deploy.get("createdAt"))
        finished_at = parse_render_time(deploy.get("finishedAt"))
        if not started_at:
            started_at = dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=15)
        end = finished_at or (started_at + dt.timedelta(minutes=25))
        if end <= started_at:
            end = started_at + dt.timedelta(minutes=25)

        log_items: list[dict[str, Any]] = []
        app_items: list[dict[str, Any]] = []
        try:
            log_items = fetch_logs_between(
                api_key, owner_id, service_id, started_at, end, limit=limit, log_type="build"
            )
        except Exception:
            pass
        errors = collect_error_snippets(log_items, limit=5)
        context = collect_build_context(log_items, limit=8)
        if not errors:
            try:
                app_items = fetch_logs_between(
                    api_key, owner_id, service_id, started_at, end, limit=limit, log_type="app"
                )
                errors = collect_error_snippets(app_items, limit=5)
            except Exception:
                pass
        failed.append(
            {
                "deployId": deploy_id,
                "status": status,
                "trigger": deploy.get("trigger"),
                "createdAt": deploy.get("createdAt"),
                "finishedAt": deploy.get("finishedAt"),
                "commitId": commit.get("id"),
                "commitMessage": commit.get("message"),
                "suspectedCause": classify_cause(errors + context),
                "errorSnippets": errors,
                "buildContext": context,
            }
        )
    return {"service": service_name, "resourceId": service_id, "failedDeploys": failed}


# ── Output helpers ────────────────────────────────────────────────────────────


def _c(text: str, ansi_code: str, no_color: bool) -> str:
    return text if no_color else f"\033[{ansi_code}m{text}\033[0m"


def time_ago(ts_str: str | None) -> str:
    parsed = parse_render_time(ts_str)
    if not parsed:
        return ts_str or ""
    delta = dt.datetime.now(dt.timezone.utc) - parsed
    secs = int(delta.total_seconds())
    if secs < 60:
        return f"{secs}s ago"
    if secs < 3600:
        return f"{secs // 60}m ago"
    if secs < 86400:
        return f"{secs // 3600}h ago"
    return f"{secs // 86400}d ago"


# ── Report printers ───────────────────────────────────────────────────────────


def print_latest_builds_report(results: list[dict[str, Any]], no_color: bool) -> None:
    print("\n" + "=" * 65)
    print("  LATEST BUILD STATUS")
    print("=" * 65)
    for r in results:
        status = r.get("status", "unknown")
        icon = STATUS_ICON.get(status, "?")
        commit = (r.get("commit") or "")[:72]
        ago = time_ago(r.get("createdAt"))
        is_failed = status in {"build_failed", "update_failed", "pre_deploy_failed"}
        is_progress = "in_progress" in status or status == "created"

        color = "31" if is_failed else ("32" if status == "live" else ("33" if is_progress else "37"))

        print()
        header = f"[{r['service']}] {icon} {status.upper()}"
        if ago:
            header += f"  ({ago})"
        print(_c(header, color, no_color))
        if commit:
            print(f"  commit : {commit}")
        print(f"  deploy : {r.get('deployId', 'n/a')}  |  logs : {r['logLines']} lines")

        if r.get("errorSnippets"):
            cause = r.get("suspectedCause", "unknown")
            if cause != "unknown":
                print(_c(f"  cause  : {cause}", "33", no_color))
            for snippet in r["errorSnippets"][:5]:
                print(_c(f"  ✗ {snippet[:120]}", "31", no_color))
        elif is_failed and r.get("buildContext"):
            for line in r["buildContext"][:3]:
                print(f"  → {line[:120]}")
        elif is_progress:
            print(_c("  Build in progress — re-run after it completes.", "33", no_color))
        elif not is_failed:
            print(_c("  No errors detected.", "32", no_color))


def print_failed_deploys_report(results: list[dict[str, Any]], minutes: int, no_color: bool) -> None:
    print("\n" + "=" * 65)
    print(f"  FAILED DEPLOYS  (last {minutes} minutes)")
    print("=" * 65)
    total_failed = sum(len(r.get("failedDeploys", [])) for r in results)
    for r in results:
        deploys = r.get("failedDeploys", [])
        count_str = f"{len(deploys)} failed" if deploys else "no failures"
        color = "31" if deploys else "32"
        print(f"\n[{r['service']}] {_c(count_str, color, no_color)}")
        for d in deploys[:5]:
            ago = time_ago(d.get("createdAt"))
            print(f"  - {d['status']}  deploy={d['deployId']}  {ago}")
            if d.get("commitMessage"):
                print(f"    commit : {str(d['commitMessage'])[:80]}")
            if d.get("suspectedCause") and d["suspectedCause"] != "unknown":
                print(_c(f"    cause  : {d['suspectedCause']}", "33", no_color))
            for snippet in d.get("errorSnippets", [])[:3]:
                print(_c(f"    ✗ {snippet[:120]}", "31", no_color))
            if not d.get("errorSnippets") and d.get("buildContext"):
                print(f"    → {d['buildContext'][0][:120]}")
    if not total_failed:
        print(_c("\n  No failed deploys found in the time window.", "32", no_color))


def print_runtime_errors_report(results: list[dict[str, Any]], no_color: bool) -> None:
    print("\n" + "=" * 65)
    print("  RUNTIME ERROR SCAN")
    print("=" * 65)
    for r in results:
        ec = r.get("error_count", 0)
        color = "31" if ec > 0 else "32"
        print(f"\n[{r['service']}] {_c(str(ec) + ' probable error(s)', color, no_color)}  (scanned {r.get('logCount', 0)} lines)")
        for entry in r.get("top_errors", [])[:5]:
            latest_msg = str(entry["latest"].get("message", "")).splitlines()[0][:120]
            ts = entry["latest"].get("timestamp", "")
            print(f"  - x{entry['count']}  {entry['signature'][:80]}")
            if latest_msg:
                print(_c(f"    {ts} | {latest_msg}", "31", no_color))


# ── Main ──────────────────────────────────────────────────────────────────────


def main() -> int:
    load_env_file(LOCAL_ENV_FILE)
    args = parse_args()

    # Legacy compat: --deploy-only maps to failed-deploys mode
    if getattr(args, "deploy_only", False):
        args.mode = "failed-deploys"

    api_key = os.getenv("RENDER_API_KEY", "").strip()
    if not api_key:
        print("[ERROR] RENDER_API_KEY not set. Add it to the environment or .env file.", file=sys.stderr)
        return 1

    owner_id = args.owner_id.strip()
    if not owner_id:
        print("[WARN] RENDER_OWNER_ID not set — log fetching will be skipped (build analysis unavailable).", file=sys.stderr)

    render_yaml = Path(args.render_yaml)
    service_refs = args.services or read_render_service_names(render_yaml)
    if not service_refs:
        print("[ERROR] No services found. Check render.yaml or pass --services.", file=sys.stderr)
        return 1

    resolved: list[dict[str, Any]] = []
    for ref in service_refs:
        try:
            svc = unwrap_service_entry(resolve_service(api_key, ref))
            resolved.append(svc)
            print(f"[INFO] Resolved: {svc.get('name')} ({svc.get('id')})")
        except Exception as exc:
            print(f"[WARN] Could not resolve '{ref}': {exc}", file=sys.stderr)

    if not resolved:
        print("[ERROR] None of the services could be resolved.", file=sys.stderr)
        return 1

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    deploy_statuses: list[str] = (
        getattr(args, "deploy_status", None) or ["build_failed", "update_failed", "pre_deploy_failed"]
    )
    mode = args.mode
    log_type_override: str | None = getattr(args, "log_type", None)

    print(f"[INFO] Mode: {mode} | Services: {len(resolved)} | Window: {args.minutes}m")

    report: dict[str, Any] = {
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "mode": mode,
        "minutes": args.minutes,
        "services": [],
    }
    results: list[dict[str, Any]] = []

    for svc in resolved:
        name = svc.get("name", svc.get("id", "unknown"))
        rid = str(svc.get("id", ""))
        print(f"[INFO] Analyzing {name} ...")
        try:
            if mode == "latest-builds":
                result = analyze_latest_build(api_key, owner_id, rid, name, args.limit)
            elif mode == "failed-deploys":
                result = analyze_failed_deploys(
                    api_key, owner_id, rid, name, args.minutes, deploy_statuses, args.limit
                )
            else:  # runtime-errors
                log_items = fetch_logs_window(
                    api_key, owner_id, [rid], args.minutes, args.limit, log_type=log_type_override
                )
                result = summarize_log_items(name, log_items)
                result["resourceId"] = rid
                result["logCount"] = len(log_items)
        except Exception as exc:
            print(f"[WARN] Error analyzing {name}: {exc}", file=sys.stderr)
            result = {"service": name, "resourceId": rid, "error": str(exc)}

        results.append(result)
        report["services"].append(result)

    no_color = args.no_color
    if mode == "latest-builds":
        print_latest_builds_report(results, no_color)
    elif mode == "failed-deploys":
        print_failed_deploys_report(results, args.minutes, no_color)
    else:
        print_runtime_errors_report(results, no_color)

    artifact = output_dir / f"render-log-report-{dt.datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    artifact.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\n[INFO] Artifact: {artifact}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
