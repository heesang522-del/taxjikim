"""PDF에서 내장 텍스트와 OCR 결과를 읽어 날짜별 거래를 추출한다."""

from __future__ import annotations

import csv
import math
import re
import statistics

from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Any, Callable

import pymupdf

from . import merge_parser, ocr_engine, ocr_parser


CategoryResolver = Callable[[dict[str, Any]], str | None]

# FastAPI 요청 스레드가 달라도 PDF 분석은 전용 스레드에서 순서대로 실행한다.
_PDF_EXECUTOR = ThreadPoolExecutor(
    max_workers=1,
    thread_name_prefix="pdf-analysis",
)

DATE_PATTERN = re.compile(
    r"(?<!\d)((?:19|20)\d{2})\s*[./\-년]\s*"
    r"(\d{1,2})\s*[./\-월]\s*(\d{1,2})\s*일?(?!\d)"
)

NUMBER_PATTERN = re.compile(
    r"[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?"
)

IDENTIFIER_PATTERN = re.compile(
    r"(?<!\d)(?:\d{6}-?[\d*]{7}|\d{3}-\d{2}-\d{5})(?!\d)"
)

DEFAULTS = {
    "max_pages": 50,
    "column_match_ratio": 0.9,
    "min_ocr_image_area_ratio": 0.01,
    "row_top_tolerance": 3.0,
    "cell_gap_factor": 2.0,
    "min_cell_gap": 7.0,
    "word_x_tolerance": 1.5,
    "word_y_tolerance": 3.0,
    "direction_aliases": {
        "수입": "INCOME",
        "입금": "INCOME",
        "INCOME": "INCOME",
        "지출": "EXPENSE",
        "출금": "EXPENSE",
        "EXPENSE": "EXPENSE",
    },
}

# 공백과 단위를 제거한 헤더 전체를 비교해 열의 역할을 판별한다.
HEADER_ROLE_PATTERNS = (
    ("date", r"날짜|일자|거래일(?:자|시)?|사용일자|date"),
    ("type", r"(?:거래)?구분|거래유형|type"),
    ("description", r"(?:거래)?(?:내용|설명)|적요|가맹점|description"),
    (
        "net",
        r"실제(?:금액|(?:입출금|입금|출금)(?:액|금액))"
        r"|실(?:수령|입금)액|세후(?:금액|수입|입금액)|netamount",
    ),
    ("gross", r"세전(?:금액|수입|수입금액|입금액)|지급총액|grossamount"),
    ("amount", r"(?:거래|사용|결제|수입|지출)?금액|amount"),
    ("tax", r"(?:공제)?세액|원천징수액|차감세액|차감세금|tax"),
    ("tax_rate", r"(?:차감|원천징수)?세율|taxrate"),
    ("category", r"카테고리|분류|category"),
    ("sequence", r"(?:거래|승인|계좌)?번호|순번|no\.?"),
)


def _text(value):
    return str(value if value is not None else "").strip()


def _compact(value):
    source = re.sub(r"[\u2010-\u2015\u2212]", "-", _text(value))
    return re.sub(r"\s+", "", source)


def _mask(value):
    return IDENTIFIER_PATTERN.sub("***-****-****", _text(value))


def _settings(config):
    if config is not None and not isinstance(config, dict):
        raise ValueError("config는 사전이어야 합니다.")

    unknown = set(config or {}) - set(DEFAULTS)

    if unknown:
        raise ValueError(
            "지원하지 않는 설정입니다: " + ", ".join(sorted(unknown))
        )

    result = {**DEFAULTS, **(config or {})}

    if type(result["max_pages"]) is not int or result["max_pages"] < 1:
        raise ValueError("max_pages 설정을 확인해주세요.")

    for key in (
        "column_match_ratio",
        "row_top_tolerance",
        "cell_gap_factor",
        "min_cell_gap",
        "word_x_tolerance",
        "word_y_tolerance",
    ):
        value = result[key]

        if (
            isinstance(value, bool)
            or not isinstance(value, (int, float))
            or not math.isfinite(value)
            or value <= 0
        ):
            raise ValueError(f"{key}는 양의 유한한 숫자여야 합니다.")

    image_ratio = result["min_ocr_image_area_ratio"]

    if (
        type(image_ratio) not in (int, float)
        or not math.isfinite(image_ratio)
        or not 0 <= image_ratio <= 1
    ):
        raise ValueError("OCR 이미지 면적 비율은 0 이상 1 이하여야 합니다.")

    if result["column_match_ratio"] > 1:
        raise ValueError("컬럼 일치 비율은 1 이하여야 합니다.")

    directions = result["direction_aliases"]

    if (
        not isinstance(directions, dict)
        or any(
            not isinstance(key, str)
            or value not in {"INCOME", "EXPENSE"}
            for key, value in directions.items()
        )
    ):
        raise ValueError("거래 방향 별칭을 확인해주세요.")

    result["direction_aliases"] = {
        _compact(key).upper(): value
        for key, value in directions.items()
    }

    return result


