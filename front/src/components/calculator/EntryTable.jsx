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

function categoriesFor(kind) {
  if (kind === 'in') {
    return INCOME_TRANSACTION_CATEGORIES;
  }

  if (kind === 'out') {
    return EXPENSE_TRANSACTION_CATEGORIES;
  }

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

        // 식별자는 화면에 표시하지 않는다.
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

// 서버에서 성공한 거래만 화면 상태에 반영한다.
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
  const [deletedKeys, setDeletedKeys] = useState([]);
  const [checkedKeys, setCheckedKeys] = useState([]);
  const [onlyUnclassified, setOnlyUnclassified] = useState(false);
  const [bulkCategory, setBulkCategory] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const busy = state.taxSaveBusy === true;

  const rows = collectRows(state);
  const deleted = new Set(deletedKeys);
  const checked = new Set(checkedKeys);

  const visibleRows = onlyUnclassified ? rows.filter((row) => row.category === 'UNCLASSIFIED') : rows;

  const selectableRows = visibleRows.filter((row) => !deleted.has(row.key));

  const selectedRows = rows.filter((row) => checked.has(row.key) && !deleted.has(row.key));

  const changedRows = rows.filter(
    (row) => !deleted.has(row.key) && drafts[row.key] !== undefined && drafts[row.key] !== row.category
  );

  const removingRows = rows.filter((row) => deleted.has(row.key));

  const pendingRows = rows.filter((row) => !row.persisted && !row.needsReview && row.selected);

  const hasChanges = changedRows.length > 0 || removingRows.length > 0;

  const bulkOptions = ALL_CATEGORIES.filter(
    (option) =>
      selectedRows.length > 0 &&
      selectedRows.every((row) => categoriesFor(row.kind).some((item) => item.value === option.value))
  );

  const selectedBulk = bulkOptions.some((item) => item.value === bulkCategory) ? bulkCategory : '';

  const rowKeys = JSON.stringify(rows.map((row) => row.key).sort());

  // 새로운 거래가 추가됐을 때만 팝업을 자동으로 연다.
  // 최초 저장 후 거래번호만 갱신되면 다시 열리지 않는다.
  useEffect(() => {
    if (busy) return;

    const keys = new Set(JSON.parse(rowKeys));

    const added = [...keys].some((key) => !previousKeysRef.current.has(key));

    previousKeysRef.current = keys;

    if (added) {
      setOpen(true);
    }
  }, [rowKeys, busy]);

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
    setDeletedKeys([]);
    setCheckedKeys([]);
    setBulkCategory('');
  }

  function openEditor() {
    if (busy || workingRef.current) return;

    resetDrafts();
    setOnlyUnclassified(false);
    clearStatus();
    setOpen(true);
  }

  function closeEditor() {
    if (busy || workingRef.current) return;

    resetDrafts();
    setError('');
    setOpen(false);
  }

  function undoChanges() {
    if (busy || workingRef.current) return;

    resetDrafts();
    setError('');
    setMessage('아직 반영하지 않은 변경을 되돌렸습니다.');
  }

  function resetCategory(key) {
    setDrafts((previous) => {
      const next = { ...previous };
      delete next[key];
      return next;
    });
  }

  function toggleCheck(key, enabled) {
    setCheckedKeys((previous) =>
      enabled ? [...new Set([...previous, key])] : previous.filter((value) => value !== key)
    );

    setBulkCategory('');
  }

  function toggleDelete(key) {
    clearStatus();

    setDeletedKeys((previous) =>
      previous.includes(key) ? previous.filter((value) => value !== key) : [...previous, key]
    );

    setCheckedKeys((previous) => previous.filter((value) => value !== key));

    setBulkCategory('');
  }

  function deleteSelected() {
    clearStatus();

    setDeletedKeys((previous) => [...new Set([...previous, ...selectedRows.map((row) => row.key)])]);

    setCheckedKeys([]);
    setBulkCategory('');
  }

  function applyBulkCategory() {
    if (!selectedBulk) return;

    clearStatus();

    setDrafts((previous) => ({
      ...previous,
      ...Object.fromEntries(selectedRows.map((row) => [row.key, selectedBulk])),
    }));

    setBulkCategory('');
  }

  function commitOperation(operation) {
    useTaxStore.setState((current) => applyOperation(current, operation));

    // 성공한 항목의 초안만 제거한다.
    resetCategory(operation.key);

    if (operation.type === 'delete') {
      setDeletedKeys((previous) => previous.filter((key) => key !== operation.key));
    }

    setCheckedKeys((previous) => previous.filter((key) => key !== operation.key));
  }

  function updateSavedSignature(initial, completedKeys) {
    useTaxStore.setState((current) => {
      const remainingChanges = collectRows(current).some((row) => {
        if (completedKeys.has(row.key)) return false;

        return deletedKeys.includes(row.key) || (drafts[row.key] !== undefined && drafts[row.key] !== row.category);
      });

      return {
        taxLastSavedSignature:
          !remainingChanges &&
          !hasPendingRows(current) &&
          JSON.stringify(current.profile) === JSON.stringify(initial.profile)
            ? getTaxReviewSignature(current)
            : null,
      };
    });
  }

  // 수정과 삭제는 선택한 작업만 각각 전송한다.
  async function submitChanges(action) {
    if (workingRef.current || useTaxStore.getState().taxSaveBusy) {
      return;
    }

    clearStatus();

    const initial = useTaxStore.getState();
    const removing = new Set(deletedKeys);
    const changes = new Map();

    if (action === 'update') {
      for (const row of collectRows(initial)) {
        const category = drafts[row.key];

        if (removing.has(row.key) || category === undefined || category === row.category) {
          continue;
        }

        if (!categoriesFor(row.kind).some((item) => item.value === category)) {
          setError(`${row.description}: 카테고리를 확인해주세요.`);
          return;
        }

        changes.set(row.key, category);
      }

      if (changes.size === 0) {
        setMessage('반영할 카테고리 변경이 없습니다.');
        return;
      }
    }

    if (action === 'delete' && !collectRows(initial).some((row) => removing.has(row.key))) {
      setMessage('삭제 예정 거래가 없습니다.');
      return;
    }

    function proposedRows(items, source) {
      return (items ?? []).flatMap((row) => {
        const key = `${source}:${row.id}`;

        if (action === 'delete') {
          return removing.has(key) ? [] : [row];
        }

        const category = changes.get(key);

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
      // 신규 분석 생성·전체 거래 저장은 호출하지 않는다.
      const result = await saveTaxReview(proposed, {
        originalState: initial,
        onOperationSuccess: commitOperation,
      });

      const completedKeys = new Set(result.operations.map((operation) => operation.key));

      updateSavedSignature(initial, completedKeys);

      setMessage(
        action === 'update'
          ? `DB 거래 수정 ${result.updatedCount}건` + ` · 미저장 목록 수정 ${result.localCount}건을 반영했습니다.`
          : `DB 거래 삭제 ${result.deletedCount}건` + ` · 미저장 목록 제거 ${result.localCount}건을 반영했습니다.`
      );
    } catch (cause) {
      const completed = cause.completedOperations?.length ?? 0;

      setError(
        (cause.message ?? '변경 반영에 실패했습니다.') +
          (completed > 0 ? ` 성공한 변경 ${completed}건은 유지됩니다. 남은 항목을 확인해주세요.` : '')
      );
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

  function commitInitialGroup({ taxYear, taxInputId, rows: savedRows, bindings }) {
    const savedByKey = new Map(bindings.map((binding) => [`${binding.source}:${binding.rowId}`, binding.saved]));

    function applySaved(items, source) {
      return (items ?? []).map((row) => {
        const saved = savedByKey.get(`${source}:${row.id}`);

        if (!saved) return row;

        // 화면 행의 id는 유지해 팝업이 다시 열리지 않도록 한다.
        return {
          ...row,
          taxInputId: saved.taxInputId,
          transactionId: saved.transactionId,
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

      delete pending[taxYear];

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
            rows: savedRows,
          },
        ],

        taxLastSavedSignature: null,
        calculated: false,
      };
    });
  }

  async function savePendingRows() {
    if (workingRef.current || useTaxStore.getState().taxSaveBusy) {
      return;
    }

    clearStatus();

    if (hasChanges) {
      setError('수정·삭제 예정 내용을 먼저 반영하거나 되돌린 뒤 최초 저장해주세요.');
      return;
    }

    const initial = useTaxStore.getState();

    if (!hasPendingRows(initial)) {
      setMessage('최초 저장할 거래가 없습니다.');
      return;
    }

    workingRef.current = true;
    useTaxStore.setState({ taxSaveBusy: true });

    let completedCount = 0;

    try {
      const result = await saveInitialTaxRows(initial, {
        onInputCreated: rememberCreatedInput,

        onGroupSaved: (group) => {
          commitInitialGroup(group);
          completedCount += group.rows.length;
        },
      });

      useTaxStore.setState((current) => ({
        taxLastSavedSignature:
          !hasPendingRows(current) && JSON.stringify(current.profile) === JSON.stringify(initial.profile)
            ? getTaxReviewSignature(current)
            : null,
      }));

      setMessage(`미저장 거래 ${result.savedCount}건을 최초 저장했습니다.`);

      // 최초 저장 요청이 모두 성공했을 때만 팝업을 닫는다.
      resetDrafts();
      setOpen(false);
    } catch (cause) {
      setError(
        (cause.message ?? '최초 저장에 실패했습니다.') +
          (completedCount > 0 ? ` 앞서 저장한 ${completedCount}건은 유지됩니다. 남은 거래를 확인해주세요.` : '')
      );

      // 실패하면 팝업을 유지해 오류 확인과 재시도를 허용한다.
    } finally {
      workingRef.current = false;
      useTaxStore.setState({ taxSaveBusy: false });
    }
  }

  return (
    <Card title="수입·지출 내역" className="tax-entry-card">
      <div className="tax-toolbar">
        <span>
          전체 {rows.length}건{' · '}수입 {rows.filter((row) => row.kind === 'in').length}건{' · '}지출{' '}
          {rows.filter((row) => row.kind === 'out').length}건
        </span>

        <Button disabled={busy} onClick={openEditor}>
          내역 확인·수정
        </Button>
      </div>

      <p className="tax-notice">수정·삭제는 팝업에서 각각 반영합니다. 기존 분석번호와 거래번호는 유지됩니다.</p>

      <div className="tax-status" aria-live="polite">
        {message}
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
            closeEditor();
          }}
        >
          <header className="tax-toolbar">
            <h2 id="tax-review-title">수입·지출 내역 수정</h2>

            <Button disabled={busy} onClick={closeEditor}>
              닫기
            </Button>
          </header>

          <div className="tax-status" aria-live="polite">
            {error ? (
              <span role="alert">{error}</span>
            ) : (
              <span>{message || '수정·삭제는 각각 반영합니다. 검토 거래는 최초 저장에서 제외됩니다.'}</span>
            )}
          </div>

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

              {bulkOptions.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>

            <Button disabled={busy || !selectedBulk} onClick={applyBulkCategory}>
              일괄 적용
            </Button>

            <Button disabled={busy || selectedRows.length === 0} onClick={deleteSelected}>
              선택 삭제 예정
            </Button>
          </div>

          <div className="tax-table-area tax-edit-area">
            <table className="tax-table tax-edit-table">
              <colgroup>
                <col className="tax-col-check" />
                <col className="tax-col-kind" />
                <col className="tax-col-date" />
                <col />
                <col className="tax-col-amount" />
                <col className="tax-col-category-edit" />
                <col className="tax-col-delete" />
              </colgroup>

              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      aria-label="현재 목록 전체 선택"
                      disabled={busy || selectableRows.length === 0}
                      checked={selectableRows.length > 0 && selectableRows.every((row) => checked.has(row.key))}
                      onChange={(event) => {
                        const keys = new Set(selectableRows.map((row) => row.key));

                        setCheckedKeys((previous) =>
                          event.target.checked
                            ? [...new Set([...previous, ...keys])]
                            : previous.filter((key) => !keys.has(key))
                        );

                        setBulkCategory('');
                      }}
                    />
                  </th>
                  <th>구분</th>
                  <th>날짜</th>
                  <th>설명</th>
                  <th className="tax-amount">금액</th>
                  <th>카테고리</th>
                  <th>삭제</th>
                </tr>
              </thead>

              <tbody>
                {visibleRows.map((row) => {
                  const removing = deleted.has(row.key);
                  const options = categoriesFor(row.kind);
                  const value = drafts[row.key] ?? row.category;

                  return (
                    <tr key={row.key} className={removing ? 'tax-row-deleting' : ''}>
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`${row.description} 선택`}
                          disabled={busy || removing}
                          checked={checked.has(row.key)}
                          onChange={(event) => toggleCheck(row.key, event.target.checked)}
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
                        <div className="tax-category-control">
                          <select
                            aria-label={`${row.description} 카테고리`}
                            value={value}
                            disabled={busy || removing}
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

                          <Button
                            size="sm"
                            disabled={busy || removing || drafts[row.key] === undefined}
                            onClick={() => resetCategory(row.key)}
                          >
                            되돌림
                          </Button>
                        </div>
                      </td>

                      <td>
                        <Button size="sm" disabled={busy} onClick={() => toggleDelete(row.key)}>
                          {removing ? '삭제 취소' : '삭제 예정'}
                        </Button>
                      </td>
                    </tr>
                  );
                })}

                {visibleRows.length === 0 && (
                  <tr>
                    <td colSpan={7}>표시할 거래가 없습니다.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <footer className="tax-editor-footer">
            <p className="tax-change-count">
              수정 {changedRows.length}건{' · '}삭제 예정 {removingRows.length}건{' · '}최초 저장 대기{' '}
              {pendingRows.length}건
            </p>

            <div className="tax-separated-actions">
              <Button
                variant="primary"
                disabled={busy || changedRows.length === 0}
                onClick={() => void submitChanges('update')}
              >
                수정 반영
              </Button>

              <Button
                variant="danger"
                disabled={busy || removingRows.length === 0}
                onClick={() => void submitChanges('delete')}
              >
                삭제 반영
              </Button>

              <Button disabled={busy || !hasChanges} onClick={undoChanges}>
                변경 되돌리기
              </Button>

              <Button disabled={busy || hasChanges || pendingRows.length === 0} onClick={() => void savePendingRows()}>
                최초 저장
              </Button>
            </div>
          </footer>
        </dialog>,
        document.body
      )}
    </Card>
  );
}
