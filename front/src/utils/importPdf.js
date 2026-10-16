import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../data/options';
import { classifyRows } from './importCsv';
import pdfWorkerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url';

// 한글 PDF가 참조하는 문자 매핑도 외부 CDN 대신 앱과 함께 제공한다.
const cMapUrls = import.meta.glob('/node_modules/pdfjs-dist/cmaps/*.bcmap', {
  query: '?url', import: 'default', eager: true,
});

class LocalCMapReaderFactory {
  async fetch({ name }) {
    const url = cMapUrls[`/node_modules/pdfjs-dist/cmaps/${name}.bcmap`];
    if (!url) throw new Error('PDF 문자 매핑을 찾을 수 없습니다.');
    const response = await fetch(url);
    if (!response.ok) throw new Error('PDF 문자 매핑을 읽지 못했습니다.');
    return { cMapData: new Uint8Array(await response.arrayBuffer()), compressionType: 1 };
  }
}

const DATE_RE = /\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|(?<!\d)\d{1,2}[-/.]\d{1,2}(?![\d.])/;
const KIND_RE = /수입|입금|지출|출금/;
const AMOUNT_RE = /(\d{1,3}(?:,\d{3})+|\d{4,})\s*원?\s*$/;
const ALL_CATEGORIES = [...INCOME_CATEGORIES, ...EXPENSE_CATEGORIES];
const HEADER_WORDS = ['날짜', '구분', '카테고리', '내용', '금액'];

/** PDF에서 글자를 뽑아 줄 단위로 합친다. 같은 높이(y)에 놓인 글자 조각을 한 줄로 본다. */
export function groupPdfTextLines(items) {
  const rows = [];
  // PDF의 텍스트 조각은 저장 순서가 뒤섞일 수 있어 위치(y, x)로 행과 열을 복원한다.
  for (const item of items.filter((item) => typeof item.str === 'string' && item.str.trim())) {
    const x = item.transform[4];
    const y = item.transform[5];
    const tolerance = Math.max(1, Math.min(Math.abs(item.height || 10) * 0.25, 3));
    let row = rows.find((candidate) => Math.abs(candidate.y - y) <= tolerance);
    if (!row) { row = { y, items: [] }; rows.push(row); }
    row.items.push({ x, text: item.str });
  }
  return rows.sort((a, b) => b.y - a.y)
    .map((row) => row.items.sort((a, b) => a.x - b.x).map((item) => item.text).join(' ').replace(/\s+/g, ' ').trim());
}

export async function extractPdfLines(file, { withSource = false } = {}) {
  if (!file?.size) throw new Error('빈 PDF 파일은 읽을 수 없습니다.');
  const { getDocument, GlobalWorkerOptions } = await import('pdfjs-dist/legacy/build/pdf.mjs');
  GlobalWorkerOptions.workerSrc = pdfWorkerUrl;
  const task = getDocument({
    data: new Uint8Array(await file.arrayBuffer()),
    CMapReaderFactory: LocalCMapReaderFactory,
    isEvalSupported: false,
  });
  try {
    const document = await task.promise;
    const lines = [];
    for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
      const page = await document.getPage(pageNumber);
      const content = await page.getTextContent();
      const pageLines = groupPdfTextLines(content.items);
      // 기존 문자열 반환은 유지하며, 검토용 요청에는 파일명과 페이지도 함께 전달한다.
      lines.push(...pageLines.map((text) => withSource
        ? { text, fileName: file.name, page: pageNumber }
        : text));
      page.cleanup();
    }
    if (!lines.length) throw new Error('추출 가능한 글자가 없습니다. 스캔 PDF는 지원하지 않으므로 직접 입력해 주세요.');
    return lines;
  } catch (error) {
    if (error.name === 'PasswordException') throw new Error('암호가 설정된 PDF입니다. 암호를 해제한 파일을 선택해 주세요.');
    if (error.name === 'InvalidPDFException') throw new Error('올바른 PDF가 아니거나 파일이 손상되었습니다.');
    throw error;
  } finally {
    // 성공·실패와 관계없이 PDF 문서와 작업자 자원을 정리한다.
    await task.destroy();
  }
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
    .map((line) => typeof line === 'string' ? { text: line } : line)
    .filter((line) => !HEADER_WORDS.every((word) => line.text.includes(word)))
    .map((line) => ({ ...parsePdfLine(line.text), source: { text: line.text, fileName: line.fileName, page: line.page } }))
    .filter((r) => r.date || r.amt);
  const result = classifyRows(rawRows);
  // 여러 PDF를 연속 처리해도 확인 내역 ID가 충돌하지 않도록 한다.
  result.ambiguous = result.ambiguous.map((row) => ({ ...row, id: `imp${crypto.randomUUID()}` }));
  console.log(result);
  return result;
}
