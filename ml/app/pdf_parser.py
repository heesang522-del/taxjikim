"""일별 거래와 카드 월별 집계를 자동 추출한다."""

from __future__ import annotations

import re

from datetime import date
from decimal import Decimal, InvalidOperation
from io import BytesIO
from typing import Any

import pdfplumber


MAX_PAGES = 50
MAX_SAFE_INTEGER = 9_007_199_254_740_991

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

MONTH_PATTERN = re.compile(r"^(0?[1-9]|1[0-2])월$")

NUMBER_PATTERN = re.compile(
    r"[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?"
)

SUMMARY_PATTERN = re.compile(
    r"합계|소계|총계|총액|금액계|납입액계"
)

IDENTIFIER_PATTERN = re.compile(
    r"(?<!\d)(?:"
    r"\d{6}-?[\d*]{7}"
    r"|\d{3}-\d{2}-\d{5}"
    r")(?!\d)"
)

DIRECTIONS = {
    "수입": "INCOME",
    "입금": "INCOME",
    "INCOME": "INCOME",
    "지출": "EXPENSE",
    "출금": "EXPENSE",
    "EXPENSE": "EXPENSE",
}

DOCUMENT_CATEGORIES = {
    "의료비": "MEDICAL_EXPENSE",
    "교육비": "EDUCATION_EXPENSE",
    "보험료": "INSURANCE_PREMIUM",
    "기부금": "DONATION",
}

HEADER_PATTERNS = {
    "date": re.compile(
        r"^(?:날짜|일자|거래일자?|사용일자?|지출일자?|납입일자?|date)$"
    ),
    "sequence": re.compile(
        r"^(?:번호|순번|연번|no\.?)$"
    ),
    "type": re.compile(
        r"^(?:구분|유형|거래구분|입출금|type)$"
    ),
    "description": re.compile(
        r"^(?:상호(?:\(사업자번호\))?|항목|내용|거래내용|거래설명|"
        r"적요|내역|.*기관명|.*회사명|.*학교명|description)$"
    ),
    "net": re.compile(
        r"^(?:실제입금액|실입금액|실수령액|입금액|actualamount|netamount)$"
    ),
    "gross": re.compile(
        r"^(?:세전금액|세전수입|총수입|지급총액|pretaxincome|grossamount)$"
    ),
    "tax": re.compile(
        r"^(?:차감액|차감세액|원천징수액|공제세액|taxdeducted)$"
    ),
    "amount": re.compile(
        r"^(?:금액|지출금액계?|사용금액|거래금액|결제금액|"
        r"납입금액|납입액|기부금액|amount)$"
    ),
}


def text(value: object) -> str:
    return str(value if value is not None else "").strip()


def compact(value: object) -> str:
    return re.sub(r"\s+", "", text(value))


def mask_text(value: object) -> str:
    return IDENTIFIER_PATTERN.sub(
        "***-****-****",
        text(value),
    )


def parse_date(value: object) -> str | None:
    source = re.sub(
        r"[\u2010-\u2015\u2212]",
        "-",
        text(value),
    )

    found = set()

    for match in DATE_PATTERN.finditer(source):
        try:
            found.add(date(
                int(match[1]),
                int(match[2]),
                int(match[3]),
            ).isoformat())
        except ValueError:
            continue

    return next(iter(found)) if len(found) == 1 else None


def parse_amount(value: object) -> int | None:
    source = text(value)
    source = re.sub(r",\s+(?=\d)", ",", source)

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
        number = Decimal(source.replace(",", ""))
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


def direction(value: object) -> str:
    return DIRECTIONS.get(
        compact(value).upper(),
        "UNKNOWN",
    )


def header_role(value: object) -> str | None:
    source = compact(value).lower()

    source = re.sub(
        r"[\(\[（](?:원|₩|krw)[\)\]）]",
        "",
        source,
    )

    for name, pattern in HEADER_PATTERNS.items():
        if pattern.fullmatch(source):
            return name

    return None


def normalize_rows(rows: list[dict]) -> list[list[str]]:
    result = []

    for row in rows:
        cells = row.get("cells")

        if not isinstance(cells, list) or not cells:
            source = text(row.get("text", row.get("rawText", "")))

            cells = (
                source.split("|")
                if "|" in source
                else re.split(r"\t+| {2,}", source)
            )

        values = [text(cell) for cell in cells]

        if any(values):
            result.append(values)

    return result


