'use no memo';

import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Button, Card } from '../common/index';
import { getTaxReviewSignature, saveInitialTaxRows, saveTaxReview } from '../../api/taxApi';
import { normalizeTaxKind, parseTaxAmount, useTaxStore } from '../../store/useTaxStore';
import { getTransactionCashAmount } from '../../utils/taxInputSummary';
import {
  INCOME_TRANSACTION_CATEGORIES,
  EXPENSE_TRANSACTION_CATEGORIES,
  getTransactionCategoryLabel,
} from '../../data/transactionCategories';

const ALL_CATEGORIES = [
  ...new Map(
    [...INCOME_TRANSACTION_CATEGORIES, ...EXPENSE_TRANSACTION_CATEGORIES].map((item) => [item.value, item])
  ).values(),
];

const CATEGORY_GROUPS = [
  {
    label: '수입',
    options: INCOME_TRANSACTION_CATEGORIES.filter((item) => item.value !== 'OTHER' && item.value !== 'UNCLASSIFIED'),
  },
  {
    label: '지출',
    options: EXPENSE_TRANSACTION_CATEGORIES.filter((item) => item.value !== 'OTHER' && item.value !== 'UNCLASSIFIED'),
  },
  {
    label: '공통',
    options: ALL_CATEGORIES.filter((item) => item.value === 'OTHER' || item.value === 'UNCLASSIFIED'),
  },
];

function categoriesFor(kind) {
  if (kind === 'in') return INCOME_TRANSACTION_CATEGORIES;
  if (kind === 'out') return EXPENSE_TRANSACTION_CATEGORIES;

  return ALL_CATEGORIES;
}

function formatDate(value) {
  if (Array.isArray(value) && value.length === 3) {
    return value.map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
  }

  return String(value || '날짜 미확인');
}

function formatMoney(value) {
  const amount = parseTaxAmount(value);

  return amount === null ? '금액 미확인' : `${amount.toLocaleString('ko-KR')}원`;
}

function kindLabel(kind) {
  if (kind === 'in') return '수입';
  if (kind === 'out') return '지출';

  return '미확인';
}

// 직접 입력과 PDF 거래를 동일한 화면 목록으로 변환한다.
function collectRows(state) {
  const result = [];

  function add(items, source) {
    for (const row of items ?? []) {
      result.push({
        key: `${source}:${row.id}`,
        rowId: row.id,
        source,
        kind: normalizeTaxKind(row.kind),
        date: formatDate(row.date),
        description: row.memo || '설명 미기재',
        category: row.cat || 'UNCLASSIFIED',
        amount: getTransactionCashAmount(row),
        taxInputId: row.taxInputId ?? null,
        transactionId: row.transactionId ?? null,
        persisted: Number.isSafeInteger(row.transactionId) && row.transactionId > 0,
        needsReview: row.needsReview === true || row.excluded === true,
        selected: row.selected !== false,
      });
    }
  }

  add(state.manualRows, 'manual');
  add(state.fileRows, 'file');
  return result;
}

function hasPendingRows(state) {
  return collectRows(state).some((row) => !row.persisted && !row.needsReview && row.selected);
}

// 성공한 작업만 화면 데이터와 저장 스냅샷에 확정 반영한다.
function applyOperation(state, operation) {
  function change(items, source) {
    return (items ?? []).flatMap((row) => {
      if (source !== operation.source || row.id !== operation.rowId) {
        return [row];
      }

      return operation.type === 'delete' ? [] : [{ ...row, cat: operation.category }];
    });
  }

  const taxSavedSnapshots = (state.taxSavedSnapshots ?? []).map((snapshot) => ({
    ...snapshot,
    rows: (snapshot.rows ?? []).flatMap((row) => {
      const matching =
        operation.persisted && row.taxInputId === operation.taxInputId && row.transactionId === operation.transactionId;

      if (!matching) return [row];

      return operation.type === 'delete' ? [] : [{ ...row, category: operation.category }];
    }),
  }));

  return {
    manualRows: change(state.manualRows, 'manual'),
    fileRows: change(state.fileRows, 'file'),
    taxSavedSnapshots,
    taxLastSavedSignature: null,
    calculated: false,
  };
}

