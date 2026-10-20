
import re
from datetime import date
from decimal import Decimal, InvalidOperation
from io import BytesIO

import pdfplumber


DATE_RE = re.compile(
    r"(?<!\d)(\d{4})[./-](\d{1,2})[./-](\d{1,2})(?!\d)"
)

NUMBER_RE = re.compile(
    r"(?<![\w.])[+-]?"
    r"(?:\d{1,3}(?:,\d{3})+|\d+)"
    r"(?:\.\d+)?(?![\w.])"
)

FULL_NUMBER_RE = re.compile(
    r"^[+-]?"
    r"(?:\d{1,3}(?:,\d{3})+|\d+)"
    r"(?:\.\d+)?$"
)

# 시스템에서 사용하는 거래 유형의 표준값.
# 특정 거래처나 PDF 데이터가 아닌 의미 변환 규칙이다.
TYPE_MAP = {
    "수입": "INCOME",
    "INCOME": "INCOME",
    "IN": "INCOME",
    "지출": "EXPENSE",
    "EXPENSE": "EXPENSE",
    "OUT": "EXPENSE",
}


def clean(value):
    if value is None:
        return ""

    return re.sub(
        r"\s+",
        " ",
        str(value)
    ).strip()


def parse_date(value):
    text = clean(value)

    match = DATE_RE.search(text)

    if not match:
        return None

    try:
        year, month, day = map(
            int,
            match.groups()
        )

        return date(
            year,
            month,
            day
        ).isoformat()

    except ValueError:
        return None


def parse_amount(value):
    text = clean(value)

    if not text:
        return None

    text = (
        text.replace(",", "")
        .replace("₩", "")
        .replace("원", "")
        .replace(" ", "")
    )

    if not FULL_NUMBER_RE.fullmatch(text):
        return None

    try:
        amount = Decimal(text)

    except InvalidOperation:
        return None

    if not amount.is_finite():
        return None

    return str(amount)


def normalize_transaction_type(value):
    text = clean(value).upper()

    return TYPE_MAP.get(
        text,
        "UNKNOWN"
    )


def extract_explicit_type(text):
    """
    원문 행의 시작에 명시된 거래 구분만 인식한다.

    예:
    수입 거래처명 후원 입금
    지출 거래처명 서비스 결제

    문장 중간에 포함된 단어는
    거래 유형 판정에 사용하지 않는다.
    """

    original = clean(text)

    if not original:
        return "UNKNOWN", original

    parts = original.split(
        " ",
        1
    )

    first = parts[0]

    kind = normalize_transaction_type(
        first
    )

    if kind == "UNKNOWN":
        return "UNKNOWN", original

    description = (
        parts[1]
        if len(parts) > 1
        else ""
    )

    return kind, description


def create_transaction(
    amount,
    transaction_date,
    description,
    page_number,
    raw_text,
    transaction_type="UNKNOWN",
):
    return {
        "transactionId": None,
        "userId": None,
        "transactionDate": transaction_date,
        "amount": amount,
        "description": description,
        "transactionType": transaction_type,
        "status": None,
        "pageNumber": page_number,
        "rawText": raw_text,
    }


def normalize_table(table):
    rows = []

    for raw_row in table or []:
        if not raw_row:
            continue

        row = [
            clean(cell)
            for cell in raw_row
        ]

        if any(row):
            rows.append(row)

    return rows


