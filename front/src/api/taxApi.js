import { client } from './client';
import { parseDeductedTaxRate, parseWon, taxProfileNumbers } from '../utils/transactionAmount';

// 마이페이지는 본인의 저장 결과 번호·귀속연도·저장 시각만 받는다.
export async function getSavedTaxResults({ signal } = {}) {
  return request({ url: '/tax/inputs/saved-results', method: 'GET', signal });
}

// 시나리오는 브라우저 저장소 대신 기존 SIMULATION 테이블에서 관리한다.
export async function getSimulationScenarios(taxInputId) {
  return request({ url: '/tax/simulations', method: 'GET', params: { taxInputId } });
}

export async function saveSimulationScenario(scenario) {
  return request({ url: '/tax/simulations', method: 'POST', data: scenario });
}

export async function deleteSimulationScenario(simulationId) {
  return request({ url: `/tax/simulations/${simulationId}`, method: 'DELETE' });
}

// 결과 데이터와 추가 가정만 보내 기존 백엔드 계산을 재사용한다.
export async function simulateTax(input, sim, { signal } = {}) {
  return request({ url: '/tax/inputs/simulation', method: 'POST', data: { input, ...sim }, signal });
}

// 결과 화면의 데이터 전체를 JSON으로 보관한다. 같은 분석번호는 덮어쓴다.
export async function saveTaxResult(taxInputId, result) {
  return request({ url: `${inputPath(taxInputId)}/result`, method: 'PUT', data: result });
}

// 저장 당시 결과를 조회하며 서버에서 다시 계산하지 않는다.
export async function getSavedTaxResult(taxInputId, { signal } = {}) {
  return request({ url: `${inputPath(taxInputId)}/result`, method: 'GET', signal });
}

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

function normalizeTaxInput(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    throw new Error('분석 기본정보가 필요합니다.');
  }

  // 저장 가능한 기본정보만 전달한다.
  return {
    taxYear: validateYear(input.taxYear),
    businessRegistered: input.businessRegistered ?? null,
    ...taxProfileNumbers(input),
    saveStatus: input.saveStatus ?? 'TEMP',
  };
}

// 산출 근거와 월별 금액을 결과 페이지에서 한 번에 조회한다.
export async function getTaxCalculationResult(taxInputId, { signal } = {}) {
  return request({ url: `${inputPath(taxInputId)}/calculation-result`, method: 'GET', signal });
}

function inputPath(taxInputId) {
  return `/tax/inputs/${taxInputId}`;
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

export async function getTransactions(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/transactions/list`,
    method: 'POST',
  });
}

export async function getTaxResult(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/results/detail`,
    method: 'POST',
  });
}

// input만 보내면 새 분석을 생성한다.
// taxInputId와 transactions를 보내면 기존 분석에 거래를 저장한다.
// input과 transactions를 보내면 새 분석과 거래를 함께 저장한다.
export async function saveTaxData({ input, taxInputId, transactions, expectedTransactionCount } = {}) {
  const data = {};

  if (taxInputId == null) {
    data.input = normalizeTaxInput(input);
  } else {
    data.taxInputId = taxInputId;

    if (input != null) {
      throw new Error('기존 분석의 기본정보는 수정 API를 이용해주세요.');
    }

    if (transactions == null) {
      throw new Error('저장할 거래 목록이 필요합니다.');
    }
  }

  if (transactions != null) {
    if (!Array.isArray(transactions)) {
      throw new Error('저장할 거래 목록은 배열이어야 합니다.');
    }

    data.transactions = transactions;

    if (expectedTransactionCount !== undefined) {
      data.expectedTransactionCount = expectedTransactionCount;
    }
  }

  const result = await request({
    url: '/tax/save',
    method: 'POST',
    data,
  });

  return result;
}

// 기존 화면에서 사용하는 생성 함수 이름과 응답 형식을 유지한다.
export async function createTaxInput(input) {
  const result = await saveTaxData({ input });

  return {
    ...result,
    data: result.data.input,
  };
}

// 기존 화면에서 사용하는 거래 저장 함수 이름과 응답 형식을 유지한다.
export async function saveTransactions(taxInputId, rows, expectedTransactionCount) {
  if (!Array.isArray(rows)) {
    throw new Error('저장할 거래 목록이 필요합니다.');
  }

  const result = await saveTaxData({
    taxInputId,
    transactions: rows,
    expectedTransactionCount,
  });

  return {
    ...result,
    data: result.data.transactions,
  };
}

