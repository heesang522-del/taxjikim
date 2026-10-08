import { useState } from 'react'
import styled from 'styled-components'
import Icon from '../Icon'

const Drop = styled.label`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
  text-align: center;
  border: 2px dashed var(--border);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.5);
  cursor: pointer;
  transition: border-color 0.15s;

  &:hover {
    border-color: var(--primary);
  }
  svg {
    color: var(--primary);
  }
  input {
    display: none;
  }
`

// 파일을 고르면 이름만 보여준다. 파일 내용은 분석하지 않는다 (프로토타입 범위 밖)
export default function FileUploader() {
  const [fileName, setFileName] = useState('')

  return (
    <Drop>
      <Icon name="uploadCloud" size={40} />
      <p style={{ fontSize: 14, fontWeight: 600 }}>장부 내역 파일 업로드 (Excel / CSV / PDF)</p>
      <p style={{ fontSize: 12, color: 'var(--text-faint)' }}>
        국세청 홈택스 다운로드 파일 또는 가계부 엑셀 파일을 드래그하여 업로드하세요.
      </p>
      {fileName && (
        <p style={{ fontSize: 12, color: 'var(--primary)', fontWeight: 600 }}>
          선택한 파일: {fileName} (파일 분석은 아직 지원하지 않습니다)
        </p>
      )}
      <input type="file" onChange={(e) => setFileName(e.target.files[0]?.name ?? '')} />
    </Drop>
  )
}