def _empty(value):
    return _compact(value) in {"", "-", "없음", "해당없음"}


def _date(value):
    found = set()
    source = re.sub(r"[\u2010-\u2015\u2212]", "-", _text(value))

    for match in DATE_PATTERN.finditer(source):
        try:
            found.add(
                date(
                    int(match[1]),
                    int(match[2]),
                    int(match[3]),
                ).isoformat()
            )
        except ValueError:
            pass

    return next(iter(found)) if len(found) == 1 else None


def _amount(value):
    source = re.sub(r",\s+(?=\d)", ",", _text(value))

    source = re.sub(
        r"^(?:₩|KRW)\s*|\s*(?:원|₩|KRW)$",
        "",
        source,
        flags=re.I,
    ).strip()

    if not NUMBER_PATTERN.fullmatch(source):
        return None

    try:
        number = Decimal(source.replace(",", ""))
    except InvalidOperation:
        return None

    if (
        not number.is_finite()
        or number != number.to_integral_value()
        # 비정상적으로 긴 숫자는 JSON 응답으로 직렬화하지 않는다.
        or number.adjusted() > 308
    ):
        return None

    return int(number)


def _rate(value):
    match = re.fullmatch(r"([+-]?\d+(?:\.\d+)?)%", _compact(value))

    if not match:
        return None

    number = Decimal(match[1])
    return number if math.isfinite(float(number)) else None


def _direction(value, config):
    return config["direction_aliases"].get(
        _compact(value).upper(),
        "UNKNOWN",
    )


def _direction_hint(source):
    """제목이나 헤더에 명시된 거래 방향만 사용한다."""

    heading = _compact(source)
    expense = bool(re.search(r"지출(?:내역|금액)|사용금액|출금액", heading))
    income = bool(re.search(r"수입(?:내역|금액)|입금액|세전수입", heading))

    if expense and not income:
        return "EXPENSE"

    if income and not expense:
        return "INCOME"

    return "UNKNOWN"


def words_to_rows(words, config=None, *, align_transaction_columns=False):
    """PDF 내장 단어를 좌표에 따라 행으로 묶는다."""

    settings = _settings(config)
    prepared = []

    for word in words:
        if not isinstance(word, dict) or not _text(word.get("text")):
            continue

        try:
            x0, x1, top = (
                float(word[key])
                for key in ("x0", "x1", "top")
            )
        except (KeyError, TypeError, ValueError, OverflowError):
            continue

        if (
            all(math.isfinite(value) for value in (x0, x1, top))
            and x1 > x0
            and min(x0, top) >= 0
        ):
            prepared.append(
                {
                    "text": _text(word["text"]),
                    "x0": x0,
                    "x1": x1,
                    "top": top,
                }
            )

    lines = []

    for word in sorted(prepared, key=lambda item: (item["top"], item["x0"])):
        nearest = min(
            lines,
            key=lambda line: abs(
                statistics.median(item["top"] for item in line) - word["top"]
            ),
            default=None,
        )

        if (
            nearest is None
            or abs(
                statistics.median(item["top"] for item in nearest) - word["top"]
            ) > settings["row_top_tolerance"]
        ):
            lines.append([word])
        else:
            nearest.append(word)

    result = []

    header_index = None
    column_starts = None
    aligned_rows = []

    for line in sorted(
        lines,
        key=lambda row: statistics.median(word["top"] for word in row),
    ):
        line.sort(key=lambda word: word["x0"])

        gap = max(
            settings["min_cell_gap"],
            statistics.median(
                (word["x1"] - word["x0"])
                / max(1, len(_compact(word["text"])))
                for word in line
            ) * settings["cell_gap_factor"],
        )

        cells = []
        cell_starts = []
        current = []
        right = None

        for word in line:
            if current and right is not None and word["x0"] - right > gap:
                cells.append(" ".join(current))
                current = []
                right = None

            if not current:
                cell_starts.append(word["x0"])
            current.append(word["text"])
            right = word["x1"] if right is None else max(right, word["x1"])

        if current:
            cells.append(" ".join(current))

        if align_transaction_columns:
            roles = [_header_role(cell) for cell in cells]
            if roles[0] == "date" and {"type", "description", "gross", "tax", "net"}.issubset(roles):
                if header_index is not None:
                    # 표가 여러 개면 페이지 전체를 하나의 헤더로 해석하지 않는다.
                    align_transaction_columns = False
                else:
                    header_index = len(result)
                    column_starts = cell_starts
            elif column_starts is not None and _date(cells[0]):
                # 비어 있는 세전 수입·세액 열도 보존하도록 헤더 좌표에 단어를 배치한다.
                columns = [[] for _ in column_starts]
                column = 0
                for word in line:
                    while column + 1 < len(column_starts) and word["x0"] >= column_starts[column + 1]:
                        column += 1
                    columns[column].append(word["text"])
                aligned_cells = [" ".join(words) for words in columns]
                if not _date(aligned_cells[0]):
                    align_transaction_columns = False
                aligned_rows.append({"cells": aligned_cells})

        result.append(
            {
                "cells": cells,
                "text": " | ".join(cells),
            }
        )

    if align_transaction_columns and aligned_rows:
        header = result[header_index]
        original_rows = [
            row for row in result[header_index + 1:]
            if any(_date(cell) for cell in row["cells"])
        ]
        has_earlier_transactions = any(
            _date(row["cells"][0]) for row in result[:header_index]
        )
        # 다른 거래 행을 잃지 않고 열 수 차이를 복원할 수 있을 때만 적용한다.
        if (
            not has_earlier_transactions
            and len(aligned_rows) == len(original_rows)
            and any(len(row["cells"]) != len(header["cells"]) for row in original_rows)
        ):
            return [header, *aligned_rows]

    return result