def find_document_context(source: str) -> dict[str, Any]:
    title = compact("\n".join(source.splitlines()[:15]))

    years = {
        int(match[1])
        for match in YEAR_PATTERN.finditer(source)
    }

    category = "UNCLASSIFIED"

    # 문서 제목에 명시된 항목만 카테고리 근거로 사용한다.
    for label, code in DOCUMENT_CATEGORIES.items():
        if f"[{label}]" in title:
            category = code
            break

    expense = "지출내역" in title or "지출별" in title
    income = "수입내역" in title

    typ = "UNKNOWN"

    if expense and not income:
        typ = "EXPENSE"
    elif income and not expense:
        typ = "INCOME"

    return {
        "year": next(iter(years)) if len(years) == 1 else None,
        "category": category,
        "direction": typ,
    }


def make_item(
    *,
    page: int,
    raw: str,
    description: str,
    amount: int | None,
    typ: str,
    category: str,
    year: int | None,
    when: str | None = None,
    month: str | None = None,
    value_kind: str = "DETAIL",
    amount_basis: str = "UNSPECIFIED",
    gross: int | None = None,
    tax: int | None = None,
    reasons: list[str] | None = None,
) -> dict[str, Any]:
    errors = list(reasons or [])

    if amount is None:
        errors.append("금액 확인 필요")

    if not description:
        errors.append("항목 확인 필요")

    if when is None and year is None:
        errors.append("귀속 연도 확인 필요")

    extracted = amount is not None and bool(description)

    # 공제대상금액은 실제 입출금액으로 변환하지 않는다.
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
        "amount": amount,
        "actualAmount": actual_amount,
        "preTaxIncome": gross if typ == "INCOME" else None,
        "taxDeducted": tax if typ == "INCOME" else None,
        "deductedTaxRate": None,
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
            " / ".join(dict.fromkeys(errors))
            if errors
            else None
        ),
    }


def month_header(
    rows: list[list[str]],
) -> list[tuple[int, dict[int, int]]]:
    found = []

    for row_index, cells in enumerate(rows):
        mapping = {}

        for column_index, cell in enumerate(cells):
            match = MONTH_PATTERN.fullmatch(compact(cell))

            if match:
                mapping[column_index] = int(match[1])

        if mapping:
            found.append((row_index, mapping))

    return found


def parse_monthly_table(
    rows: list[list[str]],
    page: int,
    context: dict[str, Any],
) -> list[dict[str, Any]] | None:
    headers = month_header(rows)

    if not headers:
        return None

    # 월 이름의 위치를 기준으로 읽으며, 특정 카드사 이름에 의존하지 않는다.
    months = {
        month
        for _, mapping in headers
        for month in mapping.values()
    }

    if months != set(range(1, 13)):
        return None

    row_indexes = [index for index, _ in headers]

    if row_indexes != list(range(min(row_indexes), max(row_indexes) + 1)):
        return None

    month_maps = [mapping for _, mapping in headers]
    month_columns = {
        column
        for mapping in month_maps
        for column in mapping
    }

    first_month_column = min(month_columns)
    last_month_column = max(month_columns)

    header_text = compact(
        " ".join(
            cell
            for row in rows[:max(row_indexes) + 1]
            for cell in row
        )
    )

    eligible = "공제대상금액" in header_text
    amount_basis = "DEDUCTION_ELIGIBLE" if eligible else "UNSPECIFIED"

    # 항목 합계 열은 월별 열의 오른쪽에서 명시된 열만 사용한다.
    total_columns = []

    for row in rows[:min(row_indexes)]:
        for index, cell in enumerate(row):
            if index <= last_month_column:
                continue

            label = compact(cell)

            if "금액" in label or SUMMARY_PATTERN.search(label):
                total_columns.append(index)

    total_column = (
        total_columns[0]
        if len(set(total_columns)) == 1
        else None
    )

    result = []
    block_height = len(month_maps)
    index = max(row_indexes) + 1

    while index < len(rows):
        block = rows[index:index + block_height]

        if not block or not any(block[0]):
            index += 1
            continue

        prefix = [
            cell
            for cell in block[0][:first_month_column]
            if cell
        ]

        if not prefix:
            index += 1
            continue

        description = " · ".join(
            mask_text(cell.replace("\n", " "))
            for cell in prefix
        )

        values: dict[int, int | None] = {}
        errors = []

        if len(block) != block_height:
            errors.append("월별 자료의 행 수 확인 필요")

        for offset, mapping in enumerate(month_maps):
            current = block[offset] if offset < len(block) else []

            for column, month in mapping.items():
                values[month] = parse_amount(
                    current[column] if column < len(current) else None
                )

        if any(values.get(month) is None for month in range(1, 13)):
            errors.append("12개월 금액 중 읽지 못한 값이 있습니다.")

        annual_total = None

        if total_column is not None:
            annual_total = parse_amount(
                block[0][total_column]
                if total_column < len(block[0])
                else None
            )

        if (
            not errors
            and annual_total is not None
            and sum(values.values()) != annual_total
        ):
            errors.append("월별 금액 합계와 항목 합계가 다릅니다.")

        if context["year"] is None:
            errors.append("귀속 연도를 확인하지 못했습니다.")

        raw = "\n".join(" | ".join(row) for row in block)

        for month in range(1, 13):
            period = (
                f"{context['year']}-{month:02d}"
                if context["year"] is not None
                else None
            )

            result.append(make_item(
                page=page,
                raw=raw,
                description=description,
                amount=values.get(month),
                typ="EXPENSE",
                category="UNCLASSIFIED",
                year=context["year"],
                month=period,
                value_kind="MONTHLY_AGGREGATE",
                amount_basis=amount_basis,
                reasons=errors,
            ))

        index += block_height

    return result


