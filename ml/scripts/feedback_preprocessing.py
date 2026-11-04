"""추가 학습 후보를 정리하고 제외 사유를 기록한다."""

import csv
import unicodedata
from pathlib import Path


# 비교에만 사용할 설명을 만든다. CSV의 원본 설명은 유지한다.
def normalized(text: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", text).split()).casefold()


# 기본 자료를 우선하고 추가 후보의 중복과 카테고리 충돌을 구분한다.
def filter_feedback(
    base_rows: list[dict[str, str]],
    additions: list[dict[str, str]],
) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    base_categories: dict[str, set[str]] = {}
    candidate_categories: dict[str, set[str]] = {}
    # 기존 학습 검증이 필요한 후보는 제외 판정으로 가리지 않는다.
    labels = {(row.get("category_code") or "").strip() for row in base_rows}

    def eligible(row: dict[str, str]) -> bool:
        return (
            (row.get("review_status") or "").strip() == "APPROVED"
            and (row.get("category_code") or "").strip() in labels
            and all((row.get(field) or "").strip() for field in ("sample_id", "group_id", "description"))
        )

    candidates = [row for row in additions if eligible(row)]
    for rows, categories in ((base_rows, base_categories), (candidates, candidate_categories)):
        for row in rows:
            description = normalized(row.get("description") or "")
            category = (row.get("category_code") or "").strip()
            if description and category:
                categories.setdefault(description, set()).add(category)

    accepted = []
    excluded = []
    seen: set[tuple[str, str]] = set()
    for row in additions:
        description = normalized(row.get("description") or "")
        category = (row.get("category_code") or "").strip()
        if not eligible(row):
            accepted.append(row)
            continue

        base_labels = base_categories.get(description, set())
        conflicting_labels = base_labels | candidate_categories[description]
        key = (description, category)
        if (base_labels and category not in base_labels) or (not base_labels and len(conflicting_labels) > 1):
            reason, detail = "카테고리 충돌", ", ".join(sorted(conflicting_labels))
        elif category in base_labels or key in seen:
            reason, detail = "중복", "동일한 설명과 카테고리"
        else:
            accepted.append(row)
            seen.add(key)
            continue

        excluded.append({
            "sample_id": row.get("sample_id") or "",
            "description": row.get("description") or "",
            "category_code": row.get("category_code") or "",
            "reason": reason,
            "detail": detail,
        })
    return accepted, excluded


# 추가 학습 폴더 밖에 이번 제외 결과를 덮어쓴다.
def write_feedback_review(incoming_dir: Path, excluded: list[dict[str, str]]) -> None:
    path = incoming_dir.parent / "user_approved_review.csv"
    if not excluded and not path.exists():
        return
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=("sample_id", "description", "category_code", "reason", "detail"))
        writer.writeheader()
        writer.writerows(excluded)
