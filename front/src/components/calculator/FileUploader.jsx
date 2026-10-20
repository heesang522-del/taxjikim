// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다.
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { Button, Card, Icon } from '../common/index';
import { useAuth } from '../../store/useAuthStore';
import { useTax } from '../../store/useTaxStore';
import { useToast } from '../../store/useToastStore';
import { uploadTaxPdfApi, EVIDENCE_STATUSES } from '../../api/taxApi';
import { YEAR } from '../../data/taxConstants';

const MAX_FILES = 5;
const MAX_FILE_BYTES = 6 * 1024 * 1024;

export default function FileUploader({ onUploadSuccess }) {
  const inputRef = useRef(null);
  const uploading = useRef(false);
  const uploadController = useRef(null);
  const [uploadErrors, setUploadErrors] = useState([]);

  // 화면을 떠나면 응답 대기를 중단해 이전 요청이 새 화면 상태를 바꾸지 않게 한다.
  useEffect(() => () => uploadController.current?.abort(), []);
  const [currentName, setCurrentName] = useState('');
  const [loading, setLoading] = useState(false);
  const { loggedIn } = useAuth();
  const {
    uploadedFiles: files,
    fileRows,
    addFileResult,
    addEvidenceResult,
    setFileSelected,
    removeUploadedFile,
  } = useTax();
  const toast = useToast();

  const accept = async (fileList) => {
    if (uploading.current) return;
    const selected = Array.from(fileList);
    if (!selected.length) return;
    if (selected.some((file) => !/\.pdf$/i.test(file.name))) {
      toast('PDF 파일만 선택해 주세요.');
      return;
    }
    if (selected.some((file) => !file.size || file.size > MAX_FILE_BYTES)) {
      toast('각 PDF는 비어 있지 않은 6MB 이하 파일이어야 합니다.');
      return;
    }
    if (files.length + selected.length > MAX_FILES) {
      toast(`PDF는 현재 화면에서 최대 ${MAX_FILES}개까지 추가할 수 있습니다.`);
      return;
    }
    const fileKey = (file) => `${file.name}:${file.size}:${file.lastModified}`;
    const keys = selected.map(fileKey);
    if (new Set(keys).size !== keys.length || files.some((file) => keys.includes(file.key))) {
      toast('이미 업로드한 파일을 제외하고 선택해 주세요.');
      return;
    }
    const controller = new AbortController();
    uploadController.current = controller;
    setUploadErrors([]);
    uploading.current = true;
    setLoading(true);
    try {
      // 서버가 원본 저장과 분석을 끝낸 응답만 내역에 반영하며, 파일은 순차 처리한다.
      for (const file of selected) {
        if (controller.signal.aborted) break;
        setCurrentName(file.name);
        try {
          const result = await uploadTaxPdfApi(file, { taxYear: YEAR, signal: controller.signal });
          if (controller.signal.aborted) break;
          const fileInfo = {
            key: fileKey(file),
            name: result.originalFileName,
            fileId: result.fileId,
            persisted: result.persisted,
          };
          if (!result.transactions.length && result.evidence) {
            addEvidenceResult(result.evidence, {
              ...fileInfo,
              status: result.evidence.status,
              count: result.evidence.items.length,
            });
            toast(
              `${file.name}: 금액 검토 문서 · ${EVIDENCE_STATUSES[result.evidence.status] || '분석 상태 확인 필요'}`
            );
            onUploadSuccess?.(result);
            continue;
          }
          const rows = result.transactions.map((transaction) => ({
            kind: transaction.transactionType === 'INCOME' ? 'in' : 'out',
            date: transaction.transactionDate,
            m: Number(transaction.transactionDate.slice(5, 7)),
            cat: transaction.category,
            memo: transaction.description,
            amt: Number(transaction.amount),
            sourceFileId: result.fileId,
          }));
          addFileResult(rows, {
            ...fileInfo,
            count: result.transactions.length,
            skipped: result.skippedCount,
            persisted: result.persisted,
          });
          if (result.evidence?.items?.length) addEvidenceResult(result.evidence, fileInfo);
          toast(`${file.name}: ${result.transactions.length}건 반영, ${result.skippedCount}건 제외`);
          onUploadSuccess?.(result);
        } catch (error) {
          // 토스트가 사라져도 실패 원인을 확인할 수 있도록 화면에 남긴다.
          const message = controller.signal.aborted
            ? '요청 대기를 중단했습니다. 서버에서 이미 저장한 파일은 삭제되지 않습니다.'
            : `${file.name}: ${error.message}`;
          setUploadErrors((previous) => [...previous, message]);
          if (controller.signal.aborted) break;
          toast(message);
        }
      }
    } finally {
      uploadController.current = null;
      uploading.current = false;
      setLoading(false);
      setCurrentName('');
    }
  };

  return (
    <Card title="파일로 불러오기" icon="uploadCloud">
      <div
        className={`drop ${files.length ? 'on' : ''}`}
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault();
          accept(event.dataTransfer.files);
        }}
      >
        <Icon name="uploadCloud" size={40} />
        <p style={{ fontSize: 14, fontWeight: 600 }}>장부·금액 검토 문서 PDF 업로드 (최대 5개)</p>
        <p className="small muted">{YEAR}년 귀속 · 파일당 최대 6MB · 텍스트 PDF와 이미지 PDF를 분석합니다.</p>
        <p className="small muted">
          PDF의 금액 후보를 추출해 아래에 표시합니다. 거래 정보가 명확한 내역은 계산 목록에 반영하고, 표의 숫자와 OCR
          결과는 원본 확인이 필요한 후보로 표시합니다.
        </p>
        <p className="small muted">
          금액 검토 문서는 별도 분석 결과로 표시하며, 기재금액을 경비나 공제액으로 자동 합산하지 않습니다.
        </p>
        <p className="small muted">
          {loggedIn
            ? '업로드한 원본 PDF와 인식된 거래를 서버에 저장합니다.'
            : '비회원은 분석 결과만 체험하며, 원본 PDF와 거래는 서버에 보관하지 않습니다.'}
        </p>
        {files.length > 0 && (
          <div className="small" style={{ overflowWrap: 'anywhere' }}>
            <p>
              처리한 파일 {files.length}/{MAX_FILES}
            </p>
            {files.map((file) => {
              if (file.status)
                return (
                  <div
                    key={file.key}
                    style={{ padding: '10px 0', borderBottom: '1px solid var(--border-soft)', textAlign: 'left' }}
                  >
                    <p>
                      {file.name} · 금액 검토 문서 · {file.persisted ? '원본 저장 완료' : '체험 완료'}
                    </p>
                    <p>
                      {EVIDENCE_STATUSES[file.status] || '분석 상태 확인 필요'} · 기재금액 후보 {file.count}건 · 거래
                      계산에는 미포함
                    </p>
                    <Button size="sm" variant="ghost" disabled={loading} onClick={() => removeUploadedFile(file.key)}>
                      목록에서 제거
                    </Button>
                  </div>
                );
              const relatedRows = fileRows.filter((row) => row.sourceFileKey === file.key);
              const selectedCount = relatedRows.filter((row) => row.selected !== false).length;
              return (
                <div
                  key={file.key}
                  style={{ padding: '10px 0', borderBottom: '1px solid var(--border-soft)', textAlign: 'left' }}
                >
                  <label style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                    <input
                      type="checkbox"
                      checked={selectedCount > 0}
                      disabled={loading || !relatedRows.length}
                      onChange={(event) => setFileSelected(file.key, event.target.checked)}
                      aria-label={`${file.name} 계산에 포함`}
                    />
                    <span>
                      {file.name} · {file.persisted ? '저장 완료' : '체험 완료'} · {selectedCount}/{relatedRows.length}
                      건 선택 · {file.skipped}건 인식 제외
                    </span>
                  </label>
                  {!relatedRows.length && (
                    <p className="small muted" style={{ marginTop: 6 }}>
                      계산할 거래가 없습니다. 문서 형식이 지원되는지 확인하거나 직접 입력 방식을 이용해 주세요.
                    </p>
                  )}
                  <Button size="sm" variant="ghost" disabled={loading} onClick={() => removeUploadedFile(file.key)}>
                    목록에서 제거
                  </Button>
                </div>
              );
            })}
            <p className="small muted" style={{ marginTop: 8 }}>
              선택 해제·목록 제거는 현재 계산에만 적용됩니다. 서버에 저장한 원본과 DB 기록은 삭제하지 않습니다.
            </p>
          </div>
        )}
        {loading && (
          <div role="status" className="small">
            <p>{currentName} 업로드 및 분석 중...</p>
            <p>파일당 최대 2분까지 걸릴 수 있습니다. 여러 파일은 순서대로 처리합니다.</p>
            <Button size="sm" variant="ghost" onClick={() => uploadController.current?.abort()}>
              요청 대기 중단
            </Button>
          </div>
        )}
        {uploadErrors.length > 0 && (
          <div role="alert" className="small" style={{ overflowWrap: 'anywhere', marginTop: 10 }}>
            {uploadErrors.map((message, index) => (
              <p key={index}>{message}</p>
            ))}
          </div>
        )}
        <Button
          size="sm"
          variant="soft"
          disabled={loading || files.length >= MAX_FILES}
          onClick={() => inputRef.current?.click()}
        >
          파일 선택
        </Button>
      </div>
      <input
        ref={inputRef}
        type="file"
        multiple
        hidden
        disabled={loading || files.length >= MAX_FILES}
        accept=".pdf,application/pdf"
        onChange={(event) => {
          const selected = Array.from(event.target.files);
          event.target.value = '';
          accept(selected);
        }}
      />
    </Card>
  );
}
