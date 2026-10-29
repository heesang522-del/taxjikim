// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Navigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { getMonthlyIncomes, getMonthlyExpenses } from '../api/taxApi';
import { Button, Icon, Note } from '../components/common/index';
import { SummaryCards } from '../components/calculator/SummaryCards';
import { TaxChain } from '../components/calculator/TaxChain';
import { MonthlyChart } from '../components/calculator/MonthlyChart';
import RegistrationCompare from '../components/calculator/RegistrationCompare';
import ActionList from '../components/calculator/ActionList';
import { ROUTES } from '../utils/routes';
import { YEAR } from '../data/taxConstants';
import { useTax } from '../store/useTaxStore';
import { useAuth } from '../store/useAuthStore';
import { useToast } from '../store/useToastStore';
import { useTaxResult, useSimpleResult } from '../utils/useTaxResult';
import { useHistory } from '../utils/useHistory';

const TOTAL_TAX_OPTIONS = { excludeWithholding: true };
const YELLOW_EXAMPLE = { ...TOTAL_TAX_OPTIONS, yellow: 3e6 };
const EXPENSE_EXAMPLE = { ...TOTAL_TAX_OPTIONS, extraExp: 1e6 };

export default function CalcResultPage() {
  // 입력창의 분석번호 전달을 연결할 때 이 테스트 번호를 교체한다.
  const taxInputId = 19;
  const { calculated } = useTax();
  const { loggedIn } = useAuth();
  const toast = useToast();
  // 결과 표시·저장·비교에 동일한 기납부세액 차감 전 기준을 적용한다.
  const c = useTaxResult(TOTAL_TAX_OPTIONS);
  const withYellow = useTaxResult(YELLOW_EXAMPLE);
  const withExpense = useTaxResult(EXPENSE_EXAMPLE);
  const simple = useSimpleResult(TOTAL_TAX_OPTIONS);
  const { save } = useHistory();
  const [monthlyData, setMonthlyData] = useState({ incomes: [], expenses: [] });
  const [monthlyLoading, setMonthlyLoading] = useState(true);
  const [monthlyError, setMonthlyError] = useState('');

  // Spring에서 받은 월별 목록을 그래프에 전달한다. 금액과 예상 여부는 서버 계산값을 사용한다.
  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    async function loadMonthlyData() {
      try {
        const [incomeResult, expenseResult] = await Promise.all([
          getMonthlyIncomes(taxInputId, { signal: controller.signal }),
          getMonthlyExpenses(taxInputId, { signal: controller.signal }),
        ]);
        if (!cancelled) setMonthlyData({ incomes: incomeResult.data, expenses: expenseResult.data });
      } catch (error) {
        if (!cancelled) setMonthlyError(error.message || '월별 데이터를 불러오지 못했습니다.');
      } finally {
        if (!cancelled) setMonthlyLoading(false);
      }
    }
    loadMonthlyData();
    // 화면을 떠난 뒤 도착한 응답으로 상태를 변경하지 않는다.
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [taxInputId]);

  const saveResult = () => {
    if (!loggedIn) {
      toast('저장하려면 로그인하세요');
      return;
    }
    const ok = save({
      year: YEAR,
      income: Math.round(c.income),
      expense: Math.round(c.expense),
      total: Math.round(c.total),
    });
    toast(ok ? `${YEAR}년 결과를 마이페이지에 저장했습니다` : '저장하지 못했습니다. 브라우저 저장 공간을 확인하세요');
  };

  if (!calculated) return <Navigate to={ROUTES.calcLoading} replace />;

  return (
    <div className="grid">
      <p className="hint">
        총 세금은 기납부세액을 차감하기 전 소득세와 지방소득세의 합계이며, 추가 납부액이나 환급액이 아닙니다.
        세율·공제는 예시값이며 실제 세액은 신고 시 달라질 수 있습니다.
      </p>
      <SummaryCards result={c} />
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
        <TaxChain result={c} />
        <MonthlyChart
          incomes={monthlyData.incomes}
          expenses={monthlyData.expenses}
          isLoading={monthlyLoading}
          errorMessage={monthlyError}
        />
      </div>
      <RegistrationCompare netNow={c.total} netSimple={simple.total} />
      <ActionList gainYellow={c.total - withYellow.total} gainExpense={c.total - withExpense.total} />
      <Note title="안내">
        이 결과는 입력값과 예시 세율로 계산한 참고용 추정치이며 세무 조언이 아닙니다. 실제 신고 전에 국세청 자료를
        확인하거나 세무사와 상담하세요.
      </Note>
    </div>
  );
}
