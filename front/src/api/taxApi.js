import { client } from './client';

async function request(config) {
  try {
    const response = await client.request(config);
    const body = response.data;

    if (body?.success === false) {
      const error = new Error(
          body.error?.message ?? '요청 처리에 실패했습니다.',
      );

      error.code = body.error?.code;
      error.status = response.status;
      error.fields = body.error?.fields;

      throw error;
    }

    return body;
  } catch (cause) {
    if (
        cause.code === 'ERR_CANCELED'
        || cause.name === 'CanceledError'
    ) {
      throw cause;
    }

    const detail = cause.response?.data?.error;

    const error = new Error(
        detail?.message ?? cause.message ?? '서버 요청에 실패했습니다.',
    );

    error.code = detail?.code ?? cause.code;
    error.status = cause.response?.status ?? cause.status;
    error.fields = detail?.fields ?? cause.fields;

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

function inputPath(taxInputId) {
  validateId(taxInputId, '분석번호');
  return `/tax/inputs/${taxInputId}`;
}

function transactionPath(taxInputId, transactionId) {
  validateId(transactionId, '거래번호');
  return `${inputPath(taxInputId)}/transactions/${transactionId}`;
}

export async function createTaxInput(input) {
  validateYear(input?.taxYear);

  return request({
    url: '/tax/inputs/create',
    method: 'POST',
    data: input,
  });
}

export async function updateTaxInput(taxInputId, input) {
  validateYear(input?.taxYear);

  return request({
    url: `${inputPath(taxInputId)}/update`,
    method: 'POST',
    data: input,
  });
}

export async function getTaxInput(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/detail`,
    method: 'POST',
  });
}

export async function getSavedTaxInputs(taxYear = null) {
  if (taxYear !== null) validateYear(taxYear);

  return request({
    url: '/tax/inputs/list',
    method: 'POST',
    params: taxYear === null ? {} : { taxYear },
  });
}

export async function deleteTaxInput(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/delete`,
    method: 'POST',
  });
}

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