def _text_rows(source, config):
    result = []
    lines = source.splitlines()
    csv_rows = {}
    csv_widths = set()

    # 날짜가 독립된 필드인 행으로 CSV 형식을 확인한다.
    for index, line in enumerate(lines):
        if "," not in line:
            continue

        try:
            cells = next(
                csv.reader([line], skipinitialspace=True, strict=True)
            )
        except csv.Error:
            continue

        if len(cells) < 3:
            continue

        csv_rows[index] = cells

        if any(DATE_PATTERN.fullmatch(_text(cell)) for cell in cells):
            csv_widths.add(len(cells))

    for index, line in enumerate(lines):
        line = line.strip()

        if not line:
            continue

        csv_cells = csv_rows.get(index)

        if csv_cells is not None and len(csv_cells) in csv_widths:
            cells = csv_cells

        elif "|" in line:
            cells = [_text(cell) for cell in line.split("|")]

            if cells and not cells[0]:
                cells = cells[1:]

            if cells and not cells[-1]:
                cells = cells[:-1]

        else:
            cells = re.split(r"\t+| {2,}", line)
            match = DATE_PATTERN.match(line)

            if len(cells) == 1 and match:
                tokens = line[match.end():].split()
                cells = [match.group(0)]

                if tokens and _direction(tokens[0], config) != "UNKNOWN":
                    cells.append(tokens.pop(0))

                trailing = []

                while tokens and (
                    _amount(tokens[-1]) is not None
                    or _rate(tokens[-1]) is not None
                    or _empty(tokens[-1])
                ):
                    trailing.append(tokens.pop())

                cells.extend([" ".join(tokens), *reversed(trailing)])

        result.append(
            {
                "cells": [_text(cell) for cell in cells],
                "reasons": [],
            }
        )

    return result


def _rows(source, config):
    if isinstance(source, str):
        return _text_rows(source, config)

    result = []

    for row in source or []:
        if isinstance(row, (list, tuple)):
            cells = list(row)
            reasons = []

        elif isinstance(row, dict):
            reasons = (
                list(row.get("reviewReasons") or [])
                if row.get("needsReview")
                else []
            )

            if row.get("needsReview") and not reasons:
                reasons = ["행 복원 결과 확인 필요"]

            cells = row.get("cells")

            if not isinstance(cells, list) or not cells:
                parsed = _text_rows(
                    _text(row.get("text") or row.get("rawText")),
                    config,
                )

                for item in parsed:
                    item["reasons"].extend(reasons)

                result.extend(parsed)
                continue

        else:
            continue

        values = [_text(cell) for cell in cells]

        if any(values):
            result.append({"cells": values, "reasons": reasons})

    return result


