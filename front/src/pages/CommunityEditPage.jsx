// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useNavigate, useParams } from 'react-router-dom';
import { Button } from '../components/common/index';
import PostForm from '../components/community/PostForm';
import NoticeCard from '../components/community/NoticeCard';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { useBoard } from '../store/useBoardStore';
import { useToast } from '../store/useToastStore';

export default function BoardEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { loggedIn, user } = useAuth();
  const { posts, updatePost } = useBoard();
  const post = posts.find((p) => p.id === id);

  if (!post) {
    return <NoticeCard title="게시글을 찾을 수 없습니다" desc="삭제되었거나 주소가 잘못되었습니다." action={<Button variant="primary" to={ROUTES.board}>목록으로</Button>} />;
  }
  if (!loggedIn) {
    return <NoticeCard title="로그인이 필요합니다" desc="글 수정은 로그인 후 이용할 수 있습니다." action={<Button variant="primary" to={ROUTES.auth}>로그인</Button>} />;
  }
  const mine = post.source === 'server'
    ? String(post.userId) === String(user?.userId)
    : post.author === user?.nickname;
  if (post.deletedByAdmin || !mine) {
    return <NoticeCard title="수정할 수 없는 글입니다" desc="내가 쓴 글만 수정할 수 있습니다." action={<Button variant="primary" to={ROUTES.boardPost(post.id)}>글로 돌아가기</Button>} />;
  }

  const submit = (values) => {
    updatePost(post.id, values);
    toast('글을 수정했습니다');
    navigate(ROUTES.boardPost(post.id), { replace: true });
  };

  return (
    <PostForm
      heading="글 수정"
      postId={post.source === 'server' ? post.id : undefined}
      initial={{ tab: post.tab, title: post.title, body: post.body, files: post.attachments ?? post.files ?? [] }}
      submitLabel="수정 완료"
      onSubmit={submit}
      onCancel={() => navigate(ROUTES.boardPost(post.id))}
    />
  );
}
