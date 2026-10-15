// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, Icon, Modal } from '../components/common/index';
import { LoginForm } from '../components/auth/LoginForm';
import { SignupForm } from '../components/auth/SignupForm';
import { RecoveryForm } from '../components/auth/RecoveryForm';
import { takeReturnTo } from '../utils/returnTo';
import { ROUTES } from '../utils/routes';
import { useToast } from '../store/useToastStore';

const TABS = [
  ['login', '로그인'],
  ['signup', '회원가입'],
  ['findid', '아이디 찾기'],
  ['findpw', '비밀번호 찾기'],
];
const TITLES = Object.fromEntries(TABS);

export default function AuthPage() {
  const [params] = useSearchParams();
  // 화면 종류를 URL에서 계산해 헤더 이동·뒤로가기와 표시 화면이 일치하도록 한다.
  const { pathname } = useLocation();
  const queryTab = params.get('tab');
  const tab =
    pathname === ROUTES.signup
      ? 'signup'
      : pathname === ROUTES.findAccount
        ? queryTab === 'findpw'
          ? 'findpw'
          : 'findid'
        : TABS.some(([key]) => key === queryTab)
          ? queryTab
          : 'login';
  const [signedUp, setSignedUp] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();
  const handleTab = (nextTab) => {
    // 폼 내부 링크도 헤더와 같은 주소로 이동하며 계정 찾기의 종류만 쿼리에 둔다.
    if (nextTab === 'signup') navigate(ROUTES.signup);
    else if (nextTab === 'login') navigate(ROUTES.auth);
    else navigate(`${ROUTES.findAccount}?tab=${nextTab}`);
  };
  // 로그인에 성공하면 로그인 페이지로 오기 직전에 보던 페이지로 돌아간다 (없으면 메인)
  const enter = () => {
    navigate(takeReturnTo(), { replace: true });
    toast('로그인했습니다');
  };

  return (
    <div style={{ maxWidth: 480, margin: tab === 'signup' ? '4px auto 16px' : '24px auto' }}>
      {tab === 'signup' ? (
        <h2
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            fontSize: 22,
            marginBottom: 16,
          }}
        >
          <Icon name="userPlus" size={22} />
          회원가입
        </h2>
      ) : (
        <>
          <div className="icon-box" style={{ margin: '0 auto 16px' }}>
            <Icon name={tab === 'signup' ? 'userPlus' : 'logIn'} size={24} />
          </div>
          <h2 style={{ fontSize: 22, textAlign: 'center', marginBottom: 24 }}>{TITLES[tab]}</h2>
        </>
      )}
      <Card>
        {tab === 'login' && (
          <LoginForm
            onSuccess={enter}
            onBlocked={() => navigate(ROUTES.blocked, { replace: true })}
            onTab={handleTab}
          />
        )}
        {tab === 'signup' && <SignupForm onDone={() => setSignedUp(true)} />}
        {tab === 'findid' && <RecoveryForm key="findid" mode="id" onTab={handleTab} />}
        {tab === 'findpw' && <RecoveryForm key="findpw" mode="pw" onTab={handleTab} />}
      </Card>
      {tab !== 'login' && (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Button variant="link" onClick={() => handleTab('login')}>
            로그인으로 돌아가기
          </Button>
        </div>
      )}
      {signedUp && (
        <Modal
          title="가입 완료"
          onClose={() => {
            setSignedUp(false);
            handleTab('login');
          }}
        >
          <div className="grid" style={{ gap: 16, textAlign: 'center', justifyItems: 'center' }}>
            <div className="icon-box">
              <Icon name="check" size={24} />
            </div>
            <div>
              <h3 style={{ fontSize: 18, marginBottom: 6 }}>가입이 완료되었습니다</h3>
              <p className="small muted">가입한 아이디로 로그인해 주세요.</p>
            </div>
            <Button
              variant="primary"
              size="lg"
              block
              autoFocus
              onClick={() => {
                setSignedUp(false);
                handleTab('login');
              }}
            >
              로그인하기
            </Button>
          </div>
        </Modal>
      )}
    </div>
  );
}
