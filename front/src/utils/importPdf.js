import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../data/options';
import { classifyRows } from './importCsv';

const DATE_RE = /\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|(?<!\d)\d{1,2}[-/.]\d{1,2}(?![\d.])/;
const KIND_RE = /수입|입금|지출|출금/;
const AMOUNT_RE = /(\d{1,3}(?:,\d{3})+|\d{4,})\s*원?\s*$/;
const ALL_CATEGORIES = [...INCOME_CATEGORIES, ...EXPENSE_CATEGORIES];
const HEADER_WORDS = ['날짜', '구분', '카테고리', '내용', '금액'];

/** PDF에서 글자를 뽑아 줄 단위로 합친다. 같은 높이(y)에 놓인 글자 조각을 한 줄로 본다. */
export async function extractPdfLines() {
  // TODO: PDF 읽기는 pdfjs-dist 패키지가 필요하다. 설치 승인 후 taxlab 원본(src/utils/importPdf.js)의 extractPdfLines를 옮겨 온다.
  throw new Error('PDF 가져오기는 준비 중입니다. CSV 파일을 이용해 주세요.');
}

/** 줄 하나(예: "2025-03-12 지출 장비·소프트웨어 맥북 1,500,000")를 날짜·구분·카테고리·내용·금액으로 나눈다. 못 읽은 항목은 빈 문자열로 둔다. */
export function parsePdfLine(line) {
  let rest = line.trim();
  const date = rest.match(DATE_RE)?.[0] ?? '';
  if (date) rest = rest.replace(date, ' ');
  const amt = rest.match(AMOUNT_RE)?.[1] ?? '';
  if (amt) rest = rest.replace(AMOUNT_RE, ' ');
  const kind = rest.match(KIND_RE)?.[0] ?? '';
  if (kind) rest = rest.replace(kind, ' ');
  const cat = ALL_CATEGORIES.find((c) => rest.includes(c)) ?? '';
  if (cat) rest = rest.replace(cat, ' ');
  return { date, kind, cat, memo: rest.replace(/\s+/g, ' ').trim(), amt };
}

/** 줄 목록에서 내역처럼 보이는 줄(날짜나 금액이 있는 줄)만 골라 확실한 내역과 확인이 필요한 내역으로 나눈다. */
export function parsePdfLines(lines) {
  const rawRows = lines
    .filter((l) => !HEADER_WORDS.every((w) => l.includes(w)))
    .map(parsePdfLine)
    .filter((r) => r.date || r.amt);
  return classifyRows(rawRows);
}