export function EntryTable() {
  const state = useTaxStore();

  const dialogRef = useRef(null);
  const workingRef = useRef(false);
  const previousKeysRef = useRef(new Set());

  const [open, setOpen] = useState(false);
  const [drafts, setDrafts] = useState({});
  const [checkedKeys, setCheckedKeys] = useState([]);
  const [onlyUnclassified, setOnlyUnclassified] = useState(false);
  const [bulkCategory, setBulkCategory] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const busy = state.taxSaveBusy === true;
  const rows = collectRows(state);
  const checked = new Set(checkedKeys);

  const visibleRows = onlyUnclassified ? rows.filter((row) => row.category === 'UNCLASSIFIED') : rows;

  const selectedRows = rows.filter((row) => checked.has(row.key));

  // 카테고리 변경과 삭제할 거래의 선택은 별도로 관리한다.
  const changedRows = rows.filter((row) => drafts[row.key] !== undefined && drafts[row.key] !== row.category);

  const pendingRows = rows.filter((row) => !row.persisted && !row.needsReview && row.selected);

  const hasChanges = changedRows.length > 0;
  const canUndo = hasChanges || selectedRows.length > 0 || bulkCategory !== '';

  const selectedBulk = ALL_CATEGORIES.some((item) => item.value === bulkCategory) ? bulkCategory : '';

  const rowKeys = JSON.stringify(rows.map((row) => row.key).sort());

  // 새 거래가 추가된 경우에만 팝업을 자동으로 연다.
  useEffect(() => {
    if (busy) return;

    const currentKeys = new Set(JSON.parse(rowKeys));
    const hasNewRows = [...currentKeys].some((key) => !previousKeysRef.current.has(key));

    previousKeysRef.current = currentKeys;

    if (hasNewRows) {
      setOpen(true);
    }
  }, [rowKeys, busy]);

  // React 상태에 맞춰 dialog를 제어하고 배경 스크롤을 막는다.
  useEffect(() => {
    const dialog = dialogRef.current;

    if (!dialog) return;

    if (open && !dialog.open) {
      dialog.showModal();
    }

    if (!open && dialog.open) {
      dialog.close();
    }

    if (!open) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  function clearStatus() {
    setError('');
    setMessage('');
  }

  function resetDrafts() {
    setDrafts({});
    setCheckedKeys([]);
    setBulkCategory('');
  }

  function handleOpenEditor() {
    if (busy || workingRef.current) return;

    resetDrafts();
    setOnlyUnclassified(false);
    clearStatus();
    setOpen(true);
  }

  function handleCloseEditor() {
    if (busy || workingRef.current) return;

    resetDrafts();
    setError('');
    setOpen(false);
  }

  // 이미 서버에 반영한 작업은 유지하고 미반영 초안과 선택만 초기화한다.
  function handleUndoChanges() {
    if (busy || workingRef.current) return;

    resetDrafts();
    setError('');
    setMessage('미반영 카테고리 변경과 거래 선택을 되돌렸습니다.');
  }

  function resetCategory(key) {
    setDrafts((previous) => {
      const next = { ...previous };
      delete next[key];
      return next;
    });
  }

  function handleToggleCheck(key, enabled) {
    clearStatus();

    setCheckedKeys((previous) =>
      enabled ? [...new Set([...previous, key])] : previous.filter((value) => value !== key)
    );

    setBulkCategory('');
  }

  function handleApplyBulkCategory() {
    if (busy || !selectedBulk || selectedRows.length === 0) {
      return;
    }

    clearStatus();

    // 해당 카테고리를 허용하는 수입·지출 구분의 거래만 변경한다.
    const matchingRows = selectedRows.filter((row) =>
      categoriesFor(row.kind).some((item) => item.value === selectedBulk)
    );

    if (matchingRows.length === 0) {
      setError('선택한 카테고리를 적용할 수 있는 거래가 없습니다. 수입·지출 구분을 확인해주세요.');
      return;
    }

    // 일괄 적용은 초안만 바꾸고 실제 수정은 수정 반영에서 수행한다.
    setDrafts((previous) => ({
      ...previous,
      ...Object.fromEntries(matchingRows.map((row) => [row.key, selectedBulk])),
    }));

    const skippedCount = selectedRows.length - matchingRows.length;

    setMessage(
      `${matchingRows.length}건에 카테고리를 지정했습니다. 수정 반영을 눌러주세요.` +
        (skippedCount > 0 ? ` 구분이 다른 ${skippedCount}건은 유지했습니다.` : '')
    );

    setBulkCategory('');
  }

  // 서버에서 성공한 작업 또는 미저장 거래의 로컬 작업을 확정한다.
  function commitOperation(operation) {
    useTaxStore.setState((current) => applyOperation(current, operation));

    resetCategory(operation.key);
    setCheckedKeys((previous) => previous.filter((key) => key !== operation.key));
  }

  function hasRemainingChanges(current, completedKeys) {
    return collectRows(current).some((row) => {
      if (completedKeys.has(row.key)) {
        return false;
      }

      return drafts[row.key] !== undefined && drafts[row.key] !== row.category;
    });
  }

  async function handleSubmitChanges(action) {
    const initial = useTaxStore.getState();

    if (workingRef.current || initial.taxSaveBusy) {
      return;
    }

    clearStatus();

    const initialRows = collectRows(initial);

    // 삭제 버튼을 눌렀을 때만 체크한 거래를 삭제 대상으로 사용한다.
    const removing = new Set(action === 'delete' ? checkedKeys : []);

    const serverChanges = new Map();
    const localOperations = [];

    if (action === 'update') {
      for (const row of initialRows) {
        const category = drafts[row.key];

        if (category === undefined || category === row.category) {
          continue;
        }

        const allowed = categoriesFor(row.kind).some((item) => item.value === category);

        if (!allowed) {
          setError(`${row.description}: 카테고리를 확인해주세요.`);
          return;
        }

        if (row.persisted) {
          serverChanges.set(row.key, category);
        } else {
          // 미저장 거래의 수정은 DB 요청 없이 화면에만 반영한다.
          localOperations.push({
            key: row.key,
            source: row.source,
            rowId: row.rowId,
            type: 'update',
            category,
            taxInputId: null,
            transactionId: null,
            persisted: false,
          });
        }
      }

      if (serverChanges.size === 0 && localOperations.length === 0) {
        setMessage('반영할 카테고리 변경이 없습니다.');
        return;
      }
    }

    if (action === 'delete' && !initialRows.some((row) => removing.has(row.key))) {
      setMessage('삭제할 거래를 체크박스로 선택해주세요.');
      return;
    }

    // 수정 요청에는 카테고리 변경만, 삭제 요청에는 선택 거래 제거만 반영한다.
    function proposedRows(items, source) {
      return (items ?? []).flatMap((row) => {
        const key = `${source}:${row.id}`;

        if (action === 'delete') {
          return removing.has(key) ? [] : [row];
        }

        const category = serverChanges.get(key);

        return [category === undefined ? row : { ...row, cat: category }];
      });
    }

    const proposed = {
      ...initial,
      manualRows: proposedRows(initial.manualRows, 'manual'),
      fileRows: proposedRows(initial.fileRows, 'file'),
    };

    workingRef.current = true;
    useTaxStore.setState({ taxSaveBusy: true });

    try {
      const result = await saveTaxReview(proposed, {
        originalState: initial,
        onOperationSuccess: commitOperation,
      });

      for (const operation of localOperations) {
        commitOperation(operation);
      }

      const completedKeys = new Set([
        ...result.operations.map((operation) => operation.key),
        ...localOperations.map((operation) => operation.key),
      ]);

      const current = useTaxStore.getState();
      const remainingChanges = hasRemainingChanges(current, completedKeys);

      useTaxStore.setState({
        taxLastSavedSignature:
          !remainingChanges &&
          !hasPendingRows(current) &&
          JSON.stringify(current.profile) === JSON.stringify(initial.profile)
            ? getTaxReviewSignature(current)
            : null,
      });

      if (action === 'update') {
        const parts = [];

        if (result.updatedCount > 0) {
          parts.push(`DB 거래 ${result.updatedCount}건을 수정했습니다.`);
        }

        if (localOperations.length > 0) {
          parts.push(
            `미저장 거래 ${localOperations.length}건의 카테고리를 화면에 반영했습니다. ` +
              'DB에 저장하려면 본문의 저장을 눌러주세요.'
          );
        }

        setMessage(parts.join(' '));
      } else {
        setMessage(
          `DB 거래 ${result.deletedCount}건을 삭제하고 ` + `미저장 거래 ${result.localCount}건을 화면에서 제거했습니다.`
        );
      }

      // 다른 거래의 카테고리 초안이 남아 있으면 팝업을 유지한다.
      if (!remainingChanges) {
        previousKeysRef.current = new Set(collectRows(useTaxStore.getState()).map((row) => row.key));

        resetDrafts();
        setOpen(false);
      }
    } catch (cause) {
      const completed = cause.completedOperations?.length ?? 0;

      setError(
        (cause.message ?? '변경 반영에 실패했습니다.') +
          (completed > 0 ? ` 성공한 변경 ${completed}건은 유지됩니다. 남은 항목을 확인해주세요.` : '')
      );

      // 실패한 거래의 선택과 수정 초안은 재시도를 위해 유지한다.
    } finally {
      workingRef.current = false;
      useTaxStore.setState({ taxSaveBusy: false });
    }
  }

  function rememberCreatedInput({ taxYear, taxInputId }) {
    useTaxStore.setState((current) => ({
      taxPendingInputIdsByYear: {
        ...(current.taxPendingInputIdsByYear ?? {}),
        [taxYear]: taxInputId,
      },
    }));
  }

  // 최초 저장 응답의 분석번호와 거래번호를 화면 거래에 연결한다.
  function commitSavedGroup({ taxYear, taxInputId, rows: storedRows, bindings }) {
    const savedByKey = new Map(bindings.map((binding) => [`${binding.source}:${binding.rowId}`, binding.saved]));

    function applySaved(items, source) {
      return (items ?? []).map((row) => {
        const saved = savedByKey.get(`${source}:${row.id}`);

        if (!saved) return row;

        return {
          ...row,
          taxInputId: saved.taxInputId,
          transactionId: saved.transactionId,
          cat: saved.category,
          persisted: true,
          needsReview: false,
          excluded: false,
          selected: true,
          failureReason: null,
        };
      });
    }

    useTaxStore.setState((current) => {
      const pending = {
        ...(current.taxPendingInputIdsByYear ?? {}),
      };

      if (pending[taxYear] === taxInputId) {
        delete pending[taxYear];
      }

      return {
        manualRows: applySaved(current.manualRows, 'manual'),
        fileRows: applySaved(current.fileRows, 'file'),
        taxInputIdsByYear: {
          ...(current.taxInputIdsByYear ?? {}),
          [taxYear]: taxInputId,
        },
        taxPendingInputIdsByYear: pending,
        taxSavedSnapshots: [
          ...(current.taxSavedSnapshots ?? []).filter((snapshot) => snapshot.taxInputId !== taxInputId),
          {
            taxYear,
            taxInputId,
            rows: storedRows,
          },
        ],
        taxLastSavedSignature: null,
        calculated: false,
      };
    });
  }

  // 본문 저장은 미저장 정상 거래의 최초 INSERT만 담당한다.
  async function handleSavePendingRows() {
    const initial = useTaxStore.getState();

    if (workingRef.current || initial.taxSaveBusy) {
      return;
    }

    clearStatus();

    if (hasChanges) {
      setError('편집 중인 카테고리 변경을 먼저 반영하거나 되돌려주세요.');
      return;
    }

    if (!hasPendingRows(initial)) {
      setMessage('저장할 거래가 없습니다.');
      return;
    }

    workingRef.current = true;
    useTaxStore.setState({ taxSaveBusy: true });

    let completedCount = 0;

    try {
      const result = await saveInitialTaxRows(initial, {
        onInputCreated: rememberCreatedInput,
        onGroupSaved: (group) => {
          commitSavedGroup(group);
          completedCount += group.rows.length;
        },
      });

      useTaxStore.setState((current) => ({
        taxLastSavedSignature:
          !hasPendingRows(current) && JSON.stringify(current.profile) === JSON.stringify(initial.profile)
            ? getTaxReviewSignature(current)
            : null,
      }));

      resetDrafts();
      setMessage(`거래 ${result.savedCount}건을 DB에 저장했습니다.`);
    } catch (cause) {
      setError(
        (cause.message ?? '거래 저장에 실패했습니다.') +
          (completedCount > 0 ? ` 앞서 저장한 ${completedCount}건은 유지됩니다.` : '')
      );
    } finally {
      workingRef.current = false;
      useTaxStore.setState({ taxSaveBusy: false });
    }
  }

  return (
    <Card title="수입·지출 내역" className="tax-entry-card">
      <div className="tax-toolbar">
        <span>
          전체 {rows.length}건{' · '}
          DB 저장 {rows.filter((row) => row.persisted).length}건{' · '}
          저장 대기 {pendingRows.length}건
        </span>

        <div className="tax-separated-actions">
          <Button
            disabled={busy || hasChanges || pendingRows.length === 0}
            onClick={() => void handleSavePendingRows()}
          >
            저장
          </Button>

          <Button disabled={busy || rows.length === 0} onClick={handleOpenEditor}>
            내역 확인·수정
          </Button>
        </div>
      </div>

      <p className="tax-notice">
        미저장 거래는 카테고리를 편집한 뒤 저장 버튼으로 DB에 저장합니다. 저장된 거래는 수정 반영 시 DB를 갱신합니다.
      </p>

      <div className="tax-status" aria-live="polite">
        {error ? <span role="alert">{error}</span> : <span>{message}</span>}
      </div>

      <div className="tax-table-area tax-read-area">
        <table className="tax-table tax-read-table">
          <colgroup>
            <col className="tax-col-kind" />
            <col className="tax-col-date" />
            <col />
            <col className="tax-col-amount" />
            <col className="tax-col-category-read" />
          </colgroup>

          <thead>
            <tr>
              <th>구분</th>
              <th>날짜</th>
              <th>설명</th>
              <th className="tax-amount">금액</th>
              <th>카테고리</th>
            </tr>
          </thead>

          <tbody>
            {rows.map((row) => (
              <tr key={row.key}>
                <td>{kindLabel(row.kind)}</td>
                <td>{row.date}</td>
                <td title={row.description}>
                  {row.needsReview ? '[검토] ' : !row.persisted ? '[저장 대기] ' : ''}
                  {row.description}
                </td>
                <td className="tax-amount">{formatMoney(row.amount)}</td>
                <td>{getTransactionCategoryLabel(row.category)}</td>
              </tr>
            ))}

            {rows.length === 0 && (
              <tr>
                <td colSpan={5}>표시할 거래가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {createPortal(
        <dialog
          ref={dialogRef}
          className="tax-review-dialog"
          aria-labelledby="tax-review-title"
          onCancel={(event) => {
            event.preventDefault();
            handleCloseEditor();
          }}
        >
          <header className="tax-toolbar">
            <h2 id="tax-review-title">수입·지출 내역 수정</h2>
            <Button disabled={busy} onClick={handleCloseEditor}>
              닫기
            </Button>
          </header>

          <div className="tax-status" aria-live="polite">
            {error ? (
              <span role="alert">{error}</span>
            ) : (
              <span>
                {message || '카테고리를 변경한 뒤 수정 반영을 누르세요. 삭제할 거래는 체크한 뒤 삭제 반영을 누르세요.'}
              </span>
            )}
          </div>

          {pendingRows.length > 0 && (
            <p className="tax-notice">미저장 거래는 수정 반영 후 본문의 저장을 눌러야 DB에 저장됩니다.</p>
          )}

          <div className="tax-toolbar tax-editor-tools">
            <label>
              <input
                type="checkbox"
                checked={onlyUnclassified}
                disabled={busy}
                onChange={(event) => {
                  setOnlyUnclassified(event.target.checked);
                  setCheckedKeys([]);
                  setBulkCategory('');
                  clearStatus();
                }}
              />{' '}
              미분류만 보기
            </label>

            <select
              className="tax-bulk-select"
              aria-label="일괄 적용할 카테고리"
              value={selectedBulk}
              disabled={busy || selectedRows.length === 0}
              onChange={(event) => setBulkCategory(event.target.value)}
            >
              <option value="">일괄 적용할 카테고리</option>

              {CATEGORY_GROUPS.map((group) => (
                <optgroup key={group.label} label={group.label}>
                  {group.options.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>

            <Button disabled={busy || !selectedBulk || selectedRows.length === 0} onClick={handleApplyBulkCategory}>
              일괄 적용
            </Button>

            <span>선택 {selectedRows.length}건</span>
          </div>

          <p className="tax-notice">
            일괄 적용은 선택한 거래 중 수입·지출 구분에 맞는 거래만 변경합니다. 삭제 반영을 누르면 체크한 거래가
            삭제됩니다.
          </p>

          <div className="tax-table-area tax-edit-area">
            <table className="tax-table tax-edit-table">
              <colgroup>
                <col className="tax-col-check" />
                <col className="tax-col-kind" />
                <col className="tax-col-date" />
                <col />
                <col className="tax-col-amount" />
                <col className="tax-col-category-edit" />
              </colgroup>

              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      aria-label="현재 목록 전체 선택"
                      disabled={busy || visibleRows.length === 0}
                      checked={visibleRows.length > 0 && visibleRows.every((row) => checked.has(row.key))}
                      onChange={(event) => {
                        const keys = new Set(visibleRows.map((row) => row.key));

                        setCheckedKeys((previous) =>
                          event.target.checked
                            ? [...new Set([...previous, ...keys])]
                            : previous.filter((key) => !keys.has(key))
                        );

                        setBulkCategory('');
                        clearStatus();
                      }}
                    />
                  </th>
                  <th>구분</th>
                  <th>날짜</th>
                  <th>설명</th>
                  <th className="tax-amount">금액</th>
                  <th>카테고리</th>
                </tr>
              </thead>

              <tbody>
                {visibleRows.map((row) => {
                  const options = categoriesFor(row.kind);
                  const value = drafts[row.key] ?? row.category;

                  return (
                    <tr key={row.key}>
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`${row.description} 선택`}
                          disabled={busy}
                          checked={checked.has(row.key)}
                          onChange={(event) => handleToggleCheck(row.key, event.target.checked)}
                        />
                      </td>
                      <td>{kindLabel(row.kind)}</td>
                      <td>{row.date}</td>
                      <td className="tax-description" title={row.description}>
                        {row.needsReview ? '[검토] ' : !row.persisted ? '[저장 대기] ' : ''}
                        {row.description}
                      </td>
                      <td className="tax-amount">{formatMoney(row.amount)}</td>
                      <td>
                        <select
                          aria-label={`${row.description} 카테고리`}
                          value={value}
                          disabled={busy}
                          onChange={(event) => {
                            clearStatus();
                            setDrafts((previous) => ({
                              ...previous,
                              [row.key]: event.target.value,
                            }));
                          }}
                        >
                          {!options.some((item) => item.value === value) && (
                            <option value={value}>{getTransactionCategoryLabel(value)}</option>
                          )}

                          {options.map((item) => (
                            <option key={item.value} value={item.value}>
                              {item.label}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  );
                })}

                {visibleRows.length === 0 && (
                  <tr>
                    <td colSpan={6}>표시할 거래가 없습니다.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <footer className="tax-editor-footer">
            <p className="tax-change-count">
              카테고리 변경 {changedRows.length}건{' · '}
              선택 {selectedRows.length}건
            </p>

            <div className="tax-separated-actions">
              <Button
                variant="primary"
                disabled={busy || changedRows.length === 0}
                onClick={() => void handleSubmitChanges('update')}
              >
                수정 반영
              </Button>

              <Button
                variant="danger"
                disabled={busy || selectedRows.length === 0}
                onClick={() => void handleSubmitChanges('delete')}
              >
                삭제 반영
              </Button>

              <Button disabled={busy || !canUndo} onClick={handleUndoChanges}>
                변경 되돌리기
              </Button>
            </div>
          </footer>
        </dialog>,
        document.body
      )}
    </Card>
  );
}
