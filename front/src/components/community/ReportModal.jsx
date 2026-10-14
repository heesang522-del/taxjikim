// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Modal, TextAreaField } from '../common/index';
import { useToast } from '../../store/useToastStore';

/** 게시글·댓글 신고 사유 입력 창. onSubmit(reason)은 사유가 있을 때만 부른다. */
export default function ReportModal({ targetLabel, onSubmit, onClose }) {
  const toast = useToast();
  const [reason, setReason] = useState('');
  const submit = () => {
    if (!reason.trim()) { toast('신고 사유를 입력하세요'); return; }
    onSubmit(reason);
  };
  return (
    <Modal title={`${targetLabel} 신고`} onClose={onClose}>
      <div className="grid" style={{ gap: 12, minWidth: 280 }}>
        <div>
          <h3 style={{ fontSize: 18, marginBottom: 6 }}>{targetLabel}을(를) 신고할까요?</h3>
          <p className="small muted">관리자가 확인한 뒤 삭제하거나 반려합니다.</p>
        </div>
        <TextAreaField id="reportReason" label="신고 사유" rows={3} maxLength={200} placeholder="예: 광고성 링크, 욕설, 허위 정보" value={reason} onChange={(e) => setReason(e.target.value)} />
        <div className="grid" style={{ gap: 8 }}>
          <Button variant="danger" size="lg" onClick={submit}>신고</Button>
          <Button size="lg" onClick={onClose}>취소</Button>
        </div>
      </div>
    </Modal>
  );
}
