// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { getBoardPosts } from '../api/post.js';

const KEY = 'taxlab.board.v2';
const OLD_KEY = 'taxlab.board.v1';
const COMMENT_KEY = 'taxlab.comments.v1';

const isComment = (c) => c && [c.id, c.postId, c.author, c.body, c.createdAt].every((v) => typeof v === 'string');
const isPost = (p) => p && [p.id, p.tab, p.title, p.body, p.author, p.createdAt].every((v) => typeof v === 'string') && Number.isFinite(p.views);

const load = (key, valid) => {
  try {
    const list = JSON.parse(localStorage.getItem(key));
    if (Array.isArray(list)) return list.filter(valid);
  } catch { /* 저장값이 깨진 경우 예시 글로 시작 */ }
  return null;
};
const save = (key, value) => {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
};

const readComments = () => load(COMMENT_KEY, isComment) ?? [];

// 기존 브라우저 글을 복원한다. 저장 이력이 없으면 빈 목록으로 시작하고 서버 글만 보여준다.
const readPosts = () => load(KEY, isPost) ?? load(OLD_KEY, isPost) ?? [];
const mergePosts = (serverPosts, browserPosts) => {
  const serverIds = new Set(serverPosts.map((post) => post.id));
  return [...serverPosts, ...browserPosts.filter((post) => post.source !== 'server' && !serverIds.has(post.id))];
};

/** 다른 탭(예: 관리자 탭)에서 바뀐 글·댓글을 바로 반영한다. */
export const syncBoardFromStorage = (key) => {
  // 다른 탭의 브라우저 글 변경도 서버 글을 유지하면서 반영한다.
  if (key === KEY) {
    const serverPosts = useBoardStore.getState().posts.filter((post) => post.source === 'server');
    useBoardStore.setState({ posts: mergePosts(serverPosts, readPosts()) });
  }
  if (key === COMMENT_KEY) useBoardStore.setState({ comments: readComments() });
};

/**
 * 서버 게시글과 (남아있다면) 과거 브라우저 저장 글을 함께 표시한다.
 * deletedByAdmin이 true인 글은 관리자 삭제로 내용을 가리고 "관리자에 의해 삭제됨" 흔적만 남긴다.
 * comments/adminDeleteComment는 AdminReportsPage의 신고 댓글 처리용으로만 남겨 둔다.
 */
export const useBoardStore = create((set, get) => {
  // 서버 글을 브라우저에 복제 저장하면 삭제 후 다시 나타날 수 있어 예시 글만 저장한다.
  const setPosts = (fn) => { const next = fn(get().posts); save(KEY, next.filter((post) => post.source !== 'server')); set({ posts: next }); };
  const setComments = (fn) => { const next = fn(get().comments); save(COMMENT_KEY, next); set({ comments: next }); };

  return {
    posts: readPosts(),
    // DB 최신 목록에 기존 브라우저 글을 더하고 같은 ID는 서버 데이터를 우선한다.
    loadPosts: async () => {
      const posts = await getBoardPosts();
      set({ posts: mergePosts(posts.map((post) => ({ ...post, source: 'server' })), readPosts()) });
    },
    comments: readComments(),

    updatePost: (id, { tab, title, body, files }) => {
      setPosts((list) => list.map((p) => (p.id === id ? { ...p, tab, title: title.trim(), body: body.trim(), ...(files ? { files } : {}) } : p)));
    },
    removePost: (id) => setPosts((list) => list.filter((p) => p.id !== id)),
    addView: (id) => setPosts((list) => list.map((p) => (p.id === id ? { ...p, views: p.views + 1 } : p))),
    /** 신고 처리: 글을 지우지 않고 관리자 삭제 표시만 남긴다 */
    adminDeletePost: (id) => setPosts((list) => list.map((p) => (p.id === id ? { ...p, deletedByAdmin: true } : p))),

    // comments: 신고 관리자 화면(AdminReportsPage)이 신고된 댓글을 찾아 "관리자 삭제" 표시를 남기는 데만 쓴다.
    // 실제(서버) 댓글은 각 CommentSection이 API로 직접 불러와 이 배열에 올라오지 않는다.
    adminDeleteComment: (id) => setComments((list) => list.map((c) => (c.id === id ? { ...c, deletedByAdmin: true } : c))),
  };
});

export const useBoard = () => useBoardStore();