def parse_daily_table(
    rows: list[list[str]],
    page: int,
    context: dict[str, Any],
) -> list[dict[str, Any]]:
    result = []
    header = {}
    header_width = None
    previous_description = ""

    for cells in rows:
        roles = {}
        duplicates = set()

        for index, cell in enumerate(cells):
            name = header_role(cell)

            if name:
                if name in roles:
                    duplicates.add(name)
                roles[name] = index

        for name in duplicates:
            roles.pop(name, None)

        if (
            any(name in roles for name in ("amount", "net", "gross"))
            and any(name in roles for name in ("date", "description", "type"))
        ):
            header = roles
            header_width = len(cells)
            previous_description = ""
            continue

        dates = [
            (index, parse_date(cell))
            for index, cell in enumerate(cells)
            if parse_date(cell) is not None
        ]

        active = (
            header
            if header and len(cells) == header_width
            else {}
        )

        if not active and not dates:
            continue

        if SUMMARY_PATTERN.search(compact(" ".join(cells))):
            previous_description = ""
            continue

        def get(name: str) -> str:
            column = active.get(name)

            return (
                cells[column]
                if column is not None and column < len(cells)
                else ""
            )

        when = (
            parse_date(get("date"))
            if "date" in active
            else dates[0][1] if len(dates) == 1 else None
        )

        if "date" in active and when is None:
            continue

        ignored = {index for index, _ in dates}

        if "sequence" in active:
            ignored.add(active["sequence"])
        elif (
            len(dates) == 1
            and dates[0][0] == 1
            and re.fullmatch(r"\d+", cells[0])
        ):
            ignored.add(0)

        kinds = [
            (index, direction(cell))
            for index, cell in enumerate(cells)
            if direction(cell) != "UNKNOWN"
        ]

        ignored.update(index for index, _ in kinds)

        typ = direction(get("type"))

        if typ == "UNKNOWN":
            typ = kinds[0][1] if len(kinds) == 1 else context["direction"]

        amount = None
        gross = parse_amount(get("gross"))
        tax = parse_amount(get("tax"))

        money_roles = [
            name
            for name in ("net", "amount", "gross")
            if name in active
        ]

        if money_roles:
            amount = parse_amount(get(money_roles[0]))

            for name in ("net", "amount", "gross", "tax"):
                if name in active:
                    ignored.add(active[name])

        else:
            candidates = [
                (index, parse_amount(cell))
                for index, cell in enumerate(cells)
                if index not in ignored
                and parse_amount(cell) is not None
            ]

            ignored.update(index for index, _ in candidates)

            if len(candidates) == 1:
                amount = candidates[0][1]

        description = get("description")

        if "description" in active:
            if description:
                previous_description = description
            else:
                description = previous_description
        else:
            description = " ".join(
                cell
                for index, cell in enumerate(cells)
                if index not in ignored
                and cell
                and not re.search(r"[*＊]", cell)
                and not IDENTIFIER_PATTERN.search(cell)
            )

        if amount is None and when is None:
            continue

        result.append(make_item(
            page=page,
            raw=" | ".join(cells),
            description=description,
            amount=amount,
            typ=typ,
            category=context["category"],
            year=context["year"],
            when=when,
            value_kind="DETAIL" if when else "ANNUAL_TOTAL",
            gross=gross,
            tax=tax,
        ))

    return result


def parse_table(
    rows: list[list[str]],
    page: int,
    context: dict[str, Any],
) -> list[dict[str, Any]]:
    monthly = parse_monthly_table(rows, page, context)

    if monthly is not None:
        return monthly

    return parse_daily_table(rows, page, context)


