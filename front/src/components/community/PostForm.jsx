// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Card, SelectField, TextAreaField, TextField } from '../common/index';
import { BOARD_WRITE_TABS } from '../../data/content';
import { useAuth } from '../../store/useAuthStore';
import { useToast } from '../../store/useToastStore';

/** 글쓰기·수정 공용 폼. initial: { tab, title, body, files }, onSubmit(values). 공지 분류는 관리자만 고를 수 있다. */
export default function PostForm({ heading, initial, submitLabel, onSubmit, onCancel }) {
  const toast = useToast();
  const { isAdmin } = useAuth();
  const [tab, setTab] = useState(initial.tab);
  const [title, setTitle] = useState(initial.title);
  const [body, setBody] = useState(initial.body);
  const [files, setFiles] = useState(initial.files ?? []); // 프로토타입: 파일 자체는 저장하지 않고 이름만 남긴다

  const tabOptions = isAdmin ? BOARD_WRITE_TABS : BOARD_WRITE_TABS.filter((t) => t !== '공지');

  const submit = (e) => {
    e.preventDefault();
    if (!title.trim() || !body.trim()) { toast('제목과 내용을 입력하세요'); return; }
    onSubmit({ tab, title, body, files });
  };

  return (
    <Card title={heading} icon="fileText">
      <form className="grid" style={{ gap: 12 }} onSubmit={submit}>
        <div className="row">
          <SelectField id="pCat" label="분류" options={tabOptions} value={tab} onChange={(e) => setTab(e.target.value)} />
        </div>
        <TextField id="pTitle" label="제목" placeholder="제목을 입력하세요" maxLength={60} value={title} onChange={(e) => setTitle(e.target.value)} />
        <TextAreaField id="pBody" label="내용" rows={10} placeholder="내용을 입력하세요" maxLength={3000} value={body} onChange={(e) => setBody(e.target.value)} />
        <div className="field">
          <label htmlFor="pFiles">파일 첨부 (선택)</label>
          <input id="pFiles" className="in" type="file" multiple onChange={(e) => setFiles([...e.target.files].map((f) => f.name))} />
          {files.length > 0 && <p className="small muted" style={{ marginTop: 6 }}>첨부: {files.join(', ')}</p>}
        </div>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button onClick={onCancel}>취소</Button>
          <Button type="submit" variant="primary">{submitLabel}</Button>
        </div>
      </form>
    </Card>
  );
}
