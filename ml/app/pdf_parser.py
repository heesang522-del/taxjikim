"""PDF와 Spring OCR 결과에서 날짜별 거래를 추출한다."""

from __future__ import annotations

import math
import re
import statistics

from collections import Counter
from datetime import date
from decimal import Decimal, InvalidOperation
from io import BytesIO
from typing import Any, Callable

import pdfplumber


CategoryResolver = Callable[
    [dict[str, Any]],
    str | None,
]

DATE_PATTERN = re.compile(
    r"(?<!\d)((?:19|20)\d{2})\s*[./\-년]\s*"
    r"(\d{1,2})\s*[./\-월]\s*(\d{1,2})\s*일?(?!\d)"
)

NUMBER_PATTERN = re.compile(
    r"[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?"
)

IDENTIFIER_PATTERN = re.compile(
    r"(?<!\d)(?:"
    r"\d{6}-?[\d*]{7}"
    r"|\d{3}-\d{2}-\d{5}"
    r")(?!\d)"
)

DEFAULTS = {
    "max_pages": 50,
    "max_amount": 9_007_199_254_740_991,
    "column_match_ratio": 0.9,
    "amount_tolerance": 1,
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

ROLES = {
    "date",
    "type",
    "description",
    "amount",
    "net",
    "gross",
    "tax",
    "tax_rate",
    "category",
    "sequence",
}


def _text(value):
    return str(
        value if value is not None else ""
    ).strip()


def _compact(value):
    source = re.sub(
        r"[\u2010-\u2015\u2212]",
        "-",
        _text(value),
    )

    return re.sub(r"\s+", "", source)


def _mask(value):
    return IDENTIFIER_PATTERN.sub(
        "***-****-****",
        _text(value),
    )


def _settings(config):
    if config is not None and not isinstance(config, dict):
        raise ValueError("config는 사전이어야 합니다.")

    unknown = set(config or {}) - set(DEFAULTS)

    if unknown:
        raise ValueError(
            "지원하지 않는 설정입니다: "
            + ", ".join(sorted(unknown))
        )

    result = {
        **DEFAULTS,
        **(config or {}),
    }

    for key in (
        "max_pages",
        "max_amount",
        "amount_tolerance",
    ):
        minimum = (
            0 if key == "amount_tolerance" else 1
        )

        if (
            type(result[key]) is not int
            or result[key] < minimum
        ):
            raise ValueError(
                f"{key} 설정을 확인해주세요."
            )

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
            raise ValueError(
                f"{key}는 양의 유한한 숫자여야 합니다."
            )

    if result["column_match_ratio"] > 1:
        raise ValueError(
            "컬럼 일치 비율은 1 이하여야 합니다."
        )

    directions = result["direction_aliases"]

    if (
        not isinstance(directions, dict)
        or any(
            not isinstance(key, str)
            or value not in {"INCOME", "EXPENSE"}
            for key, value in directions.items()
        )
    ):
        raise ValueError(
            "거래 방향 별칭을 확인해주세요."
        )

    result["direction_aliases"] = {
        _compact(key).upper(): value
        for key, value in directions.items()
    }

    return result


def _empty(value):
    return _compact(value) in {
        "",
        "-",
        "없음",
        "해당없음",
    }


def _date(value):
    found = set()

    source = re.sub(
        r"[\u2010-\u2015\u2212]",
        "-",
        _text(value),
    )

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

    return (
        next(iter(found))
        if len(found) == 1
        else None
    )


def _amount(value, config):
    source = re.sub(
        r",\s+(?=\d)",
        ",",
        _text(value),
    )

    source = re.sub(
        r"^(?:₩|KRW)\s*|\s*(?:원|₩|KRW)$",
        "",
        source,
        flags=re.I,
    ).strip()

    if not NUMBER_PATTERN.fullmatch(source):
        return None

    try:
        number = Decimal(
            source.replace(",", "")
        )
    except InvalidOperation:
        return None

    if (
        not number.is_finite()
        or number < 0
        or number > config["max_amount"]
        or number != number.to_integral_value()
    ):
        return None

    return int(number)


def _rate(value):
    match = re.fullmatch(
        r"([+-]?\d+(?:\.\d+)?)%",
        _compact(value),
    )

    if not match:
        return None

    number = Decimal(match[1])

    return (
        number
        if number.is_finite()
        and 0 <= number <= 100
        else None
    )


def _direction(value, config):
    return config["direction_aliases"].get(
        _compact(value).upper(),
        "UNKNOWN",
    )


def _direction_hint(source):
    """제목이나 헤더에 명시된 거래 방향만 사용한다."""

    heading = _compact(source)

    expense = bool(
        re.search(
            r"지출(?:내역|금액)|사용금액|출금액",
            heading,
        )
    )

    income = bool(
        re.search(
            r"수입(?:내역|금액)|입금액|세전수입",
            heading,
        )
    )

    if expense and not income:
        return "EXPENSE"

    if income and not expense:
        return "INCOME"

    return "UNKNOWN"


def words_to_rows(words, config=None):
    """Spring의 단어 좌표를 행과 셀로 복원한다."""

    settings = _settings(config)
    prepared = []

    for word in words:
        if (
            not isinstance(word, dict)
            or not _text(word.get("text"))
        ):
            continue

        try:
            x0, x1, top = (
                float(word[key])
                for key in ("x0", "x1", "top")
            )
        except (
            KeyError,
            TypeError,
            ValueError,
            OverflowError,
        ):
            continue

        if (
            all(
                math.isfinite(value)
                for value in (x0, x1, top)
            )
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

    for word in sorted(
        prepared,
        key=lambda item: (
            item["top"],
            item["x0"],
        ),
    ):
        nearest = min(
            lines,
            key=lambda line: abs(
                statistics.median(
                    item["top"]
                    for item in line
                ) - word["top"]
            ),
            default=None,
        )

        if (
            nearest is None
            or abs(
                statistics.median(
                    item["top"]
                    for item in nearest
                ) - word["top"]
            ) > settings["row_top_tolerance"]
        ):
            lines.append([word])
        else:
            nearest.append(word)

    result = []

    for line in sorted(
        lines,
        key=lambda row: statistics.median(
            word["top"]
            for word in row
        ),
    ):
        line.sort(
            key=lambda word: word["x0"]
        )

        gap = max(
            settings["min_cell_gap"],
            statistics.median(
                (word["x1"] - word["x0"])
                / max(
                    1,
                    len(_compact(word["text"])),
                )
                for word in line
            ) * settings["cell_gap_factor"],
        )

        cells = []
        current = []
        right = None

        for word in line:
            if (
                current
                and right is not None
                and word["x0"] - right > gap
            ):
                cells.append(" ".join(current))
                current = []
                right = None

            current.append(word["text"])

            right = (
                word["x1"]
                if right is None
                else max(right, word["x1"])
            )

        if current:
            cells.append(" ".join(current))

        result.append(
            {
                "cells": cells,
                "text": " | ".join(cells),
            }
        )

    return result


def _text_rows(source, config):
    result = []

    for line in source.splitlines():
        line = line.strip()

        if not line:
            continue

        if "|" in line:
            cells = [
                _text(cell)
                for cell in line.split("|")
            ]

            if cells and not cells[0]:
                cells = cells[1:]

            if cells and not cells[-1]:
                cells = cells[:-1]

        else:
            cells = re.split(
                r"\t+| {2,}",
                line,
            )

            match = DATE_PATTERN.match(line)

            if len(cells) == 1 and match:
                tokens = line[match.end():].split()
                cells = [match.group(0)]

                if (
                    tokens
                    and _direction(tokens[0], config)
                    != "UNKNOWN"
                ):
                    cells.append(tokens.pop(0))

                trailing = []

                while tokens and (
                    _amount(tokens[-1], config) is not None
                    or _rate(tokens[-1]) is not None
                    or _empty(tokens[-1])
                ):
                    trailing.append(tokens.pop())

                cells.extend(
                    [
                        " ".join(tokens),
                        *reversed(trailing),
                    ]
                )

        result.append(
            {
                "cells": [
                    _text(cell)
                    for cell in cells
                ],
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
                reasons = [
                    "행 복원 결과 확인 필요"
                ]

            cells = row.get("cells")

            if not isinstance(cells, list) or not cells:
                parsed = _text_rows(
                    _text(
                        row.get("text")
                        or row.get("rawText")
                    ),
                    config,
                )

                for item in parsed:
                    item["reasons"].extend(reasons)

                result.extend(parsed)
                continue

        else:
            continue

        values = [
            _text(cell)
            for cell in cells
        ]

        if any(values):
            result.append(
                {
                    "cells": values,
                    "reasons": reasons,
                }
            )

    return result


def _table_rows(table, config):
    """실제 병합 셀의 영역 안에서만 값을 연결한다."""

    values = table.extract()
    owners = {}

    for index, physical in enumerate(table.rows):
        if index >= len(values):
            break

        boxes = [
            box
            for box in physical.cells
            if box is not None
        ]

        if not boxes:
            continue

        minimum_height = min(
            box[3] - box[1]
            for box in boxes
        )

        # 날짜·금액처럼 병합되지 않은 셀로 현재 행의 중심을 구한다.
        center = statistics.median(
            (box[1] + box[3]) / 2
            for box in boxes
            if abs(
                (box[3] - box[1]) - minimum_height
            ) < 0.01
        )

        for column, box in enumerate(
            physical.cells
        ):
            if column >= len(values[index]):
                continue

            if box is not None:
                owners[column] = (
                    box,
                    values[index][column],
                )

            elif column in owners:
                owner, value = owners[column]

                # 빈칸을 무조건 이전 값으로 채우지 않는다.
                # 원래 병합 셀의 세로 영역 안에 있을 때만 연결한다.
                if owner[1] < center < owner[3]:
                    values[index][column] = value

    return _rows(values, config)


def _aliases(values):
    result = {}

    for role, names in (values or {}).items():
        if (
            role not in ROLES
            or not isinstance(names, list)
            or any(
                not isinstance(value, str)
                for value in names
            )
        ):
            raise ValueError(
                "헤더 별칭 설정을 확인해주세요."
            )

        result[role] = {
            _compact(value).casefold()
            for value in names
        }

    return result


def _schema(rows, aliases, config):
    dated = [
        (index, row["cells"])
        for index, row in enumerate(rows)
        if any(
            _date(cell)
            for cell in row["cells"]
        )
    ]

    if not dated:
        return {}, [], None, "UNKNOWN"

    width = Counter(
        len(cells)
        for _, cells in dated
    ).most_common(1)[0][0]

    samples = [
        cells
        for _, cells in dated
        if len(cells) == width
    ]

    first = next(
        index
        for index, cells in dated
        if len(cells) == width
    )

    roles = {}
    conflicts = set()

    for row in rows[:first]:
        if len(row["cells"]) != width:
            continue

        for column, cell in enumerate(
            row["cells"]
        ):
            label = re.sub(
                r"[\(\[（](?:원|₩|krw|%)[\)\]）]",
                "",
                _compact(cell).casefold(),
            )

            matched = [
                role
                for role, names in aliases.items()
                if label in names
            ]

            if len(matched) == 1:
                role = matched[0]

                if (
                    role in roles
                    and roles[role] != column
                ):
                    conflicts.add(role)
                else:
                    roles[role] = column

    for role in conflicts:
        roles.pop(role, None)

    def ratio(column, predicate):
        values = [
            row[column]
            for row in samples
            if not _empty(row[column])
        ]

        return (
            sum(
                predicate(value)
                for value in values
            ) / len(values)
            if values
            else 0
        )

    for role, predicate in (
        (
            "date",
            lambda value: _date(value) is not None,
        ),
        (
            "type",
            lambda value: _direction(value, config)
            != "UNKNOWN",
        ),
        (
            "tax_rate",
            lambda value: _rate(value) is not None,
        ),
    ):
        if role in roles:
            continue

        candidates = [
            column
            for column in range(width)
            if column not in roles.values()
            and ratio(column, predicate)
            >= config["column_match_ratio"]
        ]

        if len(candidates) == 1:
            roles[role] = candidates[0]

    numeric = [
        column
        for column in range(width)
        if column not in roles.values()
        and (
            "date" not in roles
            or column >= roles["date"]
        )
        and ratio(
            column,
            lambda value: (
                _amount(value, config) is not None
            ),
        ) >= config["column_match_ratio"]
    ]

    if (
        "tax_rate" in roles
        and not any(
            key in roles
            for key in ("gross", "net", "amount")
        )
        and len(numeric) == 2
    ):
        pairs = []

        for gross_column, net_column in (
            tuple(numeric),
            tuple(reversed(numeric)),
        ):
            votes = []

            for row in samples:
                gross = _amount(
                    row[gross_column],
                    config,
                )
                net = _amount(
                    row[net_column],
                    config,
                )
                rate = _rate(
                    row[roles["tax_rate"]]
                )

                if (
                    gross is not None
                    and net is not None
                    and rate is not None
                    and rate != 0
                ):
                    expected = (
                        Decimal(gross)
                        * (100 - rate)
                        / 100
                    )

                    votes.append(
                        gross >= net
                        and abs(
                            Decimal(net) - expected
                        ) <= config["amount_tolerance"]
                    )

            if votes and all(votes):
                pairs.append(
                    (gross_column, net_column)
                )

        if len(pairs) == 1:
            roles["gross"], roles["net"] = pairs[0]

    if (
        "net" not in roles
        and "amount" not in roles
        and len(numeric) == 1
    ):
        roles["amount"] = numeric[0]

    excluded = set(roles.values()) | set(numeric)

    descriptions = (
        []
        if "description" in roles
        else [
            column
            for column in range(width)
            if column not in excluded
            and any(
                not _empty(row[column])
                for row in samples
            )
            and not all(
                _empty(row[column])
                or re.fullmatch(
                    r"[\d*＊-]+",
                    _compact(row[column]),
                )
                for row in samples
            )
        ]
    )

    hint = _direction_hint(
        " ".join(
            " ".join(row["cells"])
            for row in rows[:first]
        )
    )

    return roles, descriptions, width, hint


def _parse(
    rows,
    page,
    aliases,
    config,
    page_hint,
):
    roles, descriptions, width, table_hint = _schema(
        rows,
        aliases,
        config,
    )

    if width is None:
        return []

    result = []

    for row in rows:
        cells = row["cells"]

        dates = {
            _date(cell)
            for cell in cells
        } - {None}

        if not dates:
            continue

        valid = len(cells) == width

        def get(role):
            column = roles.get(role)

            return (
                cells[column]
                if valid and column is not None
                else ""
            )

        when = (
            _date(get("date"))
            if valid and "date" in roles
            else next(iter(dates))
            if len(dates) == 1
            else None
        )

        typ = _direction(get("type"), config)

        if typ == "UNKNOWN" and valid:
            kinds = {
                _direction(cell, config)
                for cell in cells
            } - {"UNKNOWN"}

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
                and not IDENTIFIER_PATTERN.search(
                    cells[column]
                )
            )
            if valid
            else " | ".join(cells)
        )

        gross = _amount(get("gross"), config)
        tax = _amount(get("tax"), config)
        rate = _rate(get("tax_rate"))

        amount = None
        basis = "UNSPECIFIED"

        if valid:
            if "net" in roles:
                amount = _amount(
                    get("net"),
                    config,
                )
                basis = "ACTUAL"

            elif "amount" in roles:
                amount = _amount(
                    get("amount"),
                    config,
                )

            elif (
                gross is not None
                and tax is not None
                and gross >= tax
            ):
                amount = gross - tax
                basis = "ACTUAL"

        reasons = list(row["reasons"])

        if not valid:
            reasons.append(
                "컬럼 수가 달라 행 구조 확인 필요"
            )

        if (
            typ == "INCOME"
            and gross is not None
            and amount is not None
        ):
            if amount > gross:
                reasons.append(
                    "실제 입금액이 세전 금액보다 큽니다."
                )
            else:
                difference = gross - amount

                if (
                    tax is not None
                    and tax != difference
                ):
                    reasons.append(
                        "세액과 세전·실제 금액의 차이가 다릅니다."
                    )

                if tax is None:
                    tax = difference

                if (
                    rate is not None
                    and abs(
                        Decimal(difference)
                        - Decimal(gross) * rate / 100
                    ) > config["amount_tolerance"]
                ):
                    reasons.append(
                        "세율과 실제 금액이 일치하지 않습니다."
                    )

        for invalid, reason in (
            (when is None, "거래 일자 확인 필요"),
            (amount is None, "금액 확인 필요"),
            (not description, "항목 확인 필요"),
            (
                typ == "UNKNOWN",
                "수입 및 지출 구분 확인 필요",
            ),
        ):
            if invalid:
                reasons.append(reason)

        reasons = list(dict.fromkeys(reasons))

        result.append(
            {
                "transactionId": None,
                "taxInputId": None,
                "transactionDate": when,
                "transactionMonth": None,
                "documentYear": (
                    int(when[:4]) if when else None
                ),
                "transactionType": typ,
                "description": _mask(description),
                "category": "UNCLASSIFIED",
                "categoryName": (
                    _mask(get("category")) or None
                ),
                "amount": amount,
                "actualAmount": amount,
                "preTaxIncome": (
                    gross if typ == "INCOME" else None
                ),
                "taxDeducted": (
                    tax if typ == "INCOME" else None
                ),
                "deductedTaxRate": (
                    float(rate)
                    if rate is not None
                    and typ == "INCOME"
                    else None
                ),
                "pageNumber": page,
                "rawText": _mask(
                    " | ".join(cells)
                ),
                "valueKind": "DETAIL",
                "amountBasis": basis,
                "parseSuccess": (
                    amount is not None
                    and bool(description)
                ),
                "needsReview": bool(reasons),
                "excluded": bool(reasons),
                "failureReason": (
                    " / ".join(reasons)
                    if reasons
                    else None
                ),
            }
        )

    return result


def _score(items):
    return (
        sum(
            item["parseSuccess"]
            and not item["excluded"]
            for item in items
        ),
        sum(
            item["parseSuccess"]
            for item in items
        ),
        len(items),
    )


def extract_transactions(
    pdf_bytes: bytes,
    ocr_pages: list[dict] | None = None,
    *,
    header_aliases: dict[str, list[str]] | None = None,
    category_resolver: CategoryResolver | None = None,
    config: dict[str, Any] | None = None,
) -> dict[str, Any]:
    settings = _settings(config)
    aliases = _aliases(header_aliases)

    if (
        not isinstance(pdf_bytes, bytes)
        or not pdf_bytes.startswith(b"%PDF-")
    ):
        raise ValueError(
            "올바른 PDF 파일이 아닙니다."
        )

    ocr_map = {
        page["pageNumber"]: page
        for page in (ocr_pages or [])
        if isinstance(page, dict)
        and type(page.get("pageNumber")) is int
    }

    transactions = []
    pages = []
    document_parts = []
    image_without_ocr = False

    with pdfplumber.open(BytesIO(pdf_bytes)) as pdf:
        if pdf.doc.encryption is not None:
            raise ValueError(
                "암호화된 PDF는 지원하지 않습니다."
            )

        if not 1 <= len(pdf.pages) <= settings["max_pages"]:
            raise ValueError(
                "PDF 페이지 수가 허용 범위를 벗어났습니다."
            )

        for number, page in enumerate(
            pdf.pages,
            1,
        ):
            native = page.extract_text() or ""
            ocr = ocr_map.get(number) or {}

            ocr_rows = _rows(
                ocr.get("rows") or [],
                settings,
            )

            # 이미 구성된 행이 없을 때만 단어 좌표를 복원한다.
            if not ocr_rows and ocr.get("words"):
                ocr_rows = _rows(
                    words_to_rows(
                        ocr["words"],
                        settings,
                    ),
                    settings,
                )

            ocr_text = _text(
                ocr.get("text")
            ) or "\n".join(
                " | ".join(row["cells"])
                for row in ocr_rows
            )

            image_without_ocr |= bool(
                not native.strip()
                and page.images
                and not ocr_rows
                and not ocr_text.strip()
            )

            document_parts.append(
                native or ocr_text
            )

            hint = _direction_hint(
                "\n".join(
                    (native or ocr_text).splitlines()[:15]
                )
            )

            sources = [
                (
                    "PDF_TABLE",
                    [
                        _table_rows(table, settings)
                        for table in page.find_tables()
                    ],
                ),
                (
                    "PDF_POSITION",
                    [
                        _rows(
                            words_to_rows(
                                page.extract_words(
                                    x_tolerance=(
                                        settings["word_x_tolerance"]
                                    ),
                                    y_tolerance=(
                                        settings["word_y_tolerance"]
                                    ),
                                    keep_blank_chars=False,
                                ),
                                settings,
                            ),
                            settings,
                        )
                    ],
                ),
                (
                    "PDF_TEXT",
                    [_rows(native, settings)],
                ),
                (
                    "OCR",
                    [ocr_rows],
                ),
            ]

            # 구조화된 행의 검토 정보를 잃지 않도록
            # OCR 일반 텍스트는 대체 경로로만 사용한다.
            if not ocr_rows and ocr_text:
                sources.append(
                    (
                        "OCR",
                        [_rows(ocr_text, settings)],
                    )
                )

            candidates = [
                (
                    method,
                    [
                        item
                        for group in groups
                        for item in _parse(
                            group,
                            number,
                            aliases,
                            settings,
                            hint,
                        )
                    ],
                )
                for method, groups in sources
            ]

            method, selected = max(
                candidates,
                key=lambda pair: _score(pair[1]),
            )

            transactions.extend(selected)

            pages.append(
                {
                    "pageNumber": number,
                    "method": (
                        method if selected else "NONE"
                    ),
                    "extractedCount": len(selected),
                    "parsedCount": sum(
                        item["parseSuccess"]
                        for item in selected
                    ),
                    "needsReviewCount": sum(
                        item["needsReview"]
                        for item in selected
                    ),
                    "failureReason": (
                        None
                        if selected
                        else "날짜별 거래를 찾지 못했습니다."
                    ),
                }
            )

    document_text = "\n".join(document_parts)

    if not transactions:
        if image_without_ocr:
            raise ValueError(
                "PDF_OCR_REQUIRED"
            )

        heading = _compact(
            "\n".join(
                part[:1500]
                for part in document_parts
            )
        )

        if (
            re.search(
                r"집계|월별|지출처별|합계금액",
                heading,
            )
            and not DATE_PATTERN.search(document_text)
        ):
            raise ValueError(
                "PDF_DAILY_DETAILS_REQUIRED"
            )

        raise ValueError(
            "PDF_TRANSACTIONS_NOT_FOUND"
        )

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

            item["category"] = (
                _text(resolved) or "UNCLASSIFIED"
            )

    parsed = sum(
        item["parseSuccess"]
        for item in transactions
    )

    return {
        "totalPages": len(pages),
        "extractedCount": len(transactions),
        "parsedCount": parsed,
        "failedCount": len(transactions) - parsed,
        "needsReviewCount": sum(
            item["needsReview"]
            for item in transactions
        ),
        "pageResults": pages,
        "transactions": transactions,
        "tables": [],
    }