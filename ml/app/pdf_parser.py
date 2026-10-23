import re

from datetime import date
from decimal import Decimal, InvalidOperation
from io import BytesIO
from typing import Any

import pdfplumber


DATE_AT_START = re.compile(
    r"^\s*(\d{4})[./-](\d{1,2})[./-](\d{1,2})(?!\d)"
)

AMOUNT_PATTERN = re.compile(
    r"^[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?$"
)

NUMBER_PATTERN = re.compile(
    r"(?<!\w)[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)"
    r"(?:\.\d+)?(?!\w)"
)

TRANSACTION_TYPES = {
    "수입": "INCOME",
    "INCOME": "INCOME",
    "IN": "INCOME",
    "지출": "EXPENSE",
    "EXPENSE": "EXPENSE",
    "OUT": "EXPENSE",
}


def clean(value: object) -> str:
    if value is None:
        return ""

    return re.sub(r"\s+", " ", str(value)).strip()


def parse_amount(value: object) -> str | None:
    """금액 누락과 0원을 구분한다."""

    text = clean(value)

    if not text:
        return None

    text = (
        text.replace("₩", "")
        .replace("원", "")
        .replace("KRW", "")
        .strip()
    )

    if not AMOUNT_PATTERN.fullmatch(text):
        return None

    try:
        amount = Decimal(text.replace(",", ""))
    except InvalidOperation:
        return None

    if not amount.is_finite():
        return None

    return format(amount, "f")


def parse_leading_fields(
    text: str,
) -> dict[str, Any] | None:
    original = clean(text)

    match = DATE_AT_START.match(original)

    if not match:
        return None

    try:
        year, month, day = map(int, match.groups())
        transaction_date = date(year, month, day).isoformat()

    except ValueError:
        transaction_date = None

    remaining = original[match.end():].strip()

    parts = remaining.split(" ", 1)
    first = parts[0] if parts else ""

    transaction_type = TRANSACTION_TYPES.get(
        first.upper(),
        "UNKNOWN",
    )

    if transaction_type == "UNKNOWN":
        description = remaining
    else:
        description = parts[1] if len(parts) > 1 else ""

    return {
        "transactionDate": transaction_date,
        "transactionType": transaction_type,
        "description": description,
    }


def make_transaction(
    *,
    page_number: int,
    fields: dict[str, Any],
    raw_text: str,
    pre_tax_income: str | None = None,
    tax_deducted: str | None = None,
    actual_amount: str | None = None,
    errors: list[str] | None = None,
) -> dict[str, Any]:
    """인식 실패 거래도 원문과 함께 결과에 남긴다."""

    errors = list(errors or [])

    if fields["transactionDate"] is None:
        errors.append("날짜 인식 실패")

    if fields["transactionType"] == "UNKNOWN":
        errors.append("수입·지출 구분 인식 실패")

    if not fields["description"]:
        errors.append("거래 설명 인식 실패")

    if actual_amount is None:
        errors.append("실제 입출금액 인식 실패")

    # 지출 행의 빈 세전 수입 칸이 0으로 표기된 경우만 NULL로 정규화한다.
    if (
        fields["transactionType"] == "EXPENSE"
        and pre_tax_income is not None
    ):
        if Decimal(pre_tax_income) == 0:
            pre_tax_income = None
        else:
            errors.append("지출 거래의 세전 수입 확인 필요")

    errors = list(dict.fromkeys(errors))
    success = not errors

    return {
        "transactionId": None,
        "taxInputId": None,
        "transactionDate": fields["transactionDate"],
        "transactionType": fields["transactionType"],
        "description": fields["description"],
        "category": None,
        "amount": actual_amount,
        "preTaxIncome": pre_tax_income,
        # 차감 세금만으로 세율을 임의 확정하지 않는다.
        "deductedTaxRate": None,
        "taxDeducted": tax_deducted,
        "actualAmount": actual_amount,
        "pageNumber": page_number,
        "rawText": raw_text,
        "parseSuccess": success,
        "excluded": actual_amount is None,
        "failureReason": " / ".join(errors) if errors else None,
    }


