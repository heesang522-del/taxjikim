import { client } from './client';

// 서버의 공통 응답과 오류를 처리한다.
// 본문이 없는 204 응답도 정상적으로 처리한다.
async function request(config) {
  try {
    const response = await client.request(config);
    const body = response.data;

    if (body?.success === false) {
      const error = new Error(body.error?.message ?? '요청 처리에 실패했습니다.');

      error.code = body.error?.code;
      error.status = response.status;
      error.fields = body.error?.fields;

      throw error;
    }

    return body;
  } catch (cause) {
    if (cause?.code === 'ERR_CANCELED' || cause?.name === 'CanceledError') {
      throw cause;
    }

    const detail = cause?.response?.data?.error;

    const error = new Error(detail?.message ?? cause?.message ?? '서버 요청에 실패했습니다.');

    error.code = detail?.code ?? cause?.code;
    error.status = cause?.response?.status ?? cause?.status;
    error.fields = detail?.fields ?? cause?.fields;

    throw error;
  }
}

function validateId(value, label = '번호') {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${label}를 확인해주세요.`);
  }

  return value;
}

function validateYear(value) {
  const year = Number(value);

  if (!Number.isInteger(year) || year < 1000 || year > 9999) {
    throw new Error('귀속 연도를 확인해주세요.');
  }

  return year;
}

function validateCategory(value) {
  if (typeof value !== 'string' || !/^[A-Z][A-Z0-9_]{0,49}$/.test(value)) {
    throw new Error('카테고리 코드를 확인해주세요.');
  }

  return value;
}

function optionalNumber(value, label, fallback = null) {
  if (value == null || String(value).trim() === '') {
    return fallback;
  }

  const number = Number(String(value).replace(/,/g, '').trim());

  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`${label}를 확인해주세요.`);
  }

  return number;
}

function optionalInteger(value, label, fallback = null) {
  const number = optionalNumber(value, label, fallback);

  if (number !== null && !Number.isSafeInteger(number)) {
    throw new Error(`${label}는 정수로 입력해주세요.`);
  }

  return number;
}

// 분석 기본정보를 서버 요청 형식으로 정리한다.
function normalizeTaxInput(input) {
  if (!input || typeof input !== 'object') {
    throw new Error('분석 기본정보가 필요합니다.');
  }

  const region = String(input.businessRegion ?? '').trim();

  return {
    ...input,
    taxYear: validateYear(input.taxYear),
    businessRegion: region || null,
    age: optionalInteger(input.age, '만 나이'),
    dependentCount: optionalInteger(input.dependentCount, '부양가족 수', 0),
    donationAmount: optionalNumber(input.donationAmount, '기부금', 0),
  };
}

function inputPath(taxInputId) {
  validateId(taxInputId, '분석번호');

  return `/tax/inputs/${taxInputId}`;
}

function transactionPath(taxInputId, transactionId) {
  validateId(transactionId, '거래번호');

  return `${inputPath(taxInputId)}/transactions/${transactionId}`;
}

// 분석번호에 해당하는 실제·예상 세전 총수입을 조회한다.
export async function getTotalIncome(taxInputId, { signal } = {}) {
  return request({ url: `${inputPath(taxInputId)}/total-income`, method: 'GET', signal });
}

// 분석번호에 해당하는 실제·예상 총지출을 조회한다.
export async function getTotalExpense(taxInputId, { signal } = {}) {
  return request({ url: `${inputPath(taxInputId)}/total-expense`, method: 'GET', signal });
}

// 분석번호에 해당하는 월별 실제·예상 수입을 조회한다.
export async function getMonthlyIncomes(taxInputId, { signal } = {}) {
  return request({ url: `${inputPath(taxInputId)}/monthly-incomes`, method: 'GET', signal });
}

// 분석번호에 해당하는 월별 실제·예상 지출을 조회한다.
export async function getMonthlyExpenses(taxInputId, { signal } = {}) {
  return request({ url: `${inputPath(taxInputId)}/monthly-expenses`, method: 'GET', signal });
}

function dateText(value) {
  if (Array.isArray(value) && value.length === 3) {
    return value.map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
  }

  return typeof value === 'string' ? value.trim() : '';
}

function validateTransactionDate(value) {
  const date = dateText(value);

  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    throw new Error('날짜별 거래 내역이 필요합니다.');
  }

  const parsed = new Date(`${date}T00:00:00Z`);

  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
    throw new Error('유효한 거래 날짜를 입력해주세요.');
  }

  validateYear(date.slice(0, 4));

  return date;
}

// 저장 응답의 거래를 원래 화면 거래와 연결한다.
// 동일한 내용의 거래는 별도 배열에 담아 순서대로 연결한다.
function transactionKey(row) {
  return JSON.stringify([
    row.transactionType,
    dateText(row.transactionDate),
    row.description,
    row.category,
    Number(row.amount),
    row.preTaxIncome == null ? null : Number(row.preTaxIncome),
    row.deductedTaxRate == null ? null : Number(row.deductedTaxRate),
  ]);
}

export async function createTaxInput(input) {
  return request({
    url: '/tax/inputs/create',
    method: 'POST',
    data: normalizeTaxInput(input),
  });
}

export async function updateTaxInput(taxInputId, input) {
  return request({
    url: `${inputPath(taxInputId)}/update`,
    method: 'POST',
    data: normalizeTaxInput(input),
  });
}

export async function getTaxInput(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/detail`,
    method: 'POST',
  });
}

