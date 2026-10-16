import { client } from './client.js';

async function requestComments(postId, config = {}) {
  try {
    // 댓글도 공통 client를 사용하며 인증 쿠키는 브라우저가 자동으로 보낸다.
    const response = await client.request({
      baseURL: '/api',
      url: `/api/posts/${encodeURIComponent(postId)}/comments`,
      ...config,
    });
    if (!response.data?.success) throw new Error('댓글 요청에 실패했습니다.');
    return response.data.data;
  } catch (error) {
    throw new Error(error.response?.data?.error?.message || error.message || '댓글 요청에 실패했습니다.');
  }
}

export async function getComments(postId) {
  const comments = await requestComments(postId);
  // DB 응답을 기존 댓글 UI 필드에 맞추되 작성자 회원 번호를 함께 보관한다.
  return comments.map((comment) => ({
    id: String(comment.commentId),
    postId: String(comment.postId),
    userId: comment.userId,
    body: comment.content,
    author: comment.writer,
    createdAt: comment.createdAt,
    updatedAt: comment.updatedAt,
  }));
}

export function createComment(postId, content) {
  return requestComments(postId, { method: 'POST', data: { content } });
}

export function deleteComment(postId, commentId) {
  return requestComments(postId, {
    url: `/api/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`,
    method: 'DELETE',
  });
}
