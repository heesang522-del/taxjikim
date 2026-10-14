import { BRACKETS, BASIC_DEDUCTION, STANDARD_CREDIT, SIMPLE_EXPENSE_RATE, WITHHOLDING_RATE, LOCAL_TAX_RATE, MONTH_NOW, FORECAST_WINDOW } from '../data/taxConstants';

const sum = (arr) => arr.reduce((s, x) => s + x, 0);

export function findBracket(taxable) {
  for (const [limit, rate, deduction] of BRACKETS) {
    if (taxable <= limit) return { rate, tax: Math.max(0, taxable * rate - deduction) };
  }
}

/** 월별 수입·지출. MONTH_NOW 이후 달은 최근 평균으로 예측한다. */
export function buildMonthly(rows) {
  const inc = Array(12).fill(0);
  const exp = Array(12).fill(0);
  rows.forEach((r) => { (r.kind === 'in' ? inc : exp)[r.m - 1] += r.amt; });
  const avg = (arr) => {
    let s = 0, c = 0;
    for (let i = MONTH_NOW - FORECAST_WINDOW; i < MONTH_NOW; i++) if (arr[i] > 0) { s += arr[i]; c++; }
    return c ? s / c : 0;
  };
  const ai = avg(inc), ae = avg(exp);
  for (let i = MONTH_NOW; i < 12; i++) { inc[i] = ai; exp[i] = ae; }
  return { inc, exp };
}

/** opts: { extraInc, extraExp, yellow } — 시뮬레이션 가정값 */
export function calcTax(rows, auto33, opts = {}) {
  const m = buildMonthly(rows);
  const income = sum(m.inc) + (opts.extraInc || 0);
  const expense = sum(m.exp) + (opts.extraExp || 0);
  const biz = Math.max(0, income - expense);
  const yellow = opts.yellow || 0;
  const deduction = BASIC_DEDUCTION + yellow;
  const taxable = Math.max(0, biz - deduction);
  const { rate, tax } = findBracket(taxable);
  const credit = Math.min(STANDARD_CREDIT, tax);
  const withheld = auto33 ? income * WITHHOLDING_RATE : 0;
  const net = tax - credit - withheld; // 소득세만. 음수 = 환급
  const local = Math.max(0, tax - credit) * LOCAL_TAX_RATE; // 지방소득세
  const localWithheld = withheld * LOCAL_TAX_RATE; // 3.3% 원천징수 중 지방소득세 0.3%
  const total = net + local - localWithheld; // 소득세 + 지방소득세. 음수 = 환급
  return { income, expense, biz, deduction, taxable, rate, tax, credit, withheld, net, local, localWithheld, total, monthly: m };
}

/** 단순경비율 적용 비교(예시값) */
export function calcSimple(rows, auto33) {
  const m = buildMonthly(rows);
  const income = sum(m.inc);
  const biz = income * (1 - SIMPLE_EXPENSE_RATE);
  const taxable = Math.max(0, biz - BASIC_DEDUCTION);
  const { tax } = findBracket(taxable);
  const credit = Math.min(STANDARD_CREDIT, tax);
  const withheld = auto33 ? income * WITHHOLDING_RATE : 0;
  const net = tax - credit - withheld;
  const total = net + Math.max(0, tax - credit) * LOCAL_TAX_RATE - withheld * LOCAL_TAX_RATE;
  return { tax, net, total };
}

export const sumBy = (arr, fn) => arr.reduce((s, x) => s + fn(x), 0);
