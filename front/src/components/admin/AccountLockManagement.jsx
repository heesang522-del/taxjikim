import { useEffect, useState } from 'react';
import { getLockedAccounts, unlockAccount } from '../../api/authApi.js';
import { Button, Card, DataTable, Pill } from '../common/index';

export function AccountLockManagement() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const [message, setMessage] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    // 기존 회원 관리 위치에서 실제 BANNED·LOCKED 계정만 관리한다.
    getLockedAccounts()
      .then((result) => {
        if (active) setAccounts(result.data.filter((account) => ['BANNED', 'LOCKED'].includes(account.userStatus)));
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
    if (!window.confirm(`${account.loginId} 계정의 제한을 해제하고 실패 기록을 초기화할까요?`)) return;
    setPendingId(account.userId);
    setError('');
    setMessage('');
    try {
      // 서버 해제 성공 후에만 목록을 갱신해 mock 해제처럼 보이지 않도록 한다.
      await unlockAccount(account.loginId);
      setMessage('제한을 해제하고 로그인 실패 기록을 초기화했습니다.');
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
        <p role="status">잠금 계정을 불러오는 중입니다.</p>
      ) : accounts.length === 0 ? (
        <p>잠금·이용 정지 계정이 없습니다.</p>
      ) : (
        <DataTable
          columns={[
            { key: 'loginId', header: '아이디', render: (account) => account.loginId },
            {
              key: 'status',
              header: '상태',
              align: 'right',
              render: (account) => (
                <Pill tone={account.userStatus === 'BANNED' ? 'danger' : 'warn'}>
                  {account.userStatus === 'BANNED' ? '이용 정지 (BANNED)' : '로그인 잠금 (LOCKED)'}
                </Pill>
              ),
            },
            {
              key: 'action',
              header: '',
              align: 'right',
              render: (account) => (
                <Button size="sm" variant="soft" onClick={() => handleUnlock(account)} disabled={pendingId !== null}>
                  {pendingId === account.userId
                    ? '해제 중...'
                    : account.userStatus === 'BANNED'
                      ? '정지 해제'
                      : '잠금 해제'}
                </Button>
              ),
            },
          ]}
          rows={accounts}
          rowKey={(account) => account.userId}
        />
      )}
    </Card>
  );
}
