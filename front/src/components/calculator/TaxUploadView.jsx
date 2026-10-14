import React, { useState } from 'react';
import { uploadTaxPdfApi } from '../../api/taxApi'; // 🌟 앞서 만든 API 연동 함수 임포트


const TaxCalculatorView = () => {
    // 상태 관리 (나이, 지역, 직군 및 파일 상태)
    const [age, setAge] = useState(32);
    const [region, setRegion] = useState('서울특별시');
    const [jobCategory, setJobCategory] = useState('개발자 (소프트웨어)');
    
    // 파일 업로드 관련 상태
    const [selectedFile, setSelectedFile] = useState(null);
    const [loading, setLoading] = useState(false);
    const [uploadMessage, setUploadMessage] = useState('');

    // 1. 파일 선택 및 유효성 검사 핸들러
    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            if (!file.name.toLowerCase().endsWith('.pdf')) {
                alert('유효한 PDF 파일만 업로드 가능합니다.');
                return;
            }
            setSelectedFile(file);
            setUploadMessage(`선택된 파일: ${file.name}`);
        }
    };

    // 2. PDF 업로드 및 백엔드 전송 핸들러 (실제 데이터 파싱 및 DB 적재 연동)
    const handlePdfUploadSubmit = async () => {
        if (!selectedFile) {
            alert('업로드할 연말정산 PDF 파일을 먼저 선택해주세요.');
            return;
        }

        const formData = new FormData();
        formData.append('file', selectedFile);
        formData.append('userId', 1);       // 로그인한 유저 ID (필요 시 동적 값으로 변경)
        formData.append('taxInputId', 1);   // 세무 입력 ID
        formData.append('taxYear', 2025);   // 귀속 연도

        setLoading(true);
        setUploadMessage('PDF 문서 분석 및 OCR/DB 적재 중입니다...');

        try {
            // 백엔드 API 호출 (/api/v1/tax/upload-pdf)
            const responseMessage = await uploadTaxPdfApi(formData);
            setUploadMessage(responseMessage); // 성공 메시지 출력
            alert('성공: ' + responseMessage);
        } catch (error) {
            const errorMsg = error.response?.data || '서버 통신 중 오류가 발생했습니다.';
            setUploadMessage(errorMsg);
            alert('실패: ' + errorMsg);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="tax-calculator-container" style={{ padding: '20px', maxWidth: '900px', margin: '0 auto' }}>
            <h2>프리랜서 세무 데이터 입력</h2>
            <p className="sub-desc">엑셀/CSV/PDF 파일을 업로드하거나 내역을 직접 입력하여 예상 세액을 계산하세요.</p>

            {/* 입력 폼 필드 영역 */}
            <div className="input-group-row" style={{ display: 'flex', gap: '20px', marginBottom: '20px' }}>
                <div style={{ flex: 1 }}>
                    <label>나이</label>
                    <input 
                        type="number" 
                        value={age} 
                        onChange={(e) => setAge(e.target.value)} 
                        style={{ width: '100%', padding: '8px' }}
                    />
                </div>
                <div style={{ flex: 1 }}>
                    <label>지역</label>
                    <select value={region} onChange={(e) => setRegion(e.target.value)} style={{ width: '100%', padding: '8px' }}>
                        <option value="서울특별시">서울특별시</option>
                        <option value="경기도">경기도</option>
                        <option value="기타">기타</option>
                    </select>
                </div>
                <div style={{ flex: 1 }}>
                    <label>직군 카테고리</label>
                    <select value={jobCategory} onChange={(e) => setJobCategory(e.target.value)} style={{ width: '100%', padding: '8px' }}>
                        <option value="개발자 (소프트웨어)">개발자 (소프트웨어)</option>
                        <option value="디자이너">디자이너</option>
                        <option value="기타">기타</option>
                    </select>
                </div>
            </div>

            {/* 🌟 PDF 업로드 및 백엔드 연동 박스 영역 */}
            <div 
                className="upload-box" 
                style={{ 
                    border: '2px dashed #4299e1', 
                    borderRadius: '12px', 
                    padding: '30px', 
                    textAlign: 'center', 
                    backgroundColor: '#ebf8ff',
                    marginBottom: '30px'
                }}
            >
                <h3>📁 장부 내역 파일 업로드 (PDF)</h3>
                <p style={{ color: '#666', fontSize: '14px', marginBottom: '15px' }}>
                    국세청 홈택스 다운로드 PDF 파일을 선택해주세요.
                </p>

                <input 
                    type="file" 
                    accept=".pdf" 
                    onChange={handleFileChange} 
                    style={{ marginBottom: '15px' }}
                />

                <div>
                    <button 
                        onClick={handlePdfUploadSubmit} 
                        disabled={loading}
                        style={{
                            padding: '10px 20px',
                            backgroundColor: '#3182ce',
                            color: '#white',
                            border: 'none',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            fontWeight: 'bold'
                        }}
                    >
                        {loading ? '분석 및 적재 중...' : 'PDF 분석 및 DB 적재 실행'}
                    </button>
                </div>

                {uploadMessage && (
                    <p style={{ marginTop: '15px', fontWeight: 'bold', color: '#2b6cb0' }}>
                        {uploadMessage}
                    </p>
                )}
            </div>
        </div>
    );
};

export default TaxCalculatorView;