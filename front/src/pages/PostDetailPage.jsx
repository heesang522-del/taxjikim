// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Modal, Pill } from '../components/common/index';
import NoticeCard from '../components/post/NoticeCard';
import CommentSection from '../components/post/CommentSection';
import ReportModal from '../components/post/ReportModal';
import { DELETED_POST } from '../data/options';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { usePost } from '../store/usePostStore';
import { useToast } from '../store/useToastStore';
import { useReports } from '../utils/useReports';
import { createPostReport } from '../api/report';
import { deletePost, getPost } from '../api/post';
import { imagePreviewProps, videoPreviewProps } from '../store/post-preview';

const listOf = (tab) => `${ROUTES.postList}?tab=${encodeURIComponent(tab)}`;
// 서버 글은 숫자 postId, 브라우저에만 남아있는 예시 글은 문자열 id를 쓴다. SupportQnaPostPage와 같은 구분 방식.
const isServerId = (id) => /^\d+$/.test(id);

export function PostDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  // 게시판 목록에서 들어왔으면 "목록"을 눌렀을 때 그 목록 화면(탭·검색어·페이지)으로 그대로 돌아간다.
  // 알림 클릭 등으로 상세 페이지에 직접 들어온 경우는 돌아갈 목록 화면이 없어 기본 목록으로 보낸다.
  const cameFromList = Boolean(location.state?.fromList);
  const toast = useToast();
  const { loggedIn, user } = useAuth();
  const { addReport, hasReported } = useReports();
  const { posts, removePost } = usePost();
  const [serverPost, setServerPost] = useState(null);
  const [loading, setLoading] = useState(isServerId(id));
  const [loadError, setLoadError] = useState(false);

  // 서버 글은 상세 API로 직접 조회한다. 목록을 먼저 거치지 않고(알림 클릭 등) 바로 들어와도 복원되고,
  // 이 호출에서 서버가 조회수(하루 1회 제한)도 함께 반영한다.
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

  const [confirm, setConfirm] = useState(false);
  const [reporting, setReporting] = useState(false);
  // 브라우저에만 남아있는 예시 글은 usePostStore가 들고 있는 로컬 목록에서 찾는다.
  const post = isServerId(id) ? serverPost : posts.find((p) => p.id === id);

  if (loading)
    return (
      <p role="status" className="state-msg">
        게시글을 불러오는 중입니다.
      </p>
    );

  // 글이 없을 때(잘못된 주소·삭제됨)와 조회 자체가 실패했을 때(네트워크 오류 등)를 같은 안내로 묶는다.
  // 전자는 404라서 "없음"과 "오류"를 사용자 입장에서 구분해 보여줄 이유가 없다.
  if (!post || loadError) {
    return (
      <NoticeCard
        title="게시글을 찾을 수 없습니다"
        desc="삭제되었거나 주소가 잘못되었습니다."
        action={
          cameFromList ? (
            <Button variant="primary" onClick={() => navigate(-1)}>
              목록으로
            </Button>
          ) : (
            <Button variant="primary" to={ROUTES.postList}>
              목록으로
            </Button>
          )
        }
      />
    );
  }

  if (post.deletedByAdmin) {
    return (
      <NoticeCard
        title={DELETED_POST}
        desc="운영 정책에 따라 삭제된 글입니다."
        action={
          cameFromList ? (
            <Button variant="primary" onClick={() => navigate(-1)}>
              목록으로
            </Button>
          ) : (
            <Button variant="primary" to={listOf(post.tab)}>
              목록으로
            </Button>
          )
        }
      />
    );
  }

  // 서버 글의 소유자는 표시 닉네임 대신 변경되지 않는 회원 번호로 비교한다.
  const mine =
    loggedIn &&
    (isServerId(id) ? String(post.userId) === String(user.userId) : post.author === user.nickname);
  // 서버 글은 백엔드 신고 API를 호출한다. 브라우저에만 있는 예시 글은 아직 신고 API가 없어 로컬 기록으로 처리한다.
  const report = async (reason) => {
    if (isServerId(id)) {
      try {
        await createPostReport(post.id, reason);
        toast('신고를 접수했습니다');
      } catch (error) {
        toast(error.message || '신고 접수에 실패했습니다.');
      }
      setReporting(false);
      return;
    }
    const target = { targetType: 'post', postId: post.id };
    if (hasReported(user.nickname, target)) toast('이미 신고한 글입니다');
    else {
      addReport({ ...target, reason, reporter: user.nickname });
      toast('신고를 접수했습니다');
    }
    setReporting(false);
  };
  const remove = async () => {
    try {
      // 실제 게시글은 서버에서 소유자를 확인한 뒤 소프트 삭제한다. 더 이상 로컬에 서버 글 목록을
      // 들고 있지 않으므로(상세는 매번 API로 직접 조회), 지울 것도 없이 바로 목록으로 돌아간다.
      if (isServerId(id)) await deletePost(post.id);
      else removePost(post.id);
      toast('글을 삭제했습니다');
      navigate(listOf(post.tab), { replace: true });
    } catch (error) {
      toast(error.response?.data?.error?.message || error.message || '삭제에 실패했습니다.');
    }
  };

  return (
    <>
      <Card>
        <Pill tone={post.tab === '공지' ? 'danger' : 'primary'}>{post.tab}</Pill>
        <h2 className="post-title">{post.title}</h2>
        <p className="small muted">
          {post.author} · {post.createdAt} · 조회 {post.views.toLocaleString('ko-KR')}
        </p>
        <div className="post-body">{post.body}</div>
        {post.attachments?.length > 0 ? (
          <div className="grid" style={{ gap: 16, marginTop: 16 }}>
            {post.attachments.map((file, index) => (
              <figure key={file.fileId || file.storedFileName} style={{ margin: 0 }}>
                {/* 우클릭과 기본 다운로드 메뉴를 제한한다. 파일 접근 권한을 대신하는 보안 장치는 아니다. */}
                {file.previewUrl &&
                  (file.mediaType === 'video' ? (
                    <video
                      {...videoPreviewProps}
                      src={file.previewUrl}
                      controls
                      preload="metadata"
                      style={{ maxWidth: '100%', maxHeight: 560, borderRadius: 8 }}
                    />
                  ) : (
                    <img
                      {...imagePreviewProps}
                      src={file.previewUrl}
                      alt={`첨부 사진 ${index + 1}`}
                      loading="lazy"
                      style={{
                        display: 'block',
                        maxWidth: '100%',
                        maxHeight: 560,
                        objectFit: 'contain',
                        borderRadius: 8,
                      }}
                    />
                  ))}
              </figure>
            ))}
          </div>
        ) : (
          post.files?.length > 0 && (
            <p className="small muted" style={{ marginTop: 8 }}>
              첨부: {post.files.join(', ')}
            </p>
          )
        )}
        <div className="row between" style={{ marginTop: 16 }}>
          {cameFromList
            ? <Button onClick={() => navigate(-1)}>목록</Button>
            : <Button to={listOf(post.tab)}>목록</Button>}
          {mine && (
            <div className="row">
              <Button variant="soft" to={ROUTES.postEdit(post.id)}>
                수정
              </Button>
              <Button variant="danger" onClick={() => setConfirm(true)}>
                삭제
              </Button>
            </div>
          )}
          {!mine && (
            <Button
              variant="ghost"
              onClick={() => {
                if (loggedIn) setReporting(true);
                else {
                  toast('신고는 로그인 후 이용할 수 있습니다');
                  navigate(ROUTES.auth);
                }
              }}
            >
              신고
            </Button>
          )}
        </div>
      </Card>
      <CommentSection key={post.id} postId={post.id} />
      {reporting && <ReportModal targetLabel="게시글" onSubmit={report} onClose={() => setReporting(false)} />}
      {confirm && (
        <Modal title="글 삭제" onClose={() => setConfirm(false)}>
          <div className="grid" style={{ gap: 16, textAlign: 'center' }}>
            <div>
              <h3 style={{ fontSize: 18, marginBottom: 6 }}>이 글을 삭제할까요?</h3>
              <p className="small muted">삭제한 글은 되돌릴 수 없습니다.</p>
            </div>
            <div className="grid" style={{ gap: 8 }}>
              <Button variant="danger" size="lg" onClick={remove}>
                삭제
              </Button>
              <Button size="lg" autoFocus onClick={() => setConfirm(false)}>
                취소
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
