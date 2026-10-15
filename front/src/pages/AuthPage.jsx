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
  // 주소(/login, /signup, /find-account)나 ?tab= 값으로 처음 보여줄 탭을 정한다
  const { pathname } = useLocation();
  const pathTab = pathname === ROUTES.signup ? 'signup' : pathname === ROUTES.findAccount ? 'findid' : null;
  const [tab, setTab] = useState(
    pathTab ?? (TABS.some(([k]) => k === params.get('tab')) ? params.get('tab') : 'login')
  );
  const [signedUp, setSignedUp] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();
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
          <LoginForm onSuccess={enter} onBlocked={() => navigate(ROUTES.blocked, { replace: true })} onTab={setTab} />
        )}
        {tab === 'signup' && <SignupForm onDone={() => setSignedUp(true)} />}
        {tab === 'findid' && <RecoveryForm key="findid" mode="id" onTab={setTab} />}
        {tab === 'findpw' && <RecoveryForm key="findpw" mode="pw" onTab={setTab} />}
      </Card>
      {tab !== 'login' && (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Button variant="link" onClick={() => setTab('login')}>
            로그인으로 돌아가기
          </Button>
        </div>
      )}
      {signedUp && (
        <Modal
          title="가입 완료"
          onClose={() => {
            setSignedUp(false);
            setTab('login');
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
                setTab('login');
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
