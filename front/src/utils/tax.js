import { YEAR, BRACKETS, BASIC_DEDUCTION, STANDARD_CREDIT, SIMPLE_EXPENSE_RATE, WITHHOLDING_RATE, LOCAL_TAX_RATE, MONTH_NOW, FORECAST_WINDOW, } from '../data/taxConstants';

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
 * 사용자가 제외하지 않은 유효 금액 거래만 계산한다.
 */
function isIncluded(row) {
  return row != null && row.selected !== false && row.excluded !== true && toAmount(row.amt) !== null;
}

const sum = (values) => values.reduce((total, value) => total + value, 0);

export const sumBy = (rows, getValue) => rows.reduce((total, row) => total + getValue(row), 0);

/**
 * 날짜·월별 자료·연간 자료에 명시된 기간을 확인한다.
 * 연도가 없는 거래에 현재 연도나 선택 연도를 임의로 붙이지 않는다.
 */
function getPeriod(row) {
  if (!row) {
    return null;
  }

  if (row.date) {
    const match = String(row.date).match(/^(\d{4})-(\d{2})-(\d{2})$/);

    if (!match) {
      return null;
    }

    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);

    if (year < 1000 || year > 9999) {
      return null;
    }

    const date = new Date(Date.UTC(year, month - 1, day));

    if (date.getUTCFullYear() !== year || date.getUTCMonth() + 1 !== month || date.getUTCDate() !== day) {
      return null;
    }

    return { year, month };
  }

  if (row.month) {
    const match = String(row.month).match(/^(\d{4})-(\d{2})$/);

    if (!match) {
      return null;
    }

    const year = Number(match[1]);
    const month = Number(match[2]);

    if (year < 1000 || year > 9999 || month < 1 || month > 12) {
      return null;
    }

    return { year, month };
  }

  if (/^\d{4}$/.test(String(row.year ?? ''))) {
    const year = Number(row.year);

    if (year >= 1000 && year <= 9999) {
      return { year, month: null };
    }
  }

  return null;
}

/**
 * 자료 저장 지원과 세액 계산 지원을 분리한다.
 * 기존 예시 계산 기준을 다른 귀속 연도에 적용하지 않는다.
 */
function validateCalculationYear(taxYear) {
  if (!Number.isInteger(taxYear) || taxYear < 1000 || taxYear > 9999) {
    throw new Error('귀속 연도를 네 자리 숫자로 선택해주세요.');
  }

  if (taxYear !== YEAR) {
    throw new Error(`${taxYear}년 세액 계산 기준은 아직 제공하지 않습니다. 자료 저장·조회는 가능합니다.`);
  }
}

/**
 * 선택한 연도의 유효 거래만 계산 대상으로 사용한다.
 */
function getCalculationRows(rows, taxYear) {
  validateCalculationYear(taxYear);

  if (!Array.isArray(rows)) {
    throw new Error('거래 목록이 올바르지 않습니다.');
  }

  return rows.filter((row) => {
    if (!isIncluded(row)) {
      return false;
    }

    const period = getPeriod(row);

    return period !== null && period.year === taxYear;
  });
}

/**
 * 기존 예시 세율 구간으로 산출세액을 계산한다.
 */
export function findBracket(taxable) {
  if (!Number.isFinite(taxable) || taxable < 0) {
    throw new Error('과세표준이 올바르지 않습니다.');
  }

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
 * 월별 수입·지출을 집계한다.
 * 거래에 적힌 날짜·월을 사용하여 다른 연도와 섞이지 않게 한다.
 *
 * MONTH_NOW와 FORECAST_WINDOW에 따른 예측 방식은
 * 기존 예시 계산의 동작을 유지한다.
 */
export function buildMonthly(rows, taxYear = YEAR) {
  const calculationRows = getCalculationRows(rows, taxYear);

  const inc = Array(12).fill(0);
  const exp = Array(12).fill(0);

  const incomeMonths = new Set();
  const expenseMonths = new Set();

  calculationRows.forEach((row) => {
    const period = getPeriod(row);

    // 월이 없는 연간 자료는 별도 합산한다.
    if (period.month === null) {
      return;
    }

    const amount = toAmount(row.amt);
    const index = period.month - 1;

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
 * 월이 없는 연간 자료만 합산한다.
 * 월별 거래는 buildMonthly에서 처리하므로 중복 반영하지 않는다.
 */
function sumAnnualRows(rows, kind) {
  return sumBy(
    rows.filter((row) => {
      const period = getPeriod(row);

      return period !== null && period.month === null && row.kind === kind;
    }),
    (row) => toAmount(row.amt)
  );
}

/**
 * 일반 계산과 단순경비율 비교에서 사용하는 공통 세액 계산이다.
 * 기존 예시 공제·원천징수·지방세 계산식을 유지한다.
 */
function calculateSettlement(income, taxable, auto33) {
  const { rate, tax } = findBracket(taxable);

  const credit = Math.min(STANDARD_CREDIT, tax);
  const withheld = auto33 ? income * WITHHOLDING_RATE : 0;

  // 소득세 차액이 음수이면 기존 시뮬레이션상 환급액이다.
  const net = tax - credit - withheld;

  const local = Math.max(0, tax - credit) * LOCAL_TAX_RATE;
  const localWithheld = withheld * LOCAL_TAX_RATE;
  const total = net + local - localWithheld;

  return {
    rate,
    tax,
    credit,
    withheld,
    net,
    local,
    localWithheld,
    total,
  };
}

/**
 * opts:
 * {
 *   taxYear,
 *   extraInc,
 *   extraExp,
 *   yellow
 * }
 *
 * 기존 호출부 호환을 위해 taxYear를 생략하면
 * 기존 예시 기준 연도인 YEAR를 사용한다.
 */
export function calcTax(rows, auto33, opts = {}) {
  const options = opts ?? {};
  const taxYear = options.taxYear ?? YEAR;

  const calculationRows = getCalculationRows(rows, taxYear);
  const monthly = buildMonthly(calculationRows, taxYear);

  const income = sum(monthly.inc) + sumAnnualRows(calculationRows, 'in') + (toAmount(options.extraInc) ?? 0);

  const expense = sum(monthly.exp) + sumAnnualRows(calculationRows, 'out') + (toAmount(options.extraExp) ?? 0);

  const biz = Math.max(0, income - expense);
  const yellow = toAmount(options.yellow) ?? 0;
  const deduction = BASIC_DEDUCTION + yellow;
  const taxable = Math.max(0, biz - deduction);

  const settlement = calculateSettlement(income, taxable, auto33);

  return {
    taxYear,
    income,
    expense,
    biz,
    deduction,
    taxable,
    ...settlement,
    monthly,
  };
}

/**
 * 기존 예시 단순경비율로 비교 세액을 계산한다.
 */
export function calcSimple(rows, auto33, taxYear = YEAR) {
  const calculationRows = getCalculationRows(rows, taxYear);
  const monthly = buildMonthly(calculationRows, taxYear);

  const income = sum(monthly.inc) + sumAnnualRows(calculationRows, 'in');

  const biz = income * (1 - SIMPLE_EXPENSE_RATE);
  const taxable = Math.max(0, biz - BASIC_DEDUCTION);

  const { tax, net, total } = calculateSettlement(income, taxable, auto33);

  return {
    taxYear,
    tax,
    net,
    total,
  };
}
