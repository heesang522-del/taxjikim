import styled, { keyframes } from 'styled-components'
import Icon from '../Icon'
import { Card } from '../ui'

const spin = keyframes`
  to { transform: rotate(360deg); }
`

const Spinner = styled.div`
  width: 64px;
  height: 64px;
  margin: 0 auto;
  border-radius: 24px;
  background: var(--primary-bg);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: ${spin} 1s linear infinite;
`

const Track = styled.div`
  width: 256px;
  height: 8px;
  margin: 0 auto;
  border-radius: 999px;
  background: var(--fill);
  overflow: hidden;

  div {
    height: 100%;
    border-radius: 999px;
    background: var(--primary);
    transition: width 1s;
  }
`

export default function LoadingScreen({ progress }) {
  return (
    <Card $pad={64} style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 24 }}>
      <Spinner>
        <Icon name="loader" size={32} />
      </Spinner>
      <div>
        <h3 style={{ fontSize: 20, fontWeight: 700, marginBottom: 8 }}>입력한 거래 내역을 분석하고 있습니다...</h3>
        <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>필요경비 산정, 예상 종합소득세 및 지방소득세 계산 중</p>
      </div>
      <Track>
        <div style={{ width: `${Math.min(progress, 100)}%` }} />
      </Track>
    </Card>
  )
}
