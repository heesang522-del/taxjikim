// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button } from '../components/common/index';
import PostForm from '../components/post/PostForm';
import NoticeCard from '../components/post/NoticeCard';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../stores/useAuthStore';
import { usePost } from '../stores/usePostStore';
import { useToast } from '../stores/useToastStore';
import { getPost } from '../api/post';

// 서버 글은 숫자 postId, 브라우저에만 남아있는 예시 글은 문자열 id를 쓴다. PostDetailPage와 같은 구분 방식.
const isServerId = (id) => /^\d+$/.test(id);

export function PostEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { loggedIn, user } = useAuth();
  const { posts, updatePost } = usePost();
  const [serverPost, setServerPost] = useState(null);
  const [loading, setLoading] = useState(isServerId(id));
  const [loadError, setLoadError] = useState(false);

  // 서버 글은 상세 API로 직접 조회한다. 새로고침으로 바로 수정 주소에 들어와도(목록·상세를 안 거쳐도)
  // 복원되도록 하기 위함이다. 본인 글 조회라 조회수는 올라가지 않는다(findById가 작성자 본인은 제외한다).
  useEffect(() => {
    if (!isServerId(id)) return undefined;
    let active = true;
    setLoading(true);
    setLoadError(false);
    getPost(id)
      .then((data) => {
        if (active) setServerPost(data);
      })
      .catch(() => {
        if (active) setLoadError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id]);

  // 브라우저에만 남아있는 예시 글은 usePostStore가 들고 있는 로컬 목록에서 찾는다.
  const post = isServerId(id) ? serverPost : posts.find((p) => p.id === id);

  if (isServerId(id) && loading) {
    return (
      <p role="status" className="state-msg">
        게시글을 불러오는 중입니다.
      </p>
    );
  }

  if (!post || loadError) {
    return <NoticeCard title="게시글을 찾을 수 없습니다" desc="삭제되었거나 주소가 잘못되었습니다." action={<Button variant="primary" to={ROUTES.postList}>목록으로</Button>} />;
  }
  if (!loggedIn) {
    return <NoticeCard title="로그인이 필요합니다" desc="글 수정은 로그인 후 이용할 수 있습니다." action={<Button variant="primary" to={ROUTES.auth}>로그인</Button>} />;
  }
  const mine = isServerId(id)
    ? String(post.userId) === String(user?.userId)
    : post.author === user?.nickname;
  if (post.deletedByAdmin || !mine) {
    return <NoticeCard title="수정할 수 없는 글입니다" desc="내가 쓴 글만 수정할 수 있습니다." action={<Button variant="primary" to={ROUTES.postDetail(post.id)}>글로 돌아가기</Button>} />;
  }

  const submit = (values) => {
    updatePost(post.id, values);
    toast('글을 수정했습니다');
    navigate(ROUTES.postDetail(post.id), { replace: true });
  };

  return (
    <PostForm
      heading="글 수정"
      postId={isServerId(id) ? post.id : undefined}
      initial={{ tab: post.tab, title: post.title, body: post.body, files: post.attachments ?? post.files ?? [] }}
      submitLabel="수정 완료"
      onSubmit={submit}
      onCancel={() => navigate(ROUTES.postDetail(post.id))}
    />
  );
}
