"""PDF에서 일별 거래와 월별 집계를 추출한다."""

from __future__ import annotations

import re
import statistics

from collections import Counter
from datetime import date
from decimal import Decimal, InvalidOperation
from io import BytesIO
from typing import Any, Callable

import pdfplumber


MAX_PAGES = 50
MAX_SAFE_INTEGER = 9_007_199_254_740_991

# 헤더 이름이 아닌 데이터 형식을 확인한다.
DATE_PATTERN = re.compile(
    r"(?<!\d)"
    r"((?:19|20)\d{2})"
    r"\s*[./\-년]\s*"
    r"(\d{1,2})"
    r"\s*[./\-월]\s*"
    r"(\d{1,2})"
    r"\s*일?(?!\d)"
)

YEAR_PATTERN = re.compile(
    r"(?<!\d)((?:19|20)\d{2})\s*년\s*귀속"
)

MONTH_PATTERN = re.compile(
    r"^(0?[1-9]|1[0-2])\s*월$"
)

NUMBER_PATTERN = re.compile(
    r"[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?"
)

RATE_PATTERN = re.compile(
    r"^([+-]?\d+(?:\.\d+)?)\s*%$"
)

IDENTIFIER_PATTERN = re.compile(
    r"(?<!\d)(?:"
    r"\d{6}-?[\d*]{7}"
    r"|\d{3}-\d{2}-\d{5}"
    r")(?!\d)"
)

EMPTY_VALUES = {
    "",
    "-",
    "—",
    "–",
    "없음",
    "해당없음",
}

# 거래 방향은 문서 종류가 아닌 반환 데이터의 표준 값이다.
DIRECTION_VALUES = {
    "수입": "INCOME",
    "입금": "INCOME",
    "INCOME": "INCOME",
    "지출": "EXPENSE",
    "출금": "EXPENSE",
    "EXPENSE": "EXPENSE",
}

