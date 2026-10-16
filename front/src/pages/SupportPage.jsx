// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, DataTable, Pill, Segmented, Tabs, TextAreaField, TextField } from '../components/common/index';
import { HELP_TABS, LIBRARY } from '../data/content';
import { ROUTES } from '../utils/routes';
import { useToast } from '../store/useToastStore';
import { useAuth } from '../store/useAuthStore';
import { useInquiry } from '../store/useInquiryStore';
import { useFaq } from '../utils/useFaq';

const VISIBILITY_OPTIONS = [{ value: 'public', label: '공개' }, { value: 'private', label: '비공개' }];

function QnaForm({ onSubmit, onCancel }) {
  const toast = useToast();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [visibility, setVisibility] = useState('public');
  const [files, setFiles] = useState([]); // 프로토타입: 파일 자체는 저장하지 않고 이름만 남긴다

  const submit = () => {
    if (!title.trim() || !body.trim()) { toast('제목과 내용을 입력하세요'); return; }
    onSubmit({ title, body, visibility, files });
  };

  return (
    <Card>
      <div className="grid" style={{ gap: 12 }}>
        <TextField id="qTitle" label="제목" placeholder="제목을 입력하세요" maxLength={60} value={title} onChange={(e) => setTitle(e.target.value)} />
        <TextAreaField id="qBody" label="문의 내용" rows={4} placeholder="문의 내용을 입력하세요" maxLength={2000} value={body} onChange={(e) => setBody(e.target.value)} />
        <div className="field">
          <label htmlFor="qFiles">파일 첨부 (선택)</label>
          <input id="qFiles" className="in" type="file" multiple onChange={(e) => setFiles([...e.target.files].map((f) => f.name))} />
          {files.length > 0 && <p className="small muted" style={{ marginTop: 6 }}>첨부: {files.join(', ')}</p>}
        </div>
        <div className="field">
          <label>공개 여부</label>
          <Segmented options={VISIBILITY_OPTIONS} value={visibility} onChange={setVisibility} label="공개 여부" />
          <p className="small muted" style={{ marginTop: 6 }}>비공개로 올리면 나와 관리자만 볼 수 있어요. 관리자 답변은 글을 열어서 확인합니다.</p>
        </div>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button onClick={onCancel}>취소</Button>
          <Button variant="primary" onClick={submit}>등록</Button>
        </div>
      </div>
    </Card>
  );
}

const HELP_TAB_IDS = HELP_TABS.map(([id]) => id);

export default function HelpPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState(HELP_TAB_IDS.includes(params.get('tab')) ? params.get('tab') : 'faq');
  const [writingQna, setWritingQna] = useState(false);
  const toast = useToast();
  const navigate = useNavigate();
  const { loggedIn, user, isAdmin } = useAuth();
  const { inquiries, addInquiry } = useInquiry();
  const { faqs } = useFaq();
  const todo = (m) => () => toast(`프로토타입: ${m}`);

  // 공개 글은 모두에게, 비공개 글은 작성자와 관리자에게만 보인다 (비회원·다른 회원에게는 목록에도 나오지 않는다)
  const visibleInquiries = inquiries
    .filter((q) => q.visibility === 'public' || isAdmin || (loggedIn && q.author === user.nickname))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  // 등록하면 방금 쓴 글의 상세 페이지로 이동한다
  const submitQna = ({ title, body, visibility, files }) => {
    const id = addInquiry({ title, body, visibility, files });
    toast(visibility === 'private' ? '비공개로 등록했습니다. 나와 관리자만 볼 수 있어요' : '문의를 등록했습니다');
    navigate(ROUTES.qnaPost(id));
  };

  return (
    <Card>
      <div style={{ marginBottom: 16 }}><Tabs tabs={HELP_TABS.map(([id, label]) => ({ id, label }))} active={tab} onChange={setTab} /></div>
      {tab === 'faq' && faqs.map((f) => <details key={f.id}><summary>{f.q}</summary><p>{f.a}</p></details>)}
      {tab === 'lib' && <DataTable columns={[{ key: 'n', header: '자료', render: (x) => x }, { key: 'd', header: '', align: 'right', render: () => <Pill tone="primary">다운로드</Pill> }]} rows={LIBRARY} rowKey={(x) => x} onRowClick={todo('자료 다운로드')} />}
      {tab === 'qna' && (
        <div className="grid" style={{ gap: 12 }}>
          <div className="row between">
            <p className="small muted">궁금한 점을 올리면 다른 이용자도 볼 수 있어요. 개인 정보가 담긴 문의는 비공개로 올려주세요.</p>
            {loggedIn && !writingQna && <Button variant="soft" size="sm" onClick={() => setWritingQna(true)}>글쓰기</Button>}
            {!loggedIn && <Button variant="soft" size="sm" to={ROUTES.auth}>로그인 후 글쓰기</Button>}
          </div>
          {writingQna && <QnaForm onSubmit={submitQna} onCancel={() => setWritingQna(false)} />}
          {visibleInquiries.length
            ? <DataTable
              columns={[
                { key: 't', header: '제목', render: (q) => <>{q.visibility === 'private' && <Pill>비공개</Pill>} {q.title}</> },
                { key: 'a', header: '작성자', render: (q) => q.author },
                { key: 'd', header: '등록일', render: (q) => q.createdAt },
                { key: 's', header: '상태', align: 'right', render: (q) => <Pill tone={q.status === '답변 완료' ? 'success' : 'warn'}>{q.status}</Pill> },
              ]}
              rows={visibleInquiries}
              rowKey={(q) => q.id}
              onRowClick={(q) => navigate(ROUTES.qnaPost(q.id))}
            />
            : <p className="state-msg">등록된 문의가 없습니다</p>}
        </div>
      )}
    </Card>
  );
}