def group_words_into_lines(
    words: list[dict[str, Any]],
    tolerance: float = 3,
) -> list[dict[str, Any]]:
    lines: list[dict[str, Any]] = []

    ordered_words = sorted(
        words,
        key=lambda item: (item["top"], item["x0"]),
    )

    for word in ordered_words:
        target = None

        for line in reversed(lines):
            difference = word["top"] - line["top"]

            if abs(difference) <= tolerance:
                target = line
                break

            if difference > tolerance:
                break

        if target is None:
            target = {
                "top": word["top"],
                "words": [],
            }
            lines.append(target)

        target["words"].append(word)

    for line in lines:
        line["words"].sort(key=lambda item: item["x0"])

        line["text"] = clean(
            " ".join(item["text"] for item in line["words"])
        )

    return lines


def find_money_columns(
    lines: list[dict[str, Any]],
) -> dict[str, float] | None:
    for line in lines:
        pre_tax_x = None
        tax_x = None
        actual_x = None

        for word in line["words"]:
            token = clean(word["text"]).casefold()

            if (
                token.startswith("세전")
                or token.startswith("pre-tax")
                or token.startswith("pretax")
            ):
                pre_tax_x = word["x0"]

            elif token.startswith("차감") or token == "tax":
                tax_x = word["x0"]

            elif token.startswith("실제") or token == "actual":
                actual_x = word["x0"]

        if (
            pre_tax_x is not None
            and tax_x is not None
            and actual_x is not None
            and pre_tax_x < tax_x < actual_x
        ):
            return {
                "headerTop": line["top"],
                "preTaxX": pre_tax_x,
                "taxX": tax_x,
                "actualX": actual_x,
            }

    return None


def read_money_cell(
    words: list[dict[str, Any]],
    label: str,
    errors: list[str],
) -> str | None:
    text = clean(
        " ".join(word["text"] for word in words)
    )

    if not text:
        return None

    amount = parse_amount(text)

    if amount is None:
        errors.append(f"{label} 인식 실패")

    return amount


def parse_positioned_line(
    line: dict[str, Any],
    columns: dict[str, float],
    page_number: int,
) -> dict[str, Any] | None:
    if line["top"] <= columns["headerTop"]:
        return None

    if not DATE_AT_START.match(line["text"]):
        return None

    leading_words = []
    pre_tax_words = []
    tax_words = []
    actual_words = []

    for word in line["words"]:
        center = (word["x0"] + word["x1"]) / 2

        if center < columns["preTaxX"]:
            leading_words.append(word)
        elif center < columns["taxX"]:
            pre_tax_words.append(word)
        elif center < columns["actualX"]:
            tax_words.append(word)
        else:
            actual_words.append(word)

    leading_text = clean(
        " ".join(word["text"] for word in leading_words)
    )

    fields = parse_leading_fields(leading_text)

    if fields is None:
        fields = parse_leading_fields(line["text"])

        if fields is None:
            return None

        return make_transaction(
            page_number=page_number,
            fields=fields,
            raw_text=line["text"],
            errors=["거래 열 분리 실패"],
        )

    errors: list[str] = []

    pre_tax_income = read_money_cell(
        pre_tax_words,
        "세전 수입",
        errors,
    )

    tax_deducted = read_money_cell(
        tax_words,
        "차감 세금",
        errors,
    )

    actual_amount = read_money_cell(
        actual_words,
        "실제 입출금액",
        errors,
    )

    return make_transaction(
        page_number=page_number,
        fields=fields,
        raw_text=line["text"],
        pre_tax_income=pre_tax_income,
        tax_deducted=tax_deducted,
        actual_amount=actual_amount,
        errors=errors,
    )


def has_multiple_money_headers(text: str) -> bool:
    normalized = clean(text).casefold()

    korean = (
        "세전" in normalized
        and "차감" in normalized
        and "실제" in normalized
    )

    english = (
        ("pre-tax" in normalized or "pretax" in normalized)
        and "tax deducted" in normalized
        and "actual" in normalized
    )

    return korean or english