def _table_rows(table, config):
    """실제 병합 셀의 영역 안에서만 이전 값을 연결한다."""

    values = table.extract()
    owners = {}

    for index, physical in enumerate(table.rows):
        if index >= len(values):
            break

        boxes = [box for box in physical.cells if box is not None]

        if not boxes:
            continue

        minimum_height = min(box[3] - box[1] for box in boxes)
        center = statistics.median(
            (box[1] + box[3]) / 2
            for box in boxes
            if abs((box[3] - box[1]) - minimum_height) < 0.01
        )

        for column, box in enumerate(physical.cells):
            if column >= len(values[index]):
                continue

            if box is not None:
                owners[column] = (box, values[index][column])

            elif column in owners:
                owner, value = owners[column]

                if owner[1] < center < owner[3]:
                    values[index][column] = value

    return _rows(values, config)


def _header_label(value: str) -> str:
    return re.sub(
        r"[\(\[（](?:원|₩|krw|%)[\)\]）]",
        "",
        _compact(value).casefold(),
    )


def _header_role(value: str) -> str | None:
    label = _header_label(value)
    matches = [
        role
        for role, pattern in HEADER_ROLE_PATTERNS
        if re.fullmatch(pattern, label)
    ]

    return matches[0] if len(matches) == 1 else None


def _schema(rows, config):
    dated = [
        (index, row["cells"])
        for index, row in enumerate(rows)
        if any(_date(cell) for cell in row["cells"])
    ]

    if not dated:
        return {}, [], None, "UNKNOWN", False

    width = Counter(
        len(cells) for _, cells in dated
    ).most_common(1)[0][0]

    samples = [
        cells for _, cells in dated if len(cells) == width
    ]

    first = next(
        index for index, cells in dated if len(cells) == width
    )

    roles = {}
    conflicts = set()

    for row in rows[:first]:
        if len(row["cells"]) != width:
            continue

        for column, cell in enumerate(row["cells"]):
            role = _header_role(cell)

            if role is None:
                continue

            if role in roles and roles[role] != column:
                conflicts.add(role)
            else:
                roles[role] = column

    for role in conflicts:
        roles.pop(role, None)

    # 실제 금액 열이 겹치면 다른 숫자 열로 대체하지 않는다.
    ambiguous_amount = "net" in conflicts or (
        "net" not in roles and "amount" in conflicts
    )

    def ratio(column, predicate):
        values = [
            row[column] for row in samples if not _empty(row[column])
        ]

        return (
            sum(predicate(value) for value in values) / len(values)
            if values
            else 0
        )

    for role, predicate in (
        ("date", lambda value: _date(value) is not None),
        ("type", lambda value: _direction(value, config) != "UNKNOWN"),
        ("tax_rate", lambda value: _rate(value) is not None),
    ):
        if role in roles:
            continue

        candidates = [
            column
            for column in range(width)
            if column not in roles.values()
            and ratio(column, predicate) >= config["column_match_ratio"]
        ]

        if len(candidates) == 1:
            roles[role] = candidates[0]

    numeric = [
        column
        for column in range(width)
        if column not in roles.values()
        and ("date" not in roles or column >= roles["date"])
        and ratio(
            column, lambda value: _amount(value) is not None
        ) >= config["column_match_ratio"]
    ]

    # 숫자 열은 설명 후보에서 제외한다. 금액 열의 역할은 헤더에서 확인한다.
    excluded = set(roles.values()) | set(numeric)

    descriptions = (
        []
        if "description" in roles
        else [
            column
            for column in range(width)
            if column not in excluded
            and any(not _empty(row[column]) for row in samples)
            and not all(
                _empty(row[column])
                or re.fullmatch(r"[\d*＊-]+", _compact(row[column]))
                for row in samples
            )
        ]
    )

    hint = _direction_hint(
        " ".join(" ".join(row["cells"]) for row in rows[:first])
    )

    return roles, descriptions, width, hint, ambiguous_amount


