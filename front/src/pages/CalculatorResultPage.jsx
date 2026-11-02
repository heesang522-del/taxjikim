// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Navigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { getTaxBreakdown } from '../api/taxApi';
import { Button, Icon, Note } from '../components/common/index';
import { SummaryCards } from '../components/calculator/result/SummaryCards';
import { TaxChain } from '../components/calculator/result/TaxChain';
import { MonthlyChart } from '../components/calculator/result/MonthlyChart';
import RegistrationCompare from '../components/calculator/result/RegistrationCompare';
import { ROUTES } from '../utils/routes';
import { useTax } from '../store/useTaxStore';
import { useAuth } from '../store/useAuthStore';
import { useToast } from '../store/useToastStore';
import { useTaxResult, useSimpleResult } from '../utils/useTaxResult';
import { useHistory } from '../utils/useHistory';

const TOTAL_TAX_OPTIONS = { excludeWithholding: true };

export default function CalcResultPage() {
  // TODO: 입력창의 분석번호 전달을 연결할 때 이 테스트 번호를 교체한다.
  const taxInputId = 21;
  const { calculated } = useTax();
  const [breakdown, setBreakdown] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  // 같은 분석의 결과를 한 번 조회하고 요약 카드와 산출 근거에 같은 값을 전달한다.
  useEffect(() => {
    if (!calculated) return;
    let cancelled = false;
    const controller = new AbortController();

    // 서버의 계산 결과를 받아 표시하며 기존 예시 계산값으로 대체하지 않는다.
    async function loadBreakdown() {
      setIsLoading(true);
      setErrorMessage('');
      setBreakdown(null);
      try {
        const response = await getTaxBreakdown(taxInputId, { signal: controller.signal });
        if (!cancelled) setBreakdown(response.data);
      } catch (error) {
        if (!cancelled) setErrorMessage(error.message || '계산 결과를 불러오지 못했습니다.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }
    loadBreakdown();
    // 화면 이탈·분석번호 변경 후 도착한 이전 응답은 반영하지 않는다.
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [calculated, taxInputId]);
  const { loggedIn } = useAuth();
  const toast = useToast();
  // 사업자등록 비교는 기존 예시 계산을 유지하며 서버 계산 결과와 구분한다.
  const c = useTaxResult(TOTAL_TAX_OPTIONS);
  const simple = useSimpleResult(TOTAL_TAX_OPTIONS);
  const { save } = useHistory();
  const saveResult = () => {
    if (!loggedIn) {
      toast('저장하려면 로그인하세요');
      return;
    }
    if (!breakdown || isLoading || errorMessage) {
      toast('계산 결과를 확인한 뒤 저장해주세요');
      return;
    }
    const ok = save({
      year: breakdown.taxYear,
      income: breakdown.totalIncome,
      expense: breakdown.totalExpense,
      total: breakdown.totalTax,
    });
    toast(
      ok
        ? `${breakdown.taxYear}년 결과를 마이페이지에 저장했습니다`
        : '저장하지 못했습니다. 브라우저 저장 공간을 확인하세요'
    );
  };

  if (!calculated) return <Navigate to={ROUTES.calcLoading} replace />;

  return (
    <div className="grid">
      <p className="hint">
        총 세금은 기납부세액을 차감하기 전 소득세와 지방소득세의 합계이며, 추가 납부액이나 환급액이 아닙니다. 입력
        조건과 단순화 가정을 적용한 예상 결과이며 실제 신고 세액과 다를 수 있습니다.
      </p>
      <SummaryCards result={breakdown} isLoading={isLoading} errorMessage={errorMessage} />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        {!loggedIn && (
          <span className="small muted" style={{ alignSelf: 'center' }}>
            결과 저장은 로그인 후 이용할 수 있어요.
          </span>
        )}
        {!loggedIn && (
          <Button variant="soft" to={ROUTES.auth}>
            로그인
          </Button>
        )}
        <Button onClick={saveResult}>결과 저장</Button>
        <Button variant="primary" to={ROUTES.calcSim}>
          <Icon name="sliders" size={16} /> 시뮬레이션으로 줄여보기
        </Button>
      </div>
      <div className="grid g2">
        <TaxChain result={breakdown} isLoading={isLoading} errorMessage={errorMessage} />
        <MonthlyChart taxInputId={taxInputId} />
      </div>
      <div>
        <p className="hint">아래 사업자등록 비교는 예시입니다.</p>
        <RegistrationCompare netNow={c.total} netSimple={simple.total} />
      </div>
      <Note title="안내">
        이 결과는 입력값과 계산 가정에 따른 참고용 추정치이며 세무 조언이 아닙니다. 실제 신고 전에 국세청 자료를
        확인하거나 세무사와 상담하세요.
      </Note>
    </div>
  );
}
