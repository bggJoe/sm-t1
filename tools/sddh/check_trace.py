#!/usr/bin/env python3
"""SDD Harness Trace Checker v0.

Scans SLICE specs and Java test methods for AC/INV trace ids.
Baseline v0 is observation-only: missing, orphan, and disabled findings are
reported as warnings and do not fail the command.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Literal, cast

TraceKind = Literal["AC", "INV"]
FindingType = Literal["missing", "orphan", "disabled"]
Severity = Literal["warning"]

SPEC_ID_RE = re.compile(r"\*\*(AC|INV)-(\d{3})\*\*")
TEST_METHOD_RE = re.compile(
    r"\b(?:public\s+|protected\s+|private\s+)?(?:static\s+)?void\s+([A-Za-z0-9_]+)\s*\("
)
TEST_ID_RE = re.compile(
    r"(?<![A-Za-z0-9])(ac|inv)(\d{3})(?=$|[^A-Za-z0-9])", re.IGNORECASE
)


@dataclass(frozen=True, order=True)
class TraceId:
    kind: TraceKind
    number: str

    @property
    def label(self) -> str:
        return f"{self.kind}-{self.number}"

    @property
    def test_token(self) -> str:
        return f"{self.kind.lower()}{self.number}"


@dataclass(frozen=True)
class SpecOccurrence:
    trace_id: TraceId
    file: str
    line: int
    text: str


@dataclass(frozen=True)
class TestOccurrence:
    trace_id: TraceId
    file: str
    line: int
    method: str
    disabled: bool
    disabled_reason: str | None


@dataclass(frozen=True)
class Finding:
    type: FindingType
    severity: Severity
    trace_id: str
    message: str
    spec_occurrences: list[SpecOccurrence]
    test_occurrences: list[TestOccurrence]


@dataclass(frozen=True)
class Report:
    mode: str
    summary: dict[str, int]
    spec_occurrences: list[SpecOccurrence]
    test_occurrences: list[TestOccurrence]
    findings: list[Finding]


def repo_root_from_script() -> Path:
    return Path(__file__).resolve().parents[2]


def relative_to_root(path: Path, repo_root: Path) -> str:
    return path.resolve().relative_to(repo_root.resolve()).as_posix()


def iter_slice_files(repo_root: Path) -> Iterable[Path]:
    yield from sorted(repo_root.glob("SLICE-*.md"))


def iter_test_files(repo_root: Path) -> Iterable[Path]:
    test_root = repo_root / "backend" / "src" / "test" / "java"
    if not test_root.exists():
        return
    yield from sorted(test_root.rglob("*.java"))


def scan_spec_ids(repo_root: Path) -> list[SpecOccurrence]:
    occurrences: list[SpecOccurrence] = []
    for path in iter_slice_files(repo_root):
        rel = relative_to_root(path, repo_root)
        lines = path.read_text(encoding="utf-8").splitlines()
        for line_no, line in enumerate(lines, start=1):
            for match in SPEC_ID_RE.finditer(line):
                kind = match.group(1)
                number = match.group(2)
                occurrences.append(
                    SpecOccurrence(
                        trace_id=TraceId(kind=cast(TraceKind, kind), number=number),
                        file=rel,
                        line=line_no,
                        text=line.strip(),
                    )
                )
    return occurrences


def disabled_reason_from_annotations(annotations: list[str]) -> str | None:
    for annotation in annotations:
        if "@Disabled" not in annotation:
            continue
        match = re.search(r'@Disabled\("(.*)"\)', annotation)
        if match:
            return match.group(1)
        return "@Disabled"
    return None


def scan_test_ids(repo_root: Path) -> list[TestOccurrence]:
    occurrences: list[TestOccurrence] = []
    for path in iter_test_files(repo_root):
        rel = relative_to_root(path, repo_root)
        pending_annotations: list[str] = []
        lines = path.read_text(encoding="utf-8").splitlines()
        for line_no, line in enumerate(lines, start=1):
            stripped = line.strip()
            if stripped.startswith("@"):
                pending_annotations.append(stripped)
                continue

            method_match = TEST_METHOD_RE.search(line)
            if method_match:
                method = method_match.group(1)
                disabled_reason = disabled_reason_from_annotations(pending_annotations)
                for id_match in TEST_ID_RE.finditer(method):
                    kind = id_match.group(1).upper()
                    number = id_match.group(2)
                    occurrences.append(
                        TestOccurrence(
                            trace_id=TraceId(kind=cast(TraceKind, kind), number=number),
                            file=rel,
                            line=line_no,
                            method=method,
                            disabled=disabled_reason is not None,
                            disabled_reason=disabled_reason,
                        )
                    )
                pending_annotations = []
                continue

            if stripped and not stripped.startswith("//"):
                pending_annotations = []
    return occurrences


def group_spec_ids(occurrences: list[SpecOccurrence]) -> dict[TraceId, list[SpecOccurrence]]:
    grouped: dict[TraceId, list[SpecOccurrence]] = {}
    for occurrence in occurrences:
        grouped.setdefault(occurrence.trace_id, []).append(occurrence)
    return grouped


def group_test_ids(occurrences: list[TestOccurrence]) -> dict[TraceId, list[TestOccurrence]]:
    grouped: dict[TraceId, list[TestOccurrence]] = {}
    for occurrence in occurrences:
        grouped.setdefault(occurrence.trace_id, []).append(occurrence)
    return grouped


def classify(specs: list[SpecOccurrence], tests: list[TestOccurrence]) -> list[Finding]:
    spec_by_id = group_spec_ids(specs)
    test_by_id = group_test_ids(tests)
    findings: list[Finding] = []

    for trace_id in sorted(set(spec_by_id) - set(test_by_id)):
        findings.append(
            Finding(
                type="missing",
                severity="warning",
                trace_id=trace_id.label,
                message=f"{trace_id.label} appears in SLICE specs but has no {trace_id.test_token} test method.",
                spec_occurrences=spec_by_id[trace_id],
                test_occurrences=[],
            )
        )

    for trace_id in sorted(set(test_by_id) - set(spec_by_id)):
        findings.append(
            Finding(
                type="orphan",
                severity="warning",
                trace_id=trace_id.label,
                message=f"{trace_id.test_token} test method exists but no matching {trace_id.label} appears in SLICE specs.",
                spec_occurrences=[],
                test_occurrences=test_by_id[trace_id],
            )
        )

    disabled_tests = (test for test in tests if test.disabled)
    for occurrence in sorted(disabled_tests, key=lambda item: (item.trace_id, item.file, item.line)):
        findings.append(
            Finding(
                type="disabled",
                severity="warning",
                trace_id=occurrence.trace_id.label,
                message=f"{occurrence.method} is disabled; baseline v0 reports this as warning only.",
                spec_occurrences=spec_by_id.get(occurrence.trace_id, []),
                test_occurrences=[occurrence],
            )
        )

    return sorted(findings, key=lambda item: (item.type, item.trace_id, item.message))


def build_report(repo_root: Path) -> Report:
    specs = scan_spec_ids(repo_root)
    tests = scan_test_ids(repo_root)
    findings = classify(specs, tests)
    summary = {
        "spec_occurrences": len(specs),
        "spec_ids": len(group_spec_ids(specs)),
        "test_occurrences": len(tests),
        "test_ids": len(group_test_ids(tests)),
        "missing": sum(1 for finding in findings if finding.type == "missing"),
        "orphan": sum(1 for finding in findings if finding.type == "orphan"),
        "disabled": sum(1 for finding in findings if finding.type == "disabled"),
        "warnings": len(findings),
        "errors": 0,
    }
    return Report(
        mode="baseline-warning",
        summary=summary,
        spec_occurrences=specs,
        test_occurrences=tests,
        findings=findings,
    )


def location(file: str, line: int) -> str:
    return f"{file}:{line}"


def render_text(report: Report) -> str:
    lines: list[str] = []
    lines.append("SDD Harness Trace Check v0")
    lines.append("Mode: baseline-warning (missing / orphan / disabled are warnings)")
    lines.append("")
    lines.append("Summary:")
    for key in (
        "spec_ids",
        "spec_occurrences",
        "test_ids",
        "test_occurrences",
        "missing",
        "orphan",
        "disabled",
        "warnings",
        "errors",
    ):
        lines.append(f"  {key}: {report.summary[key]}")

    if not report.findings:
        lines.append("")
        lines.append("No trace findings.")
        return "\n".join(lines)

    for finding_type in ("missing", "orphan", "disabled"):
        typed_findings = [
            finding for finding in report.findings if finding.type == finding_type
        ]
        if not typed_findings:
            continue
        lines.append("")
        lines.append(f"{finding_type.title()} warnings:")
        for finding in typed_findings:
            lines.append(f"  - [{finding.trace_id}] {finding.message}")
            for occurrence in finding.spec_occurrences:
                lines.append(f"      spec: {location(occurrence.file, occurrence.line)}")
            for occurrence in finding.test_occurrences:
                test_location = location(occurrence.file, occurrence.line)
                lines.append(f"      test: {test_location}::{occurrence.method}")
                if occurrence.disabled_reason:
                    lines.append(f"      disabled_reason: {occurrence.disabled_reason}")

    return "\n".join(lines)


def render_json(report: Report) -> str:
    return json.dumps(asdict(report), ensure_ascii=False, indent=2, sort_keys=True)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check SLICE AC/INV ids against Java test method ids."
    )
    parser.add_argument(
        "--format",
        choices=("text", "json"),
        default="text",
        help="Output format. Default: text.",
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=repo_root_from_script(),
        help="Repository root. Default: inferred from this script location.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    repo_root = args.repo_root.resolve()
    if not repo_root.exists():
        print(f"error: repo root does not exist: {repo_root}", file=sys.stderr)
        return 2

    report = build_report(repo_root)
    if args.format == "json":
        print(render_json(report))
    else:
        print(render_text(report))

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
