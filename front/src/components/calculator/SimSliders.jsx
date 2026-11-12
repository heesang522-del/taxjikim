// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card } from '../common/index';
import { useScenarios } from '../../utils/useScenarios';
import { man } from '../../utils/format';
import { useToast } from '../../stores/useToastStore';
import { MAX_SCENARIOS } from '../../data/options';

const MAX_EXTRA_AMOUNT = 2_000_000_000_000;

const FIELDS = [
  { key: 'extraInc', label: '연말까지 추가 수입', hint: '기존 예상 금액보다 추가로 생기는 세전 수입' },
  { key: 'extraExp', label: '연말까지 추가 사업 지출', hint: '장비, 교육, 소프트웨어 등 증빙 가능한 경비' },
  { key: 'extraDonation', label: '추가 기부금', hint: '기존 일반기부금에 더하며, 계산 한도 안의 금액만 반영합니다.' },
];

export function SimSliders({taxInputId, sim, patchSim, resetSim, scenario, predictedIncome, predictedExpense, disabled = false,}) {
  const { save, isSaving, isLoading: isScenarioLoading } = useScenarios(taxInputId);
  const toast = useToast();
  const onSave = async () => {
    const r = await save({
      taxInputId,
      extraInc: sim.extraInc,
      extraExp: sim.extraExp,
      extraDonation: sim.extraDonation ?? 0,
      totalTax: scenario?.totalTax,
      totalIncome: scenario?.totalIncome,
      totalExpense: scenario?.totalExpense,
    });
    toast(
      {
        ok: '현재 분석의 시나리오를 저장했습니다',
        login: '시나리오 저장은 로그인 후 이용할 수 있습니다',
        max: `분석별 시나리오는 최대 ${MAX_SCENARIOS}개까지 저장됩니다. 아래 목록에서 삭제 후 저장하세요`,
        error: '시나리오를 DB에 저장하지 못했습니다',
        busy: '시나리오를 저장 중입니다',
      }[r]
    );
  };
  const handleAmountChange = (key) => (e) => {
    const digits = e.target.value.replace(/\D/g, '');
    // 만원으로 입력받고 기존 상태·서버 계산에 사용하는 원 단위로 변환한다.
    const amount = digits ? Number(digits) * 10_000 : 0;
    // 직접 입력과 붙여넣기 모두 기존 2조 원 상한을 적용한다.
    patchSim({ [key]: Math.min(amount, MAX_EXTRA_AMOUNT) });
    if (amount > MAX_EXTRA_AMOUNT) toast('추가 금액은 항목별 최대 200,000,000만원까지 입력할 수 있습니다');
  };
  return (
    <Card title="연말까지 이렇게 하면?" icon="sliders">
      {FIELDS.map((s) => (
        <div className="slider" key={s.key}>
          <div className="top">
            <label htmlFor={`s_${s.key}`}>
              <b>{s.label}</b>
            </label>
            <span className="num">{man(sim[s.key])}</span>
          </div>
          {/* 기존 예상액을 해당 입력 바로 위에 표시해 추가 가정과 구분한다. */}
          {s.key === 'extraInc' && Number.isFinite(predictedIncome) && (
            <p className="small muted">예상 수입 {man(predictedIncome)}</p>
          )}
          {s.key === 'extraExp' && Number.isFinite(predictedExpense) && (
            <p className="small muted">예상 지출 {man(predictedExpense)}</p>
          )}
          <input
            id={`s_${s.key}`}
            className="in"
            inputMode="numeric"
            placeholder="0 (만원)"
            aria-label={`${s.label} (만원)`}
            value={sim[s.key] ? sim[s.key] / 10_000 : ''}
            onChange={handleAmountChange(s.key)}
          />
          <span className="small muted">{s.hint} (단위: 만원, 최대 200,000,000만원)</span>
        </div>
      ))}
      <div className="row" style={{ marginTop: 12 }}>
        <Button onClick={resetSim}>초기화</Button>
        <Button variant="primary" onClick={onSave} disabled={disabled || !scenario || isSaving || isScenarioLoading}>
          {isSaving ? '저장 중…' : '이 시나리오 저장'}
        </Button>
      </div>
    </Card>
  );
}
