'use no memo';

import { Card, Segmented, TextField } from '../common/index';
import { useTax } from '../../stores/useTaxStore';
import { parseDependentCount, parseWon } from '../../utils/transactionAmount';
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
  .deductions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
  .deductions .field {
    min-width: 0;
  }
  .deductions input {
    box-sizing: border-box;
    min-width: 0;
    width: 100%;
  }
  .hint {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: var(--text-muted);
  }
  @media (max-width: 640px) {
    .profile-columns,
    .deductions {
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

const BUSINESS_STATUS = [
  { value: true, label: '등록' },
  { value: false, label: '미등록' },
];

export function ProfileForm() {
  const { profile, updateProfile } = useTax();
  const handleChange = (field, parseNumber) => (event) => {
    const value = event.target.value;
    const number = parseNumber(value);

    // 입력과 붙여넣기 모두 범위를 넘으면 이전 값을 유지하고, 앞자리 0은 제거한다.
    if (value === '' || number !== null) {
      updateProfile({ [field]: value === '' ? '' : String(number) });
    }
  };

  return (
    <ProfileSection>
      <Card>
        <div className="intro">
          <div>
            <h2>기본정보</h2>
            <p className="small muted">
              이 서비스는 부가가치세 면세 대상인 1인 미디어 창작자의 예상 소득세와 지방소득세를 계산합니다.
              사업자등록 여부는 결과 화면의 안내에만 사용됩니다.
            </p>
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
            <div style={{ marginTop: 20 }}>
              <TextField
                id="dependentCount"
                label="공제 대상 가족 수 (본인 제외)"
                inputMode="numeric"
                value={profile.dependentCount}
                onChange={handleChange('dependentCount', parseDependentCount)}
              />
              <p className="hint">공제 요건에 해당하는 가족만 입력하세요. 본인은 제외합니다.</p>
            </div>
          </div>
          <div className="profile-column deduction-column">
            <h3>
              기부금 <span className="small muted">선택 입력</span>
            </h3>
            {/* 기부금은 영수증 유형에 맞춰 나누고, 없으면 기본값 0원을 사용한다. */}
            <div className="deductions">
              <TextField
                id="specialDonationAmount"
                label="특례기부금 (원)"
                inputMode="numeric"
                value={profile.specialDonationAmount}
                onChange={handleChange('specialDonationAmount', parseWon)}
              />
              <TextField
                id="generalDonationAmount"
                label="일반기부금 (원)"
                inputMode="numeric"
                value={profile.generalDonationAmount}
                onChange={handleChange('generalDonationAmount', parseWon)}
              />
            </div>
            <p className="hint">
              기부금 영수증의 유형을 확인해 입력하세요. 일반기부금은 종교단체 외 기부금이며, 정치·종교 기부금은 지원하지
              않습니다.
            </p>
          </div>
        </div>
      </Card>
    </ProfileSection>
  );
}
