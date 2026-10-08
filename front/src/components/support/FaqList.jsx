import styled from 'styled-components'
import { Stack } from '../ui'

const Item = styled.details`
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg);
  font-size: 12px;

  summary {
    cursor: pointer;
    font-weight: 700;
    color: var(--text);
  }
  p {
    margin-top: 8px;
    color: var(--text-muted);
  }
`

export default function FaqList({ faqs }) {
  return (
    <Stack $gap={12} style={{ paddingTop: 16, borderTop: '1px solid var(--border-soft)' }}>
      <h3 style={{ fontSize: 14, fontWeight: 700 }}>자주 묻는 질문 (FAQ)</h3>
      <Stack $gap={8}>
        {faqs.map((f) => (
          <Item key={f.id}>
            <summary>{f.q}</summary>
            <p>{f.a}</p>
          </Item>
        ))}
      </Stack>
    </Stack>
  )
}
