import { YEAR } from './taxConstants';
import { EXPENSE_CATEGORIES } from './options';

const incomeMan = [3600, 3300, 4200, 3900, 3500, 4100, 3800, 3700, 4300];
const incomeMemo = ['A사 앱 개발', 'B사 유지보수', 'C사 외주', 'A사 앱 개발', 'B사 유지보수', 'D사 웹 제작', 'C사 외주', 'B사 유지보수', 'E사 컨설팅'];
const expenseMan = [820, 640, 1250, 760, 980, 700, 1430, 820, 910];
const expenseCat = [0, 3, 2, 4, 1, 0, 5, 6, 2];
const expenseMemo = ['맥북 구매 할부', '통신요금', '출장 KTX', '코워킹 스페이스', '외주 디자인', '클라우드 서버', '온라인 강의', 'SaaS 구독', '출장 KTX'];
const pad = (n) => String(n).padStart(2, '0');

export const sampleRows = [
  ...incomeMan.map((v, i) => ({ id: `i${i}`, kind: 'in', m: i + 1, date: `${YEAR}-${pad(i + 1)}-25`, cat: '프리랜서 용역비', memo: incomeMemo[i], amt: v * 1e4 })),
  ...expenseMan.map((v, i) => ({ id: `e${i}`, kind: 'out', m: i + 1, date: `${YEAR}-${pad(i + 1)}-10`, cat: EXPENSE_CATEGORIES[expenseCat[i]], memo: expenseMemo[i], amt: v * 1e4 })),
];