def _parse(rows, page, config, page_hint):
    roles, descriptions, width, table_hint, ambiguous_amount = _schema(
        rows, config
    )

    if width is None:
        return []

    result = []

    for row in rows:
        cells = row["cells"]
        dates = {_date(cell) for cell in cells} - {None}

        if not dates:
            continue

        valid = len(cells) == width

        def get(role):
            column = roles.get(role)
            return cells[column] if valid and column is not None else ""

        when = (
            _date(get("date"))
            if valid and "date" in roles
            else next(iter(dates))
            if len(dates) == 1
            else None
        )

        typ = _direction(get("type"), config)

        if typ == "UNKNOWN" and valid:
            kinds = {_direction(cell, config) for cell in cells} - {"UNKNOWN"}

            typ = (
                next(iter(kinds))
                if len(kinds) == 1
                else table_hint
                if table_hint != "UNKNOWN"
                else page_hint
            )

        description = get("description") or (
            " ".join(
                cells[column]
                for column in descriptions
                if not _empty(cells[column])
                and not IDENTIFIER_PATTERN.search(cells[column])
            )
            if valid
            else " | ".join(cells)
        )

        gross = _amount(get("gross"))
        tax = _amount(get("tax"))
        rate = _rate(get("tax_rate"))

        amount = None
        basis = "UNSPECIFIED"

        if valid and not ambiguous_amount:
            if "net" in roles:
                amount = _amount(get("net"))
                basis = "ACTUAL"

            elif "amount" in roles:
                amount = _amount(get("amount"))

            elif gross is not None and tax is not None:
                amount = gross - tax
                basis = "ACTUAL"

        reasons = list(row["reasons"])

        if not valid:
            reasons.append("컬럼 수가 달라 행 구조 확인 필요")
        elif ambiguous_amount:
            reasons.append("실제 입출금액 후보 열이 여러 개입니다.")
        elif amount is None:
            reasons.append("실제 입출금액을 확인할 수 없습니다.")

        # 원문에 세액이 없으면 추출한 세전 금액과 실제 금액에서 계산한다.
        if typ == "INCOME" and gross is not None and amount is not None and tax is None:
            tax = gross - amount

        reasons = list(dict.fromkeys(reasons))

        result.append(
            {
                "transactionId": None,
                "taxInputId": None,
                "transactionDate": when,
                "transactionMonth": None,
                "documentYear": int(when[:4]) if when else None,
                "transactionType": typ,
                "description": _mask(description),
                "category": "UNCLASSIFIED",
                "categoryName": _mask(get("category")) or None,
                "amount": amount,
                "actualAmount": amount,
                "preTaxIncome": gross if typ == "INCOME" else None,
                "taxDeducted": tax if typ == "INCOME" else None,
                "deductedTaxRate": (
                    float(rate)
                    if rate is not None and typ == "INCOME"
                    else None
                ),
                "pageNumber": page,
                "rawText": _mask(" | ".join(cells)),
                "valueKind": "DETAIL",
                "amountBasis": basis,
                "parseSuccess": amount is not None and bool(description),
                "needsReview": bool(reasons),
                "excluded": bool(reasons),
                "failureReason": " / ".join(reasons) if reasons else None,
            }
        )

    return result


def _is_transaction_table(rows: list[dict[str, Any]]) -> bool:
    amount_roles = {"amount", "net", "gross"}
    header_widths = set()

    for row in rows:
        cells = row["cells"]

        # 표 감지와 실제 거래 추출에 동일한 헤더 판별을 적용한다.
        if any(_header_role(cell) in amount_roles for cell in cells):
            header_widths.add(len(cells))
            continue

        if len(cells) in header_widths and any(_date(cell) for cell in cells):
            return True

    return False


def _needs_image_ocr(
    page: pymupdf.Page,
    images: list[dict[str, Any]],
    min_area_ratio: float,
) -> bool:
    page_area = page.rect.width * page.rect.height

    for image in images:
        left, top, right, bottom = image["bbox"]
        image_area = (right - left) * (bottom - top)

        if image_area >= page_area * min_area_ratio:
            return True

    return False


