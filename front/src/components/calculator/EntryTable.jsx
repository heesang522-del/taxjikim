'use no memo';

import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import styled from 'styled-components';

import { Button, Card } from '../common/index';

import {
    getTaxReviewSignature,
    saveTaxReview,
} from '../../api/taxApi';

import {
    normalizeTaxKind,
    parseTaxAmount,
    useTaxStore,
} from '../../store/useTaxStore';

import {
    getTransactionCashAmount,
} from '../../utils/taxInputSummary';

import {
    INCOME_TRANSACTION_CATEGORIES,
    EXPENSE_TRANSACTION_CATEGORIES,
} from '../../data/transactionCategories';

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
`;

const InlineActions = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
`;

const Notice = styled.p`
  margin: 12px 0;
  color: #536476;
  font-size: 13px;
  line-height: 1.6;
`;

const Dialog = styled.dialog`
  width: min(1150px, calc(100vw - 40px));
  max-height: 85vh;
  overflow: auto;
  padding: 24px;
  border: 0;
  border-radius: 16px;
  background: #fff;
  color: #172033;

  &::backdrop {
    background: rgba(15, 23, 42, 0.45);
  }

  h2 {
    margin-top: 0;
  }

  select {
    min-width: 150px;
    padding: 8px;
    border: 1px solid #dce3ed;
    border-radius: 7px;
    background: #fff;
    font: inherit;
  }
`;

const BulkArea = styled.div`
  margin: 16px 0;
  padding: 14px;
  border: 1px solid #e1e5f0;
  border-radius: 10px;
  background: #f7f8fc;
`;

const TableArea = styled.div`
  max-height: 480px;
  overflow: auto;
  margin-top: 12px;

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
  }

  th,
  td {
    padding: 12px 10px;
    border-bottom: 1px solid #e5eaf1;
    text-align: left;
    vertical-align: middle;
  }

  th {
    position: sticky;
    top: 0;
    z-index: 1;
    background: #f5f7fb;
    white-space: nowrap;
  }

  .amount {
    text-align: right;
    white-space: nowrap;
  }
`;

const ReviewRow = styled.tr`
  background: ${({ $deleted }) => (
    $deleted ? '#fff1ef' : 'transparent'
)};

  .description {
    text-decoration: ${({ $deleted }) => (
    $deleted ? 'line-through' : 'none'
)};
  }
`;

const Footer = styled(Toolbar)`
  position: sticky;
  bottom: -24px;
  z-index: 2;
  padding: 16px 0;
  margin-top: 16px;
  border-top: 1px solid #e5eaf1;
  background: #fff;
`;

const Badge = styled.span`
  display: inline-block;
  padding: 4px 8px;
  border-radius: 6px;
  background: ${({ $unclassified }) => (
    $unclassified ? '#fff3db' : '#f0f3f8'
)};
  color: ${({ $unclassified }) => (
    $unclassified ? '#805300' : '#536476'
)};
  font-size: 12px;
  white-space: nowrap;
`;

function unique(items) {
    return [
        ...new Map(
            items.map((item) => [item.value, item]),
        ).values(),
    ];
}

const INCOME_CATEGORIES = unique([
    ...INCOME_TRANSACTION_CATEGORIES,
    { value: 'OTHER', label: '기타' },
]);

const EXPENSE_CATEGORIES = unique([
    ...EXPENSE_TRANSACTION_CATEGORIES,
    { value: 'MEDICAL_EXPENSE', label: '의료비' },
    { value: 'EDUCATION_EXPENSE', label: '교육비' },
    { value: 'INSURANCE_PREMIUM', label: '보험료' },
    { value: 'DONATION', label: '기부금' },
    { value: 'OTHER', label: '기타' },
]);

const ALL_CATEGORIES = unique([
    ...INCOME_CATEGORIES,
    ...EXPENSE_CATEGORIES,
]);

function isUnclassified(value) {
    const category = String(value ?? '').trim();

    return !category || category === 'UNCLASSIFIED';
}

function categoryLabel(value) {
    if (isUnclassified(value)) return '미분류';

    return ALL_CATEGORIES.find(
        (item) => item.value === value,
    )?.label ?? value;
}

function categoriesFor(kind) {
    const categories = kind === 'in'
        ? INCOME_CATEGORIES
        : kind === 'out'
            ? EXPENSE_CATEGORIES
            : ALL_CATEGORIES;

    return categories.filter(
        (item) => item.value !== 'UNCLASSIFIED',
    );
}