// 기본정보와 카테고리를 하나의 수정 API로 전달한다.
export async function updateTaxData(taxInputId, { input, changes } = {}) {
  const url = `${inputPath(taxInputId)}/update`;
  const hasInput = input != null;
  const hasCategoryChange = Array.isArray(changes) && changes.length > 0;

  if (!hasInput && !hasCategoryChange) {
    throw new Error('수정할 기본정보 또는 카테고리를 전달해주세요.');
  }

  const data = {};

  if (hasInput) {
    data.input = normalizeTaxInput(input);
  }

  if (hasCategoryChange) {
    data.changes = changes.map(({ transactionId, category }) => ({
      transactionId,
      category: validateCategory(category),
    }));
  }

  return request({
    url,
    method: 'POST',
    data,
  });
}

export async function updateTaxInput(taxInputId, input) {
  return updateTaxData(taxInputId, { input });
}

// 분석 전체 삭제와 거래 한 건 삭제를 하나의 API로 전달한다.
export async function deleteTaxData(taxInputId, { deleteAll = false, transactionId = null } = {}) {
  const url = `${inputPath(taxInputId)}/delete`;

  if (typeof deleteAll !== 'boolean') {
    throw new Error('전체 삭제 여부를 확인해주세요.');
  }

  if (deleteAll && transactionId !== null) {
    throw new Error('분석 전체 삭제에는 거래번호를 전달할 수 없습니다.');
  }

  const data = {};

  if (!deleteAll) {
    data.transactionId = transactionId;
  }

  const result = await request({
    url,
    method: 'POST',
    params: { deleteAll },
    data,
  });

  return result;
}

export async function deleteTaxInput(taxInputId) {
  return deleteTaxData(taxInputId, { deleteAll: true });
}

export async function deleteTransaction(taxInputId, transactionId) {
  return deleteTaxData(taxInputId, { transactionId });
}