SUPPORTED_ROLES = {
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

CategoryResolver = Callable[
    [dict[str, Any]],
    str | None,
]


def text(value: object) -> str:
    return str(
        value if value is not None else ""
    ).strip()


def compact(value: object) -> str:
    return re.sub(
        r"\s+",
        "",
        text(value),
    )


def normalize_text(value: object) -> str:
    source = text(value)

    source = re.sub(
        r"[\u2010-\u2015\u2212]",
        "-",
        source,
    )

    return source


def normalize_header(value: object) -> str:
    source = compact(value).casefold()

    # 단위 표기를 제거하되 헤더 이름 자체는 보존한다.
    return re.sub(
        r"[\(\[（](?:원|₩|krw|%)[\)\]）]",
        "",
        source,
    )


def mask_text(value: object) -> str:
    return IDENTIFIER_PATTERN.sub(
        "***-****-****",
        text(value),
    )


def is_empty(value: object) -> bool:
    return compact(value) in EMPTY_VALUES


def parse_date(value: object) -> str | None:
    source = normalize_text(value)
    found = set()

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
            continue

    if len(found) != 1:
        return None

    return next(iter(found))


def parse_amount(value: object) -> int | None:
    source = normalize_text(value)

    source = re.sub(
        r",\s+(?=\d)",
        ",",
        source,
    )

    source = re.sub(
        r"^(?:₩|KRW)\s*",
        "",
        source,
        flags=re.IGNORECASE,
    )

    source = re.sub(
        r"\s*(?:원|₩|KRW)$",
        "",
        source,
        flags=re.IGNORECASE,
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
        or number != number.to_integral_value()
        or number > MAX_SAFE_INTEGER
    ):
        return None

    return int(number)


def parse_rate(value: object) -> Decimal | None:
    source = normalize_text(value)
    match = RATE_PATTERN.fullmatch(source)

    if not match:
        return None

    try:
        rate = Decimal(match[1])
    except InvalidOperation:
        return None

    if (
        not rate.is_finite()
        or rate < 0
        or rate > 100
    ):
        return None

    return rate


def direction(value: object) -> str:
    return DIRECTION_VALUES.get(
        compact(value).upper(),
        "UNKNOWN",
    )


def find_document_context(
    source: str,
) -> dict[str, Any]:
    years = {
        int(match[1])
        for match in YEAR_PATTERN.finditer(source)
    }

    return {
        "year": (
            next(iter(years))
            if len(years) == 1
            else None
        ),
        # 제목에 특정 단어가 있다는 이유로
        # 문서 전체의 카테고리를 확정하지 않는다.
        "sourceText": source,
    }


def configured_role(
    value: object,
    header_aliases: dict[str, list[str]],
) -> str | None:
    """외부에서 전달한 헤더 별칭만 명시적 매핑에 사용한다."""

    source = normalize_header(value)
    matches = []

    for role, aliases in header_aliases.items():
        if role not in SUPPORTED_ROLES:
            continue

        if any(
            source == normalize_header(alias)
            for alias in aliases
        ):
            matches.append(role)

    if len(matches) != 1:
        return None

    return matches[0]


def resolve_category(
    *,
    category_text: str,
    description: str,
    raw: str,
    page: int,
    context: dict[str, Any],
    resolver: CategoryResolver | None,
) -> tuple[str, str | None]:
    """원문 카테고리와 외부 분류 결과를 분리한다."""

    category_name = (
        mask_text(category_text)
        if category_text
        else None
    )

    if resolver is None:
        return "UNCLASSIFIED", category_name

    resolved = resolver(
        {
            "categoryName": category_name,
            "description": mask_text(description),
            "rawText": mask_text(raw),
            "pageNumber": page,
            "documentYear": context.get("year"),
            "documentText": mask_text(
                context.get("sourceText", "")
            ),
        }
    )

    category_code = text(resolved)

    return (
        category_code or "UNCLASSIFIED",
        category_name,
    )


def make_item(
    *,
    page: int,
    raw: str,
    description: str,
    amount: int | None,
    typ: str,
    year: int | None,
    when: str | None = None,
    month: str | None = None,
    value_kind: str = "DETAIL",
    amount_basis: str = "UNSPECIFIED",
    gross: int | None = None,
    tax: int | None = None,
    tax_rate: Decimal | None = None,
    category: str = "UNCLASSIFIED",
    category_name: str | None = None,
    reasons: list[str] | None = None,
) -> dict[str, Any]:
    errors = list(reasons or [])

    if amount is None:
        errors.append("금액 확인 필요")

    if not description:
        errors.append("항목 확인 필요")

    if when is None and year is None:
        errors.append("귀속 연도 확인 필요")

    if typ == "UNKNOWN":
        errors.append("수입 및 지출 구분 확인 필요")

    errors = list(dict.fromkeys(errors))

    extracted = (
        amount is not None
        and bool(description)
    )

    actual_amount = (
        None
        if amount_basis == "DEDUCTION_ELIGIBLE"
        else amount
    )

    return {
        "transactionId": None,
        "taxInputId": None,
        "transactionDate": when,
        "transactionMonth": month,
        "documentYear": year,
        "transactionType": typ,
        "description": mask_text(description),
        "category": category,
        # 문서에 있는 임의의 카테고리 이름을 보존한다.
        "categoryName": category_name,
        "amount": amount,
        "actualAmount": actual_amount,
        "preTaxIncome": (
            gross
            if typ == "INCOME"
            else None
        ),
        "taxDeducted": (
            tax
            if typ == "INCOME"
            else None
        ),
        # 백분율 숫자 단위로 반환한다.
        # 예: 4.1%는 4.1이다.
        "deductedTaxRate": (
            float(tax_rate)
            if tax_rate is not None
            and typ == "INCOME"
            else None
        ),
        "pageNumber": page,
        "rawText": mask_text(raw),
        "valueKind": value_kind,
        "amountBasis": amount_basis,
        "parseSuccess": extracted,
        "needsReview": bool(errors),
        "excluded": (
            bool(errors)
            or amount_basis == "DEDUCTION_ELIGIBLE"
        ),
        "failureReason": (
            " / ".join(errors)
            if errors
            else None
        ),
    }


def group_word_lines(
    words: list[dict[str, Any]],
) -> list[list[dict[str, Any]]]:
    """글자의 세로 위치와 높이를 기준으로 행을 구성한다."""

    lines: list[list[dict[str, Any]]] = []

    ordered = sorted(
        words,
        key=lambda word: (
            float(word["top"]),
            float(word["x0"]),
        ),
    )

    for word in ordered:
        top = float(word["top"])
        height = (
            float(word["bottom"])
            - float(word["top"])
        )

        if not lines:
            lines.append([word])
            continue

        previous = lines[-1]
        previous_top = statistics.median(
            float(item["top"])
            for item in previous
        )

        tolerance = max(
            2.0,
            height * 0.3,
        )

        if abs(top - previous_top) <= tolerance:
            previous.append(word)
        else:
            lines.append([word])

    for line in lines:
        line.sort(
            key=lambda word: float(word["x0"])
        )

    return lines


def geometry_rows(
    page: Any,
) -> list[list[str]]:
    """고정된 컬럼 좌표 없이 단어 간 간격으로 셀을 구성한다."""

    words = page.extract_words(
        x_tolerance=1.5,
        y_tolerance=3,
        keep_blank_chars=False,
    )

    result = []

    for line in group_word_lines(words):
        if not line:
            continue

        heights = [
            float(word["bottom"])
            - float(word["top"])
            for word in line
        ]

        # 일반적인 단어 간 공백보다 큰 간격에서 셀을 나눈다.
        # 실제 추론 결과가 불확실하면 다른 후보와 비교한다.
        gap_limit = max(
            6.0,
            statistics.median(heights) * 0.7,
        )

        cells = []
        current = [text(line[0]["text"])]

        for previous, word in zip(
            line,
            line[1:],
        ):
            gap = (
                float(word["x0"])
                - float(previous["x1"])
            )

            if gap > gap_limit:
                cells.append(" ".join(current))
                current = [text(word["text"])]
            else:
                current.append(text(word["text"]))

        cells.append(" ".join(current))

        if any(cells):
            result.append(cells)

    return result


def split_transaction_line(
    source: str,
) -> list[str] | None:
    """날짜와 행 끝의 숫자 묶음을 이용해 일반 텍스트를 분리한다."""

    line = normalize_text(source)
    match = DATE_PATTERN.match(line)

    if not match:
        return None

    when_text = match.group(0).strip()
    remainder = line[match.end():].strip()

    if not remainder:
        return [when_text]

    tokens = remainder.split()

    typ = None

    if (
        tokens
        and direction(tokens[0]) != "UNKNOWN"
    ):
        typ = tokens.pop(0)

    # 설명에 있는 숫자는 보존하고,
    # 행 끝에서 연속된 숫자·백분율만 별도 셀로 분리한다.
    trailing = []

    while tokens:
        token = tokens[-1]

        if (
            parse_amount(token) is not None
            or parse_rate(token) is not None
            or is_empty(token)
        ):
            trailing.append(tokens.pop())
        else:
            break

    trailing.reverse()

    cells = [when_text]

    if typ is not None:
        cells.append(typ)

    cells.append(" ".join(tokens))
    cells.extend(trailing)

    return cells


def text_rows(source: str) -> list[list[str]]:
    result = []

    for line in source.splitlines():
        line = line.strip()

        if not line:
            continue

        if "|" in line:
            cells = [
                text(cell)
                for cell in line.split("|")
            ]

            if cells and not cells[0]:
                cells = cells[1:]

            if cells and not cells[-1]:
                cells = cells[:-1]

            result.append(cells)
            continue

        cells = re.split(
            r"\t+| {2,}",
            line,
        )

        if len(cells) > 1:
            result.append(
                [text(cell) for cell in cells]
            )
            continue

        transaction = split_transaction_line(line)

        if transaction is not None:
            result.append(transaction)
        else:
            result.append([line])

    return result


def normalize_rows(
    rows: list[dict],
) -> list[list[str]]:
    result = []

    for row in rows:
        if not isinstance(row, dict):
            continue

        cells = row.get("cells")

        if isinstance(cells, list) and cells:
            values = [
                text(cell)
                for cell in cells
            ]

            if any(values):
                result.append(values)

            continue

        source = text(
            row.get(
                "text",
                row.get("rawText", ""),
            )
        )

        result.extend(text_rows(source))

    return result


def column_ratio(
    rows: list[list[str]],
    column: int,
    parser: Callable[[object], Any],
) -> float:
    values = [
        row[column]
        for row in rows
        if column < len(row)
        and not is_empty(row[column])
    ]

    if not values:
        return 0.0

    return sum(
        parser(value) is not None
        for value in values
    ) / len(values)


def infer_schema(
    rows: list[list[str]],
    header_aliases: dict[str, list[str]],
) -> tuple[
    dict[str, int],
    list[int],
    int | None,
]:
    """ヘッダー名だけに依存せず、列の値から役割を推定する。"""

    dated_rows = [
        row
        for row in rows
        if any(
            parse_date(cell) is not None
            for cell in row
        )
    ]

    if not dated_rows:
        return {}, [], None

    width = Counter(
        len(row)
        for row in dated_rows
    ).most_common(1)[0][0]

    samples = [
        row
        for row in dated_rows
        if len(row) == width
    ]

    first_data_index = next(
        index
        for index, row in enumerate(rows)
        if row in samples
    )

    roles: dict[str, int] = {}
    conflicts = set()

    # 외부 설정이 있는 경우에만 헤더를 명시적으로 매핑한다.
    for row in rows[:first_data_index]:
        if len(row) != width:
            continue

        for column, cell in enumerate(row):
            role = configured_role(
                cell,
                header_aliases,
            )

            if role is None:
                continue

            if (
                role in roles
                and roles[role] != column
            ):
                conflicts.add(role)
            else:
                roles[role] = column

    for role in conflicts:
        roles.pop(role, None)

    if "date" not in roles:
        candidates = [
            column
            for column in range(width)
            if column_ratio(
                samples,
                column,
                parse_date,
            ) >= 0.9
        ]

        if len(candidates) == 1:
            roles["date"] = candidates[0]

    occupied = set(roles.values())

    if "type" not in roles:
        candidates = []

        for column in range(width):
            if column in occupied:
                continue

            values = [
                row[column]
                for row in samples
                if not is_empty(row[column])
            ]

            if (
                values
                and sum(
                    direction(value) != "UNKNOWN"
                    for value in values
                ) / len(values) >= 0.9
            ):
                candidates.append(column)

        if len(candidates) == 1:
            roles["type"] = candidates[0]

    occupied = set(roles.values())

    if "tax_rate" not in roles:
        candidates = [
            column
            for column in range(width)
            if column not in occupied
            and column_ratio(
                samples,
                column,
                parse_rate,
            ) >= 0.9
        ]

        if len(candidates) == 1:
            roles["tax_rate"] = candidates[0]

    numeric_columns = []

    for column in range(width):
        if column in roles.values():
            continue

        # 날짜 앞의 숫자는 순번일 가능성이 있어
        # 금액 추론에 자동 사용하지 않는다.
        if (
            "date" in roles
            and column < roles["date"]
        ):
            continue

        if column_ratio(
            samples,
            column,
            parse_amount,
        ) >= 0.9:
            numeric_columns.append(column)

    # 세율과 두 금액의 관계가 일관될 때만
    # 세전 금액과 실제 금액을 추론한다.
    if (
        "tax_rate" in roles
        and "gross" not in roles
        and "net" not in roles
        and len(numeric_columns) == 2
    ):
        valid_pairs = []

        for gross_column, net_column in (
            (
                numeric_columns[0],
                numeric_columns[1],
            ),
            (
                numeric_columns[1],
                numeric_columns[0],
            ),
        ):
            matched = 0
            contradicted = 0

            for row in samples:
                gross = parse_amount(
                    row[gross_column]
                )
                net = parse_amount(
                    row[net_column]
                )
                rate = parse_rate(
                    row[roles["tax_rate"]]
                )

                if (
                    gross is None
                    or net is None
                    or rate is None
                    or rate == 0
                ):
                    continue

                expected = (
                    Decimal(gross)
                    * (Decimal(100) - rate)
                    / 100
                )

                if (
                    gross >= net
                    and abs(
                        Decimal(net) - expected
                    ) <= 1
                ):
                    matched += 1
                else:
                    contradicted += 1

            if matched > 0 and contradicted == 0:
                valid_pairs.append(
                    (gross_column, net_column)
                )

        if len(valid_pairs) == 1:
            roles["gross"], roles["net"] = (
                valid_pairs[0]
            )

    # 숫자 컬럼이 하나일 때만 일반 금액으로 추론한다.
    if (
        "amount" not in roles
        and "net" not in roles
        and len(numeric_columns) == 1
    ):
        roles["amount"] = numeric_columns[0]

    excluded_columns = (
        set(roles.values())
        | set(numeric_columns)
    )

    description_columns = []

    if "description" not in roles:
        for column in range(width):
            if column in excluded_columns:
                continue

            if any(
                not is_empty(row[column])
                for row in samples
            ):
                description_columns.append(column)

    return roles, description_columns, width


def parse_daily_table(
    rows: list[list[str]],
    page: int,
    context: dict[str, Any],
    header_aliases: dict[str, list[str]],
    category_resolver: CategoryResolver | None,
) -> list[dict[str, Any]]:
    roles, description_columns, width = infer_schema(
        rows,
        header_aliases,
    )

    if width is None:
        return []

    result = []

    for cells in rows:
        dates = {
            parsed
            for cell in cells
            if (parsed := parse_date(cell)) is not None
        }

        if not dates:
            continue

        raw = " | ".join(cells)
        reasons = []

        if len(cells) != width:
            result.append(
                make_item(
                    page=page,
                    raw=raw,
                    description=raw,
                    amount=None,
                    typ="UNKNOWN",
                    year=context.get("year"),
                    when=(
                        next(iter(dates))
                        if len(dates) == 1
                        else None
                    ),
                    reasons=[
                        "컬럼 수가 달라 행 구조 확인 필요"
                    ],
                )
            )
            continue

        def get(role: str) -> str:
            column = roles.get(role)

            if column is None:
                return ""

            return cells[column]

        when = (
            parse_date(get("date"))
            if "date" in roles
            else next(iter(dates))
            if len(dates) == 1
            else None
        )

        if when is None:
            reasons.append(
                "거래 일자 확인 필요"
            )

        typ = direction(get("type"))

        if typ == "UNKNOWN":
            kinds = {
                direction(cell)
                for cell in cells
                if direction(cell) != "UNKNOWN"
            }

            if len(kinds) == 1:
                typ = next(iter(kinds))

        description = get("description")

        if not description:
            description = " ".join(
                cells[column]
                for column in description_columns
                if not is_empty(cells[column])
                and not IDENTIFIER_PATTERN.search(
                    cells[column]
                )
            )

        gross = parse_amount(get("gross"))
        tax = parse_amount(get("tax"))
        rate = parse_rate(get("tax_rate"))

        amount = None
        amount_basis = "UNSPECIFIED"

        if "net" in roles:
            amount = parse_amount(get("net"))
            amount_basis = "ACTUAL"
        elif "amount" in roles:
            amount = parse_amount(get("amount"))
        elif (
            gross is not None
            and tax is not None
            and gross >= tax
        ):
            amount = gross - tax
            amount_basis = "ACTUAL"
        else:
            reasons.append(
                "저장할 금액 컬럼을 확정하지 못했습니다."
            )

        if typ == "INCOME":
            if (
                gross is not None
                and amount is not None
            ):
                if amount > gross:
                    reasons.append(
                        "실제 입금액이 세전 금액보다 큽니다."
                    )
                else:
                    calculated_tax = gross - amount

                    if (
                        tax is not None
                        and tax != calculated_tax
                    ):
                        reasons.append(
                            "세액과 세전·실제 금액의 차이가 다릅니다."
                        )

                    if tax is None:
                        tax = calculated_tax

                    if rate is not None:
                        expected_tax = (
                            Decimal(gross)
                            * rate
                            / 100
                        )

                        if abs(
                            Decimal(calculated_tax)
                            - expected_tax
                        ) > 1:
                            reasons.append(
                                "세율과 실제 금액이 일치하지 않습니다."
                            )

        category, category_name = resolve_category(
            category_text=get("category"),
            description=description,
            raw=raw,
            page=page,
            context=context,
            resolver=category_resolver,
        )

        result.append(
            make_item(
                page=page,
                raw=raw,
                description=description,
                amount=amount,
                typ=typ,
                year=(
                    int(when[:4])
                    if when
                    else context.get("year")
                ),
                when=when,
                amount_basis=amount_basis,
                gross=gross,
                tax=tax,
                tax_rate=rate,
                category=category,
                category_name=category_name,
                reasons=reasons,
            )
        )

    return result


def parse_monthly_table(
    rows: list[list[str]],
    page: int,
    context: dict[str, Any],
    category_resolver: CategoryResolver | None,
) -> list[dict[str, Any]] | None:
    headers = []

    for row_index, row in enumerate(rows):
        mapping = {}

        for column, cell in enumerate(row):
            match = MONTH_PATTERN.fullmatch(
                compact(cell)
            )

            if match:
                mapping[column] = int(match[1])

        if mapping:
            headers.append(
                (row_index, mapping)
            )

    if not headers:
        return None

    months = {
        month
        for _, mapping in headers
        for month in mapping.values()
    }

    if months != set(range(1, 13)):
        return None

    indexes = [
        index
        for index, _ in headers
    ]

    if indexes != list(
        range(min(indexes), max(indexes) + 1)
    ):
        return None

    mappings = [
        mapping
        for _, mapping in headers
    ]

    first_month_column = min(
        column
        for mapping in mappings
        for column in mapping
    )

    block_height = len(mappings)
    index = max(indexes) + 1
    result = []

    while index < len(rows):
        block = rows[index:index + block_height]

        if not block or not any(block[0]):
            index += 1
            continue

        prefix = [
            cell
            for cell in block[0][:first_month_column]
            if not is_empty(cell)
        ]

        if not prefix:
            index += 1
            continue

        description = " · ".join(prefix)
        raw = "\n".join(
            " | ".join(row)
            for row in block
        )

        values = {}
        reasons = []

        if len(block) != block_height:
            reasons.append(
                "월별 자료의 행 수 확인 필요"
            )

        for offset, mapping in enumerate(mappings):
            current = (
                block[offset]
                if offset < len(block)
                else []
            )

            for column, month in mapping.items():
                values[month] = parse_amount(
                    current[column]
                    if column < len(current)
                    else None
                )

        if any(
            values.get(month) is None
            for month in range(1, 13)
        ):
            reasons.append(
                "12개월 금액 중 읽지 못한 값이 있습니다."
            )

        year = context.get("year")

        if year is None:
            reasons.append(
                "귀속 연도 확인 필요"
            )

        category, category_name = resolve_category(
            category_text="",
            description=description,
            raw=raw,
            page=page,
            context=context,
            resolver=category_resolver,
        )

        # 월별 집계라는 이유만으로
        # 지출이나 공제 대상 금액이라고 단정하지 않는다.
        reasons.append(
            "월별 금액의 거래 방향과 금액 성격 확인 필요"
        )

        for month in range(1, 13):
            result.append(
                make_item(
                    page=page,
                    raw=raw,
                    description=description,
                    amount=values.get(month),
                    typ="UNKNOWN",
                    year=year,
                    month=(
                        f"{year}-{month:02d}"
                        if year is not None
                        else None
                    ),
                    value_kind="MONTHLY_AGGREGATE",
                    category=category,
                    category_name=category_name,
                    reasons=reasons,
                )
            )

        index += block_height

    return result


def parse_rows(
    rows: list[list[str]],
    page: int,
    context: dict[str, Any],
    header_aliases: dict[str, list[str]],
    category_resolver: CategoryResolver | None,
) -> list[dict[str, Any]]:
    monthly = parse_monthly_table(
        rows,
        page,
        context,
        category_resolver,
    )

    if monthly is not None:
        return monthly

    return parse_daily_table(
        rows,
        page,
        context,
        header_aliases,
        category_resolver,
    )


def candidate_score(
    items: list[dict[str, Any]],
) -> tuple[int, int, int]:
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
) -> dict[str, Any]:
    """PDF와 외부 OCR 결과에서 거래 후보를 추출한다.

    헤더 별칭:
        필요할 때 DB나 설정에서 전달한다.
        특정 문서의 헤더 목록을 이 모듈에 내장하지 않는다.

    카테고리 분류기:
        원문 카테고리와 거래 설명을 받아
        서비스에서 사용하는 카테고리 코드를 반환한다.
        분류기가 없거나 결과가 없으면 미분류로 남긴다.
    """

    if (
        not isinstance(pdf_bytes, bytes)
        or not pdf_bytes.startswith(b"%PDF-")
    ):
        raise ValueError(
            "올바른 PDF 파일이 아닙니다."
        )

    aliases = header_aliases or {}

    for role, values in aliases.items():
        if role not in SUPPORTED_ROLES:
            raise ValueError(
                f"지원하지 않는 컬럼 역할입니다: {role}"
            )

        if (
            not isinstance(values, list)
            or not all(
                isinstance(value, str)
                for value in values
            )
        ):
            raise ValueError(
                "헤더 별칭은 문자열 목록이어야 합니다."
            )

    ocr_map = {
        item["pageNumber"]: item
        for item in (ocr_pages or [])
        if isinstance(item, dict)
        and type(item.get("pageNumber")) is int
    }

    transactions = []
    page_results = []

    with pdfplumber.open(BytesIO(pdf_bytes)) as pdf:
        if pdf.doc.encryption is not None:
            raise ValueError(
                "암호화된 PDF는 지원하지 않습니다."
            )

        if len(pdf.pages) > MAX_PAGES:
            raise ValueError(
                "PDF 페이지 수가 허용 범위를 초과했습니다."
            )

        native_texts = [
            page.extract_text() or ""
            for page in pdf.pages
        ]

        ocr_texts = []

        for page_number in range(
            1,
            len(pdf.pages) + 1,
        ):
            ocr = ocr_map.get(page_number) or {}
            source = text(ocr.get("text"))

            if not source:
                source = "\n".join(
                    " | ".join(row)
                    for row in normalize_rows(
                        ocr.get("rows") or []
                    )
                )

            ocr_texts.append(source)

        context = find_document_context(
            "\n".join(
                native or ocr
                for native, ocr in zip(
                    native_texts,
                    ocr_texts,
                )
            )
        )

        for page_number, page in enumerate(
            pdf.pages,
            1,
        ):
            native_text = native_texts[
                page_number - 1
            ]

            ocr = ocr_map.get(page_number) or {}
            ocr_source = ocr_texts[
                page_number - 1
            ]

            candidates = []

            # 각 추출 방식은 페이지 단위의 대안이다.
            # 서로 합치지 않으므로 같은 거래의 중복을 줄인다.
            native_table_items = []

            for table in page.extract_tables():
                rows = [
                    [
                        text(cell)
                        for cell in row
                    ]
                    for row in table
                ]

                native_table_items.extend(
                    parse_rows(
                        rows,
                        page_number,
                        context,
                        aliases,
                        category_resolver,
                    )
                )

            candidates.append(
                (
                    "PDF_TABLE",
                    native_table_items,
                )
            )

            geometry_items = parse_rows(
                geometry_rows(page),
                page_number,
                context,
                aliases,
                category_resolver,
            )

            candidates.append(
                (
                    "PDF_POSITION",
                    geometry_items,
                )
            )

            native_text_items = parse_rows(
                text_rows(native_text),
                page_number,
                context,
                aliases,
                category_resolver,
            )

            candidates.append(
                (
                    "PDF_TEXT",
                    native_text_items,
                )
            )

            ocr_rows = normalize_rows(
                ocr.get("rows") or []
            )

            if ocr_rows:
                candidates.append(
                    (
                        "OCR",
                        parse_rows(
                            ocr_rows,
                            page_number,
                            context,
                            aliases,
                            category_resolver,
                        ),
                    )
                )

            if ocr_source:
                candidates.append(
                    (
                        "OCR",
                        parse_rows(
                            text_rows(ocr_source),
                            page_number,
                            context,
                            aliases,
                            category_resolver,
                        ),
                    )
                )

            method, selected = max(
                candidates,
                key=lambda candidate: candidate_score(
                    candidate[1]
                ),
            )

            if not selected:
                method = "NONE"

            transactions.extend(selected)

            page_results.append(
                {
                    "pageNumber": page_number,
                    "method": method,
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
                        else (
                            "거래 구조를 확인하지 못했습니다. "
                            "이미지 문서는 OCR 결과가 필요하며, "
                            "텍스트 문서는 컬럼 구조 확인이 필요합니다."
                        )
                    ),
                }
            )

    parsed_count = sum(
        item["parseSuccess"]
        for item in transactions
    )

    return {
        "totalPages": len(page_results),
        "extractedCount": len(transactions),
        "parsedCount": parsed_count,
        "failedCount": (
            len(transactions) - parsed_count
        ),
        "needsReviewCount": sum(
            item["needsReview"]
            for item in transactions
        ),
        "pageResults": page_results,
        "transactions": transactions,
        "tables": [],
    }