function kindLabel(value) {
    const kind = normalizeTaxKind(value);

    if (kind === 'in') return '수입';
    if (kind === 'out') return '지출';

    return '구분 미확인';
}

function money(value) {
    const amount = parseTaxAmount(value);

    return amount === null
        ? '금액 미확인'
        : `${amount.toLocaleString('ko-KR')}원`;
}

function periodText(value) {
    if (Array.isArray(value)) {
        return value.map((part, index) => (
            index === 0
                ? String(part)
                : String(part).padStart(2, '0')
        )).join('-');
    }

    return value == null ? '미기재' : String(value);
}

function collectRows(state) {
    function normalRow(row, source) {
        return {
            key: `${source}:${row.id}`,
            sourceId: row.id,
            source,
            kind: normalizeTaxKind(row.kind),
            period: periodText(
                row.date || row.month || row.year,
            ),
            description: row.memo || '내용 미기재',
            category: row.cat || 'UNCLASSIFIED',
            amount: getTransactionCashAmount(row),
            evidence: false,
        };
    }

    return [
        ...state.manualRows.map(
            (row) => normalRow(row, 'manual'),
        ),

        ...state.fileRows.map(
            (row) => normalRow(row, 'file'),
        ),

        ...(state.evidenceDocuments ?? []).flatMap(
            (document) => document.rows.map((row) => ({
                key: `evidence:${row.id}`,
                sourceId: row.id,
                source: 'evidence',

                // 현재 증빙 목록은 카드 지출 공제대상금액 자료다.
                kind: 'out',

                period: periodText(
                    row.transactionMonth
                    || row.transactionDate
                    || row.documentYear,
                ),

                description: row.description || '항목 미기재',
                category: row.category || 'UNCLASSIFIED',
                amount: row.amount,
                evidence: true,
            })),
        ),
    ];
}

function attachSavedIds(state, savedRows) {
    function updateRows(rows, source) {
        return rows.map((row) => ({
            ...row,
            ...(savedRows.get(`${source}:${row.id}`) ?? {}),
        }));
    }

    return {
        ...state,
        manualRows: updateRows(state.manualRows, 'manual'),
        fileRows: updateRows(state.fileRows, 'file'),

        evidenceDocuments: (
            state.evidenceDocuments ?? []
        ).map((document) => ({
            ...document,
            persisted: true,
            rows: updateRows(document.rows, 'evidence'),
        })),
    };
}

