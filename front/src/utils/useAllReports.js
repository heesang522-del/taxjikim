// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { getAdminReports } from '../api/report';
import { useReports } from './useReports';

/**
 * 게시글·댓글 신고(백엔드 API)와, 브라우저에만 있는 예시 글 신고(아직 백엔드에 없는 postId라 API를 못 쓰는 mock)를 합쳐서 돌려준다.
 * AdminPage·AdminReportsPage가 함께 쓴다.
 */
export function useAllReports() {
  const [backendReports, setBackendReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const { reports: allMockReports, resolveReport: resolveMockReport } = useReports();
  // 댓글 신고는 이제 백엔드가 있으므로, mock은 브라우저 예시 글(게시글) 신고만 남는다.
  const mockReports = allMockReports.filter((r) => r.targetType === 'post');

  // active는 이전 요청이 늦게 도착해 최신 화면을 덮지 않도록 막는다.
  const reload = useCallback((active = { current: true }) => (
    getAdminReports()
      .then((list) => { if (active.current) { setBackendReports(list); setLoadError(''); } })
      .catch((error) => { if (active.current) setLoadError(error.message || '신고 목록을 불러오지 못했습니다.'); })
  ), []);

  useEffect(() => {
    const active = { current: true };
    reload(active).finally(() => { if (active.current) setLoading(false); });
    return () => { active.current = false; };
  }, [reload]);

  const reports = [...backendReports, ...mockReports];

  return { reports, loading, loadError, reload, resolveMockReport };
}