def document_summary(
    tables: list[list[list[str]]],
) -> int | None:
    """가로 집계표에서 세부 항목의 합과 일치하는 전체 합계만 읽는다."""

    for rows in tables:
        if len(rows) != 2:
            continue

        headers, values = rows

        if not headers or not values:
            continue

        if "합계" in "".join(headers):
            continue

        if compact(headers[-1]) not in ("합계금액", "총합계금액"):
            continue

        amounts = [parse_amount(value) for value in values]

        if (
            len(amounts) >= 2
            and all(value is not None for value in amounts)
            and sum(amounts[:-1]) == amounts[-1]
        ):
            return amounts[-1]

    return None


def extract_transactions(
    pdf_bytes: bytes,
    ocr_pages: list[dict] | None = None,
) -> dict[str, Any]:
    if (
        not isinstance(pdf_bytes, bytes)
        or not pdf_bytes.startswith(b"%PDF-")
    ):
        raise ValueError("올바른 PDF 파일이 아닙니다.")

    ocr_map = {
        item["pageNumber"]: item
        for item in (ocr_pages or [])
        if isinstance(item, dict)
        and type(item.get("pageNumber")) is int
    }

    transactions = []
    page_results = []
    summary_values = []

    context = {
        "year": None,
        "category": "UNCLASSIFIED",
        "direction": "UNKNOWN",
    }

    with pdfplumber.open(BytesIO(pdf_bytes)) as pdf:
        if pdf.doc.encryption is not None:
            raise ValueError("암호화된 PDF는 지원하지 않습니다.")

        if len(pdf.pages) > MAX_PAGES:
            raise ValueError("PDF 페이지 수가 허용 범위를 초과했습니다.")

        for page_number, page in enumerate(pdf.pages, 1):
            ocr = ocr_map.get(page_number) or {}
            ocr_rows = ocr.get("rows") or []

            native_text = page.extract_text() or ""
            ocr_text = text(ocr.get("text"))

            if ocr_rows:
                ocr_text = "\n".join(
                    " | ".join(
                        text(cell)
                        for cell in row.get("cells", [])
                    ) or text(row.get("text"))
                    for row in ocr_rows
                )

            current = find_document_context(native_text or ocr_text)

            for key, value in current.items():
                if value is not None and value not in ("UNKNOWN", "UNCLASSIFIED"):
                    context[key] = value

            native_tables = [
                [
                    [text(cell) for cell in row]
                    for row in table
                ]
                for table in page.extract_tables()
            ]

            total = document_summary(native_tables)

            if total is not None:
                summary_values.append(total)

            native_items = []

            for table in native_tables:
                native_items.extend(parse_table(
                    table,
                    page_number,
                    context,
                ))

            ocr_items = parse_table(
                normalize_rows(ocr_rows)
                if ocr_rows
                else normalize_rows([
                    {"text": line}
                    for line in ocr_text.splitlines()
                ]),
                page_number,
                context,
            )

            native_success = sum(
                item["parseSuccess"] for item in native_items
            )
            ocr_success = sum(
                item["parseSuccess"] for item in ocr_items
            )

            if ocr_success > native_success:
                selected = ocr_items
                method = "OCR"
            else:
                selected = native_items or ocr_items
                method = "PDF_TABLE" if native_items else "OCR"

            transactions.extend(selected)

            page_results.append({
                "pageNumber": page_number,
                "method": method,
                "extractedCount": len(selected),
                "parsedCount": sum(
                    item["parseSuccess"] for item in selected
                ),
            })

    monthly_items = [
        item
        for item in transactions
        if item["valueKind"] == "MONTHLY_AGGREGATE"
    ]

    # 全体合計は取引として追加せず、月別明細の検算にだけ使用する。
    if (
        monthly_items
        and len(set(summary_values)) == 1
        and all(item["amount"] is not None for item in monthly_items)
    ):
        monthly_sum = sum(
            item["amount"] for item in monthly_items
        )

        if monthly_sum != summary_values[0]:
            for item in monthly_items:
                item["needsReview"] = True
                item["excluded"] = True
                item["failureReason"] = (
                    "월별 금액과 문서 전체 합계가 다릅니다."
                )

    parsed_count = sum(
        item["parseSuccess"] for item in transactions
    )

    return {
        "totalPages": len(page_results),
        "extractedCount": len(transactions),
        "parsedCount": parsed_count,
        "failedCount": len(transactions) - parsed_count,
        "pageResults": page_results,
        "transactions": transactions,
        "tables": [],
    }