// 분석한 거래의 최초 저장은 별도 저장 API에서 수행한다.
export async function parsePdf(file, { signal, taxInputId = null } = {}) {
  if (!file) {
    throw new Error('PDF 파일을 선택해주세요.');
  }

  const formData = new FormData();
  formData.append('file', file);

  if (taxInputId !== null) {
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

// 저장 식별자 연결로 입력 변경이 발생했다고 판단하지 않도록 식별자를 제외한다.
export function getTaxReviewSignature(state) {
  function inputOnly(row) {
    return Object.fromEntries(
      Object.entries(row).filter(
        ([key]) => key !== 'transactionId' && key !== 'taxInputId' && key !== 'persisted'
      )
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

      if (next.category === previous.category) {
        continue;
      }

      validateCategory(next.category);
    }

    const transactionId = previous.row.transactionId ?? null;
    const taxInputId = previous.row.taxInputId ?? null;
    const persisted = transactionId !== null;

    if (!persisted && !removing) {
      throw new Error('아직 DB에 저장되지 않은 거래입니다. 거래 저장 후 다시 수정해주세요.');
    }

    if (persisted) {
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

  // 저장된 삭제 대상은 분석번호별로 묶어 한 번에 전송한다.
  const deletionsByInput = new Map();

  for (const operation of operations) {
    if (operation.persisted && operation.type === 'delete') {
      const group = deletionsByInput.get(operation.taxInputId) ?? [];
      group.push(operation);
      deletionsByInput.set(operation.taxInputId, group);
    }
  }

  const updatesByInput = new Map();

  for (const operation of operations) {
    if (operation.persisted && operation.type === 'update') {
      const group = updatesByInput.get(operation.taxInputId) ?? [];
      group.push(operation);
      updatesByInput.set(operation.taxInputId, group);
    }
  }

  const completedOperations = [];

  try {
    for (const operation of operations) {
      if (operation.persisted) {
        if (operation.type === 'delete') {
          const group = deletionsByInput.get(operation.taxInputId);
          if (!group) continue;

          await request({
            url: `${inputPath(operation.taxInputId)}/delete`,
            method: 'POST',
            data: {
              transactionIds: group.map((item) => item.transactionId),
            },
          });

          deletionsByInput.delete(operation.taxInputId);
          completedOperations.push(...group);

          for (const item of group) {
            await onOperationSuccess?.(item);
          }

          continue;
        } else {
          const group = updatesByInput.get(operation.taxInputId);
          if (!group) continue;

          await updateTaxData(operation.taxInputId, { changes: group });
          updatesByInput.delete(operation.taxInputId);
          completedOperations.push(...group);

          for (const item of group) {
            await onOperationSuccess?.(item);
          }

          continue;
        }
      }

      // 미저장 거래 삭제는 화면에만 반영한다.
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

// 정상 응답과 조회로 확인한 응답에 같은 거래 연결 규칙을 적용한다.
function bindSavedTaxGroup(taxYear, taxInputId, entries, savedRows, previousRows = []) {
  if (!Number.isSafeInteger(taxInputId) || taxInputId <= 0) {
    throw new Error('저장 결과의 분석번호가 올바르지 않습니다.');
  }

  if (!Array.isArray(savedRows) || savedRows.length !== entries.length) {
    throw new Error('저장 결과의 거래 건수가 다릅니다.');
  }

  const buckets = new Map();
  const seenIds = new Set();

  for (const row of savedRows) {
    if (
      row.taxInputId !== taxInputId ||
      !Number.isSafeInteger(row.transactionId) ||
      row.transactionId <= 0 ||
      seenIds.has(row.transactionId)
    ) {
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

  return {
    taxYear,
    taxInputId,
    rows: [...previousRows, ...savedRows],
    bindings,
  };
}

// 신규 정상 거래를 귀속연도별로 저장하고 DB 거래번호를 화면에 연결한다.
export async function saveInitialTaxRows(
  state,
  { onInputCreated, onGroupSaved, saveInputWithTransactions = false } = {}
) {

  if (typeof onGroupSaved !== 'function' || (!saveInputWithTransactions && typeof onInputCreated !== 'function')) {
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
      const kind = String(row.kind ?? '').trim().toUpperCase();

      const transactionType = ['IN', 'INCOME', '수입'].includes(kind)
        ? 'INCOME'
        : ['OUT', 'EXPENSE', '지출'].includes(kind)
          ? 'EXPENSE'
          : null;

      if (!transactionType) {
        throw new Error(`${row.memo || '거래'}: 수입·지출 구분을 확인해주세요.`);
      }

      const amount = parseWon(row.actualAmount ?? row.amt);

      if (amount === null) {
        throw new Error('실제 입출금액은 0~9,999,999,999,999원 사이의 정수로 입력해주세요.');
      }

      const description = String(row.memo ?? '').trim();

      if (!description) {
        throw new Error('거래 설명을 입력해주세요.');
      }

      // 이전에 작성한 직접 입력 거래도 새 세율 필드로 전송한다.
      const rateInput = row.deductedTaxRate ?? row.deductionRate;
      const deductedTaxRate = parseDeductedTaxRate(rateInput);
      if (rateInput != null && deductedTaxRate === null) {
        throw new Error('차감 세율은 0~99.9999% 사이에서 소수점 이하 4자리까지 입력해주세요.');
      }

      const payload = {
        transactionType,
        transactionDate: date,
        description,
        category: validateCategory(String(row.cat || 'UNCLASSIFIED').trim()),
        amount,
        actualAmount: amount,
        preTaxIncome: optionalNumber(row.preTaxIncome, '세전 금액'),
        deductedTaxRate,
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
    // 같은 화면에서 같은 연도에 이미 만든 분석번호를 재사용한다.
    let taxInputId = state.taxInputIdsByYear?.[year] ?? state.taxPendingInputIdsByYear?.[year] ?? null;

    const previousRows =
      (state.taxSavedSnapshots ?? []).find((snapshot) => snapshot.taxInputId === taxInputId)?.rows ?? [];

    const transactions = entries.map(({ payload }) => payload);
    let savedRows;

    if (saveInputWithTransactions) {
      const response = await saveTaxData({
        input: taxInputId == null ? { ...profile, taxYear: year, saveStatus: 'TEMP' } : undefined,
        taxInputId,
        transactions,
        expectedTransactionCount: previousRows.length,
      });

      taxInputId = response?.data?.input?.taxInputId;
      savedRows = response?.data?.transactions;
    } else {
      if (taxInputId == null) {
        const response = await createTaxInput({
          ...profile,
          taxYear: year,
          saveStatus: 'TEMP',
        });

        taxInputId = response?.data?.taxInputId;
        await onInputCreated({ taxYear: year, taxInputId });
      }

      const response = await saveTransactions(taxInputId, transactions, previousRows.length);
      savedRows = response?.data;
    }

    await onGroupSaved(bindSavedTaxGroup(year, taxInputId, entries, savedRows, previousRows));
    savedCount += savedRows.length;
  }

  return { savedCount };
}
