#!/usr/bin/env python3
"""校验开发回归集与冻结盲测集的边界及来源快照。"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "dataset-manifest.json"
NUMERIC_ID = re.compile(r"[1-9]\d*")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path.name}:{line_number} 不是有效 JSON：{exc}") from exc
        if not isinstance(row, dict) or not isinstance(row.get("id"), str) or not row["id"]:
            raise ValueError(f"{path.name}:{line_number} 必须包含非空字符串 id")
        if not isinstance(row.get("query"), str) or not row["query"]:
            raise ValueError(f"{path.name}:{line_number} 必须包含非空 query")
        rows.append(row)
    return rows


def validate() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    development_path = ROOT / manifest["development"]["path"]
    holdout_path = ROOT / manifest["holdout"]["path"]
    catalog_path = ROOT / manifest["source_catalog"]["path"]
    development = load_jsonl(development_path)
    holdout = load_jsonl(holdout_path)

    for label, path, rows in (("development", development_path, development), ("holdout", holdout_path, holdout)):
        expected = manifest[label]
        if len(rows) != expected["case_count"]:
            raise ValueError(f"{label} Case 数为 {len(rows)}，应为 {expected['case_count']}")
        if sha256(path) != expected["sha256"]:
            raise ValueError(f"{path.name} 内容已变化；冻结集不得直接修改")
        ids = [row["id"] for row in rows]
        if len(ids) != len(set(ids)):
            raise ValueError(f"{path.name} 存在重复 id")

    holdout_ids = [row["id"] for row in holdout]
    if any(not NUMERIC_ID.fullmatch(case_id) for case_id in holdout_ids):
        raise ValueError("Holdout id 必须是无类型含义的正整数文本")
    if holdout_ids != [str(index) for index in range(1, len(holdout_ids) + 1)]:
        raise ValueError("Holdout id 必须连续为 1 至 Case 数")
    overlap = {row["id"] for row in development} & set(holdout_ids)
    if overlap:
        raise ValueError(f"开发集与 Holdout id 重叠：{', '.join(sorted(overlap))}")
    if any(row.get("category") != "holdout" or row.get("tags") != ["holdout"] for row in holdout):
        raise ValueError("Holdout 不得通过 category 或 tags 暴露 Case 类型")

    if sha256(catalog_path) != manifest["source_catalog"]["sha256"]:
        raise ValueError("source-catalog.json 内容已变化；请先重新冻结数据来源")
    source_ids = {str(row["source_id"]) for row in json.loads(catalog_path.read_text(encoding="utf-8"))["sources"]}
    unknown_refs = {
        str(reference["source_id"])
        for row in holdout
        for reference in row.get("gold_evidence_refs", [])
        if str(reference.get("source_id", "")) not in source_ids
    }
    if unknown_refs:
        raise ValueError(f"Holdout 引用了来源目录中不存在的 source_id：{', '.join(sorted(unknown_refs))}")


if __name__ == "__main__":
    validate()
    print("数据集校验通过：114 条开发回归集，30 条冻结 Holdout Case。")