def parse_plain_text(
    text: str,
    page_number: int,
) -> list[dict[str, Any]]:
    transactions = []
    multiple_columns = has_multiple_money_headers(text)

    for raw_line in (text or "").splitlines():
        original = clean(raw_line)
        fields = parse_leading_fields(original)

        if fields is None:
            continue

        description = fields["description"]

        matches = list(
            NUMBER_PATTERN.finditer(description)
        )

        amount = None
        errors = []

        if multiple_columns:
            errors.append("금액 열 위치를 확인할 수 없음")

        elif len(matches) == 1:
            match = matches[0]

            if not description[match.end():].strip():
                amount = parse_amount(match.group())

                fields["description"] = clean(
                    description[:match.start()]
                )
            else:
                errors.append("금액과 설명의 경계를 확인할 수 없음")

        else:
            errors.append("거래 금액을 하나로 확정할 수 없음")

        transactions.append(
            make_transaction(
                page_number=page_number,
                fields=fields,
                raw_text=original,
                actual_amount=amount,
                errors=errors,
            )
        )

    return transactions


def parse_words(
    words: list[dict[str, Any]],
    page_number: int,
) -> tuple[list[dict[str, Any]], str]:
    lines = group_words_into_lines(words)
    columns = find_money_columns(lines)

    if columns is not None:
        transactions = []

        for line in lines:
            transaction = parse_positioned_line(
                line,
                columns,
                page_number,
            )

            if transaction is not None:
                transactions.append(transaction)

        return transactions, "COLUMN_POSITION"

    text = "\n".join(line["text"] for line in lines)

    return parse_plain_text(text, page_number), "TEXT"


def build_ocr_map(
    ocr_pages: list[dict[str, Any]] | None,
) -> dict[int, dict[str, Any]]:
    result = {}

    for item in ocr_pages or []:
        if not isinstance(item, dict):
            continue

        page_number = item.get("pageNumber")

        if type(page_number) is int and page_number > 0:
            result[page_number] = item

    return result


def candidate_score(
    transactions: list[dict[str, Any]],
) -> tuple[int, int]:
    # 실패 행을 포함한 거래 수를 먼저 비교해 누락을 줄인다.
    return (
        len(transactions),
        sum(1 for item in transactions if item["parseSuccess"]),
    )


def extract_transactions(
    pdf_bytes: bytes,
    ocr_pages: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    if (
        not isinstance(pdf_bytes, bytes)
        or not pdf_bytes.startswith(b"%PDF-")
    ):
        raise ValueError("올바른 PDF 데이터가 아닙니다.")

    ocr_by_page = build_ocr_map(ocr_pages)

    transactions = []
    page_results = []

    with pdfplumber.open(BytesIO(pdf_bytes)) as pdf:
        total_pages = len(pdf.pages)

        for page_number, page in enumerate(pdf.pages, start=1):
            native_words = page.extract_words(
                x_tolerance=2,
                y_tolerance=3,
            )

            native_rows, method = parse_words(
                native_words,
                page_number,
            )

            selected_rows = native_rows
            ocr_page = ocr_by_page.get(page_number)

            if ocr_page is not None:
                ocr_words = ocr_page.get("words") or []

                if ocr_words:
                    ocr_rows, ocr_method = parse_words(
                        ocr_words,
                        page_number,
                    )
                    ocr_method = "OCR_" + ocr_method

                else:
                    ocr_rows = parse_plain_text(
                        ocr_page.get("text") or "",
                        page_number,
                    )
                    ocr_method = "OCR_TEXT"

                # 두 결과를 합치지 않아 동일 거래의 중복 반영을 피한다.
                if candidate_score(ocr_rows) > candidate_score(native_rows):
                    selected_rows = ocr_rows
                    method = ocr_method

            transactions.extend(selected_rows)

            page_results.append({
                "pageNumber": page_number,
                "method": method,
                "extractedCount": len(selected_rows),
                "parsedCount": sum(
                    1
                    for item in selected_rows
                    if item["parseSuccess"]
                ),
            })

    parsed_count = sum(
        1
        for item in transactions
        if item["parseSuccess"]
    )

    return {
        "totalPages": total_pages,
        "extractedCount": len(transactions),
        "parsedCount": parsed_count,
        "failedCount": len(transactions) - parsed_count,
        "pageResults": page_results,
        "transactions": transactions,
    }