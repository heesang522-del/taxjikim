'use no memo';

import { create } from 'zustand';

export const EMPTY_SIM = {
  extraExp: 0,
  extraInc: 0,
  yellow: 0,
};

function makeId(prefix = 'row') {
  return `${prefix}-${crypto.randomUUID()}`;
}

export function parseTaxAmount(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const cleaned = String(value).replace(/,/g, '').replace(/원/g, '').replace(/\s/g, '');

  if (!cleaned) return null;

  const amount = Number(cleaned);

  return Number.isSafeInteger(amount) && amount >= 0 ? amount : null;
}

export function normalizeTaxKind(value) {
  const type = String(value ?? '')
    .trim()
    .toUpperCase();

  if (['INCOME', 'IN', '수입'].includes(type)) {
    return 'in';
  }

  if (['EXPENSE', 'OUT', '지출'].includes(type)) {
    return 'out';
  }

  return 'unknown';
}

export function isPdfRowReady(row) {
  return Boolean(row) && parseTaxAmount(row.amt) !== null;
}

export function getPdfItemDefaults(item) {
  return {
    kind: normalizeTaxKind(item.transactionType),
    category: item.category ?? '',
  };
}

export function getPdfItemPeriod(item, year) {
  if (item.transactionDate) {
    return {
      date: item.transactionDate,
      month: null,
      year: null,
    };
  }

  if (item.transactionMonth) {
    return {
      date: '',
      month: item.transactionMonth,
      year: null,
    };
  }

  const documentYear = item.documentYear ?? year ?? null;

  return {
    date: '',
    month: null,
    year: documentYear == null ? null : String(documentYear),
  };
}

export const getAllTaxRows = (state) => (state.inputMethod === 'file' ? state.fileRows : state.manualRows);

export const getSelectedTaxRows = (state) =>
  getAllTaxRows(state).filter(
    (row) => row.selected !== false && row.excluded !== true && parseTaxAmount(row.amt) !== null
  );

export const getSelectedTaxTotals = (state) =>
  getSelectedTaxRows(state).reduce(
    (totals, row) => {
      const amount = parseTaxAmount(row.amt);

      if (amount === null) return totals;

      const kind = normalizeTaxKind(row.kind);

      if (kind === 'in') {
        totals.income += amount;
      } else if (kind === 'out') {
        totals.expense += amount;
      } else {
        totals.unknown += amount;
        totals.unknownCount += 1;
      }

      totals.count += 1;
      return totals;
    },
    {
      income: 0,
      expense: 0,
      unknown: 0,
      unknownCount: 0,
      count: 0,
    }
  );

function normalizeFileRow(row, file) {
  const { needsReview: _removedReviewFlag, ...rest } = row;

  const normalized = {
    ...rest,
    id: makeId('pdf'),
    sourceFileKey: file.key,
    sourceFileId: row.sourceFileId ?? file.fileId ?? null,
    kind: normalizeTaxKind(row.kind),
    excluded: row.excluded === true,
    autoImported: true,
  };

  return {
    ...normalized,
    selected: !normalized.excluded && isPdfRowReady(normalized),
  };
}

function getLevel(row) {
  if (row.date) return 'detail';
  if (row.month) return 'month';
  if (row.year) return 'year';
  return 'unknown';
}

function selectPreferredRows(rows, selected, fileKey = null) {
  const keys = new Set(
    rows.filter((row) => fileKey === null || row.sourceFileKey === fileKey).map((row) => row.sourceFileKey)
  );

  let result = rows;

  for (const key of keys) {
    const available = result.filter((row) => row.sourceFileKey === key && !row.excluded && isPdfRowReady(row));

    const nonTotals = available.filter((row) => row.valueKind !== 'VALIDATION_TOTAL');

    const detail = nonTotals.filter((row) => getLevel(row) === 'detail');

    const monthly = nonTotals.filter((row) => getLevel(row) === 'month');

    const yearly = nonTotals.filter((row) => getLevel(row) === 'year');

    const undated = nonTotals.filter((row) => getLevel(row) === 'unknown');

    const preferred = detail.length
      ? [...detail, ...undated]
      : monthly.length
        ? [...monthly, ...undated]
        : yearly.length
          ? [...yearly, ...undated]
          : nonTotals.length
            ? nonTotals
            : available.slice(0, 1);

    const ids = new Set(preferred.map((row) => row.id));

    result = result.map((row) =>
      row.sourceFileKey === key
        ? {
            ...row,
            selected: Boolean(selected) && ids.has(row.id),
          }
        : row
    );
  }

  return result;
}

function changeFileRowSelection(rows, id, selected) {
  const target = rows.find((row) => row.id === id);

  if (!target) return rows;

  const enabled = Boolean(selected) && !target.excluded && isPdfRowReady(target);

  return rows.map((row) => {
    if (row.id === id) {
      return {
        ...row,
        selected: enabled,
      };
    }

    if (!enabled || row.sourceFileKey !== target.sourceFileKey) {
      return row;
    }

    if (target.valueKind === 'VALIDATION_TOTAL' || row.valueKind === 'VALIDATION_TOTAL') {
      return {
        ...row,
        selected: false,
      };
    }

    const targetLevel = getLevel(target);
    const rowLevel = getLevel(row);

    if (targetLevel !== 'unknown' && rowLevel !== 'unknown' && targetLevel !== rowLevel) {
      return {
        ...row,
        selected: false,
      };
    }

    return row;
  });
}