export async function getSavedTaxInputs(taxYear = null) {
  const year = taxYear === null ? null : validateYear(taxYear);

  return request({
    url: '/tax/inputs/list',
    method: 'POST',
    params: year === null ? {} : { taxYear: year },
  });
}

export async function deleteTaxInput(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/delete`,
    method: 'POST',
  });
}

// 신규 거래 저장 전용이다.
// 기존 거래의 수정 반영에서는 호출하지 않는다.
export async function saveTransactions(taxInputId, rows) {
  if (!Array.isArray(rows)) {
    throw new Error('저장할 거래 목록이 필요합니다.');
  }

  return request({
    url: `${inputPath(taxInputId)}/transactions/save`,
    method: 'POST',
    data: rows,
  });
}

export async function getTransactions(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/transactions/list`,
    method: 'POST',
  });
}

// 거래번호는 주소로 보내고 변경된 카테고리만 본문에 담는다.
export async function updateTransaction(taxInputId, transactionId, category) {
  validateCategory(category);

  return request({
    url: `${transactionPath(taxInputId, transactionId)}/category/update`,
    method: 'POST',
    data: { category },
  });
}

export async function deleteTransaction(taxInputId, transactionId) {
  return request({
    url: `${transactionPath(taxInputId, transactionId)}/delete`,
    method: 'POST',
  });
}

export async function getTaxResult(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/results/detail`,
    method: 'POST',
  });
}

export async function calculateTaxInput(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/calculate`,
    method: 'POST',
  });
}

// PDF 분석만 요청한다.
// 분석한 거래의 최초 저장은 별도 저장 API에서 수행한다.
export async function parsePdf(file, { signal, taxInputId = null } = {}) {
  if (!file) {
    throw new Error('PDF 파일을 선택해주세요.');
  }

  const formData = new FormData();
  formData.append('file', file);

  if (taxInputId !== null) {
    validateId(taxInputId, '분석번호');

    formData.append('taxInputId', String(taxInputId));
  }

  return request({
    url: '/tax/parse',
    method: 'POST',
    data: formData,
    timeout: 180000,
    signal,
  });
}