def analyze_table(table, page_number):
    rows = normalize_table(table)

    if not rows:
        return []

    transactions = []

    for row in rows:
        dates = []
        amounts = []
        descriptions = []
        explicit_types = []

        for index, cell in enumerate(row):
            if not cell:
                continue

            parsed_date = parse_date(cell)

            if parsed_date and DATE_RE.fullmatch(cell):
                dates.append(parsed_date)
                continue

            parsed_amount = parse_amount(cell)

            if parsed_amount is not None:
                amounts.append(
                    (index, parsed_amount)
                )
                continue

            cell_type = normalize_transaction_type(
                cell
            )

            if cell_type != "UNKNOWN":
                explicit_types.append(cell_type)
                continue

            descriptions.append(cell)

        # 금액 후보가 여러 개라면
        # 잔액 등을 임의로 거래 금액으로 사용하지 않는다.
        if len(amounts) != 1:
            continue

        if len(set(dates)) > 1:
            continue

        transaction_date = (
            dates[0]
            if dates
            else None
        )

        description = " ".join(
            descriptions
        )

        # 구분 열이 별도로 존재하면 해당 값을 우선한다.
        if len(set(explicit_types)) == 1:
            transaction_type = explicit_types[0]

        else:
            transaction_type = "UNKNOWN"

        # 별도 구분 열이 없을 때
        # 설명의 맨 앞에 명시된 수입·지출을 읽는다.
        if transaction_type == "UNKNOWN":
            extracted_type, remaining = (
                extract_explicit_type(
                    description
                )
            )

            if extracted_type != "UNKNOWN":
                transaction_type = extracted_type
                description = remaining

        if not description:
            continue

        transactions.append(
            create_transaction(
                amount=amounts[0][1],
                transaction_date=transaction_date,
                description=description,
                page_number=page_number,
                raw_text=" | ".join(row),
                transaction_type=transaction_type,
            )
        )

    return transactions


def extract_line_amount(text):
    original = clean(text)

    # 날짜의 숫자가 금액으로 인식되지 않도록 제거한다.
    remaining = DATE_RE.sub(
        " ",
        original
    )

    matches = list(
        NUMBER_RE.finditer(remaining)
    )

    if len(matches) != 1:
        return None

    match = matches[0]

    amount = parse_amount(
        match.group()
    )

    if amount is None:
        return None

    description = clean(
        remaining[:match.start()]
        + " "
        + remaining[match.end():]
    )

    return amount, description


def analyze_text_line(line, page_number):
    original = clean(line)

    if not original:
        return None

    dates = {
        parse_date(match.group())
        for match in DATE_RE.finditer(original)
    }

    dates.discard(None)

    if len(dates) > 1:
        return None

    transaction_date = (
        next(iter(dates))
        if dates
        else None
    )

    amount_result = extract_line_amount(
        original
    )

    if amount_result is None:
        return None

    amount, description = amount_result

    transaction_type, description = (
        extract_explicit_type(
            description
        )
    )

    if not description:
        return None

    return create_transaction(
        amount=amount,
        transaction_date=transaction_date,
        description=description,
        page_number=page_number,
        raw_text=original,
        transaction_type=transaction_type,
    )


def analyze_text(text, page_number):
    transactions = []

    for line in (text or "").splitlines():
        transaction = analyze_text_line(
            line,
            page_number
        )

        if transaction is not None:
            transactions.append(
                transaction
            )

    return transactions


def analyze_page(
    page_number,
    text,
    tables,
):
    table_transactions = []

    for table in tables or []:
        table_transactions.extend(
            analyze_table(
                table,
                page_number
            )
        )

    if table_transactions:
        return table_transactions

    return analyze_text(
        text,
        page_number
    )


def extract_transactions(
    pdf_bytes,
    ocr_pages=None,
):
    if not isinstance(
        pdf_bytes,
        bytes
    ):
        raise ValueError(
            "PDF 데이터는 bytes여야 합니다."
        )

    if not pdf_bytes.startswith(
        b"%PDF-"
    ):
        raise ValueError(
            "올바른 PDF 파일이 아닙니다."
        )

    ocr_by_page = {}

    for item in ocr_pages or []:
        if not isinstance(item, dict):
            continue

        page_number = item.get(
            "pageNumber"
        )

        text = item.get(
            "text"
        )

        if (
            isinstance(page_number, int)
            and isinstance(text, str)
        ):
            ocr_by_page[page_number] = text

    transactions = []

    with pdfplumber.open(
        BytesIO(pdf_bytes)
    ) as pdf:

        total_pages = len(
            pdf.pages
        )

        for page_number, page in enumerate(
            pdf.pages,
            start=1
        ):
            text = (
                page.extract_text()
                or ""
            )

            tables = (
                page.extract_tables()
                or []
            )

            if not text.strip():
                text = ocr_by_page.get(
                    page_number,
                    ""
                )

                tables = []

            transactions.extend(
                analyze_page(
                    page_number=page_number,
                    text=text,
                    tables=tables,
                )
            )

    return {
        "totalPages": total_pages,
        "extractedCount": len(transactions),
        "transactions": transactions,
    }
