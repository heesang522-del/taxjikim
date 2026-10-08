import { Button } from '../ui'

export default function KakaoLoginButton() {
  return (
    <Button
      type="button"
      $block
      onClick={() => alert('카카오 로그인 연동 시뮬레이션 완료')}
      style={{ background: '#FEE500', color: '#191919', fontWeight: 700 }}
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M12 3C6.48 3 2 6.58 2 11c0 2.87 1.83 5.39 4.57 6.84-.18.66-.67 2.4-.77 2.78-.12.48.18.47.38.34.16-.11 2.56-1.74 3.56-2.42.74.11 1.5.17 2.26.17 5.52 0 10-3.58 10-8s-4.48-8-10-8z" />
      </svg>
      카카오로 3초 만에 로그인
    </Button>
  )
}
