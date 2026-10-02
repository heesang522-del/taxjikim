import { Link } from 'react-router-dom'
import styled from 'styled-components'
import Icon from '../Icon'
import { Button, Row } from '../ui'

const Hero = styled.div`
  position: relative;
  overflow: hidden;
  padding: 48px;
  border-radius: 24px;
  color: #fff;
  background: linear-gradient(90deg, #312e81, #3730a3 50%, #4c1d95);
  box-shadow: 0 20px 40px rgba(49, 46, 129, 0.25);

  @media (max-width: 640px) {
    padding: 28px;
  }

  .bg {
    position: absolute;
    right: -40px;
    bottom: -40px;
    opacity: 0.1;
  }
  .content {
    position: relative;
    max-width: 672px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .tag {
    align-self: flex-start;
    padding: 4px 12px;
    border-radius: 999px;
    border: 1px solid rgba(129, 140, 248, 0.3);
    background: rgba(99, 102, 241, 0.3);
    color: #c7d2fe;
    font-size: 12px;
    font-weight: 600;
  }
  h1 {
    color: #fff;
    font-size: clamp(28px, 5vw, 48px);
    font-weight: 800;
    line-height: 1.2;
    letter-spacing: -0.02em;
  }
  p {
    color: #e0e7ff;
    font-size: 17px;
  }
`

export default function HeroSection() {
  return (
    <Hero>
      <div className="bg">
        <Icon name="pieChart" size={384} />
      </div>
      <div className="content">
        <span className="tag">프리랜서 & 크리에이터 전용 세무 솔루션</span>
        <h1>
          복잡한 세금 계산,
          <br />
          클릭 한 번으로 완벽하게
        </h1>
        <p>종합소득세 신고부터 사업자 등록 전후 세금 비교, AI 기반 맞춤 필요경비 계산까지 TaxFlow와 함께하세요.</p>
        <Row $gap={12} $wrap style={{ paddingTop: 8 }}>
          <Button
            as={Link}
            to="/calculator"
            $size="lg"
            style={{ background: '#fff', color: '#312e81', fontWeight: 700 }}
          >
            <Icon name="calculator" /> 무료 세금 계산하기
          </Button>
          <Button
            as={Link}
            to="/community"
            $size="lg"
            style={{ background: 'rgba(55, 48, 163, 0.8)', border: '1px solid #4f46e5', color: '#fff' }}
          >
            커뮤니티 둘러보기
          </Button>
        </Row>
      </div>
    </Hero>
  )
}
