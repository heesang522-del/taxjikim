// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { BOARD_SEED, COMMENT_SEED } from '../data/content';
import { getCurrentUser } from './useAuthStore';

const KEY = 'taxlab.board.v2';
const OLD_KEY = 'taxlab.board.v1';
const COMMENT_KEY = 'taxlab.comments.v1';

// 글 하나가 올바른 모양인지 확인한다. (taxlab 원본은 job까지 검사했지만 예시 글에 job이 없어 새로고침하면 글이 모두 사라졌다)
const isPost = (p) => p && [p.id, p.tab, p.title, p.body, p.author, p.createdAt].every((v) => typeof v === 'string') && Number.isFinite(p.views);
const isComment = (c) => c && [c.id, c.postId, c.author, c.body, c.createdAt].every((v) => typeof v === 'string');
const today = () => new Date().toLocaleDateString('sv-SE'); // YYYY-MM-DD

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

// v2 저장값이 없으면 v1 글을 이어받고, 새로 추가된 예시 글(신고 확인용)만 덧붙인다. 삭제해서 빈 목록이 된 것도 그대로 둔다.
const readPosts = () => {
  const v2 = load(KEY, isPost);
  if (v2) return v2;
  const v1 = load(OLD_KEY, isPost);
  if (!v1) return BOARD_SEED;
  return [...v1, ...BOARD_SEED.filter((p) => p.id === 'p9' && !v1.some((o) => o.id === p.id))];
};
const readComments = () => load(COMMENT_KEY, isComment) ?? COMMENT_SEED;

/** 다른 탭(예: 관리자 탭)에서 바뀐 글·댓글을 바로 반영한다. */
export const syncBoardFromStorage = (key) => {
  if (key === KEY) useBoardStore.setState({ posts: readPosts() });
  if (key === COMMENT_KEY) useBoardStore.setState({ comments: readComments() });
};

/**
 * 게시판 글·댓글 목록(최신순). MOCK: 서버 연동 전까지 이 브라우저의 localStorage에 저장한다.
 * deletedByAdmin이 true인 글·댓글은 관리자 삭제로 내용을 가리고 "관리자에 의해 삭제됨" 흔적만 남긴다.
 */
export const useBoardStore = create((set, get) => {
  const setPosts = (fn) => { const next = fn(get().posts); save(KEY, next); set({ posts: next }); };
  const setComments = (fn) => { const next = fn(get().comments); save(COMMENT_KEY, next); set({ comments: next }); };

  return {
    posts: readPosts(),
    comments: readComments(),

    addPost: ({ tab, title, body, files = [] }) => {
      const post = { id: `p${Date.now()}`, tab, title: title.trim(), body: body.trim(), files, author: getCurrentUser()?.nickname ?? '', views: 0, createdAt: today() };
      setPosts((list) => [post, ...list]);
      return post.id;
    },
    updatePost: (id, { tab, title, body, files }) => {
      setPosts((list) => list.map((p) => (p.id === id ? { ...p, tab, title: title.trim(), body: body.trim(), ...(files ? { files } : {}) } : p)));
    },
    removePost: (id) => setPosts((list) => list.filter((p) => p.id !== id)),
    addView: (id) => setPosts((list) => list.map((p) => (p.id === id ? { ...p, views: p.views + 1 } : p))),
    /** 신고 처리: 글을 지우지 않고 관리자 삭제 표시만 남긴다 */
    adminDeletePost: (id) => setPosts((list) => list.map((p) => (p.id === id ? { ...p, deletedByAdmin: true } : p))),

    addComment: (postId, body) => {
      const comment = { id: `c${Date.now()}`, postId, author: getCurrentUser()?.nickname ?? '', body: body.trim(), createdAt: today(), deletedByAdmin: false };
      setComments((list) => [...list, comment]);
    },
    removeComment: (id) => setComments((list) => list.filter((c) => c.id !== id)),
    adminDeleteComment: (id) => setComments((list) => list.map((c) => (c.id === id ? { ...c, deletedByAdmin: true } : c))),
  };
});

export const useBoard = () => useBoardStore();
