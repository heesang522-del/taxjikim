// 기존 결과 화면의 React Compiler 제외 설정을 유지한다.
'use no memo';

import { Card } from '../../common/index';
import { won } from '../../../utils/format';

// 서버가 계산한 각 단계의 금액을 원 단위로 표시하며 화면에서는 세금을 재계산하지 않는다.
export function TaxChain({ result, isLoading = false, errorMessage = '' }) {
  if (isLoading || errorMessage || !result) {
    return (
      <Card title="산출 근거" icon="fileText">
        <p role={errorMessage ? 'alert' : 'status'}>
          {isLoading ? '계산 결과를 불러오는 중입니다.' : errorMessage || '표시할 계산 결과가 없습니다.'}
        </p>
      </Card>
    );
  }

  // 음수 표시는 차감 항목을 뜻하며, 금액·한도·세율은 서버 응답을 그대로 사용한다.
  const steps = [
    ['총수입', '실제·예상 포함', result.totalIncome],
    ['총지출', '입력 지출 전액을 필요경비로 가정', -result.totalExpense],
    ['특례기부금 인정액', '한도 내 인정 금액', -result.specialDonationAllowed],
    ['일반기부금 인정액', '종교단체 제외 · 한도 내 인정 금액', -result.generalDonationAllowed],
    ['사업소득금액', '수입에서 지출·기부금 인정액 차감', result.incomeAfterDonation, true],
    ['기본공제', `본인 포함 ${result.dependentCount + 1}명`, -result.basicDeductionAmount],
    ['과세표준', '세율을 적용하는 기준 금액', result.taxBase, true],
    [
      '산출세액',
      `세율 ${Math.round(result.taxRate * 100)}% · 누진공제 ${won(result.progressiveDeduction)}`,
      result.assessedTax,
      true,
    ],
    ['표준세액공제', '일반 기준 7만원 · 산출세액 한도', -result.standardTaxCredit],
    ['소득세', '표준세액공제 반영 후 · 10원 미만 버림', result.incomeTax, true],
    ['지방소득세', '소득세의 10% · 10원 미만 버림', result.localIncomeTax],
    ['총세금', '소득세 + 지방소득세 · 기납부세액 차감 전', result.totalTax, true],
  ];
  return (
    <Card title="산출 근거" icon="fileText">
      <div className="chain">
        {steps.map(([name, sub, value, highlight]) => (
          <div key={name} className={`st ${highlight ? 'hl' : ''}`}>
            <div>
              <b>{name}</b>
              <small>{sub}</small>
            </div>
            <span className="num">
              {value < 0 ? '−' : ''}
              {won(Math.abs(value))}
            </span>
          </div>
        ))}
      </div>
      <p className="small muted">법정 성실사업자 요건을 충족하면 소득세가 최대 5만원 더 줄어들 수 있습니다.</p>
    </Card>
  );
}
