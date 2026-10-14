// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Navigate } from 'react-router-dom';
import { Button, Icon, Note } from '../components/common/index';
import SummaryCards from '../components/calculator/SummaryCards';
import TaxChain from '../components/calculator/TaxChain';
import MonthlyChart from '../components/calculator/MonthlyChart';
import RegistrationCompare from '../components/calculator/RegistrationCompare';
import ActionList from '../components/calculator/ActionList';
import { ROUTES } from '../utils/routes';
import { YEAR } from '../data/taxConstants';
import { useTax } from '../store/useTaxStore';
import { useAuth } from '../store/useAuthStore';
import { useToast } from '../store/useToastStore';
import { useTaxResult, useSimpleResult } from '../utils/useTaxResult';
import { useHistory } from '../utils/useHistory';

const YELLOW_EXAMPLE = { yellow: 3e6 };
const EXPENSE_EXAMPLE = { extraExp: 1e6 };

export default function CalcResultPage() {
  const { calculated } = useTax();
  const { loggedIn } = useAuth();
  const toast = useToast();
  const c = useTaxResult();
  const withYellow = useTaxResult(YELLOW_EXAMPLE);
  const withExpense = useTaxResult(EXPENSE_EXAMPLE);
  const simple = useSimpleResult();
  const { save } = useHistory();

  const saveResult = () => {
    if (!loggedIn) { toast('저장하려면 로그인하세요'); return; }
    const ok = save({ year: YEAR, income: Math.round(c.income), expense: Math.round(c.expense), total: Math.round(c.total) });
    toast(ok ? `${YEAR}년 결과를 마이페이지에 저장했습니다` : '저장하지 못했습니다. 브라우저 저장 공간을 확인하세요');
  };

  if (!calculated) return <Navigate to={ROUTES.calcLoading} replace />;

  return (
    <div className="grid">
      <p className="hint">지방소득세(소득세의 10%)를 포함한 금액입니다. 세율·공제는 예시값이며 실제 세액은 신고 시 달라질 수 있습니다.</p>
      <SummaryCards result={c} />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        {!loggedIn && <span className="small muted" style={{ alignSelf: 'center' }}>결과 저장은 로그인 후 이용할 수 있어요.</span>}
        {!loggedIn && <Button variant="soft" to={ROUTES.auth}>로그인</Button>}
        <Button onClick={saveResult}>결과 저장</Button>
        <Button variant="primary" to={ROUTES.calcSim}><Icon name="sliders" size={16} /> 시뮬레이션으로 줄여보기</Button>
      </div>
      <div className="grid g2">
        <TaxChain result={c} />
        <MonthlyChart monthly={c.monthly} />
      </div>
      <RegistrationCompare netNow={c.total} netSimple={simple.total} />
      <ActionList gainYellow={c.total - withYellow.total} gainExpense={c.total - withExpense.total} />
      <Note title="안내">이 결과는 입력값과 예시 세율로 계산한 참고용 추정치이며 세무 조언이 아닙니다. 실제 신고 전에 국세청 자료를 확인하거나 세무사와 상담하세요.</Note>
    </div>
  );
}
