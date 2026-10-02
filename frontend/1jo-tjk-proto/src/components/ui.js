import styled, { css } from 'styled-components'

// ---------- 레이아웃 ----------
export const Shell = styled.main`
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 64px;

  @media (max-width: 640px) {
    padding: 16px 16px 48px;
  }
`

// 세로로 쌓는 영역. $gap으로 간격, $width로 최대 너비(가운데 정렬)를 정한다
export const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(p) => p.$gap ?? 16}px;
  width: 100%;
  ${(p) => p.$width && `max-width: ${p.$width}px; margin: 0 auto;`}
`

export const Row = styled.div`
  display: flex;
  align-items: ${(p) => p.$align ?? 'center'};
  justify-content: ${(p) => p.$justify ?? 'flex-start'};
  gap: ${(p) => p.$gap ?? 8}px;
  ${(p) => p.$wrap && 'flex-wrap: wrap;'}
`

export const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(${(p) => p.$cols ?? 2}, minmax(0, 1fr));
  gap: ${(p) => p.$gap ?? 24}px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`

export const Card = styled.div`
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: ${(p) => p.$radius ?? 24}px;
  padding: ${(p) => p.$pad ?? 24}px;
  box-shadow: var(--shadow);
`

// ---------- 텍스트 ----------
export const PageTitle = styled.h1`
  font-size: 24px;
  font-weight: 700;
`

export const CardTitle = styled.h3`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;

  svg {
    color: var(--primary);
  }
`

export const Hint = styled.p`
  font-size: 12px;
  color: var(--text-muted);
`

export const StateMessage = styled.p`
  padding: 40px 0;
  text-align: center;
  font-size: 14px;
  color: ${(p) => (p.$error ? 'var(--danger)' : 'var(--text-muted)')};
`

// 구분선으로 나뉘는 목록
export const DividedList = styled.div`
  display: flex;
  flex-direction: column;

  > * + * {
    border-top: 1px solid var(--border-soft);
  }
`

// ---------- 폼 ----------
const controlStyle = css`
  width: 100%;
  font: inherit;
  font-size: ${(p) => (p.$small ? 12 : 14)}px;
  color: var(--text-h);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: ${(p) => (p.$small ? 8 : 12)}px;
  padding: ${(p) => (p.$small ? '6px 8px' : '10px 14px')};

  &:focus {
    outline: 2px solid var(--primary);
    outline-offset: -1px;
  }
`

export const Input = styled.input`
  ${controlStyle}
`
export const Select = styled.select`
  ${controlStyle}
`
export const Textarea = styled.textarea`
  ${controlStyle}
  resize: vertical;
`

export const Field = styled.label`
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;

  > span {
    font-size: 12px;
    font-weight: 600;
    color: var(--text);
  }
`

// ---------- 버튼 / 배지 ----------
const buttonVariants = {
  primary: css`
    background: var(--primary);
    color: #fff;
    box-shadow: 0 4px 10px rgba(79, 70, 229, 0.2);
    &:hover { background: var(--primary-hover); }
  `,
  soft: css`
    background: var(--primary-bg);
    color: var(--primary);
    &:hover { background: var(--primary-border); }
  `,
  secondary: css`
    background: var(--fill);
    color: var(--text);
    &:hover { background: var(--fill-hover); }
  `,
  dark: css`
    background: var(--text-h);
    color: #fff;
    &:hover { opacity: 0.88; }
  `,
  danger: css`
    background: var(--danger-bg);
    color: var(--danger);
    &:hover { opacity: 0.8; }
  `,
  ghost: css`
    background: transparent;
    color: var(--primary);
    &:hover { background: var(--primary-bg); }
  `,
  link: css`
    background: transparent;
    color: var(--text-muted);
    padding: 0;
    &:hover { color: var(--primary); }
  `,
}

const buttonSizes = {
  sm: 'padding: 6px 12px; font-size: 12px; border-radius: 10px;',
  md: 'padding: 10px 16px; font-size: 14px; border-radius: 12px;',
  lg: 'padding: 14px 24px; font-size: 15px; border-radius: 14px;',
}

export const Button = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
  ${(p) => buttonSizes[p.$size ?? 'md']}
  ${(p) => buttonVariants[p.$variant ?? 'secondary']}
  ${(p) => p.$block && 'width: 100%;'}

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`

const badgeTones = {
  primary: ['var(--primary-bg)', 'var(--primary)'],
  success: ['var(--success-bg)', 'var(--success)'],
  danger: ['var(--danger-bg)', 'var(--danger)'],
  warn: ['var(--warn-bg)', 'var(--warn)'],
  neutral: ['var(--fill)', 'var(--text-muted)'],
}

export const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  background: ${(p) => badgeTones[p.$tone ?? 'primary'][0]};
  color: ${(p) => badgeTones[p.$tone ?? 'primary'][1]};
`

// 원 모양 아이콘 박스 (홈 통계, 로그인/회원가입 상단 등)
export const IconBox = styled.div`
  width: ${(p) => p.$size ?? 48}px;
  height: ${(p) => p.$size ?? 48}px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: ${(p) => badgeTones[p.$tone ?? 'primary'][0]};
  color: ${(p) => badgeTones[p.$tone ?? 'primary'][1]};
`

export const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  text-align: left;

  th {
    padding: 12px;
    background: var(--fill);
    color: var(--text-muted);
    font-weight: 600;
    white-space: nowrap;
  }
  th:first-child { border-radius: 12px 0 0 12px; }
  th:last-child { border-radius: 0 12px 12px 0; }

  td {
    padding: 8px;
    border-bottom: 1px solid var(--border-soft);
    vertical-align: middle;
  }
  tbody tr:last-child td {
    border-bottom: none;
  }
`
