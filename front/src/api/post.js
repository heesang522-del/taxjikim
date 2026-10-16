// src/api/posts.js
import { client } from './client.js';

// 서버의 게시글을 기존 게시판 화면에서 사용하는 필드로 변환한다.
export function toBoardPost(post) {
  const tabs = { INFO: '정보 공유', TIP: '팁', NOTICE: '공지' };
  return {
    id: String(post.postId),
    userId: post.userId,
    tab: tabs[post.boardType] || post.boardType,
    title: post.title || '',
    body: post.content || '',
    author: post.writer || '알 수 없는 사용자',
    views: post.viewCount || 0,
    createdAt: post.createdAt || '',
    files: (post.files || []).map((file) => file.originalFileName || file.storedFileName),
    // 파일명과 함께 공개 조회 URL을 보존해야 상세·수정 화면에서 실제 미디어를 표시할 수 있다.
    attachments: (post.files || []).map((file) => {
      const name = file.originalFileName || file.storedFileName;
      const storedName = file.storedFileName || '';
      return {
        fileId: file.fileId,
        name,
        storedFileName: storedName,
        previewUrl: storedName ? `/uploads/files/${encodeURIComponent(storedName)}` : null,
        mediaType: /\.(mp4|webm|mov)$/i.test(storedName || name) ? 'video' : 'image',
      };
    }),
  };
}

export async function getBoardPosts() {
  const posts = [];
  let page = 1;
  let hasNext;
  // 기존 화면의 탭·검색은 전체 목록을 대상으로 하므로 서버 페이지를 모두 읽는다.
  do {
    const response = await client.get('/posts', { baseURL: '/api', params: { page } });
    const result = response.data;
    if (!result?.success || !Array.isArray(result.data?.posts)) {
      throw new Error(result?.error?.message || '게시글 목록을 불러오지 못했습니다.');
    }
    posts.push(...result.data.posts.map(toBoardPost));
    hasNext = result.data.hasNext === true;
    page += 1;
  } while (hasNext);
  return posts;
}

export async function deletePost(postId) {
  // 기존 게시판 경로를 유지하고 인증은 브라우저가 보내는 HttpOnly 쿠키에 맡긴다.
  await client.delete(`/posts/${encodeURIComponent(postId)}`, {
    baseURL: '/api',
  });
}

export async function updatePost(postId, formData) {
  const response = await client.post(`/posts/${encodeURIComponent(postId)}`, formData, {
    baseURL: '/api',
  });
  if (!response.data?.success) throw new Error('게시글 수정에 실패했습니다.');
}

// 게시글 작성 API 호출 함수
export async function createPost(formData) {
  try {
    // 토큰을 읽거나 저장하지 않고 서버가 인증 쿠키로 작성자를 확인한다.
    // FormData의 Content-Type과 boundary는 브라우저가 함께 설정한다.
    const response = await client.post('/posts', formData, {
      baseURL: '/api',
    });
    if (!response.data?.success) {
      throw new Error(response.data?.message || '게시글 등록에 실패했습니다.');
    }
    return response.data;
  } catch (error) {
    const result = error.response?.data;
    throw new Error(
      result?.error?.message ||
        result?.message ||
        (error.response?.status === 401
          ? '로그인이 만료되었습니다. 다시 로그인해 주세요.'
          : error.message || '게시글 등록에 실패했습니다.')
    );
  }
}
