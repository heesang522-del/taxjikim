import re

from datetime import date
from decimal import Decimal, InvalidOperation
from io import BytesIO

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


def clean(value):
    if value is None:
        return ""

    return re.sub(r"\s+", " ", str(value)).strip()


def parse_amount(value):
    """빈칸과 0원을 구분한다."""

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


def parse_leading_fields(text):
    """행 앞의 날짜와 거래 구분을 분리한다."""

    original = clean(text)

    match = DATE_AT_START.match(original)

    if not match:
        return None

    try:
        year, month, day = map(int, match.groups())

        transaction_date = date(
            year,
            month,
            day,
        ).isoformat()

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
    page_number,
    fields,
    raw_text,
    pre_tax_income=None,
    tax_deducted=None,
    actual_amount=None,
    errors=None,
):
    """인식에 실패한 거래 행도 결과에서 삭제하지 않는다."""

    errors = list(errors or [])

    if fields["transactionDate"] is None:
        errors.append("날짜 인식 실패")

    if fields["transactionType"] == "UNKNOWN":
        errors.append("수입·지출 구분 인식 실패")

    if not fields["description"]:
        errors.append("거래 설명 인식 실패")

    if actual_amount is None:
        errors.append("실제 입출금액 인식 실패")

    errors = list(dict.fromkeys(errors))

    success = not errors

    return {
        "transactionId": None,
        "taxInputId": None,
        "userId": None,

        "transactionDate": fields["transactionDate"],
        "transactionType": fields["transactionType"],

        "description": fields["description"],
        "category": None,

        "amount": actual_amount,

        "preTaxIncome": pre_tax_income,
        "taxDeducted": tax_deducted,
        "actualAmount": actual_amount,

        "edited": False,

        "pageNumber": page_number,
        "rawText": raw_text,

        "parseSuccess": success,

        # 구분이 미분류여도 금액이 있으면 화면에서 선택할 수 있다.
        "excluded": actual_amount is None,

        "status": "PARSED" if success else "PARSE_FAILED",

        "failureReason": (
            " / ".join(errors)
            if errors
            else None
        ),
    }


def group_words_into_lines(words, tolerance=3):
    """PDF 단어의 세로 위치를 기준으로 행을 구성한다."""

    lines = []

    ordered_words = sorted(
        words,
        key=lambda item: (
            item["top"],
            item["x0"],
        ),
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
        line["words"].sort(
            key=lambda item: item["x0"]
        )

        line["text"] = clean(
            " ".join(
                item["text"]
                for item in line["words"]
            )
        )

    return lines


def find_money_columns(lines):
    """머리글에서 세전·세금·실제 입출금액 열 위치를 찾는다."""

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

            elif (
                token.startswith("차감")
                or token == "tax"
            ):
                tax_x = word["x0"]

            elif (
                token.startswith("실제")
                or token == "actual"
            ):
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


def read_money_cell(words, label, errors):
    text = clean(
        " ".join(word["text"] for word in words)
    )

    if not text:
        return None

    amount = parse_amount(text)

    if amount is None:
        errors.append(f"{label} 인식 실패")

    return amount


def parse_positioned_line(line, columns, page_number):
    """숫자 개수가 아닌 열 위치로 거래를 읽는다."""

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
        " ".join(
            word["text"]
            for word in leading_words
        )
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

    errors = []

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


def has_multiple_money_headers(text):
    normalized = clean(text).casefold()

    korean = (
        "세전" in normalized
        and "차감" in normalized
        and "실제" in normalized
    )

    english = (
        (
            "pre-tax" in normalized
            or "pretax" in normalized
        )
        and "tax deducted" in normalized
        and "actual" in normalized
    )

    return korean or english


def parse_plain_text(text, page_number):
    """
    좌표가 없는 텍스트를 처리한다.

    여러 금액 중 하나를 임의로 선택하지 않는다.
    분석하지 못해도 거래 행은 결과에 보존한다.
    """

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


def build_ocr_map(ocr_pages):
    result = {}

    for item in ocr_pages or []:
        if not isinstance(item, dict):
            continue

        page_number = item.get("pageNumber")
        text = item.get("text")

        if (
            type(page_number) is int
            and page_number > 0
            and isinstance(text, str)
        ):
            result[page_number] = text

    return result


def extract_transactions(pdf_bytes, ocr_pages=None):
    if not isinstance(pdf_bytes, bytes):
        raise ValueError("PDF 데이터는 bytes여야 합니다.")

    if not pdf_bytes.startswith(b"%PDF-"):
        raise ValueError("올바른 PDF 파일이 아닙니다.")

    ocr_by_page = build_ocr_map(ocr_pages)

    transactions = []
    page_results = []

    with pdfplumber.open(BytesIO(pdf_bytes)) as pdf:
        total_pages = len(pdf.pages)

        for page_number, page in enumerate(
            pdf.pages,
            start=1,
        ):
            words = page.extract_words(
                x_tolerance=2,
                y_tolerance=3,
            )

            lines = group_words_into_lines(words)

            columns = find_money_columns(lines)

            page_transactions = []

            if columns is not None:
                method = "COLUMN_POSITION"

                for line in lines:
                    transaction = parse_positioned_line(
                        line,
                        columns,
                        page_number,
                    )

                    if transaction is not None:
                        page_transactions.append(transaction)

            else:
                text = page.extract_text() or ""

                if not text.strip():
                    text = ocr_by_page.get(page_number, "")
                    method = "OCR_TEXT"
                else:
                    method = "TEXT"

                page_transactions = parse_plain_text(
                    text,
                    page_number,
                )

            transactions.extend(page_transactions)

            page_results.append({
                "pageNumber": page_number,
                "method": method,
                "extractedCount": len(page_transactions),
                "parsedCount": sum(
                    1
                    for item in page_transactions
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