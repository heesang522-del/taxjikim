// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Modal, Pill } from '../components/common/index';
import NoticeCard from '../components/community/NoticeCard';
import CommentSection from '../components/community/CommentSection';
import ReportModal from '../components/community/ReportModal';
import { DELETED_POST } from '../data/options';
import { ROUTES } from '../utils/routes';
import { useAuth } from '../store/useAuthStore';
import { useBoard } from '../store/useBoardStore';
import { useToast } from '../store/useToastStore';
import { useReports } from '../utils/useReports';
import { deletePost } from '../api/post';
import { imagePreviewProps, videoPreviewProps } from '../store/post-preview';

const listOf = (tab) => `${ROUTES.board}?tab=${encodeURIComponent(tab)}`;

export default function BoardPostPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { loggedIn, user } = useAuth();
  const { addReport, hasReported } = useReports();
  const { posts, addView, removePost, loadPosts } = useBoard();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  // 상세 주소를 직접 열거나 새로고침해도 서버 게시글을 복원한다.
  useEffect(() => {
    let active = true;
    loadPosts()
      .catch(() => {
        if (active) setLoadError('게시글을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [loadPosts]);
  const [confirm, setConfirm] = useState(false);
  const [reporting, setReporting] = useState(false);
  const post = posts.find((p) => p.id === id);
  const counted = useRef(null);

  // 글을 열 때 조회수를 한 번만 올린다 (개발 모드의 이펙트 이중 실행 방지)
  const exists = Boolean(post);
  useEffect(() => {
    if (exists && counted.current !== id) {
      counted.current = id;
      addView(id);
    }
  }, [exists, id, addView]);

  if (loading)
    return (
      <p role="status" className="state-msg">
        게시글을 불러오는 중입니다.
      </p>
    );
  if (loadError)
    return (
      <p role="alert" className="state-msg">
        {loadError}
      </p>
    );

  if (!post) {
    return (
      <NoticeCard
        title="게시글을 찾을 수 없습니다"
        desc="삭제되었거나 주소가 잘못되었습니다."
        action={
          <Button variant="primary" to={ROUTES.board}>
            목록으로
          </Button>
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
          <Button variant="primary" to={listOf(post.tab)}>
            목록으로
          </Button>
        }
      />
    );
  }

  // 서버 글의 소유자는 표시 닉네임 대신 변경되지 않는 회원 번호로 비교한다.
  const mine =
    loggedIn &&
    (post.source === 'server' ? String(post.userId) === String(user.userId) : post.author === user.nickname);
  const report = (reason) => {
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
      // 실제 게시글은 서버에서 소유자를 확인한 뒤 소프트 삭제한다.
      if (post.source === 'server') await deletePost(post.id);
      removePost(post.id);
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
          <Button to={listOf(post.tab)}>목록</Button>
          {mine && (
            <div className="row">
              <Button variant="soft" to={ROUTES.boardEdit(post.id)}>
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
      <CommentSection key={post.id} postId={post.id} serverPost={post.source === 'server'} />
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