def _extract_page_rows(
    page: pymupdf.Page,
    settings: dict[str, Any],
) -> tuple[str, list[list[dict[str, Any]]], str]:
    native_textpage = page.get_textpage(flags=pymupdf.TEXTFLAGS_WORDS)
    native_text = native_textpage.extractText()
    native_words = ocr_engine.extract_words(native_textpage)

    images = page.get_image_info()
    native_method = "PDF_POSITION"
    native_groups = []

    if native_words and not _needs_image_ocr(
        page,
        images,
        settings["min_ocr_image_area_ratio"],
    ):
        # 서로 다른 표의 열 구조가 섞이지 않도록 표마다 행을 따로 보관한다.
        table_groups = []

        for table in page.find_tables().tables:
            rows = _table_rows(table, settings)

            if _is_transaction_table(rows):
                table_groups.append(rows)

        if table_groups:
            native_method = "PDF_TABLE"
            native_groups = table_groups
        else:
            rows = _rows(
                words_to_rows(native_words, settings, align_transaction_columns=True),
                settings,
            )
            native_groups = [rows] if rows else []

        # 작은 이미지는 내장 거래 구조가 확인된 경우에만 OCR에서 제외한다.
        if not images or any(
            _is_transaction_table(group) for group in native_groups
        ):
            return native_method, native_groups, native_text

    if not images and not page.get_drawings():
        return "NONE", [], native_text

    ocr_words = ocr_engine.recognize_words(page)
    ocr_text = " ".join(word["text"] for word in ocr_words)

    if native_words and ocr_words:
        # 혼합형은 단어를 병합한 뒤 행을 구성한다.
        merged_words = merge_parser.merge_words(native_words, ocr_words)
        rows = _rows(merge_parser.words_to_rows(merged_words), settings)
        return "PDF_MERGED", [rows], native_text + "\n" + ocr_text

    if native_words:
        if not native_groups:
            rows = _rows(words_to_rows(native_words, settings), settings)
            native_groups = [rows]

        return native_method, native_groups, native_text

    rows = _rows(ocr_parser.words_to_rows(ocr_words), settings)
    return "OCR", [rows], ocr_text


def _read_pdf(
    pdf_bytes: bytes,
    settings: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[str]]:
    transactions = []
    pages = []
    document_parts = []

    # PDF 원본을 한 번 열고 페이지마다 선택된 행을 거래로 변환한다.
    try:
        pdf = pymupdf.open(stream=pdf_bytes, filetype="pdf")
    except pymupdf.FileDataError as exc:
        raise ValueError("손상되었거나 분석할 수 없는 PDF 파일입니다.") from exc

    with pdf:
        if pdf.needs_pass:
            raise ValueError("암호화된 PDF는 지원하지 않습니다.")

        if not 1 <= len(pdf) <= settings["max_pages"]:
            raise ValueError("PDF 페이지 수가 허용 범위를 벗어났습니다.")

        for number, page in enumerate(pdf, start=1):
            method, groups, page_text = _extract_page_rows(page, settings)
            document_parts.append(page_text)

            hint = _direction_hint("\n".join(page_text.splitlines()[:15]))
            selected = [
                item
                for rows in groups
                for item in _parse(rows, number, settings, hint)
            ]
            transactions.extend(selected)

            pages.append(
                {
                    "pageNumber": number,
                    "method": method if selected else "NONE",
                    "extractedCount": len(selected),
                    "parsedCount": sum(
                        item["parseSuccess"] for item in selected
                    ),
                    "needsReviewCount": sum(
                        item["needsReview"] for item in selected
                    ),
                    "failureReason": (
                        None if selected else "날짜별 거래를 찾지 못했습니다."
                    ),
                }
            )

    return transactions, pages, document_parts


def extract_transactions(
    pdf_bytes: bytes,
    *,
    category_resolver: CategoryResolver | None = None,
    config: dict[str, Any] | None = None,
) -> dict[str, Any]:
    settings = _settings(config)

    transactions, pages, document_parts = _PDF_EXECUTOR.submit(
        _read_pdf,
        pdf_bytes,
        settings,
    ).result()

    document_text = "\n".join(document_parts)

    if not transactions:
        heading = _compact("\n".join(part[:1500] for part in document_parts))

        if (
            re.search(r"집계|월별|지출처별|합계금액", heading)
            and not DATE_PATTERN.search(document_text)
        ):
            raise ValueError("PDF_DAILY_DETAILS_REQUIRED")

        raise ValueError("PDF_TRANSACTIONS_NOT_FOUND")

    if category_resolver is not None:
        masked_document = _mask(document_text)

        for item in transactions:
            resolved = category_resolver(
                {
                    "categoryName": item["categoryName"],
                    "description": item["description"],
                    "rawText": item["rawText"],
                    "pageNumber": item["pageNumber"],
                    "documentYear": item["documentYear"],
                    "documentText": masked_document,
                }
            )
            item["category"] = _text(resolved) or "UNCLASSIFIED"

    parsed = sum(item["parseSuccess"] for item in transactions)

    return {
        "totalPages": len(pages),
        "extractedCount": len(transactions),
        "parsedCount": parsed,
        "failedCount": len(transactions) - parsed,
        "needsReviewCount": sum(item["needsReview"] for item in transactions),
        "pageResults": pages,
        "transactions": transactions,
        "tables": [],
    }
