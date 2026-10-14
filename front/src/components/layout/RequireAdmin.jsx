// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card } from '../common/index';
import { ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';

/** 관리자 계정만 안쪽 화면을 볼 수 있게 막는다. 비로그인·일반 회원에게는 안내만 보여준다. */
export default function RequireAdmin({ children }) {
  const { loggedIn, isAdmin } = useAuth();
  if (isAdmin) return children;
  return (
    <Card style={{ maxWidth: 480, margin: '40px auto', textAlign: 'center' }}>
      <h3>{loggedIn ? '관리자 권한이 필요합니다' : '로그인이 필요합니다'}</h3>
      <p className="muted" style={{ marginBottom: 14 }}>{loggedIn ? '관리자 계정으로 로그인한 경우에만 이용할 수 있습니다.' : '관리자 계정으로 로그인해 주세요.'}</p>
      {loggedIn ? <Button variant="primary" to={ROUTES.home}>홈으로</Button> : <Button variant="primary" to={ROUTES.auth}>로그인</Button>}
    </Card>
  );
}
