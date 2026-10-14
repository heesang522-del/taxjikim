// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Pill } from '../common/index';
import { ROUTES } from '../../utils/routes';

const TABS = [
  { to: ROUTES.calcInput, label: '1. 수입/지출 입력 및 업로드' },
  { to: ROUTES.calcResult, label: '2. 세액 산출 결과' },
  { to: ROUTES.calcSim, label: '3. 시뮬레이션' },
];

/** 세금 계산 상단 탭. 로딩 화면은 '결과' 탭이 선택된 상태로 보여준다. */
export default function CalcLayout() {
  const { pathname } = useLocation();
  return (
    <>
      <div className="calc-head">
        <div className="subtabs">
          {TABS.map((t) => {
            const active = pathname === t.to || (t.to === ROUTES.calcResult && pathname === ROUTES.calcLoading);
            return <NavLink key={t.to} to={t.to} className={active ? 'active' : ''}>{t.label}</NavLink>;
          })}
        </div>
        <Pill>예시 데이터</Pill>
      </div>
      <Outlet />
    </>
  );
}