export async function updateTransaction(
    taxInputId,
    transactionId,
    category,
) {
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

export async function parsePdf(
    file,
    { signal, taxInputId = null } = {},
) {
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

  if (
      !result
      || typeof result !== 'object'
      || Array.isArray(result)
      || !Array.isArray(result.transactions)
  ) {
    throw new Error('PDF 분석 응답 형식이 올바르지 않습니다.');
  }

  if (result.persisted !== true) {
    throw new Error('PDF 분석은 완료됐지만 DB 저장이 확인되지 않았습니다.');
  }

  if (
      !Number.isSafeInteger(result.savedCount)
      || result.savedCount !== result.transactions.length
      || result.savedCount <= 0
  ) {
    throw new Error('PDF 저장 건수와 응답 건수가 일치하지 않습니다.');
  }

  const inputIds = result.taxInputIdsByYear;

  if (!inputIds || typeof inputIds !== 'object' || Array.isArray(inputIds)) {
    throw new Error('저장된 연도별 분석번호가 없습니다.');
  }

  const groupedRows = new Map();
  const seenIds = new Set();

  for (const [year, inputId] of Object.entries(inputIds)) {
    validateYear(year);
    validateId(inputId, '저장된 분석번호');
    groupedRows.set(inputId, []);
  }

  for (const row of result.transactions) {
    validateId(row?.transactionId, '저장된 거래번호');
    validateId(row?.taxInputId, '저장된 분석번호');

    if (seenIds.has(row.transactionId)) {
      throw new Error('응답에 중복 거래번호가 있습니다.');
    }

    seenIds.add(row.transactionId);

    const rows = groupedRows.get(row.taxInputId);

    if (!rows) {
      throw new Error('거래의 분석번호가 저장 결과와 일치하지 않습니다.');
    }

    rows.push(row);
  }

  /*
   * 서비스가 DB에서 다시 조회한 거래를 연도별 화면 목록으로 묶는다.
   * 이 단계에서 추가 저장 요청을 보내지 않는다.
   */
  const snapshots = Object.entries(inputIds).map(([year, taxInputId]) => ({
    taxYear: Number(year),
    taxInputId,
    rows: groupedRows.get(taxInputId),
  }));

  return {
    ...result,
    originalFileName: result.fileName ?? file.name,
    fileId: result.fileId ?? null,
    persisted: true,
    snapshots,
  };
}

function validateCategory(value) {
  if (
      typeof value !== 'string'
      || !/^[A-Z][A-Z0-9_]{0,49}$/.test(value)
  ) {
    throw new Error('카테고리 코드를 확인해주세요.');
  }

  return value;
}

function snapshotCategory(value) {
  return validateCategory(
      String(value || 'UNCLASSIFIED').trim(),
  );
}

function snapshotAmount(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const cleaned = String(value)
      .replace(/,/g, '')
      .replace(/원/g, '')
      .replace(/\s/g, '');

  if (!cleaned) return null;

  const amount = Number(cleaned);

  return Number.isSafeInteger(amount) && amount >= 0
      ? amount
      : null;
}

function snapshotRate(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const rate = Number(value);

  if (!Number.isFinite(rate) || rate < 0 || rate > 100) {
    throw new Error('차감 세율을 확인해주세요.');
  }

  return rate;
}

function dateText(value) {
  if (Array.isArray(value) && value.length === 3) {
    return value.map((part, index) => (
        index === 0
            ? String(part).padStart(4, '0')
            : String(part).padStart(2, '0')
    )).join('-');
  }

  return value ? String(value).trim() : null;
}

function snapshotPeriod(dateValue, monthValue, yearValue) {
  const date = dateText(dateValue);
  const month = monthValue ? String(monthValue).trim() : null;

  if (date) {
    const parsed = new Date(`${date}T00:00:00Z`);

    if (
        !/^\d{4}-\d{2}-\d{2}$/.test(date)
        || Number.isNaN(parsed.getTime())
        || parsed.toISOString().slice(0, 10) !== date
    ) {
      throw new Error('거래 날짜를 확인해주세요.');
    }
  }

  if (month && !/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
    throw new Error('거래 월은 YYYY-MM 형식이어야 합니다.');
  }

  if (date && month && date.slice(0, 7) !== month) {
    throw new Error('거래 날짜와 거래 월이 다릅니다.');
  }

  const year = validateYear(
      date ? date.slice(0, 4) : month ? month.slice(0, 4) : yearValue,
  );

  if (
      yearValue !== null
      && yearValue !== undefined
      && yearValue !== ''
      && validateYear(yearValue) !== year
  ) {
    throw new Error('문서 귀속 연도와 거래 연도가 다릅니다.');
  }

  return { date, month, year };
}

export function getTaxReviewSignature(state) {
  function inputOnly(row) {
    const {
      transactionId,
      taxInputId,
      persisted,
      ...input
    } = row;

    return input;
  }

  // DB 식별자와 저장 표시는 사용자의 입력 변경 비교에서 제외한다.
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

export async function saveTaxReview(
    state,
    { onPendingChange } = {},
) {
  const groups = new Map();
  const profile = state.profile ?? {};

  function append(sourceKey, row, evidence = false) {
    const period = snapshotPeriod(
        evidence ? row.transactionDate : row.date,
        evidence ? row.transactionMonth : row.month,
        evidence
            ? row.documentYear
            : row.documentYear
            ?? row.year
            ?? (
                row.date || row.month
                    ? null
                    : profile.taxYear
            ),
    );

    const description = String(
        evidence ? row.description ?? '' : row.memo ?? '',
    ).trim();

    if (!description || Array.from(description).length > 255) {
      throw new Error('거래 설명은 1자 이상 255자 이하여야 합니다.');
    }

    const amount = snapshotAmount(
        evidence
            ? row.amount
            : row.actualAmount
            ?? (row.amountBasis === 'GROSS' ? null : row.amt),
    );

    if (amount === null) {
      throw new Error(`${description}: 금액을 확인해주세요.`);
    }

    if (row.needsReview === true) {
      throw new Error(
          `${description}: ${row.failureReason ?? '확인이 필요합니다.'}`,
      );
    }

    const kind = String(row.kind ?? '').trim().toUpperCase();

    const transactionType = evidence
        ? 'EXPENSE'
        : ['IN', 'INCOME', '수입'].includes(kind)
            ? 'INCOME'
            : ['OUT', 'EXPENSE', '지출'].includes(kind)
                ? 'EXPENSE'
                : 'UNKNOWN';

    const amountBasis = evidence
        ? 'DEDUCTION_ELIGIBLE'
        : row.amountBasis === 'GROSS' && row.actualAmount != null
            ? 'ACTUAL'
            : row.amountBasis ?? 'UNSPECIFIED';

    const entries = groups.get(period.year) ?? [];

    entries.push({
      sourceKey,
      payload: {
        // 수정본은 새 분석번호로 저장하므로 이전 거래번호를 보내지 않는다.
        transactionType,
        transactionDate: period.date,
        transactionMonth: period.month,
        documentYear: period.year,
        description,
        category: snapshotCategory(evidence ? row.category : row.cat),
        amount,
        preTaxIncome: transactionType === 'INCOME'
            ? snapshotAmount(row.preTaxIncome)
            : null,
        deductedTaxRate: evidence
            ? null
            : snapshotRate(row.deductedTaxRate),
        valueKind: row.valueKind ?? (
            period.date
                ? 'DETAIL'
                : period.month
                    ? 'MONTHLY_AGGREGATE'
                    : 'ANNUAL_TOTAL'
        ),
        amountBasis,
      },
    });

    groups.set(period.year, entries);
  }

  // 화면 필터나 카테고리 분류 여부와 관계없이 거래 전체를 저장한다.
  for (const row of state.manualRows ?? []) {
    append(`manual:${row.id}`, row);
  }

  for (const row of state.fileRows ?? []) {
    if (row.excluded !== true) {
      append(`file:${row.id}`, row);
    }
  }

  for (const document of state.evidenceDocuments ?? []) {
    for (const row of document.rows ?? []) {
      append(`evidence:${row.id}`, row, true);
    }
  }

  // 한 연도의 거래를 전부 삭제한 경우에도 빈 수정본을 남긴다.
  for (const key of Object.keys(state.taxInputIdsByYear ?? {})) {
    const year = validateYear(key);

    if (!groups.has(year)) groups.set(year, []);
  }

  if (!groups.size) {
    throw new Error('저장할 내역이 없습니다.');
  }

  const dependentCount = snapshotAmount(profile.dependentCount ?? 0);
  const donationAmount = snapshotAmount(profile.donationAmount ?? 0);

  if (dependentCount === null || donationAmount === null) {
    throw new Error('가족 수와 기부금 입력값을 확인해주세요.');
  }

  const hasAge = profile.age !== ''
      && profile.age !== null
      && profile.age !== undefined;

  const age = hasAge ? snapshotAmount(profile.age) : null;

  if (hasAge && age === null) {
    throw new Error('나이 입력값을 확인해주세요.');
  }

  const signature = getTaxReviewSignature(state);

  // 같은 입력의 실패 재시도에만 미완료 분석번호를 재사용한다.
  const pending = state.taxPendingSnapshot?.signature === signature
      ? {
        signature,
        inputIds: { ...state.taxPendingSnapshot.inputIds },
      }
      : { signature, inputIds: {} };

  function reportPending() {
    onPendingChange?.({
      signature,
      inputIds: { ...pending.inputIds },
    });
  }

  reportPending();

  const savedRows = new Map();
  const snapshots = [];

  for (const [year, entries] of groups) {
    let taxInputId = pending.inputIds[year];

    if (!taxInputId) {
      const created = await createTaxInput({
        taxYear: year,
        businessRegistered: profile.businessRegistered,
        age,
        dependentCount,
        donationAmount,
        startupReliefApplied: profile.startupReliefApplied,
        businessRegion: profile.businessRegion || null,
        saveStatus: 'TEMP',
      });

      taxInputId = created.data?.taxInputId;

      validateId(taxInputId, '새 분석번호');

      pending.inputIds[year] = taxInputId;
      reportPending();
    } else {
      validateId(taxInputId, '재시도 분석번호');
    }

    // 업로드 원본을 덮어쓰지 않고 새 수정본에 전체 목록을 저장한다.
    const saved = await saveTransactions(
        taxInputId,
        entries.map((entry) => entry.payload),
    );

    if (
        !Array.isArray(saved.data)
        || saved.data.length !== entries.length
    ) {
      throw new Error(`${year}년 저장 응답이 올바르지 않습니다.`);
    }

    const queried = await getTransactions(taxInputId);

    if (
        !Array.isArray(queried.data)
        || queried.data.length !== entries.length
    ) {
      throw new Error(`${year}년 저장 내역을 조회하지 못했습니다.`);
    }

    const queriedIds = new Set(
        queried.data.map((row) => row.transactionId),
    );

    entries.forEach((entry, index) => {
      const transactionId = saved.data[index].transactionId;

      validateId(transactionId, '저장된 거래번호');

      if (!queriedIds.has(transactionId)) {
        throw new Error('저장한 거래를 조회 결과에서 찾지 못했습니다.');
      }

      savedRows.set(entry.sourceKey, {
        transactionId,
        taxInputId,
        persisted: true,
      });
    });

    snapshots.push({
      taxYear: year,
      taxInputId,
      rows: queried.data,
    });
  }

  return {
    inputIds: { ...pending.inputIds },
    savedRows,
    snapshots,
  };
}