export async function uploadTaxPdfApi(file, options = {}) {
  const response = await parsePdf(file, options);
  const result = response?.data;

  if (!result || typeof result !== 'object' || !Array.isArray(result.transactions)) {
    throw new Error('PDF 분석 응답 형식이 올바르지 않습니다.');
  }

  if (
    result.persisted === true ||
    result.savedCount > 0 ||
    result.transactions.some((row) => row?.transactionId != null)
  ) {
    throw new Error('파싱 API에서 저장 결과가 반환됐습니다. 서버 적용 상태를 확인해주세요.');
  }

  const transactions = result.transactions.map((row) => {
    if (!row || typeof row !== 'object' || Array.isArray(row)) {
      throw new Error('PDF 응답에 잘못된 거래가 있습니다.');
    }

    const needsReview = row.parseSuccess !== true || row.needsReview === true || row.excluded === true;

    return {
      ...row,
      transactionId: null,
      taxInputId: null,
      persisted: false,
      needsReview,
      excluded: needsReview,
      failureReason: needsReview ? row.failureReason || '거래 내용을 확인해주세요.' : null,
    };
  });

  const reviewRows = transactions.filter((row) => row.needsReview);

  return {
    ...result,
    transactions,
    savedRows: [],
    reviewRows,
    reviewCount: reviewRows.length,
    savedCount: 0,
    persisted: false,
    taxInputIdsByYear: {},
    snapshots: [],
    originalFileName: result.fileName ?? file.name,
    fileId: result.fileId ?? null,
  };
}

// 저장 식별자를 제외한 입력 상태를 비교한다.
// 식별자가 연결되었다는 이유만으로 입력 변경으로 판단하지 않는다.
export function getTaxReviewSignature(state) {
  function inputOnly(row) {
    return Object.fromEntries(
      Object.entries(row).filter(([key]) => key !== 'transactionId' && key !== 'taxInputId' && key !== 'persisted')
    );
  }

  return JSON.stringify({
    manualRows: (state.manualRows ?? []).map(inputOnly),
    fileRows: (state.fileRows ?? []).map(inputOnly),
    evidenceDocuments: (state.evidenceDocuments ?? []).map((document) => ({
      key: document.key,
      name: document.name,
      rows: (document.rows ?? []).map(inputOnly),
    })),
    profile: state.profile,
  });
}

