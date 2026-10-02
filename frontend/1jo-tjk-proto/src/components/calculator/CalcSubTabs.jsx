import styled from 'styled-components'

const Tabs = styled.div`
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;

  button {
    padding: 0 0 12px;
    border: none;
    border-bottom: 2px solid transparent;
    background: transparent;
    color: var(--text-muted);
    cursor: pointer;

    &:hover {
      color: var(--text-h);
    }
    &.active {
      color: var(--primary);
      border-bottom-color: var(--primary);
    }
  }
`

const TABS = [
  { id: 'input', label: '1. 수입/지출 입력 및 업로드' },
  { id: 'output', label: '2. 세액 산출 결과 & 시뮬레이션' },
]

export default function CalcSubTabs({ active, onChange }) {
  return (
    <Tabs>
      {TABS.map((t) => (
        <button key={t.id} className={active === t.id ? 'active' : ''} onClick={() => onChange(t.id)}>
          {t.label}
        </button>
      ))}
    </Tabs>
  )
}
