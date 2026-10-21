import {
  BRACKETS,
  BASIC_DEDUCTION,
  STANDARD_CREDIT,
  SIMPLE_EXPENSE_RATE,
  WITHHOLDING_RATE,
  LOCAL_TAX_RATE,
  MONTH_NOW,
  FORECAST_WINDOW,
} from '../data/taxConstants';

/**
 * 금액을 숫자로 변환한다.
 * 빈 값, 음수, 숫자가 아닌 값은 계산에서 제외한다.
 */
function toAmount(value) {
  if (value === null || value === undefined) {
    return null;
  }

  if (typeof value !== 'number' && typeof value !== 'string') {
    return null;
  }

  const normalized = typeof value === 'string' ? value.replace(/,/g, '').replace(/원/g, '').trim() : value;

  if (normalized === '') {
    return null;
  }

  const amount = Number(normalized);

  return Number.isFinite(amount) && amount >= 0 ? amount : null;
}

/**
 * 계산에 포함할 거래인지 확인한다.
 */
function isIncluded(row) {
  return row != null && row.selected !== false && row.excluded !== true && toAmount(row.amt) !== null;
}

const sum = (arr) => arr.reduce((total, value) => total + value, 0);

export const sumBy = (arr, fn) => arr.reduce((total, item) => total + fn(item), 0);

export function findBracket(taxable) {
  for (const [limit, rate, deduction] of BRACKETS) {
    if (taxable <= limit) {
      return {
        rate,
        tax: Math.max(0, taxable * rate - deduction),
      };
    }
  }

  throw new Error('과세표준에 해당하는 세율 구간이 없습니다.');
}

/**
 * 유효한 월이 있는 거래인지 확인한다.
 */
function hasMonth(row) {
  return Number.isInteger(row.m) && row.m >= 1 && row.m <= 12;
}

/**
 * 월이 없는 연간 합계 거래를 합산한다.
 * 월이 있는 거래는 buildMonthly에서 이미 합산하므로 중복 반영하지 않는다.
 */
function sumAnnualRows(rows, kind) {
  return sumBy(
    rows.filter((row) => isIncluded(row) && row.year && !hasMonth(row) && row.kind === kind),
    (row) => toAmount(row.amt)
  );
}

/**
 * 월별 수입·지출을 집계한다.
 * 구분 미확인 거래는 수입·지출 어느 쪽에도 임의로 넣지 않는다.
 * MONTH_NOW 이후 빈 월은 기존 방식대로 최근 평균으로 예측한다.
 */
export function buildMonthly(rows) {
  const inc = Array(12).fill(0);
  const exp = Array(12).fill(0);

  const incomeMonths = new Set();
  const expenseMonths = new Set();

  rows.forEach((row) => {
    if (!isIncluded(row) || !hasMonth(row)) {
      return;
    }

    const amount = toAmount(row.amt);
    const index = row.m - 1;

    if (row.kind === 'in') {
      inc[index] += amount;
      incomeMonths.add(index);
    } else if (row.kind === 'out') {
      exp[index] += amount;
      expenseMonths.add(index);
    }
  });

  const average = (amounts) => {
    let total = 0;
    let count = 0;

    const start = Math.max(0, MONTH_NOW - FORECAST_WINDOW);
    const end = Math.min(12, MONTH_NOW);

    for (let index = start; index < end; index++) {
      if (amounts[index] > 0) {
        total += amounts[index];
        count++;
      }
    }

    return count > 0 ? total / count : 0;
  };

  const averageIncome = average(inc);
  const averageExpense = average(exp);

  // 실제 거래가 있는 월은 예측값으로 덮어쓰지 않는다.
  for (let index = Math.max(0, MONTH_NOW); index < 12; index++) {
    if (!incomeMonths.has(index)) {
      inc[index] = averageIncome;
    }

    if (!expenseMonths.has(index)) {
      exp[index] = averageExpense;
    }
  }

  return { inc, exp };
}

/**
 * opts: { extraInc, extraExp, yellow }
 * 기존 세금 시뮬레이션 공식을 유지한다.
 */
export function calcTax(rows, auto33, opts = {}) {
  const monthly = buildMonthly(rows);

  const income = sum(monthly.inc) + sumAnnualRows(rows, 'in') + (toAmount(opts.extraInc) ?? 0);

  const expense = sum(monthly.exp) + sumAnnualRows(rows, 'out') + (toAmount(opts.extraExp) ?? 0);

  const biz = Math.max(0, income - expense);
  const yellow = toAmount(opts.yellow) ?? 0;
  const deduction = BASIC_DEDUCTION + yellow;
  const taxable = Math.max(0, biz - deduction);

  const { rate, tax } = findBracket(taxable);

  const credit = Math.min(STANDARD_CREDIT, tax);
  const withheld = auto33 ? income * WITHHOLDING_RATE : 0;

  // 소득세 차액. 음수이면 시뮬레이션상 환급액이다.
  const net = tax - credit - withheld;

  const local = Math.max(0, tax - credit) * LOCAL_TAX_RATE;
  const localWithheld = withheld * LOCAL_TAX_RATE;
  const total = net + local - localWithheld;

  return {
    income,
    expense,
    biz,
    deduction,
    taxable,
    rate,
    tax,
    credit,
    withheld,
    net,
    local,
    localWithheld,
    total,
    monthly,
  };
}

/**
 * 단순경비율 적용 비교.
 * 기존 예시 경비율과 계산 공식을 유지한다.
 */
export function calcSimple(rows, auto33) {
  const monthly = buildMonthly(rows);

  const income = sum(monthly.inc) + sumAnnualRows(rows, 'in');

  const biz = income * (1 - SIMPLE_EXPENSE_RATE);
  const taxable = Math.max(0, biz - BASIC_DEDUCTION);

  const { tax } = findBracket(taxable);

  const credit = Math.min(STANDARD_CREDIT, tax);
  const withheld = auto33 ? income * WITHHOLDING_RATE : 0;

  const net = tax - credit - withheld;
  const local = Math.max(0, tax - credit) * LOCAL_TAX_RATE;
  const localWithheld = withheld * LOCAL_TAX_RATE;
  const total = net + local - localWithheld;

  return {
    tax,
    net,
    total,
  };
}