// 변경 전후 거래를 비교하여 수정 또는 삭제된 거래만 처리한다.
// 수정 요청에는 카테고리만 담고, 전체 거래를 재저장하지 않는다.
export async function saveTaxReview(proposed, { originalState, onOperationSuccess } = {}) {
  if (!originalState || !proposed) {
    throw new Error('변경 전후 거래 목록이 필요합니다.');
  }

  function collect(state) {
    const result = new Map();

    function add(rows, source, evidence = false) {
      for (const row of rows ?? []) {
        const key = `${source}:${row.id}`;

        if (result.has(key)) {
          throw new Error('화면 거래 번호가 중복됩니다.');
        }

        const category = evidence ? (row.category ?? 'UNCLASSIFIED') : (row.cat ?? 'UNCLASSIFIED');

        result.set(key, {
          key,
          source,
          rowId: row.id,
          row,
          category: String(category).trim(),
        });
      }
    }

    add(state.manualRows, 'manual');
    add(state.fileRows, 'file');

    for (const document of state.evidenceDocuments ?? []) {
      add(document.rows, 'evidence', true);
    }

    return result;
  }

  const before = collect(originalState);
  const after = collect(proposed);

  const operations = [];
  const serverTargets = new Set();

  // 현재 편집창은 카테고리 수정과 거래 삭제만 지원한다.
  const fixedFields = [
    'kind',
    'date',
    'month',
    'year',
    'documentYear',
    'memo',
    'amt',
    'actualAmount',
    'preTaxIncome',
    'deductedTaxRate',
    'deductionRate',
    'taxDeducted',
    'transactionDate',
    'transactionMonth',
    'description',
    'amount',
  ];

  for (const key of after.keys()) {
    if (!before.has(key)) {
      throw new Error('새 거래는 거래 추가 기능을 이용해주세요.');
    }
  }

  // 모든 변경 대상을 검사한 뒤 서버 요청을 시작한다.
  for (const [key, previous] of before) {
    const next = after.get(key);
    const removing = !next;

    if (!removing) {
      if (next.row.transactionId !== previous.row.transactionId || next.row.taxInputId !== previous.row.taxInputId) {
        throw new Error('기존 분석번호와 거래번호를 변경할 수 없습니다.');
      }

      const unsupportedChange = fixedFields.some(
        (field) => JSON.stringify(previous.row[field]) !== JSON.stringify(next.row[field])
      );

      if (unsupportedChange) {
        throw new Error('현재 편집창에서는 카테고리 수정과 거래 삭제만 지원합니다.');
      }

      // 변경하지 않은 거래는 요청 대상에서 제외한다.
      if (next.category === previous.category) {
        continue;
      }

      validateCategory(next.category);
    }

    const transactionId = previous.row.transactionId ?? null;

    const taxInputId = previous.row.taxInputId ?? null;

    const persisted = transactionId !== null;

    // 미저장 거래는 기존 DB 행을 수정할 수 없다.
    if (!persisted && !removing) {
      throw new Error('아직 DB에 저장되지 않은 거래입니다. ' + '거래 저장 후 다시 수정해주세요.');
    }

    if (persisted) {
      validateId(transactionId, '기존 거래번호');

      validateId(taxInputId, '기존 분석번호');

      const target = `${taxInputId}:${transactionId}`;

      if (serverTargets.has(target)) {
        throw new Error('동일한 저장 거래가 중복되어 있습니다.');
      }

      serverTargets.add(target);
    }

    operations.push({
      key,
      source: previous.source,
      rowId: previous.rowId,
      type: removing ? 'delete' : 'update',
      category: removing ? null : next.category,
      taxInputId,
      transactionId,
      persisted,
    });
  }

  const completedOperations = [];

  try {
    for (const operation of operations) {
      if (operation.persisted) {
        if (operation.type === 'delete') {
          try {
            await deleteTransaction(operation.taxInputId, operation.transactionId);
          } catch (error) {
            const alreadyDeleted = error.status === 404 && error.code === 'TRANSACTION_NOT_FOUND';

            if (!alreadyDeleted) {
              throw error;
            }
          }
        } else {
          await updateTransaction(operation.taxInputId, operation.transactionId, operation.category);
        }
      }

      // 저장된 거래는 서버 성공 이후 화면에 반영한다.
      // 미저장 거래 삭제는 화면에서만 처리한다.
      completedOperations.push(operation);

      await onOperationSuccess?.(operation);
    }
  } catch (error) {
    // 일부 성공 후 실패한 경우 성공한 작업을 호출부에 전달한다.
    error.completedOperations = completedOperations;

    throw error;
  }

  return {
    operations: completedOperations,
    updatedCount: completedOperations.filter((item) => item.persisted && item.type === 'update').length,
    deletedCount: completedOperations.filter((item) => item.persisted && item.type === 'delete').length,
    localCount: completedOperations.filter((item) => !item.persisted).length,
  };
}

