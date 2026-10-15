import { useState } from 'react'
import styled from 'styled-components'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'
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

export default function FileUploader({ onUploadSuccess }) {
  const navigate = useNavigate()
  const [fileName, setFileName] = useState('')
  const [loading, setLoading] = useState(false)

  const handleFileChange = async (e) => {
    const file = e.target.files[0]
    if (!file) return

    if (!file.name.toLowerCase().endsWith('.pdf') && file.type !== 'application/pdf') {
      alert('PDF 파일만 업로드할 수 있습니다.')
      return
    }

    setFileName(file.name)
    await uploadAndParsePdf(file)
  }

  const uploadAndParsePdf = async (file) => {
    const formData = new FormData()
    formData.append('file', file)
    
    const token = localStorage.getItem('token') || localStorage.getItem('accessToken')
    const currentUserId = localStorage.getItem('userId') || '0'
    const currentTaxYear = new Date().getFullYear().toString()

    formData.append('userId', currentUserId)
    formData.append('taxInputId', '0')
    formData.append('taxYear', currentTaxYear)

    try {
      setLoading(true)
      const headers = {}
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }

      const response = await axios.post('/api/v1/tax/upload-pdf', formData, { headers })

      // 비회원 체험 완료 후 가입 유도
      if (!token) {
        const wantSignup = window.confirm(
          '🎉 무료 간이 세금 계산이 완료되었습니다!\n상세 산출 결과를 확인하려면 회원가입/로그인이 필요합니다. 이동하시겠습니까?'
        )
        if (wantSignup) {
          navigate('/login')
          return
        }
      }

      alert(response.data || 'PDF 문서 분석이 완료되었습니다.')
      if (onUploadSuccess) {
        onUploadSuccess(response.data)
      }
    } catch (error) {
      console.error('PDF 업로드 실패:', error)
      const errorMsg = error.response?.data || '파일 분석 중 오류가 발생했습니다.'
      alert(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Drop>
      <Icon name="uploadCloud" size={40} />
      <p style={{ fontSize: 14, fontWeight: 600 }}>장부 내역 PDF 파일 업로드 (비회원 체험 가능)</p>
      <p style={{ fontSize: 12, color: 'var(--text-faint)' }}>
        홈택스 PDF를 업로드하고 간편하게 예상 세액을 확인해보세요.
      </p>
      {loading && (
        <p style={{ fontSize: 12, color: 'var(--primary)', fontWeight: 600 }}>
          🔄 PDF 문서 분석 중...
        </p>
      )}
      {!loading && fileName && (
        <p style={{ fontSize: 12, color: 'var(--primary)', fontWeight: 600 }}>
          선택한 파일: {fileName}
        </p>
      )}
      <input type="file" accept=".pdf,application/pdf" onChange={handleFileChange} disabled={loading} />
    </Drop>
  )
}