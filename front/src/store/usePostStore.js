// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';
import { getPosts } from '../api/post.js';

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

// 기존 브라우저 글을 복원한다. 저장 이력이 없으면 빈 목록으로 시작하고 서버 글만 보여준다.
const readPosts = () => load(KEY, isPost) ?? load(OLD_KEY, isPost) ?? [];
const mergePosts = (serverPosts, browserPosts) => {
  const serverIds = new Set(serverPosts.map((post) => post.id));
  return [...serverPosts, ...browserPosts.filter((post) => post.source !== 'server' && !serverIds.has(post.id))];
};

/** 다른 탭(예: 관리자 탭)에서 바뀐 글을 바로 반영한다. */
export const syncPostFromStorage = (key) => {
  // 다른 탭의 브라우저 글 변경도 서버 글을 유지하면서 반영한다.
  if (key === KEY) {
    const serverPosts = usePostStore.getState().posts.filter((post) => post.source === 'server');
    usePostStore.setState({ posts: mergePosts(serverPosts, readPosts()) });
  }
};

/**
 * 서버 게시글과 (남아있다면) 과거 브라우저 저장 글을 함께 표시한다.
 * deletedByAdmin이 true인 글은 관리자 삭제로 내용을 가리고 "관리자에 의해 삭제됨" 흔적만 남긴다.
 */
export const usePostStore = create((set, get) => {
  // 서버 글을 브라우저에 복제 저장하면 삭제 후 다시 나타날 수 있어 예시 글만 저장한다.
  const setPosts = (fn) => { const next = fn(get().posts); save(KEY, next.filter((post) => post.source !== 'server')); set({ posts: next }); };

  return {
    posts: readPosts(),
    // DB 최신 목록에 기존 브라우저 글을 더하고 같은 ID는 서버 데이터를 우선한다.
    loadPosts: async () => {
      const posts = await getPosts();
      set({ posts: mergePosts(posts.map((post) => ({ ...post, source: 'server' })), readPosts()) });
    },

    updatePost: (id, { tab, title, body, files }) => {
      setPosts((list) => list.map((p) => (p.id === id ? { ...p, tab, title: title.trim(), body: body.trim(), ...(files ? { files } : {}) } : p)));
    },
    removePost: (id) => setPosts((list) => list.filter((p) => p.id !== id)),
    addView: (id) => setPosts((list) => list.map((p) => (p.id === id ? { ...p, views: p.views + 1 } : p))),
    /** 브라우저 예시 글 신고 처리: 글을 지우지 않고 관리자 삭제 표시만 남긴다 */
    adminDeletePost: (id) => setPosts((list) => list.map((p) => (p.id === id ? { ...p, deletedByAdmin: true } : p))),
  };
});

export const usePost = () => usePostStore();
