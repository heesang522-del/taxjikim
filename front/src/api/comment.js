import { client } from './client.js';

async function requestComments(postId, config = {}) {
  try {
    // baseURL에 /api가 있으므로 요청 경로에는 중복해서 붙이지 않는다. 인증은 쿠키를 사용한다.
    const response = await client.request({
      baseURL: '/api',
      url: `/posts/${encodeURIComponent(postId)}/comments`,
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
  // parentCommentId가 있으면 대댓글이므로, 화면에서 부모 댓글 id와 비교할 수 있게 문자열로 맞춘다.
  return comments.map((comment) => ({
    id: String(comment.commentId),
    postId: String(comment.postId),
    userId: comment.userId,
    parentCommentId: comment.parentCommentId != null ? String(comment.parentCommentId) : null,
    body: comment.content,
    author: comment.writer,
    createdAt: comment.createdAt,
    updatedAt: comment.updatedAt,
    // 부모가 삭제됐어도 살아있는 답글이 있으면 자리는 유지된 채로 내려온다.
    // deletedByType('SELF'|'ADMIN')이 있으면 삭제된 댓글이고 body/author가 없다.
    deleted: comment.deletedByType != null,
  }));
}

export function createComment(postId, content, parentCommentId = null) {
  return requestComments(postId, { method: 'POST', data: { content, parentCommentId } });
}

export function updateComment(postId, commentId, content) {
  return requestComments(postId, {
    url: `/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`,
    method: 'PATCH',
    data: { content },
  });
}

export function deleteComment(postId, commentId) {
  return requestComments(postId, {
    url: `/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`,
    method: 'DELETE',
  });
}