// 신규 정상 거래를 저장하고 DB 거래번호를 화면에 연결한다.
// 기존 거래의 수정 반영에서는 호출하지 않는다.
export async function saveInitialTaxRows(state, { onInputCreated, onGroupSaved } = {}) {
  if (typeof onInputCreated !== 'function' || typeof onGroupSaved !== 'function') {
    throw new Error('저장 결과를 반영할 함수가 필요합니다.');
  }

  const groups = new Map();

  function collect(rows, source) {
    for (const row of rows ?? []) {
      // 이미 저장된 거래와 검토·제외 대상은 저장하지 않는다.
      if (row.transactionId != null || row.needsReview === true || row.excluded === true || row.selected === false) {
        continue;
      }

      const date = validateTransactionDate(row.date);

      const year = validateYear(date.slice(0, 4));

      const kind = String(row.kind ?? '')
        .trim()
        .toUpperCase();

      const transactionType = ['IN', 'INCOME', '수입'].includes(kind)
        ? 'INCOME'
        : ['OUT', 'EXPENSE', '지출'].includes(kind)
          ? 'EXPENSE'
          : null;

      if (!transactionType) {
        throw new Error(`${row.memo || '거래'}: 수입·지출 구분을 확인해주세요.`);
      }

      const amount = optionalInteger(row.actualAmount ?? row.amt, '실제 입출금액');

      if (amount === null) {
        throw new Error('실제 입출금액이 필요합니다.');
      }

      const description = String(row.memo ?? '').trim();

      if (!description) {
        throw new Error('거래 설명을 입력해주세요.');
      }

      const payload = {
        transactionType,
        transactionDate: date,
        description,
        category: validateCategory(String(row.cat || 'UNCLASSIFIED').trim()),
        amount,
        actualAmount: amount,
        preTaxIncome: optionalNumber(row.preTaxIncome, '세전 금액'),
        deductedTaxRate: optionalNumber(row.deductedTaxRate, '차감 세율'),
        amountBasis: row.amountBasis ?? 'UNSPECIFIED',
        valueKind: row.valueKind ?? 'DETAIL',
        needsReview: false,
        excluded: false,
      };

      if (!groups.has(year)) {
        groups.set(year, []);
      }

      groups.get(year).push({
        source,
        rowId: row.id,
        payload,
      });
    }
  }

  collect(state.manualRows, 'manual');

  collect(state.fileRows, 'file');

  if (groups.size === 0) {
    return { savedCount: 0 };
  }

  const profile = normalizeTaxInput({
    ...state.profile,
    taxYear: groups.keys().next().value,
  });

  let savedCount = 0;

  for (const [year, entries] of groups) {
    // 같은 화면에서 재시도할 때 기존에 생성한 분석번호를 사용한다.
    let taxInputId = state.taxPendingInputIdsByYear?.[year] ?? null;

    if (taxInputId == null) {
      const response = await createTaxInput({
        ...profile,
        taxYear: year,
        saveStatus: 'TEMP',
      });

      taxInputId = response?.data?.taxInputId;

      validateId(taxInputId, '생성된 분석번호');

      await onInputCreated({
        taxYear: year,
        taxInputId,
      });
    } else {
      validateId(taxInputId, '저장 분석번호');
    }

    const response = await saveTransactions(
      taxInputId,
      entries.map(({ payload }) => payload)
    );

    const savedRows = response?.data;

    if (!Array.isArray(savedRows) || savedRows.length !== entries.length) {
      throw new Error('저장 결과의 거래 건수가 다릅니다.');
    }

    const buckets = new Map();
    const seenIds = new Set();

    for (const row of savedRows) {
      validateId(row.transactionId, '저장된 거래번호');

      if (row.taxInputId !== taxInputId || seenIds.has(row.transactionId)) {
        throw new Error('저장 결과의 식별자가 올바르지 않습니다.');
      }

      seenIds.add(row.transactionId);

      const key = transactionKey(row);

      if (!buckets.has(key)) {
        buckets.set(key, []);
      }

      buckets.get(key).push(row);
    }

    // 저장 응답의 DB 거래번호를 원래 화면 행에 연결한다.
    const bindings = entries.map((entry) => {
      const saved = buckets.get(transactionKey(entry.payload))?.shift();

      if (!saved) {
        throw new Error('저장 결과를 화면 거래와 연결하지 못했습니다.');
      }

      return {
        source: entry.source,
        rowId: entry.rowId,
        saved,
      };
    });

    await onGroupSaved({
      taxYear: year,
      taxInputId,
      rows: savedRows,
      bindings,
    });

    savedCount += savedRows.length;
  }

  return { savedCount };
}
