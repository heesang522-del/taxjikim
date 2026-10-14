// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useRef, useState } from 'react';
import { Button, Card, Icon } from '../common/index';
import { useToast } from '../../store/useToastStore';
import { useTax } from '../../store/useTaxStore';
import { extractPdfLines, parsePdfLines } from '../../utils/importPdf';

export default function FileDropzone() {
  const inputRef = useRef(null);
  const [fileName, setFileName] = useState('');
  const toast = useToast();
  const { addRow, addPendingRows } = useTax();

  const accept = async (file) => {
    setFileName(file.name);
    if (!/\.pdf$/i.test(file.name)) {
      toast('PDF 파일만 올릴 수 있어요. 다른 형식은 아래에서 직접 입력해 주세요.');
      return;
    }
    try {
      const { confident, ambiguous } = parsePdfLines(await extractPdfLines(file));
      confident.forEach((row) => addRow(row));
      if (ambiguous.length) addPendingRows(ambiguous);
      const parts = [];
      if (confident.length) parts.push(`${confident.length}건 추가`);
      if (ambiguous.length) parts.push(`${ambiguous.length}건 확인 필요`);
      toast(parts.length ? parts.join(' · ') : '읽을 수 있는 내역을 찾지 못했습니다');
    } catch {
      toast('PDF를 읽지 못했습니다. 글자를 복사할 수 있는 PDF인지 확인해 주세요.');
    }
  };

  return (
    <Card title="파일로 불러오기" icon="uploadCloud">
      <div
        className={`drop ${fileName ? 'on' : ''}`}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => { e.preventDefault(); if (e.dataTransfer.files[0]) accept(e.dataTransfer.files[0]); }}
      >
        <Icon name="uploadCloud" size={40} />
        <p style={{ fontSize: 14, fontWeight: 600 }}>장부 내역 PDF 업로드</p>
        <p className="small" style={{ color: 'var(--text-faint)' }}>글자를 복사할 수 있는 PDF만 읽을 수 있어요(스캔 이미지는 불가). 한 줄에 날짜·구분·카테고리·내용·금액이 있으면 바로 내역에 추가하고, 확실히 읽지 못한 내역은 아래 "확인이 필요한 내역"에 따로 모아 보여드려요.</p>
        {fileName && <p className="small" style={{ color: 'var(--primary)', fontWeight: 600 }}>선택한 파일: {fileName}</p>}
        <div className="row" style={{ justifyContent: 'center' }}>
          <Button size="sm" variant="soft" onClick={() => inputRef.current?.click()}>파일 선택</Button>
        </div>
      </div>
      <input ref={inputRef} type="file" hidden accept=".pdf,application/pdf" onChange={(e) => e.target.files[0] && accept(e.target.files[0])} />
    </Card>
  );
}