export function getPdfCalculationIssue(state) {
  const rows = getSelectedTaxRows(state);

  for (const file of state.uploadedFiles ?? []) {
    const selected = rows.filter((row) => row.sourceFileKey === file.key);

    if (selected.length > 1 && selected.some((row) => row.valueKind === 'VALIDATION_TOTAL')) {
      return `${file.name}: 전체 합계와 개별 거래가 중복 선택되었습니다.`;
    }

    const levels = new Set(selected.map(getLevel).filter((level) => level !== 'unknown'));

    if (levels.size > 1) {
      return `${file.name}: 상세·월별·연간 거래를 동시에 합산할 수 없습니다.`;
    }
  }

  return '';
}

export const useTaxStore = create((set, get) => ({
  inputMethod: 'manual',

  manualRows: [],
  fileRows: [],
  uploadedFiles: [],

  // 기존 SummaryRail.jsx 호환용
  evidenceDocuments: [],

  pendingRows: [],

  profile: {
    type: null,
    auto33: false,
  },

  sim: { ...EMPTY_SIM },
  calculated: false,

  addRow: (row) =>
    set((state) => ({
      manualRows: [
        ...state.manualRows,
        {
          ...row,
          id: makeId('manual'),
          kind: normalizeTaxKind(row.kind),
          selected: row.selected !== false,
        },
      ],
      calculated: false,
    })),

  setInputMethod: (method) => {
    if (!['manual', 'file'].includes(method)) {
      return;
    }

    set((state) => ({
      inputMethod: method,
      sim: state.inputMethod === method ? state.sim : { ...EMPTY_SIM },
      calculated: false,
    }));
  },

  addFileResult: (rows, file) =>
    set((state) => {
      const normalized = rows.map((row) => normalizeFileRow(row, file));

      const selectedRows = selectPreferredRows(normalized, true, file.key);

      return {
        fileRows: [...state.fileRows.filter((row) => row.sourceFileKey !== file.key), ...selectedRows],
        uploadedFiles: [...state.uploadedFiles.filter((entry) => entry.key !== file.key), file],
        inputMethod: 'file',
        calculated: false,
      };
    }),

  // 기존 컴포넌트 호환용.
  // 사용자 직접 검토 기능은 수행하지 않는다.
  setPdfDocumentReviewed: () => {},

  removeRow: (id) =>
    set((state) => ({
      manualRows: state.manualRows.filter((row) => row.id !== id),
      fileRows: state.fileRows.filter((row) => row.id !== id),
      calculated: false,
    })),

  setRowSelected: (id, selected) =>
    set((state) => ({
      manualRows: state.manualRows.map((row) =>
        row.id === id
          ? {
              ...row,
              selected: Boolean(selected),
            }
          : row
      ),
      fileRows: changeFileRowSelection(state.fileRows, id, selected),
      calculated: false,
    })),

  setAllRowsSelected: (selected) =>
    set((state) => {
      if (state.inputMethod === 'file') {
        return {
          fileRows: selectPreferredRows(state.fileRows, selected),
          calculated: false,
        };
      }

      return {
        manualRows: state.manualRows.map((row) => ({
          ...row,
          selected: Boolean(selected),
        })),
        calculated: false,
      };
    }),

  setFileSelected: (key, selected) =>
    set((state) => ({
      fileRows: selectPreferredRows(state.fileRows, selected, key),
      calculated: false,
    })),

  removeUploadedFile: (key) =>
    set((state) => ({
      uploadedFiles: state.uploadedFiles.filter((file) => file.key !== key),
      fileRows: state.fileRows.filter((row) => row.sourceFileKey !== key),
      calculated: false,
    })),

  addPendingRows: (items) =>
    set((state) => ({
      pendingRows: [...state.pendingRows, ...items],
    })),

  resolvePendingRow: (id, patch) => {
    const row = get().pendingRows.find((item) => item.id === id);

    if (!row) return;

    const merged = {
      ...row,
      ...patch,
    };

    get().addRow({
      kind: merged.kind,
      date: merged.date ?? '',
      m: merged.date ? Number(String(merged.date).slice(5, 7)) : null,
      cat: merged.cat,
      memo: merged.memo,
      amt: merged.amt,
      selected: true,
    });

    set((state) => ({
      pendingRows: state.pendingRows.filter((item) => item.id !== id),
    }));
  },

  discardPendingRow: (id) =>
    set((state) => ({
      pendingRows: state.pendingRows.filter((row) => row.id !== id),
    })),

  updateProfile: (patch) =>
    set((state) => ({
      profile: {
        ...state.profile,
        ...patch,
      },
      calculated: false,
    })),

  patchSim: (patch) =>
    set((state) => ({
      sim: {
        ...state.sim,
        ...patch,
      },
      calculated: false,
    })),

  resetSim: () =>
    set({
      sim: { ...EMPTY_SIM },
      calculated: false,
    }),

  setCalculated: (value) =>
    set({
      calculated: Boolean(value),
    }),
}));

export const useTax = () => {
  const state = useTaxStore();

  return {
    ...state,
    allRows: getAllTaxRows(state),
    rows: getSelectedTaxRows(state),
    selectedTaxTotals: getSelectedTaxTotals(state),
  };
};
