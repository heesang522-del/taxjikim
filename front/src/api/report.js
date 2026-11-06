import { client } from './client.js';

// 게시글 신고 API의 공통 주소다.
const POST_API_BASE = '/api/v1/posts/community';
const ADMIN_REPORT_API_BASE = '/api/v1/admin/reports';

// 관리자 신고 목록을 AdminReportsPage가 쓰는 필드로 변환한다.
export function toReport(report) {
  const isComment = report.targetType === 'COMMENT';
  return {
    id: String(report.reportId),
    source: 'server',
    targetType: isComment ? 'comment' : 'post',
    postId: isComment ? undefined : String(report.targetId),
    commentId: isComment ? String(report.targetId) : undefined,
    reason: report.reasonDetail || '',
    reporter: report.reporterNickname || '-',
    targetAuthor: report.targetUserNickname || '-',
    createdAt: report.createdAt || '',
    status: report.status === 'PENDING' ? '대기' : '처리됨',
    result: report.result === 'DELETE' ? '삭제' : report.result === 'REJECT' ? '반려' : null,
    postTitle: report.postTitle,
    postDeletedAt: report.postDeletedAt,
    postDeletedByType: report.postDeletedByType,
    commentContent: report.commentContent,
    commentDeletedAt: report.commentDeletedAt,
    commentDeletedByType: report.commentDeletedByType,
  };
}

export async function createPostReport(postId, reasonDetail) {
  try {
    const response = await client.post(`/${encodeURIComponent(postId)}/reports`, { reasonDetail }, {
      baseURL: POST_API_BASE,
    });

    if (!response.data?.success) {
      throw new Error(response.data?.error?.message || response.data?.message || '신고 접수에 실패했습니다.');
    }
  } catch (error) {
    const result = error.response?.data;
    throw new Error(result?.error?.message || result?.message || error.message || '신고 접수에 실패했습니다.');
  }
}

export async function createCommentReport(postId, commentId, reasonDetail) {
  try {
    // client의 기본 baseURL(/api/v1)을 그대로 쓴다. 댓글 API 경로와 맞춘다.
    const response = await client.post(
      `/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}/reports`,
      { reasonDetail }
    );

    if (!response.data?.success) {
      throw new Error(response.data?.error?.message || response.data?.message || '신고 접수에 실패했습니다.');
    }
  } catch (error) {
    const result = error.response?.data;
    throw new Error(result?.error?.message || result?.message || error.message || '신고 접수에 실패했습니다.');
  }
}

export async function getAdminReports() {
  const response = await client.get('', { baseURL: ADMIN_REPORT_API_BASE });

  if (!response.data?.success || !Array.isArray(response.data.data)) {
    throw new Error(response.data?.error?.message || response.data?.message || '신고 목록을 불러오지 못했습니다.');
  }

  return response.data.data.map(toReport);
}

// result: 'DELETE' | 'REJECT'
export async function resolveReport(reportId, result) {
  const response = await client.patch(`/${encodeURIComponent(reportId)}`, { result }, {
    baseURL: ADMIN_REPORT_API_BASE,
  });

  if (!response.data?.success) {
    throw new Error(response.data?.error?.message || response.data?.message || '신고 처리에 실패했습니다.');
  }
}
