// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import SimSliders from '../components/calculator/SimSliders';
import SimCompare from '../components/calculator/SimCompare';
import NextMonthForecast from '../components/calculator/NextMonthForecast';

export default function CalcSimPage() {
  return (
    <div className="grid g-side">
      <div className="grid" style={{ minWidth: 0 }}>
        <NextMonthForecast />
        <SimSliders />
      </div>
      <SimCompare />
    </div>
  );
}