export function EntryTable() {
    const state = useTaxStore();

    const dialogRef = useRef(null);
    const previousKeysRef = useRef(new Set());
    const savingRef = useRef(false);

    const [open, setOpen] = useState(false);
    const [categoryDrafts, setCategoryDrafts] = useState({});
    const [editingKeys, setEditingKeys] = useState([]);
    const [deletedKeys, setDeletedKeys] = useState([]);
    const [checkedKeys, setCheckedKeys] = useState([]);
    const [onlyUnclassified, setOnlyUnclassified] = useState(false);
    const [bulkCategory, setBulkCategory] = useState('');
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');

    const busy = state.taxSaveBusy === true;
    const allRows = collectRows(state);
    const snapshots = state.taxSavedSnapshots ?? [];

    const editingSet = new Set(editingKeys);
    const deletedSet = new Set(deletedKeys);
    const checkedSet = new Set(checkedKeys);

    const visibleRows = onlyUnclassified
        ? allRows.filter((row) => isUnclassified(row.category))
        : allRows;

    const selectableRows = visibleRows.filter(
        (row) => !deletedSet.has(row.key),
    );

    const allChecked = selectableRows.length > 0
        && selectableRows.every(
            (row) => checkedSet.has(row.key),
        );

    const bulkTargetRows = allRows.filter((row) => (
        checkedSet.has(row.key)
        && !deletedSet.has(row.key)
        && isUnclassified(row.category)
    ));

    // 선택한 모든 행에 사용할 수 있는 카테고리만 제안한다.
    // 수입에 지출 전용 카테고리가 적용되는 것을 막는다.
    const bulkCategories = ALL_CATEGORIES.filter((category) => (
        category.value !== 'UNCLASSIFIED'
        && bulkTargetRows.length > 0
        && bulkTargetRows.every((row) => (
            categoriesFor(row.kind).some(
                (item) => item.value === category.value,
            )
        ))
    ));

    const validBulkCategory = bulkCategories.some(
        (item) => item.value === bulkCategory,
    ) ? bulkCategory : '';

    const changeCount = allRows.filter((row) => (
        !deletedSet.has(row.key)
        && isUnclassified(row.category)
        && categoryDrafts[row.key]
        && categoryDrafts[row.key] !== 'UNCLASSIFIED'
    )).length;

    const deleteCount = allRows.filter(
        (row) => deletedSet.has(row.key),
    ).length;

    const keySignature = JSON.stringify(
        allRows.map((row) => row.key).sort(),
    );

    useEffect(() => {
        if (busy) return;

        const keys = new Set(JSON.parse(keySignature));

        const added = [...keys].some(
            (key) => !previousKeysRef.current.has(key),
        );

        previousKeysRef.current = keys;

        if (added) {
            setError('');
            setOpen(true);
        }
    }, [keySignature, busy]);

    useEffect(() => {
        const dialog = dialogRef.current;

        if (!dialog) return;

        if (open && !dialog.open) dialog.showModal();
        if (!open && dialog.open) dialog.close();
    }, [open]);

    function resetDrafts() {
        setCategoryDrafts({});
        setEditingKeys([]);
        setDeletedKeys([]);
        setCheckedKeys([]);
        setOnlyUnclassified(false);
        setBulkCategory('');
        setError('');
    }

    function handleCancel() {
        if (busy || savingRef.current) return;

        resetDrafts();
        setOpen(false);
    }

    function handleStartCategoryEdit(row) {
        if (
            busy
            || deletedSet.has(row.key)
            || !isUnclassified(row.category)
        ) {
            return;
        }

        setEditingKeys((previous) => [
            ...new Set([...previous, row.key]),
        ]);

        setCategoryDrafts((previous) => ({
            ...previous,
            [row.key]: previous[row.key] ?? 'UNCLASSIFIED',
        }));
    }

    function handleCancelCategoryEdit(key) {
        if (busy) return;

        setEditingKeys((previous) => previous.filter(
            (value) => value !== key,
        ));

        setCategoryDrafts((previous) => {
            const next = { ...previous };
            delete next[key];
            return next;
        });
    }

    function handleCheck(key, checked) {
        setCheckedKeys((previous) => (
            checked
                ? [...new Set([...previous, key])]
                : previous.filter((value) => value !== key)
        ));

        setBulkCategory('');
        setError('');
    }

    function handleCheckAll(checked) {
        const keys = new Set(
            selectableRows.map((row) => row.key),
        );

        setCheckedKeys((previous) => (
            checked
                ? [...new Set([...previous, ...keys])]
                : previous.filter((key) => !keys.has(key))
        ));

        setBulkCategory('');
        setError('');
    }

    function handleSelectUnclassified(kind) {
        // 수입 또는 지출의 미분류를 한 번에 선택할 수 있다.
        const keys = allRows
            .filter((row) => (
                row.kind === kind
                && isUnclassified(row.category)
                && !deletedSet.has(row.key)
            ))
            .map((row) => row.key);

        setCheckedKeys(keys);
        setBulkCategory('');
        setError('');
    }

    function handleBulkCategoryApply() {
        if (busy) return;

        if (bulkTargetRows.length === 0) {
            setError('수정할 미분류 내역을 체크해주세요.');
            return;
        }

        if (!validBulkCategory) {
            setError('일괄 적용할 카테고리를 선택해주세요.');
            return;
        }

        // 선택한 미분류 행의 임시 카테고리를 한 번에 변경한다.
        setCategoryDrafts((previous) => {
            const next = { ...previous };

            for (const row of bulkTargetRows) {
                next[row.key] = validBulkCategory;
            }

            return next;
        });

        setEditingKeys((previous) => [
            ...new Set([
                ...previous,
                ...bulkTargetRows.map((row) => row.key),
            ]),
        ]);

        setBulkCategory('');
        setError('');
    }

    function handleToggleDelete(key) {
        if (busy) return;

        setDeletedKeys((previous) => (
            previous.includes(key)
                ? previous.filter((value) => value !== key)
                : [...previous, key]
        ));

        setCheckedKeys((previous) => previous.filter(
            (value) => value !== key,
        ));

        setBulkCategory('');
    }

    async function handleDecide() {
        if (
            savingRef.current
            || useTaxStore.getState().taxSaveBusy
        ) {
            return;
        }

        setError('');
        setMessage('');

        const initial = useTaxStore.getState();
        const initialSignature = getTaxReviewSignature(initial);

        const deletes = new Set(deletedKeys);
        const changes = new Map();

        for (const row of collectRows(initial)) {
            if (
                deletes.has(row.key)
                || !isUnclassified(row.category)
            ) {
                continue;
            }

            const category = categoryDrafts[row.key];

            if (!category || category === 'UNCLASSIFIED') {
                continue;
            }

            if (!categoriesFor(row.kind).some(
                (item) => item.value === category,
            )) {
                setError(
                    `${row.description}: 카테고리를 확인해주세요.`,
                );
                return;
            }

            changes.set(row.key, category);
        }

        function updateTransactions(rows, source) {
            return rows
                .filter(
                    (row) => !deletes.has(`${source}:${row.id}`),
                )
                .map((row) => {
                    const category = changes.get(
                        `${source}:${row.id}`,
                    );

                    // 수입·지출, 날짜, 설명, 금액은 그대로 유지한다.
                    return category && isUnclassified(row.cat)
                        ? { ...row, cat: category }
                        : row;
                });
        }

        const proposed = {
            ...initial,

            manualRows: updateTransactions(
                initial.manualRows,
                'manual',
            ),

            fileRows: updateTransactions(
                initial.fileRows,
                'file',
            ),

            evidenceDocuments: (
                initial.evidenceDocuments ?? []
            )
                .map((document) => ({
                    ...document,

                    rows: document.rows
                        .filter(
                            (row) => !deletes.has(`evidence:${row.id}`),
                        )
                        .map((row) => {
                            const category = changes.get(
                                `evidence:${row.id}`,
                            );

                            return category && isUnclassified(row.category)
                                ? { ...row, category }
                                : row;
                        }),
                }))
                .filter((document) => document.rows.length > 0),
        };

        savingRef.current = true;
        useTaxStore.setState({ taxSaveBusy: true });

        try {
            const result = await saveTaxReview(proposed, {
                onPendingChange(pending) {
                    useTaxStore.setState({
                        taxPendingSnapshot: pending,
                    });
                },
            });

            if (
                getTaxReviewSignature(useTaxStore.getState())
                !== initialSignature
            ) {
                throw new Error(
                    '저장 중 입력이 변경됐습니다. '
                    + '새 분석 묶음이 저장됐으므로 내용을 확인해주세요.',
                );
            }

            const committed = attachSavedIds(
                proposed,
                result.savedRows,
            );

            // 저장 성공 후 수정 결과와 DB 재조회 목록을 함께 반영한다.
            useTaxStore.setState({
                manualRows: committed.manualRows,
                fileRows: committed.fileRows,
                evidenceDocuments: committed.evidenceDocuments,
                taxInputIdsByYear: result.inputIds,
                taxSavedSnapshots: result.snapshots,
                taxPendingSnapshot: null,
                taxLastSavedSignature: getTaxReviewSignature(committed),
                calculated: false,
            });

            resetDrafts();
            setOpen(false);

            setMessage(
                '카테고리 변경·삭제 내용을 새 분석번호로 저장했습니다.',
            );
        } catch (cause) {
            setError(
                cause.message ?? '저장에 실패했습니다.',
            );

            dialogRef.current?.scrollTo({
                top: 0,
                behavior: 'smooth',
            });
        } finally {
            savingRef.current = false;
            useTaxStore.setState({ taxSaveBusy: false });
        }
    }

    return (
        <Card title="수입·지출 내역">
            <Toolbar>
        <span>
          전체 {allRows.length}건
            {' · '}수입 {
            allRows.filter((row) => row.kind === 'in').length
        }건
            {' · '}지출 {
            allRows.filter((row) => row.kind === 'out').length
        }건
        </span>

                <Button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                        resetDrafts();
                        setOpen(true);
                    }}
                >
                    내역 확인·수정
                </Button>
            </Toolbar>

            <Notice>
                미분류 카테고리를 개별 또는 일괄 수정할 수 있습니다.
                결정하면 새 분석번호로 전체 내역을 저장합니다.
            </Notice>

            {message && (
                <Notice role="status">{message}</Notice>
            )}

            {snapshots.map((snapshot) => (
                <section key={snapshot.taxInputId}>
                    <h3>
                        DB 저장 내역 · {snapshot.taxYear}년
                        {' · '}분석번호 {snapshot.taxInputId}
                    </h3>

                    <Notice>
                        에서 다시 조회한 {snapshot.rows.length}건입니다.
                    </Notice>

                    {snapshot.rows.length === 0 ? (
                        <Notice>빈 분석 묶음으로 저장됐습니다.</Notice>
                    ) : (
                        <TableArea>
                            <table>
                                <thead>
                                <tr>
                                    <th>거래번호</th>
                                    <th>구분</th>
                                    <th>기간</th>
                                    <th>설명</th>
                                    <th className="amount">금액</th>
                                    <th>카테고리</th>
                                </tr>
                                </thead>

                                <tbody>
                                {snapshot.rows.map((row) => (
                                    <tr key={row.transactionId}>
                                        <td>{row.transactionId}</td>
                                        <td>{kindLabel(row.transactionType)}</td>
                                        <td>
                                            {periodText(
                                                row.transactionDate
                                                || row.transactionMonth
                                                || row.documentYear,
                                            )}
                                        </td>
                                        <td>
                                            {row.description}

                                            {row.amountBasis === 'DEDUCTION_ELIGIBLE' && (
                                                <div>
                                                    <Badge>공제대상금액</Badge>
                                                </div>
                                            )}
                                        </td>
                                        <td className="amount">{money(row.amount)}</td>
                                        <td>{categoryLabel(row.category)}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </TableArea>
                    )}
                </section>
            ))}

            {createPortal(
                <Dialog
                    ref={dialogRef}
                    aria-labelledby="review-title"
                    onCancel={(event) => {
                        event.preventDefault();
                        handleCancel();
                    }}
                >
                    <h2 id="review-title">
                        수입·지출 내역 확인
                    </h2>

                    <Notice>
                        여러 미분류 내역을 체크하고 카테고리를 한 번에 적용할 수 있습니다.
                        결정 버튼을 눌러야 DB에 저장됩니다.
                    </Notice>

                    {error && <p role="alert">{error}</p>}

                    <Toolbar>
                        <label>
                            <input
                                type="checkbox"
                                checked={onlyUnclassified}
                                disabled={busy}
                                onChange={(event) => {
                                    setOnlyUnclassified(event.target.checked);
                                    setCheckedKeys([]);
                                    setBulkCategory('');
                                    setError('');
                                }}
                            />{' '}
                            미분류만 보기
                        </label>

                        <Button
                            type="button"
                            size="sm"
                            disabled={busy || checkedKeys.length === 0}
                            onClick={() => {
                                setDeletedKeys((previous) => [
                                    ...new Set([...previous, ...checkedKeys]),
                                ]);
                                setCheckedKeys([]);
                                setBulkCategory('');
                            }}
                        >
                            선택 삭제 ({checkedKeys.length}건)
                        </Button>
                    </Toolbar>

                    <BulkArea>
                        <InlineActions>
                            <Button
                                type="button"
                                size="sm"
                                disabled={busy}
                                onClick={() => handleSelectUnclassified('in')}
                            >
                                수입 미분류 전체 선택
                            </Button>

                            <Button
                                type="button"
                                size="sm"
                                disabled={busy}
                                onClick={() => handleSelectUnclassified('out')}
                            >
                                지출 미분류 전체 선택
                            </Button>

                            <span>
                선택한 미분류 {bulkTargetRows.length}건
              </span>
                        </InlineActions>

                        <InlineActions style={{ marginTop: 12 }}>
                            <select
                                value={validBulkCategory}
                                disabled={busy || bulkTargetRows.length === 0}
                                aria-label="일괄 적용할 카테고리"
                                onChange={(event) => setBulkCategory(event.target.value)}
                            >
                                <option value="">
                                    일괄 적용할 카테고리
                                </option>

                                {bulkCategories.map((item) => (
                                    <option key={item.value} value={item.value}>
                                        {item.label}
                                    </option>
                                ))}
                            </select>

                            <Button
                                type="button"
                                disabled={
                                    busy
                                    || bulkTargetRows.length === 0
                                    || !validBulkCategory
                                }
                                onClick={handleBulkCategoryApply}
                            >
                                선택 내역 일괄 적용
                            </Button>
                        </InlineActions>

                        <Notice>
                            분류 완료된 내역은 변경하지 않습니다.
                            수입·지출을 함께 선택하면 공통 카테고리만 표시합니다.
                        </Notice>
                    </BulkArea>

                    <TableArea>
                        <table>
                            <thead>
                            <tr>
                                <th>
                                    <input
                                        type="checkbox"
                                        aria-label="현재 목록 전체 선택"
                                        checked={allChecked}
                                        disabled={busy || selectableRows.length === 0}
                                        onChange={(event) => handleCheckAll(
                                            event.target.checked,
                                        )}
                                    />
                                </th>
                                <th>구분</th>
                                <th>기간</th>
                                <th>설명</th>
                                <th className="amount">금액</th>
                                <th>카테고리</th>
                                <th>삭제</th>
                            </tr>
                            </thead>

                            <tbody>
                            {visibleRows.map((row) => {
                                const removing = deletedSet.has(row.key);
                                const editing = editingSet.has(row.key);
                                const editable = isUnclassified(row.category);

                                return (
                                    <ReviewRow key={row.key} $deleted={removing}>
                                        <td>
                                            <input
                                                type="checkbox"
                                                checked={checkedSet.has(row.key)}
                                                disabled={busy || removing}
                                                aria-label={`${row.description} 선택`}
                                                onChange={(event) => handleCheck(
                                                    row.key,
                                                    event.target.checked,
                                                )}
                                            />
                                        </td>

                                        <td>{kindLabel(row.kind)}</td>
                                        <td>{row.period}</td>

                                        <td className="description">
                                            {row.description}

                                            {row.evidence && (
                                                <div>
                                                    <Badge>공제대상금액</Badge>
                                                </div>
                                            )}

                                            {removing && (
                                                <div>
                                                    <Badge>이번 저장에서 제외</Badge>
                                                </div>
                                            )}
                                        </td>

                                        <td className="amount">
                                            {money(row.amount)}
                                        </td>

                                        <td>
                                            {!editable ? (
                                                <Badge>
                                                    {categoryLabel(row.category)}
                                                </Badge>
                                            ) : editing ? (
                                                <InlineActions>
                                                    <select
                                                        disabled={busy || removing}
                                                        value={
                                                            categoryDrafts[row.key]
                                                            ?? 'UNCLASSIFIED'
                                                        }
                                                        aria-label={`${row.description} 카테고리`}
                                                        onChange={(event) => {
                                                            const category = event.target.value;

                                                            setCategoryDrafts((previous) => ({
                                                                ...previous,
                                                                [row.key]: category,
                                                            }));
                                                        }}
                                                    >
                                                        <option value="UNCLASSIFIED">
                                                            미분류 유지
                                                        </option>

                                                        {categoriesFor(row.kind).map((item) => (
                                                            <option
                                                                key={item.value}
                                                                value={item.value}
                                                            >
                                                                {item.label}
                                                            </option>
                                                        ))}
                                                    </select>

                                                    <Button
                                                        type="button"
                                                        size="sm"
                                                        disabled={busy || removing}
                                                        onClick={() => handleCancelCategoryEdit(row.key)}
                                                    >
                                                        수정 취소
                                                    </Button>
                                                </InlineActions>
                                            ) : (
                                                <InlineActions>
                                                    <Badge $unclassified>
                                                        미분류
                                                    </Badge>

                                                    <Button
                                                        type="button"
                                                        size="sm"
                                                        disabled={busy || removing}
                                                        onClick={() => handleStartCategoryEdit(row)}
                                                    >
                                                        카테고리 수정
                                                    </Button>
                                                </InlineActions>
                                            )}
                                        </td>

                                        <td>
                                            <Button
                                                type="button"
                                                size="sm"
                                                disabled={busy}
                                                onClick={() => handleToggleDelete(row.key)}
                                            >
                                                {removing ? '삭제 취소' : '삭제'}
                                            </Button>
                                        </td>
                                    </ReviewRow>
                                );
                            })}
                            </tbody>
                        </table>
                    </TableArea>

                    {visibleRows.length === 0 && (
                        <Notice>표시할 내역이 없습니다.</Notice>
                    )}

                    <Footer>
            <span>
              카테고리 변경 {changeCount}건
                {' · '}제외 예정 {deleteCount}건
            </span>

                        <InlineActions>
                            <Button
                                type="button"
                                disabled={busy}
                                onClick={handleCancel}
                            >
                                취소
                            </Button>

                            <Button
                                type="button"
                                variant="primary"
                                disabled={busy}
                                onClick={handleDecide}
                            >
                                {busy ? 'DB 저장 중…' : '결정'}
                            </Button>
                        </InlineActions>
                    </Footer>
                </Dialog>,
                document.body,
            )}
        </Card>
    );
}