'use no memo';

import { Card, Segmented, TextField } from '../common/index';
import { useTax } from '../../store/useTaxStore';
import styled from 'styled-components';

const ProfileSection = styled.section`
  display: grid;
  gap: 20px;
  .intro {
    display: flex;
    justify-content: space-between;
    align-items: start;
    gap: 16px;
  }
  .intro h2 {
    margin: 0 0 6px;
    font-size: 18px;
    line-height: 1.4;
  }
  .year {
    white-space: nowrap;
    color: var(--text-muted);
    padding-top: 3px;
    font-size: 12px;
  }
  .question {
    margin-top: 0;
  }
  .profile-columns {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 24px;
    margin-top: 24px;
  }
  .profile-column {
    min-width: 0;
  }
  .deduction-column {
    border-left: 1px solid var(--border);
    padding-left: 24px;
  }
  .deduction-column h3 {
    font-size: 13px;
    font-weight: 600;
    margin: 0 0 12px;
  }
  .profile-columns input {
    min-height: 40px;
  }
  .question-title {
    display: block;
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 8px;
  }
  .seg {
    display: inline-flex;
    flex-wrap: wrap;
    gap: 4px;
    padding: 4px;
    background: var(--border-soft);
    border-radius: 10px;
  }
  .seg button {
    min-height: 34px;
    min-width: 84px;
    padding: 6px 16px;
    border: 1px solid transparent;
    border-radius: 7px;
    background: transparent;
    font-size: 14px;
  }
  .seg button[aria-pressed='true'] {
    border-color: var(--primary-border);
    background: white;
    color: var(--primary);
    box-shadow: none;
  }
  .followup {
    margin-top: 20px;
    padding-top: 18px;
    border-top: 1px solid var(--border-soft);
  }
  .followup .question {
    margin-top: 0;
  }
  .age-input {
    display: flex;
    align-items: end;
    gap: 8px;
  }
  .age-input .field {
    width: 140px;
  }
  .age-input > span {
    padding-bottom: 10px;
    font-size: 13px;
    color: var(--text-muted);
  }
  .age-hint {
    margin-top: 8px;
  }
  .deductions {
    display: grid;
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .deductions input {
    width: 100%;
  }
  .hint {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: var(--text-muted);
  }
  .notice {
    border-top: 1px solid var(--border);
    margin-top: 18px;
    padding-top: 12px;
  }
  @media (max-width: 640px) {
    .profile-columns {
      grid-template-columns: 1fr;
    }
    .deduction-column {
      border-left: 0;
      padding-left: 0;
      border-top: 1px solid var(--border);
      padding-top: 20px;
    }
  }
`;

const YES_NO = [
  { value: true, label: '예' },
  { value: false, label: '아니요' },
];
const BUSINESS_STATUS = [
  { value: true, label: '등록' },
  { value: false, label: '미등록' },
];

const BUSINESS_REGIONS = [
  { value: 'CAPITAL_CONGESTION', label: '수도권 과밀억제권역' },
  { value: 'CAPITAL_OTHER', label: '수도권 중 과밀억제권역 외' },
  { value: 'NON_CAPITAL', label: '비수도권' },
];

const RegionChoices = styled.div`
  margin-top: 18px;
  .seg {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
  .seg button {
    padding: 6px 10px;
    line-height: 1.5;
    white-space: normal;
  }
  @media (max-width: 640px) {
    .seg {
      flex-direction: column;
      align-items: stretch;
    }
  }
`;

export function ProfileForm() {
  const { profile, updateProfile } = useTax();
  const handleChange = (field) => (event) => updateProfile({ [field]: event.target.value });

  return (
    <ProfileSection>
      <Card>
        <div className="intro">
          <div>
            <h2>기본정보</h2>
            <p className="small muted">면세 1인 유튜버의 세금 분석에 필요한 정보예요.</p>
          </div>
          <span className="year">{profile.taxYear}년 기준</span>
        </div>
        <div className="profile-columns">
          <div className="profile-column">
            <div className="question">
              <span className="question-title">사업자등록 여부</span>
              <Segmented
                label="사업자등록 여부"
                options={BUSINESS_STATUS}
                value={profile.businessRegistered}
                onChange={(value) => updateProfile({ businessRegistered: value, auto33: false })}
              />
            </div>
            {/* 미등록자는 절세 안내용 나이만, 등록자는 감면 적용에 필요한 창업정보를 받는다. */}
            {profile.businessRegistered === false && (
              <div className="followup">
                <div className="age-input">
                  <TextField
                    id="age"
                    label="현재 만 나이"
                    type="number"
                    min="0"
                    step="1"
                    value={profile.age}
                    onChange={handleChange('age')}
                  />
                  <span>세</span>
                </div>
                <p className="small muted age-hint">사업자등록 관련 혜택 안내에 사용합니다.</p>
              </div>
            )}
            {profile.businessRegistered === true && (
              <div className="followup">
                <span className="question-title">올해 창업 세액감면을 적용받나요?</span>
                <Segmented
                  label="창업 세액감면 적용 여부"
                  options={YES_NO}
                  value={profile.startupReliefApplied}
                  onChange={(value) => updateProfile({ startupReliefApplied: value })}
                />
                {profile.startupReliefApplied === true && (
                  <RegionChoices>
                    <span className="question-title">창업 당시 사업장 지역</span>
                    <Segmented
                      label="창업 당시 사업장 지역"
                      options={BUSINESS_REGIONS}
                      value={profile.businessRegion}
                      onChange={(value) => updateProfile({ businessRegion: value })}
                    />
                  </RegionChoices>
                )}
              </div>
            )}
          </div>
          <div className="profile-column deduction-column">
            <h3>기본공제·기부금</h3>
            <div className="deductions">
              <div>
                <TextField
                  id="dependentCount"
                  label="공제 대상 가족 수 (본인 제외)"
                  type="number"
                  min="0"
                  step="1"
                  value={profile.dependentCount}
                  onChange={handleChange('dependentCount')}
                />
                <p className="hint">공제 요건에 해당하는 가족만 입력하세요. 본인은 제외합니다.</p>
              </div>
              <div>
                <TextField
                  id="donationAmount"
                  label="기부금 (원)"
                  type="number"
                  min="0"
                  step="1"
                  value={profile.donationAmount}
                  onChange={handleChange('donationAmount')}
                />
                <p className="hint">영수증이 있는 공제 대상 복지단체 기부금입니다. 정치·종교 기부금은 제외합니다.</p>
              </div>
            </div>
          </div>
        </div>
        <p className="hint notice">
          현재는 입력 화면 구성 단계입니다. 이 공제·감면 정보는 아직 결과 계산에 반영되지 않습니다.
        </p>
      </Card>
    </ProfileSection>
  );
}
