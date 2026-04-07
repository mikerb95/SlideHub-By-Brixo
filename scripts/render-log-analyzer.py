#!/usr/bin/env python3
"""Fetch and summarize Render logs for SlideHub services.

Usage examples:
  RENDER_API_KEY=... RENDER_OWNER_ID=... ./scripts/render-log-analyzer.py
  ./scripts/render-log-analyzer.py --minutes 60 --services slidehub-ui slidehub-ai

The script:
  1. Reads service names from render.yaml by default.
  2. Resolves each service through the Render API.
  3. Pulls logs for a time window.
  4. Filters probable errors and groups repeated failures.
  5. Prints a concise report and writes a JSON artifact under target/render-logs/.
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
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_RENDER_YAML = ROOT_DIR / "render.yaml"
OUTPUT_DIR = ROOT_DIR / "target" / "render-logs"
LOCAL_ENV_FILE = ROOT_DIR / ".env"

ERROR_PATTERNS = [
    re.compile(r"\b(exception|error|fatal|panic|stacktrace|caused by|failed)\b", re.IGNORECASE),
    re.compile(r"\b(5\d\d|bad gateway|service unavailable|gateway timeout|connection refused|timeout)\b", re.IGNORECASE),
    re.compile(r"\b(beancreationexception|unsatisfieddependencyexception|sqlsyntaxerrorexception|constraintviolationexception)\b", re.IGNORECASE),
]

UUID_RE = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.IGNORECASE)
ISO_RE = re.compile(r"\b\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\b")
NUM_RE = re.compile(r"\b\d+\b")
WS_RE = re.compile(r"\s+")
BUILD_HINT_RE = re.compile(
    r"\b(failed|exited with|exit code|cannot|unable to|not found|missing|denied|killed|oom|timeout|timed out|compile)\b",
    re.IGNORECASE,
)

CAUSE_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (
        re.compile(r"\b(entitymanagerfactory|hibernate sessionfactory|beancreationexception|schema-validation|relation .* does not exist|table .* doesn't exist)\b", re.IGNORECASE),
        "database_schema_or_jpa_startup_failure",
    ),
    (re.compile(r"\b(outofmemory|killed|oom|memory limit)\b", re.IGNORECASE), "memory_limit"),
    (re.compile(r"\b(module not found|cannot find symbol|classnotfound|no such file)\b", re.IGNORECASE), "missing_dependency_or_file"),
    (re.compile(r"\b(authentication failed|permission denied|access denied|forbidden|unauthorized)\b", re.IGNORECASE), "permissions_or_auth"),
    (re.compile(r"\b(environment variable|missing env|not set|undefined variable)\b", re.IGNORECASE), "missing_environment_variable"),
    (re.compile(r"\b(compilation failed|build failed|maven|gradle|npm err|failed to compile)\b", re.IGNORECASE), "build_or_compile_failure"),
    (re.compile(r"\b(bind|port|address already in use|failed to start|startup)\b", re.IGNORECASE), "startup_or_port_failure"),
    (re.compile(r"\b(timeout|timed out|deadline exceeded)\b", re.IGNORECASE), "timeout"),
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Analyze Render logs for SlideHub services.")
    parser.add_argument("--minutes", type=int, default=60, help="Time window in minutes (default: 60)")
    parser.add_argument("--limit", type=int, default=100, help="Max logs per service (default: 100)")
    parser.add_argument(
        "--log-type",
        choices=["app", "request", "build"],
        help="Optional Render log type filter. Use 'build' to inspect deploy/build logs only.",
    )
    parser.add_argument(
        "--deploy-status",
        nargs="*",
        default=["build_failed", "update_failed", "pre_deploy_failed"],
        help="Render deploy statuses to report when searching for deployment errors.",
    )
    parser.add_argument(
        "--deploy-only",
        action="store_true",
        help="Skip log scraping and report only failed deploys from the last window.",
    )
    parser.add_argument(
        "--services",
        nargs="*",
        help="Service names or IDs. Default: names discovered from render.yaml.",
    )
    parser.add_argument(
        "--render-yaml",
        default=str(DEFAULT_RENDER_YAML),
        help="Path to render.yaml (default: repo root render.yaml)",
    )
    parser.add_argument(
        "--owner-id",
        default=os.getenv("RENDER_OWNER_ID", ""),
        help="Render workspace/owner ID. Falls back to RENDER_OWNER_ID env var.",
    )
    parser.add_argument(
        "--output-dir",
        default=str(OUTPUT_DIR),
        help="Directory for JSON artifacts (default: target/render-logs)",
    )
    return parser.parse_args()


def read_render_service_names(render_yaml_path: Path) -> list[str]:
    if not render_yaml_path.exists():
        raise FileNotFoundError(f"render.yaml not found: {render_yaml_path}")

    names: list[str] = []
    expecting_name = False
    for raw_line in render_yaml_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line.startswith("- type:"):
            expecting_name = True
            continue
        if expecting_name and line.startswith("name:"):
            _, value = line.split(":", 1)
            names.append(value.strip())
            expecting_name = False
            continue
        if line.startswith("-") and not line.startswith("- type:"):
            expecting_name = False
    return names


def load_env_file(env_path: Path) -> None:
    """Load simple KEY=VALUE pairs from a local .env file if present.

    Existing environment variables always win; this only fills in missing ones.
    """
    if not env_path.exists():
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or key in os.environ:
            continue
        if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
            value = value[1:-1]
        os.environ[key] = value


def http_get_json(url: str, api_key: str) -> Any:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read().decode("utf-8")
            return json.loads(payload)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
        raise RuntimeError(f"Render API HTTP {exc.code} for {url}: {body[:500]}") from exc


def render_timestamp(value: dt.datetime) -> str:
    """Format timestamps in the RFC3339 shape Render accepts reliably."""
    return value.astimezone(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def unwrap_service_entry(entry: dict[str, Any]) -> dict[str, Any]:
    """Render APIs often return wrapped objects like {cursor, service: {...}}."""
    if not isinstance(entry, dict):
        return {}
    for key in ("service", "owner", "resource"):
        value = entry.get(key)
        if isinstance(value, dict):
            return value
    return entry


def list_services(api_key: str, name: str | None = None) -> list[dict[str, Any]]:
    query: dict[str, Any] = {"limit": 100}
    if name:
        query["name"] = name
    url = "https://api.render.com/v1/services?" + urllib.parse.urlencode(query, doseq=True)
    data = http_get_json(url, api_key)
    if isinstance(data, list):
        return [unwrap_service_entry(item) for item in data]
    if isinstance(data, dict):
        items = data.get("items") or data.get("services") or data.get("data") or []
        if isinstance(items, list):
            return [unwrap_service_entry(item) for item in items]
    return []


def resolve_service(api_key: str, service_ref: str) -> dict[str, Any]:
    if service_ref.startswith("srv-"):
        return {"id": service_ref, "name": service_ref}

    services = list_services(api_key, service_ref)
    exact = [svc for svc in services if svc.get("name") == service_ref]
    if exact:
        return exact[0]
    if services:
        return services[0]
    raise RuntimeError(f"Service not found in Render API: {service_ref}")


def fetch_logs(api_key: str, owner_id: str, resource_ids: Iterable[str], minutes: int, limit: int) -> dict[str, Any]:
    end = dt.datetime.now(dt.timezone.utc)
    start = end - dt.timedelta(minutes=minutes)
    query: list[tuple[str, str]] = [
        ("ownerId", owner_id),
        ("startTime", render_timestamp(start)),
        ("endTime", render_timestamp(end)),
        ("direction", "backward"),
        ("limit", str(limit)),
    ]
    # Optional filter for deploy/build-only analysis.
    # Keep the API call flexible: if no log type is requested we fetch all types.
    # The Render API accepts type=app|request|build.
    if getattr(fetch_logs, "log_type", None):
        query.append(("type", str(getattr(fetch_logs, "log_type"))))
    for resource_id in resource_ids:
        query.append(("resource", resource_id))

    url = "https://api.render.com/v1/logs?" + urllib.parse.urlencode(query)
    return http_get_json(url, api_key)


def list_deploys(api_key: str, service_id: str, minutes: int, statuses: list[str]) -> list[dict[str, Any]]:
    end = dt.datetime.now(dt.timezone.utc)
    start = end - dt.timedelta(minutes=minutes)
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


def get_deploy_detail(api_key: str, service_id: str, deploy_id: str) -> dict[str, Any]:
    url = f"https://api.render.com/v1/services/{service_id}/deploys/{deploy_id}"
    payload = http_get_json(url, api_key)
    if isinstance(payload, dict):
        if isinstance(payload.get("deploy"), dict):
            return payload.get("deploy", {})
        return payload
    return {}


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
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=dt.timezone.utc)
    return parsed.astimezone(dt.timezone.utc)


def fetch_logs_between(
    api_key: str,
    owner_id: str,
    resource_id: str,
    start: dt.datetime,
    end: dt.datetime,
    limit: int = 250,
    log_type: str = "build",
) -> list[dict[str, Any]]:
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
        return payload.get("logs", [])
    return []


def is_error_log(message: str) -> bool:
    return any(pattern.search(message) for pattern in ERROR_PATTERNS)


def normalize_signature(message: str) -> str:
    first_line = message.splitlines()[0] if message else ""
    signature = ISO_RE.sub("<timestamp>", first_line)
    signature = UUID_RE.sub("<uuid>", signature)
    signature = NUM_RE.sub("<n>", signature)
    signature = WS_RE.sub(" ", signature).strip()
    return signature[:320]


def collect_error_snippets(log_items: list[dict[str, Any]], limit: int = 5) -> list[str]:
    seen: set[str] = set()
    snippets: list[str] = []
    for item in log_items:
        raw = str(item.get("message", "")).strip()
        if not raw:
            continue
        first_line = raw.splitlines()[0].strip()
        if not first_line:
            continue
        if not is_error_log(first_line):
            continue
        sig = normalize_signature(first_line)
        if sig in seen:
            continue
        seen.add(sig)
        snippets.append(first_line[:700])
        if len(snippets) >= limit:
            break
    return snippets


def collect_build_context(log_items: list[dict[str, Any]], limit: int = 8) -> list[str]:
    lines: list[str] = []
    for item in log_items:
        raw = str(item.get("message", "")).strip()
        if not raw:
            continue
        first_line = raw.splitlines()[0].strip()
        if not first_line:
            continue
        if BUILD_HINT_RE.search(first_line):
            lines.append(first_line[:700])

    if lines:
        return lines[:limit]

    tail = [str(item.get("message", "")).splitlines()[0].strip()[:700] for item in log_items if str(item.get("message", "")).strip()]
    return tail[-limit:]


def classify_cause(messages: list[str]) -> str:
    for message in messages:
        for pattern, label in CAUSE_PATTERNS:
            if pattern.search(message):
                return label
    return "unknown"


def summarize_logs(service_name: str, log_items: list[dict[str, Any]]) -> dict[str, Any]:
    matches: list[dict[str, Any]] = []
    signatures: Counter[str] = Counter()
    latest_by_signature: dict[str, dict[str, Any]] = {}

    for item in log_items:
        message = str(item.get("message", ""))
        if not message:
            continue
        if is_error_log(message):
            signature = normalize_signature(message)
            signatures[signature] += 1
            latest_by_signature[signature] = item
            matches.append(item)

    grouped = [
        {
            "signature": signature,
            "count": count,
            "latest": latest_by_signature[signature],
        }
        for signature, count in signatures.most_common()
    ]

    return {
        "service": service_name,
        "error_count": len(matches),
        "top_errors": grouped[:10],
        "sample_logs": matches[:20],
    }


def summarize_failed_deploys(
    service_name: str,
    deploys: list[dict[str, Any]],
    api_key: str,
    owner_id: str,
    service_id: str,
) -> list[dict[str, Any]]:
    failed = []
    for deploy in deploys:
        if not isinstance(deploy, dict):
            continue
        status = str(deploy.get("status", "")).strip()
        if status not in {"build_failed", "update_failed", "pre_deploy_failed"}:
            continue
        commit = deploy.get("commit") if isinstance(deploy.get("commit"), dict) else {}
        deploy_id = str(deploy.get("id", "")).strip()
        detail = {}
        if deploy_id:
            try:
                detail = get_deploy_detail(api_key, service_id, deploy_id)
            except Exception:
                detail = {}

        started_at = parse_render_time(str(deploy.get("startedAt", "")))
        created_at = parse_render_time(str(deploy.get("createdAt", "")))
        finished_at = parse_render_time(str(deploy.get("finishedAt", "")))
        start_time = started_at or created_at
        if start_time is None:
            start_time = dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=15)
        end_time = finished_at or (start_time + dt.timedelta(minutes=20))
        if end_time <= start_time:
            end_time = start_time + dt.timedelta(minutes=20)

        deploy_log_items: list[dict[str, Any]] = []
        try:
            deploy_log_items = fetch_logs_between(api_key, owner_id, service_id, start_time, end_time, limit=250, log_type="build")
        except Exception:
            deploy_log_items = []

        snippets = collect_error_snippets(deploy_log_items, limit=5)
        context = collect_build_context(deploy_log_items, limit=8)
        app_log_items: list[dict[str, Any]] = []
        app_snippets: list[str] = []
        if not snippets:
            try:
                app_log_items = fetch_logs_between(api_key, owner_id, service_id, start_time, end_time, limit=250, log_type="app")
            except Exception:
                app_log_items = []
            app_snippets = collect_error_snippets(app_log_items, limit=5)
            if app_snippets:
                snippets = app_snippets

        detail_messages = [
            str(detail.get("status", "")),
            str(detail.get("statusMessage", "")),
            str(detail.get("message", "")),
            str(detail.get("failureReason", "")),
            str(deploy.get("status", "")),
            *context,
            *app_snippets,
        ]
        cause = classify_cause([*snippets, *detail_messages])
        failed.append(
            {
                "deployId": deploy_id,
                "status": status,
                "trigger": deploy.get("trigger"),
                "createdAt": deploy.get("createdAt"),
                "startedAt": deploy.get("startedAt"),
                "finishedAt": deploy.get("finishedAt"),
                "commitId": commit.get("id"),
                "commitMessage": commit.get("message"),
                "suspectedCause": cause,
                "detailMessage": str(detail.get("statusMessage") or detail.get("message") or ""),
                "errorSnippets": snippets,
                "buildContext": context,
                "appContext": app_snippets,
            }
        )
    return failed


def main() -> int:
    load_env_file(LOCAL_ENV_FILE)

    args = parse_args()
    api_key = os.getenv("RENDER_API_KEY", "").strip()
    if not api_key:
        print("[ERROR] Set RENDER_API_KEY in the environment.", file=sys.stderr)
        return 1

    owner_id = args.owner_id.strip()
    if not owner_id:
        print("[ERROR] Set RENDER_OWNER_ID or pass --owner-id.", file=sys.stderr)
        return 1

    render_yaml = Path(args.render_yaml)
    service_refs = args.services or read_render_service_names(render_yaml)
    if not service_refs:
        print("[ERROR] No services found to analyze.", file=sys.stderr)
        return 1

    resolved_services: list[dict[str, Any]] = []
    for ref in service_refs:
        try:
            resolved = resolve_service(api_key, ref)
            resolved = unwrap_service_entry(resolved)
            resolved_services.append(resolved)
        except Exception as exc:
            print(f"[WARN] Could not resolve service '{ref}': {exc}", file=sys.stderr)

    if not resolved_services:
        print("[ERROR] None of the services could be resolved.", file=sys.stderr)
        return 1

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    report: dict[str, Any] = {
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "minutes": args.minutes,
        "limit": args.limit,
        "services": [],
    }

    mode_label = "deploys only" if args.deploy_only else f"logs type={args.log_type or 'all'}"
    print(f"[INFO] Querying {mode_label} for {len(resolved_services)} services")
    setattr(fetch_logs, "log_type", args.log_type)
    for svc in resolved_services:
        service_name = svc.get("name", svc.get("id", "unknown"))
        resource_id = svc.get("id")
        print(f"[INFO] Fetching logs for {service_name} ({resource_id})")
        failed_deploys: list[dict[str, Any]] = []
        if args.deploy_only or args.log_type == "build":
            try:
                deploys = list_deploys(api_key, resource_id, args.minutes, args.deploy_status)
                failed_deploys = summarize_failed_deploys(service_name, deploys, api_key, owner_id, resource_id)
            except Exception as exc:
                print(f"[WARN] Failed to fetch deploys for {service_name}: {exc}", file=sys.stderr)
        if args.deploy_only:
            summary = {
                "service": service_name,
                "resourceId": resource_id,
                "logCount": 0,
                "error_count": 0,
                "top_errors": [],
                "sample_logs": [],
                "failedDeploys": failed_deploys,
            }
            report["services"].append(summary)
            continue
        try:
            payload = fetch_logs(api_key, owner_id, [resource_id], args.minutes, args.limit)
        except Exception as exc:
            print(f"[WARN] Failed to fetch logs for {service_name}: {exc}", file=sys.stderr)
            continue

        if isinstance(payload, dict):
            log_items = payload.get("logs") or []
        else:
            log_items = []

        if not isinstance(log_items, list):
            log_items = []

        summary = summarize_logs(service_name, log_items)
        summary["resourceId"] = resource_id
        summary["logCount"] = len(log_items)
        summary["failedDeploys"] = failed_deploys
        report["services"].append(summary)

    artifact_path = output_dir / f"render-log-report-{dt.datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    artifact_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print("\n=== Render Log Summary ===")
    print(f"Window: last {args.minutes} minutes")
    print(f"Artifact: {artifact_path}")
    for svc in report["services"]:
        print(f"\n[{svc['service']}] logs={svc['logCount']} probableErrors={svc['error_count']}")
        if svc.get("failedDeploys"):
            print(f"  failed deploys={len(svc['failedDeploys'])}")
            for deploy in svc["failedDeploys"][:5]:
                print(
                    f"  - {deploy['status']} deploy={deploy['deployId']} trigger={deploy.get('trigger')} "
                    f"commit={deploy.get('commitId')}"
                )
                if deploy.get("commitMessage"):
                    print(f"    commit: {deploy['commitMessage']}")
                if deploy.get("suspectedCause"):
                    print(f"    suspectedCause: {deploy['suspectedCause']}")
                if deploy.get("detailMessage"):
                    print(f"    detail: {str(deploy['detailMessage'])[:180]}")
                if deploy.get("errorSnippets"):
                    print(f"    buildError: {deploy['errorSnippets'][0]}")
                elif deploy.get("buildContext"):
                    print(f"    buildContext: {deploy['buildContext'][0]}")
                elif deploy.get("appContext"):
                    print(f"    appContext: {deploy['appContext'][0]}")
        if not svc["top_errors"]:
            print("  no probable error signatures found")
            continue
        for entry in svc["top_errors"][:5]:
            latest_msg = str(entry["latest"].get("message", "")).splitlines()[0][:140]
            ts = entry["latest"].get("timestamp", "")
            print(f"  - x{entry['count']} {entry['signature']}")
            if latest_msg:
                print(f"    latest: {ts} | {latest_msg}")

    print("\n[INFO] Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
