// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Button, Card, Icon } from '../components/common/index';
import { ROUTES } from '../utils/routes';

/** 이용 정지된 계정이 로그인을 시도하면 보여주는 안내 페이지. 로그인 상태는 만들어지지 않는다. */
export default function BlockedPage() {
  return (
    <Card style={{ maxWidth: 480, margin: '40px auto', textAlign: 'center' }}>
      <div className="icon-box" style={{ margin: '0 auto 16px' }}><Icon name="shieldAlert" size={24} /></div>
      <h3>로그인이 차단되었습니다.</h3>
      <p className="muted" style={{ marginBottom: 14 }}>관리자에게 문의하세요.</p>
      <Button variant="primary" to={ROUTES.home}>홈으로</Button>
    </Card>
  );
}
