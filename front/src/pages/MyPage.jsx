// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { Button, Card, DataTable } from '../components/common/index';
import { getSavedTaxResults } from '../api/taxApi';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { ProfileSettings } from '../components/mypage/ProfileSettings';
import { AnnualTaxComparison } from '../components/mypage/AnnualTaxComparison';
import { useChat } from '../store/useChatStore';

export default function MyPage() {
  const { loggedIn, authStatus, userId } = useAuth();
  const { setOpen: openChat } = useChat();
  const [savedResults, setSavedResults] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  // 로그인한 회원의 목록만 조회하고, 화면을 나가면 이전 응답을 반영하지 않는다.
  useEffect(() => {
    if (!loggedIn || authStatus !== 'ready') return;
    const controller = new AbortController();
    async function loadSavedResults() {
      setIsLoading(true);
      setSavedResults([]);
      setErrorMessage('');
      try {
        const response = await getSavedTaxResults({ signal: controller.signal });
        if (!controller.signal.aborted) setSavedResults(response.data);
      } catch (error) {
        if (!controller.signal.aborted) setErrorMessage(error.message || '저장한 분석 결과를 불러오지 못했습니다.');
      } finally {
        if (!controller.signal.aborted) setIsLoading(false);
      }
    }
    loadSavedResults();
    return () => controller.abort();
  }, [loggedIn, authStatus, userId]);

  if (authStatus === 'checking') return <p className="state-msg">로그인 정보를 확인하고 있습니다.</p>;
  if (authStatus === 'error') return <p role="alert">로그인 정보를 확인하지 못했습니다. 새로고침해주세요.</p>;

  if (!loggedIn) {
    return (
      <Card className="" style={{ maxWidth: 480, margin: '40px auto', textAlign: 'center' }}>
        <h3>로그인이 필요합니다</h3>
        <p className="muted" style={{ marginBottom: 14 }}>
          로그인하면 저장한 분석 결과를 다시 확인할 수 있습니다.
        </p>
        <Button variant="primary" to={ROUTES.auth}>
          로그인
        </Button>
      </Card>
    );
  }

  const columns = [
    { key: 'year', header: '귀속연도', render: (row) => `${row.taxYear}년` },
    {
      key: 'savedAt',
      header: '저장일',
      render: (row) => new Date(row.savedAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' }),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (row) => (
        <div className="row" style={{ justifyContent: 'flex-end', gap: 24 }}>
          {/* 저장 결과 모드로 이동해 상세 화면에서 DB의 JSON을 그대로 불러온다. */}
          <Button size="sm" to={`${ROUTES.calcResult}?taxInputId=${row.taxInputId}&saved=true`}>
            결과 보기
          </Button>
          <Button size="sm" to={`${ROUTES.calcSim}?taxInputId=${row.taxInputId}&saved=true`}>
            시뮬레이션
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="grid g-side" style={{ alignItems: 'start' }}>
      <div className="grid" style={{ minWidth: 0 }}>
        <AnnualTaxComparison savedResults={savedResults} isLoading={isLoading} errorMessage={errorMessage} />
        <Card title="저장한 분석 결과" icon="calculator">
          {isLoading ? (
            <p className="state-msg">저장한 분석 결과를 불러오고 있습니다.</p>
          ) : errorMessage ? (
            <p role="alert">{errorMessage}</p>
          ) : savedResults.length === 0 ? (
            <>
              <p className="state-msg">저장한 분석 결과가 없습니다.</p>
              <Button to={ROUTES.calcInput}>세금 계산하기</Button>
            </>
          ) : (
            <DataTable columns={columns} rows={savedResults} rowKey={(row) => row.taxInputId} />
          )}
        </Card>
        <Card title="상담 내역" icon="messageSquare">
          {/* 지난 상담 목록 조회는 별도 작업으로 남겨두고, 현재 진행 중인 상담은 채팅 패널에서 바로 이어서 할 수 있다. */}
          <p className="state-msg">
            지난 상담 내역 보기는 준비 중입니다. 진행 중인 상담은 채팅 상담에서 이어서 볼 수 있어요.
          </p>
          <div style={{ marginTop: 12 }}>
            <Button onClick={() => openChat(true)}>상담하기</Button>
          </div>
        </Card>
      </div>
      <ProfileSettings />
    </div>
  );
}
