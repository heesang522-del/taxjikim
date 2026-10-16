// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Icon } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { useTax } from '../store/useTaxStore';

const STEPS = ['월별 수입·지출 집계', '연말까지 10~12월 예측', '소득금액·과세표준 계산', '세율 적용과 세액공제', '신고 방식별 비교 계산'];

export default function CalcLoadingPage() {
  const navigate = useNavigate();
  const { setCalculated } = useTax();
  const [done, setDone] = useState(0);

  useEffect(() => {
    if (done >= STEPS.length) { setCalculated(true); navigate(ROUTES.calcResult, { replace: true }); return; }
    const t = setTimeout(() => setDone((d) => d + 1), done === 0 ? 650 : 420);
    return () => clearTimeout(t);
  }, [done, navigate, setCalculated]);

  return (
    <Card className="load">
      <div className="spinner"><Icon name="loader" size={32} /></div>
      <div>
        <h3 style={{ fontSize: 20, fontWeight: 700, marginBottom: 8 }}>입력한 거래 내역을 분석하고 있습니다...</h3>
        <p className="small muted">필요경비 산정, 예상 종합소득세 계산 중</p>
      </div>
      <div className="track"><i style={{ width: `${(done / STEPS.length) * 100}%` }} /></div>
      <div className="ls">{STEPS.map((s, i) => <div key={s} className={i < done ? 'done' : ''}>{s}</div>)}</div>
    </Card>
  );
}
