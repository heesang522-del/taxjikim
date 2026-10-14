// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '../components/common/index';
import PostForm from '../components/community/PostForm';
import NoticeCard from '../components/community/NoticeCard';
import { ROUTES } from '../utils/routes';
import { BOARD_WRITE_TABS } from '../data/content';
import { useAuth } from '../store/useAuthStore';
import { useBoard } from '../store/useBoardStore';
import { useToast } from '../store/useToastStore';

export default function BoardWritePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { loggedIn, isAdmin } = useAuth();
  const { addPost } = useBoard();

  if (!loggedIn) {
    return <NoticeCard title="로그인이 필요합니다" desc="글쓰기는 로그인 후 이용할 수 있습니다." action={<Button variant="primary" to={ROUTES.auth}>로그인</Button>} />;
  }

  // 공지 분류는 관리자만 쓸 수 있다. 일반 회원이 ?tab=공지로 들어오면 첫 번째 분류로 바꾼다.
  const writable = isAdmin ? BOARD_WRITE_TABS : BOARD_WRITE_TABS.filter((t) => t !== '공지');
  const tab = writable.includes(params.get('tab')) ? params.get('tab') : writable[0];
  const submit = (values) => {
    addPost(values);
    toast('글을 등록했습니다');
    navigate(`${ROUTES.board}?tab=${encodeURIComponent(values.tab)}`, { replace: true });
  };

  return (
    <PostForm
      heading="글쓰기"
      initial={{ tab, title: '', body: '', files: [] }}
      submitLabel="등록"
      onSubmit={submit}
      onCancel={() => navigate(`${ROUTES.board}?tab=${encodeURIComponent(tab)}`)}
    />
  );
}
