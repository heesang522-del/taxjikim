// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card, SelectField } from '../common/index';
import { INCOME_TYPES } from '../../data/options';
import { useTax } from '../../store/useTaxStore';

export default function ProfileForm() {
  const { profile, updateProfile } = useTax();
  return (
    <Card title="기본 정보" icon="user">
      <div className="row">
        <SelectField id="type" label="소득 유형" options={INCOME_TYPES} value={profile.type} onChange={(e) => updateProfile({ type: e.target.value })} />
      </div>
      <label className="toggle" style={{ marginTop: 14 }}>
        <input type="checkbox" id="auto33" checked={profile.auto33} onChange={(e) => updateProfile({ auto33: e.target.checked })} />
        <span>
          <b>입금액 기준으로 입력 (3.3% 원천징수 자동 계산)</b><br />
          <span className="small muted">입금액을 적으면 세전 수입과 이미 낸 세금(3%)을 자동으로 계산합니다.</span>
        </span>
      </label>
    </Card>
  );
}
