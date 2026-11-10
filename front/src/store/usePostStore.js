// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';

const KEY = 'taxlab.post.v2';
const OLD_KEY = 'taxlab.post.v1';

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

// 브라우저에만 남아있는 과거 예시 글을 복원한다. 서버 글은 각 화면이 api/post.js로 직접 조회한다.
const readPosts = () => load(KEY, isPost) ?? load(OLD_KEY, isPost) ?? [];

/** 다른 탭(예: 관리자 탭)에서 바뀐 예시 글을 바로 반영한다. */
export const syncPostFromStorage = (key) => {
  if (key === KEY) {
    usePostStore.setState({ posts: readPosts() });
  }
};

/**
 * 브라우저에만 남아있는 과거 예시 글을 보여준다. 서버 게시글은 다루지 않는다(각 화면이 getPost·
 * getPostsPage로 직접 조회한다).
 * deletedByAdmin이 true인 글은 관리자 삭제로 내용을 가리고 "관리자에 의해 삭제됨" 흔적만 남긴다.
 */
export const usePostStore = create((set, get) => {
  const setPosts = (fn) => { const next = fn(get().posts); save(KEY, next); set({ posts: next }); };

  return {
    posts: readPosts(),

    updatePost: (id, { tab, title, body, files }) => {
      setPosts((list) => list.map((p) => (p.id === id ? { ...p, tab, title: title.trim(), body: body.trim(), ...(files ? { files } : {}) } : p)));
    },
    removePost: (id) => setPosts((list) => list.filter((p) => p.id !== id)),
    /** 브라우저 예시 글 신고 처리: 글을 지우지 않고 관리자 삭제 표시만 남긴다 */
    adminDeletePost: (id) => setPosts((list) => list.map((p) => (p.id === id ? { ...p, deletedByAdmin: true } : p))),
  };
});

export const usePost = () => usePostStore();
