import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { getLockedAccounts, unlockAccount } from '../../api/authApi.js';
import { Button, Card, DataTable, Pill } from '../common/index';

const ACCOUNT_STATUSES = {
  LOCKED: { label: '로그인 잠금 (LOCKED)', action: '잠금 해제' },
  BANNED: { label: '이용 정지 (BANNED)', action: '정지 해제' },
  DORMANT: { label: '휴면 (DORMANT)', action: '휴면 해제' },
};

const statusTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

function formatStatusTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '-' : statusTimeFormatter.format(date);
}

export function AccountLockManagement() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const [message, setMessage] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    // 실제 잠금·정지·휴면 계정을 같은 관리자 복구 경로에서 관리한다.
    getLockedAccounts()
      .then((result) => {
        if (active) setAccounts(result.data.filter((account) => Object.hasOwn(ACCOUNT_STATUSES, account.userStatus)));
      })
      .catch((cause) => {
        if (active) setError(cause.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reload]);

  const handleReload = () => {
    setLoading(true);
    setError('');
    setReload((value) => value + 1);
  };

  const handleUnlock = async (account) => {
    if (pendingId !== null) return;
    if (
      !window.confirm(
        `${account.loginId} 계정의 ${ACCOUNT_STATUSES[account.userStatus].action}를 진행하고 실패 기록을 초기화할까요?`
      )
    )
      return;
    setPendingId(account.userId);
    setError('');
    setMessage('');
    try {
      // 서버 해제 성공 후에만 목록을 갱신해 mock 해제처럼 보이지 않도록 한다.
      await unlockAccount(account.loginId);
      setMessage(`${ACCOUNT_STATUSES[account.userStatus].action}를 완료하고 로그인 실패 기록을 초기화했습니다.`);
      handleReload();
    } catch (cause) {
      setError(cause.message);
    } finally {
      setPendingId(null);
    }
  };

  return (
    <Card title="회원 관리" icon="users" aside={<Pill tone="neutral">{accounts.length}명</Pill>}>
      <Button size="sm" onClick={handleReload} disabled={loading || pendingId !== null}>
        목록 새로고침
      </Button>
      {error && (
        <p role="alert" className="small" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      )}
      {message && (
        <p role="status" className="small">
          {message}
        </p>
      )}
      {loading ? (
        <p role="status">잠금·이용 정지·휴면 계정을 불러오는 중입니다.</p>
      ) : accounts.length === 0 ? (
        <p>잠금·이용 정지·휴면 계정이 없습니다.</p>
      ) : (
        <MemberTable>
          <DataTable
            columns={[
              { key: 'loginId', header: '아이디', render: (account) => account.loginId },
              {
                key: 'status',
                header: '상태',
                render: (account) => (
                  <Pill tone={account.userStatus === 'BANNED' ? 'danger' : 'warn'}>
                    {ACCOUNT_STATUSES[account.userStatus].label}
                  </Pill>
                ),
              },
              {
                key: 'updatedAt',
                header: '상태 변경 시각',
                // 잠금·정지·휴면 회원의 UPDATED_AT을 한국 시간으로 표시한다.
                render: (account) => formatStatusTime(account.updatedAt),
              },
              {
                key: 'action',
                header: '해제',
                render: (account) => (
                  <Button size="sm" variant="soft" onClick={() => handleUnlock(account)} disabled={pendingId !== null}>
                    {pendingId === account.userId ? '해제 중...' : ACCOUNT_STATUSES[account.userStatus].action}
                  </Button>
                ),
              },
            ]}
            rows={accounts}
            rowKey={(account) => account.userId}
          />
        </MemberTable>
      )}
    </Card>
  );
}

const MemberTable = styled.div`
  margin-top: 12px;

  table {
    min-width: 640px;
    table-layout: fixed;
  }

  th,
  td {
    text-align: center;
    vertical-align: middle;
    white-space: nowrap;
  }

  th {
    width: 25%;
  }
`;
