import styled from 'styled-components'

export const Shell = styled.div`
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
  padding: 0 20px 64px;
  flex: 1;
`

export const Nav = styled.nav`
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  border-bottom: 1px solid var(--border);
  margin-bottom: 32px;

  .brand {
    font-weight: 700;
    font-size: 16px;
    color: var(--text-h);
  }

  .links {
    display: flex;
    gap: 4px;
  }

  a {
    text-decoration: none;
    color: var(--text-muted);
    font-size: 14px;
    font-weight: 600;
    padding: 8px 14px;
    border-radius: 8px;

    &.active {
      color: var(--accent);
      background: var(--accent-bg);
    }
  }
`

export const PageHeader = styled.header`
  margin-bottom: 28px;

  h1 {
    font-size: 26px;
    margin-bottom: 6px;
  }

  p {
    color: var(--text-muted);
    font-size: 15px;
  }
`

export const Section = styled.section`
  margin-bottom: 32px;
`

export const SectionTitle = styled.h2`
  font-size: 17px;
  margin-bottom: 4px;
`

export const SectionHint = styled.p`
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 14px;
`

export const Card = styled.div`
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
`

export const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(${(props) => props.$cols ?? 4}, 1fr);
  gap: 12px;

  @media (max-width: 640px) {
    grid-template-columns: repeat(2, 1fr);
  }
`

export const StatTile = styled(Card)`
  padding: 16px 18px;

  .label {
    color: var(--text-muted);
    font-size: 12.5px;
    margin-bottom: 8px;
  }

  .value {
    font-size: 20px;
    font-weight: 600;
    color: ${(props) => props.$color ?? 'var(--text-h)'};
    font-variant-numeric: proportional-nums;
  }
`

export const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;

  th,
  td {
    text-align: left;
    padding: 10px 12px;
    border-bottom: 1px solid var(--border);
  }

  th {
    color: var(--text-muted);
    font-weight: 600;
    font-size: 12.5px;
  }

  td.amount {
    text-align: right;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  tbody tr:last-child td {
    border-bottom: none;
  }
`

export const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: ${(props) => props.$fg ?? 'var(--text)'};
  background: ${(props) => props.$bg ?? 'var(--border)'};
`

export const TypeTag = styled.span`
  font-weight: 600;
  color: ${(props) => (props.$type === '수입' ? 'var(--income)' : 'var(--expense)')};
`

export const Button = styled.button`
  border: 1px solid ${(props) => (props.$variant === 'primary' ? 'transparent' : 'var(--border)')};
  background: ${(props) => (props.$variant === 'primary' ? 'var(--accent)' : 'var(--surface)')};
  color: ${(props) => (props.$variant === 'primary' ? '#fff' : 'var(--text-h)')};
  font-weight: 600;
  font-size: 14px;
  padding: 10px 16px;
  border-radius: 8px;
  cursor: pointer;

  &:hover {
    opacity: 0.88;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`

export const Field = styled.label`
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-muted);
  flex: 1;

  input,
  select {
    font: inherit;
    font-size: 14px;
    color: var(--text-h);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 9px 10px;
  }
`

export const Form = styled.form`
  display: flex;
  gap: 10px;
  align-items: flex-end;
  flex-wrap: wrap;
`
