'use no memo';

import { useRef, useState } from 'react';
import { Button, Card, KeyValue } from '../common/index';
import { getTaxReviewSignature, updateTaxInput } from '../../api/taxApi';
import { useTax, useTaxStore } from '../../store/useTaxStore';
import { getTaxInputSummary } from '../../utils/taxInputSummary';

function money(value) {
  return `${Number(value ?? 0).toLocaleString('ko-KR')}원`;
}

function dateText(value) {
  if (Array.isArray(value) && value.length === 3) {
    return value.map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
  }

  return String(value ?? '').trim();
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

function getScreenRows(state) {
  return [...(state.manualRows ?? []), ...(state.fileRows ?? [])];
}

function hasPendingTransactions(state) {
  return getScreenRows(state).some(
    (row) => row.transactionId == null && row.needsReview !== true && row.excluded !== true && row.selected !== false
  );
}

function validId(value) {
  return Number.isSafeInteger(value) && value > 0;
}

// DB에 저장할 기본정보를 정리한다.
function profileValues(profile) {
  return {
    businessRegistered: profile.businessRegistered ?? null,
    dependentCount: optionalInteger(profile.dependentCount, '공제 대상 가족 수', 0),
    generalDonationAmount: optionalNumber(profile.generalDonationAmount, '일반기부금', 0),
    specialDonationAmount: optionalNumber(profile.specialDonationAmount, '특례기부금', 0),
  };
}

function validateProfile(profile) {
  if (typeof profile.businessRegistered !== 'boolean') {
    throw new Error('사업자등록 여부를 선택해주세요.');
  }

  return profileValues(profile);
}

export function SummaryRail({ onCalculate }) {
  const state = useTaxStore();
  const { rows } = useTax();

  const movingRef = useRef(false);
  const [moving, setMoving] = useState(false);
  const [error, setError] = useState('');

  const saving = state.taxSaveBusy === true;
  const busy = saving || moving;

  const transactionRows = rows.filter(
    (row) => row.excluded !== true && row.needsReview !== true && row.amountBasis !== 'DEDUCTION_ELIGIBLE'
  );

  const { income, expense, unclassified } = getTaxInputSummary(transactionRows);

  // 저장 상태와 기본정보를 확인한 뒤 결과 화면으로 이동한다.
  async function handleCalculate() {
    const initial = useTaxStore.getState();

    if (initial.taxSaveBusy === true || movingRef.current) {
      return;
    }

    setError('');

    let desiredProfile;

    try {
      if (hasPendingTransactions(initial)) {
        throw new Error('내역 확인·수정에서 미저장 거래의 저장 버튼을 눌러주세요.');
      }

      desiredProfile = validateProfile(initial.profile ?? {});

      if (typeof onCalculate !== 'function') {
        throw new Error('분석 결과 보기 기능이 연결되지 않았습니다.');
      }
    } catch (cause) {
      setError(cause?.message ?? '입력 내용을 확인해주세요.');
      return;
    }

    const savedScreenRows = getScreenRows(initial).filter((row) => row.transactionId != null);

    if (savedScreenRows.length === 0) {
      setError('분석할 저장 거래가 없습니다.');
      return;
    }

    // 여러 분석의 거래가 함께 있으면 모든 분석을 확인한다.
    const inputIds = new Set(savedScreenRows.map((row) => row.taxInputId));

    for (const snapshot of initial.taxSavedSnapshots ?? []) {
      inputIds.add(snapshot.taxInputId);
    }

    if ([...inputIds].some((id) => !validId(id))) {
      setError('저장된 분석번호를 확인하지 못했습니다.');
      return;
    }

    // 결과 화면은 한 번에 분석 한 건을 조회한다.
    if (inputIds.size !== 1) {
      setError('거래가 분석 한 건에 속할 때 결과를 볼 수 있습니다.');
      return;
    }
    const [taxInputId] = inputIds;

    // 요청 도중 화면 입력이 변경되는지 확인한다.
    const initialSignature = getTaxReviewSignature(initial);

    movingRef.current = true;
    setMoving(true);
    useTaxStore.setState({ taxSaveBusy: true });

    try {
      const savedAnalysis = (initial.taxSavedSnapshots ?? []).find((snapshot) => snapshot.taxInputId === taxInputId);
      const taxYear = savedAnalysis?.taxYear ?? Number(dateText(savedScreenRows[0].date).slice(0, 4));

      // 저장한 임시 분석에 현재 기본정보를 반영한 뒤 결과 화면으로 이동한다.
      await updateTaxInput(taxInputId, {
        ...desiredProfile,
        taxYear,
        saveStatus: 'TEMP',
      });

      if (getTaxReviewSignature(useTaxStore.getState()) !== initialSignature) {
        throw new Error('저장 중 입력 내용이 변경됐습니다. 다시 분석 결과 보기를 눌러주세요.');
      }

      // 기본정보 저장이 성공한 상태를 기록한다.
      useTaxStore.setState((current) => ({
        taxLastSavedSignature: getTaxReviewSignature(current),
      }));

      // 후속 화면에서 거래 처리 중으로 판단하지 않도록 해제한다.
      useTaxStore.setState({ taxSaveBusy: false });

      await onCalculate(taxInputId);
    } catch (cause) {
      setError(cause?.message ?? '저장 상태를 확인하지 못했습니다.');
    } finally {
      movingRef.current = false;
      setMoving(false);
      useTaxStore.setState({ taxSaveBusy: false });
    }
  }

  return (
    <Card as="aside" className="rail tax-summary-rail" title="거래 합계">
      <KeyValue label="합계 대상 거래">{transactionRows.length}건</KeyValue>

      <KeyValue label="수입 합계">{money(income)}</KeyValue>
      <KeyValue label="지출 합계">{money(expense)}</KeyValue>
      <KeyValue label="미분류 합계">{money(unclassified)}</KeyValue>

      <KeyValue label="수입·지출 차액" total>
        {money(income - expense)}
      </KeyValue>

      <p className="small muted tax-notice">
        검토 대상과 제외한 거래는 합계에 포함하지 않습니다. 거래 수정·삭제는 내역 확인·수정에서 반영해주세요. 분석 결과
        보기에서는 저장 상태를 확인하고 변경된 기본정보를 기존 분석에 저장합니다.
      </p>

      <Button type="button" variant="primary" block disabled={busy} onClick={handleCalculate}>
        {moving ? '저장 상태 확인 중…' : saving ? '거래 처리 중…' : '분석 결과 보기'}
      </Button>

      <div className="tax-status" role="status" aria-live="polite" aria-atomic="true">
        {error && <p role="alert">{error}</p>}
      </div>
    </Card>
  );
}
