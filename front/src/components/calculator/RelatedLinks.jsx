import styled from 'styled-components'
import Icon from '../Icon'
import { Button, Card, CardTitle, Stack } from '../ui'

const GuideLink = styled.a`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: var(--bg);
  color: var(--text-h);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;

  &:hover {
    background: var(--fill);
  }
  svg {
    color: var(--text-faint);
  }
`

export default function RelatedLinks({ links, onRetry }) {
  return (
    <Card>
      <Stack $gap={16}>
        <CardTitle>
          <Icon name="link" size={16} /> 관련 안내 및 시뮬레이션 링크
        </CardTitle>
        <Stack $gap={8}>
          {links.map((l) => (
            <GuideLink
              key={l.id}
              href="#"
              onClick={(e) => {
                e.preventDefault()
                alert(l.message)
              }}
            >
              <span>{l.label}</span>
              <Icon name="externalLink" size={16} />
            </GuideLink>
          ))}
          <Button $variant="soft" $block onClick={onRetry}>
            데이터 재입력 및 시뮬레이션 비교
          </Button>
        </Stack>
      </Stack>
    </Card>
  )
}
