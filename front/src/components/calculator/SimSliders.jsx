// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useScenarios } from '../../utils/useScenarios';
import { man, won } from '../../utils/format';
import { useTax } from '../../store/useTaxStore';
import { useToast } from '../../store/useToastStore';
import { MAX_SCENARIOS } from '../../data/options';

const FIELDS = [
  { key: 'extraInc', label: '연말까지 추가 수입', hint: '기존 예상 금액보다 추가로 생기는 세전 수입' },
  { key: 'extraExp', label: '연말까지 추가 사업 지출', hint: '장비, 교육, 소프트웨어 등 증빙 가능한 경비' },
  { key: 'extraDonation', label: '추가 기부금', hint: '기존 일반기부금에 더하며, 계산 한도 안의 금액만 반영합니다.' },
];

export function SimSliders({ scenario, predictedIncome, predictedExpense, disabled = false }) {
  const { sim, patchSim, resetSim } = useTax();
  const { loggedIn } = useAuth();
  const { save } = useScenarios();
  const toast = useToast();
  const onSave = () => {
    const r = save({ ...sim, totalTax: scenario?.totalTax });
    toast(
      {
        ok: '시나리오를 저장했습니다. 마이페이지에서 확인할 수 있어요',
        login: '시나리오 저장은 로그인 후 이용할 수 있습니다',
        max: `시나리오는 최대 ${MAX_SCENARIOS}개까지 저장됩니다. 마이페이지에서 삭제 후 저장하세요`,
        error: '저장하지 못했습니다. 브라우저 저장 공간을 확인하세요',
      }[r]
    );
  };
  const onChange = (key) => (e) => {
    const digits = e.target.value.replace(/\D/g, '');
    patchSim({ [key]: digits ? Number(digits) : 0 });
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
            <p className="small muted">예상 수입 {won(predictedIncome)}</p>
          )}
          {s.key === 'extraExp' && Number.isFinite(predictedExpense) && (
            <p className="small muted">예상 지출 {won(predictedExpense)}</p>
          )}
          <input
            id={`s_${s.key}`}
            className="in"
            inputMode="numeric"
            placeholder="0"
            value={sim[s.key] || ''}
            onChange={onChange(s.key)}
          />
          <span className="small muted">{s.hint}</span>
        </div>
      ))}
      <div className="row" style={{ marginTop: 12 }}>
        <Button onClick={resetSim}>초기화</Button>
        <Button variant="primary" onClick={onSave} disabled={disabled || !scenario}>
          이 시나리오 저장
        </Button>
      </div>
      {!loggedIn && (
        <p className="small muted" style={{ marginTop: 10 }}>
          계산과 비교는 로그인 없이 가능하고, 시나리오 저장만 회원 전용입니다.{' '}
          <Button variant="link" size="sm" to={ROUTES.auth}>
            로그인
          </Button>{' '}
          후에도 입력한 값은 그대로 유지됩니다.
        </p>
      )}
    </Card>
  );
}
