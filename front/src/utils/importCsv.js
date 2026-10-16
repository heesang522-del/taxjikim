import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../data/options';
import { YEAR } from '../data/taxConstants';

// CSV 헤더 이름 → 내부 필드 매핑. 일치하는 헤더가 없으면 그 열은 무시한다.
const HEADER_MAP = { 날짜: 'date', 구분: 'kind', 카테고리: 'cat', 분류: 'cat', 내용: 'memo', 적요: 'memo', 금액: 'amt' };
const KIND_MAP = { 수입: 'in', 입금: 'in', 지출: 'out', 출금: 'out' };

// 카테고리가 비어 있을 때 내용(memo)에서 키워드로 추정한다. 추정값도 반드시 사용자가 확인해야 한다.
const EXPENSE_KEYWORDS = [
  ['장비·소프트웨어', ['노트북', '맥북', '장비', '소프트웨어', '구독', 'saas', '모니터', '아이패드', '컴퓨터']],
  ['외주·인건비', ['외주', '인건비']],
  ['교통·출장', ['출장', 'ktx', '교통', '항공', '버스', '택시', '주유']],
  ['통신비', ['통신', '휴대폰', '인터넷', '요금제']],
  ['임차료·작업공간', ['임차', '월세', '코워킹', '작업공간', '사무실']],
  ['광고·마케팅', ['광고', '마케팅', '홍보']],
  ['교육·도서', ['강의', '교육', '도서', '책', '세미나', '수강']],
];
const INCOME_KEYWORDS = [
  ['프리랜서 용역비', ['용역', '개발', '제작', '유지보수', '컨설팅']],
  ['인세·저작권료', ['인세', '저작권']],
  ['광고·후원 수익', ['광고', '후원', '협찬']],
];

const guessCategory = (kind, memo) => {
  const text = memo.toLowerCase();
  const table = kind === 'in' ? INCOME_KEYWORDS : EXPENSE_KEYWORDS;
  const hit = table.find(([, keywords]) => keywords.some((k) => text.includes(k)));
  return hit ? hit[0] : '';
};

const validMonthDay = (mo, d) => mo >= 1 && mo <= 12 && d >= 1 && d <= 31;

const parseDate = (raw) => {
  const s = raw.trim();
  let m = s.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/);
  if (m && validMonthDay(Number(m[2]), Number(m[3]))) return `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`;
  m = s.match(/^(\d{1,2})[-/.](\d{1,2})$/); // 연도 생략 (MM-DD)
  if (m && validMonthDay(Number(m[1]), Number(m[2]))) return `${YEAR}-${m[1].padStart(2, '0')}-${m[2].padStart(2, '0')}`;
  return '';
};

const parseAmount = (raw) => {
  const digits = String(raw).replace(/[^\d]/g, '');
  return digits ? Number(digits) : 0;
};

const splitLine = (line) => line.split(',').map((c) => c.trim().replace(/^"|"$/g, ''));

/** 날짜·구분·카테고리·내용·금액을 문자열로 담은 행들을 확실한 내역(confident)과 확인이 필요한 내역(ambiguous)으로 나눈다. CSV·PDF 공용. */
export function classifyRows(rawRows) {
  const confident = [];
  const ambiguous = [];

  rawRows.forEach((raw, i) => {
    const source = raw.source ? { source: raw.source } : {};
    const reasons = [];
    const date = raw.date ? parseDate(raw.date) : '';
    if (!date) reasons.push('날짜 확인 필요');

    const kind = raw.kind ? (KIND_MAP[raw.kind.trim()] || '') : '';
    if (!kind) reasons.push('구분(수입/지출) 확인 필요');

    const amt = raw.amt ? parseAmount(raw.amt) : 0;
    if (!amt) reasons.push('금액 확인 필요');

    const memo = (raw.memo || '').trim() || '(내용 없음)';
    const knownCats = kind === 'in' ? INCOME_CATEGORIES : kind === 'out' ? EXPENSE_CATEGORIES : [];
    const catRaw = (raw.cat || '').trim();
    const cat = knownCats.includes(catRaw) ? catRaw : '';
    if (!cat) {
      if (kind) reasons.push(catRaw ? '카테고리 확인 필요 (알 수 없는 값)' : '카테고리 확인 필요');
      else reasons.push('카테고리 확인 필요');
    }

    if (!reasons.length) {
      confident.push({ ...source, kind, m: Math.min(12, Number(date.slice(5, 7))), date, cat, memo, amt });
    } else {
      ambiguous.push({ ...source, id: `imp${Date.now()}_${i}`, date, kind, amt, memo, cat: cat || (kind ? guessCategory(kind, memo) : ''), reasons });
    }
  });

  return { confident, ambiguous };
}

/** CSV 텍스트를 읽어 확실한 내역(confident)과 확인이 필요한 내역(ambiguous)으로 나눈다. */
export function parseCsv(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  if (!lines.length) return { confident: [], ambiguous: [] };

  const fields = splitLine(lines[0]).map((h) => HEADER_MAP[h] || null);
  const rawRows = lines.slice(1).map((line) => {
    const cells = splitLine(line);
    const raw = {};
    fields.forEach((f, idx) => { if (f && !(f in raw)) raw[f] = cells[idx] ?? ''; });
    return raw;
  });
  return classifyRows(rawRows);
}
