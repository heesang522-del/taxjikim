import { useNavigate } from 'react-router-dom'
import AuthCard from '../components/auth/AuthCard'
import RecoveryBox from '../components/auth/RecoveryBox'
import { Button } from '../components/ui'

export default function FindAccountPage() {
  const navigate = useNavigate()

  return (
    <AuthCard
      title="아이디 / 비밀번호 찾기"
      description="이메일 인증을 통해 계정을 복구하거나 재설정합니다."
    >
      {/* MOCK: 실제 메일은 발송되지 않고 안내 문구만 띄운다 */}
      <RecoveryBox
        title="아이디 찾기"
        fields={[
          { type: 'text', placeholder: '이름 입력' },
          { type: 'email', placeholder: '가입한 이메일 입력' },
        ]}
        buttonLabel="아이디 찾기 실행"
        onSubmit={() => alert('인증 메일이 발송되었습니다. 아이디: user***')}
      />
      <RecoveryBox
        title="비밀번호 재설정"
        fields={[
          { type: 'text', placeholder: '아이디 입력' },
          { type: 'email', placeholder: '가입한 이메일 입력' },
        ]}
        buttonLabel="비밀번호 재설정"
        onSubmit={() => alert('비밀번호 재설정 링크가 이메일로 발송되었습니다.')}
      />
      <Button $variant="link" $size="sm" onClick={() => navigate('/login')} style={{ alignSelf: 'center' }}>
        로그인 화면으로 돌아가기
      </Button>
    </AuthCard>
  )
}
