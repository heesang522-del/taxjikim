import { client } from './client.js';

// 게시글 API의 공통 주소다.
const POST_API_BASE = '/api/v1/posts/community';

// 서버의 게시글을 기존 게시판 화면에서 사용하는 필드로 변환한다.
export function toBoardPost(post) {
  const tabs = {
    INFO: '정보 공유',
    TIP: '팁',
    NOTICE: '공지',
  };

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

    // 기존 파일 미리보기 주소는 유지한다.
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
  let totalPages = 1;

  // 기존 화면의 탭·검색을 위해 서버 페이지를 모두 읽는다. 페이징 정보는 meta에 담겨 온다.
  do {
    const response = await client.get('', {
      baseURL: POST_API_BASE,
      params: { page },
    });

    const result = response.data;

    if (!result?.success || !Array.isArray(result.data)) {
      throw new Error(result?.error?.message || result?.message || '게시글 목록을 불러오지 못했습니다.');
    }

    posts.push(...result.data.map(toBoardPost));
    totalPages = result.meta?.totalPages || 1;
    page += 1;
  } while (page <= totalPages);

  return posts;
}

// 게시판 목록을 한 페이지만 받아온다. 탭(boardType)·검색어(keyword)·페이지 번호를 그대로 서버에 넘기고,
// 페이지 번호 UI에 쓸 meta(page/size/total/totalPages)도 함께 돌려준다.
export async function getBoardPostsPage({ page = 1, boardType, keyword, sort = 'latest' } = {}) {
  const response = await client.get('', {
    baseURL: POST_API_BASE,
    params: { page, sort, boardType: boardType || undefined, keyword: keyword || undefined },
  });

  const result = response.data;

  if (!result?.success || !Array.isArray(result.data)) {
    throw new Error(result?.error?.message || result?.message || '게시글 목록을 불러오지 못했습니다.');
  }

  return { posts: result.data.map(toBoardPost), meta: result.meta ?? { page, size: result.data.length, total: result.data.length, totalPages: 1 } };
}

// 서버의 QNA 게시글을 Q&A 화면에서 쓰는 필드로 변환한다.
export function toInquiry(post) {
  return {
    id: String(post.postId),
    userId: post.userId,
    title: post.title || '',
    body: post.content || '',
    // Lombok의 boolean 필드 isPublic은 Jackson이 "is"를 떼고 public으로 직렬화한다.
    visibility: post.public ? 'public' : 'private',
    author: post.writer || '알 수 없는 사용자',
    status: post.answerStatus === 'DONE' ? '답변 완료' : '답변 대기',
    createdAt: post.createdAt || '',
    files: (post.files || []).map((file) => file.originalFileName || file.storedFileName),
  };
}

export async function getInquiries() {
  const inquiries = [];
  let page = 1;
  let totalPages = 1;

  do {
    const response = await client.get('', {
      baseURL: POST_API_BASE,
      params: { page, boardType: 'QNA' },
    });

    const result = response.data;

    if (!result?.success || !Array.isArray(result.data)) {
      throw new Error(result?.error?.message || result?.message || 'Q&A 목록을 불러오지 못했습니다.');
    }

    inquiries.push(...result.data.map(toInquiry));
    totalPages = result.meta?.totalPages || 1;
    page += 1;
  } while (page <= totalPages);

  return inquiries;
}

export async function getInquiryDetail(postId) {
  try {
    const response = await client.get(`/${encodeURIComponent(postId)}`, {
      baseURL: POST_API_BASE,
    });

    if (!response.data?.success) {
      throw new Error(response.data?.error?.message || response.data?.message || '문의를 불러오지 못했습니다.');
    }

    return toInquiry(response.data.data);
  } catch (error) {
    const result = error.response?.data;

    throw new Error(result?.error?.message || result?.message || error.message || '문의를 불러오지 못했습니다.');
  }
}

export async function deletePost(postId) {
  const response = await client.delete(`/${encodeURIComponent(postId)}`, {
    baseURL: POST_API_BASE,
  });

  if (!response.data?.success) {
    throw new Error(response.data?.error?.message || response.data?.message || '게시글 삭제에 실패했습니다.');
  }
}

export async function updatePost(postId, formData) {
  const response = await client.post(`/${encodeURIComponent(postId)}`, formData, {
    baseURL: POST_API_BASE,
  });

  if (!response.data?.success) {
    throw new Error(response.data?.error?.message || response.data?.message || '게시글 수정에 실패했습니다.');
  }
}

export async function createPost(formData) {
  try {
    // 기존 client의 인증 처리를 사용한다.
    // FormData의 Content-Type과 boundary는 브라우저가 설정한다.
    const response = await client.post('', formData, {
      baseURL: POST_API_BASE,
    });

    if (!response.data?.success) {
      throw new Error(response.data?.error?.message || response.data?.message || '게시글 등록에 실패했습니다.');
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
