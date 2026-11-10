import { client } from './client.js';

// 게시글 API의 공통 주소다.
const POST_API_BASE = '/api/v1/posts';

// 공통 응답 바디({success, data, meta, error})에서 실패 메시지를 뽑는다.
// success:false(HTTP 200)와 error.response.data(HTTP 4xx/5xx) 양쪽이 같은 모양이라 하나로 쓴다.
function extractErrorMessage(result, fallbackMessage) {
  return result?.error?.message || result?.message || fallbackMessage;
}

// 요청을 실행하고 공통 응답 바디를 풀어 반환한다. HTTP 상태와 무관하게 success가 아니면
// 서버 메시지로 던져서, 호출하는 쪽마다 성공/실패 두 가지 실패 경로(바디 success:false / axios 에러)를
// 따로 처리하지 않아도 되게 한다. 응답 자체가 없는 네트워크 오류는 원래 에러를 그대로 던진다.
async function unwrapRequest(promise, fallbackMessage) {
  let result;
  try {
    result = (await promise).data;
  } catch (error) {
    result = error.response?.data;
    if (!result) throw error;
  }
  if (!result?.success) {
    throw new Error(extractErrorMessage(result, fallbackMessage));
  }
  return result;
}

// 서버의 게시글을 기존 게시판 화면에서 사용하는 필드로 변환한다.
export function toPost(post) {
  const tabs = {
    INFO: '정보 공유',
    TIP: '팁',
    NOTICE: '공지',
  };

  return {
    id: String(post.postId),
    userId: post.userId,
    tab: tabs[post.postType] || post.postType,
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

// 서버가 페이지 단위로 내려주는 목록을 처음부터 끝까지 모아 하나의 배열로 돌려준다.
// getPosts(게시판 전체 캐시)·getInquiries(Q&A 전체 목록)처럼 서버 페이징 UI 없이
// 클라이언트에서 검색·필터링하는 화면이 쓴다. 페이지 단위로 보여주는 화면은 getPostsPage를 쓴다.
async function fetchAllPages(params, mapItem, fallbackMessage) {
  const items = [];
  let page = 1;
  let totalPages = 1;

  do {
    const result = await unwrapRequest(
      client.get('', { baseURL: POST_API_BASE, params: { ...params, page } }),
      fallbackMessage
    );

    if (!Array.isArray(result.data)) {
      throw new Error(fallbackMessage);
    }

    items.push(...result.data.map(mapItem));
    totalPages = result.meta?.totalPages || 1;
    page += 1;
  } while (page <= totalPages);

  return items;
}

// 관리자 "공지 관리"가 전체 공지를 한 번에 보여주는 데 쓴다.
export function getNotices() {
  return fetchAllPages({ postType: '공지' }, toPost, '공지 목록을 불러오지 못했습니다.');
}

// 게시판 목록을 한 페이지만 받아온다. 탭(postType)·검색어(keyword)·페이지 번호를 그대로 서버에 넘기고,
// 페이지 번호 UI에 쓸 meta(page/size/total/totalPages)도 함께 돌려준다.
// excludeNotice: true면 공지를 뺀 결과만 받는다("전체" 탭에서 공지를 상단에 따로 고정 노출할 때 쓴다).
export async function getPostsPage({ page = 1, postType, keyword, sort = 'latest', excludeNotice } = {}) {
  const fallbackMessage = '게시글 목록을 불러오지 못했습니다.';
  const result = await unwrapRequest(
    client.get('', {
      baseURL: POST_API_BASE,
      params: { page, sort, postType: postType || undefined, keyword: keyword || undefined, excludeNotice: excludeNotice || undefined },
    }),
    fallbackMessage
  );

  if (!Array.isArray(result.data)) {
    throw new Error(fallbackMessage);
  }

  return { posts: result.data.map(toPost), meta: result.meta ?? { page, size: result.data.length, total: result.data.length, totalPages: 1 } };
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

export function getInquiries() {
  return fetchAllPages({ postType: 'QNA' }, toInquiry, 'Q&A 목록을 불러오지 못했습니다.');
}

export async function getInquiryDetail(postId) {
  const result = await unwrapRequest(
    client.get(`/${encodeURIComponent(postId)}`, { baseURL: POST_API_BASE }),
    '문의를 불러오지 못했습니다.'
  );

  return toInquiry(result.data);
}

export async function deletePost(postId) {
  await unwrapRequest(
    client.delete(`/${encodeURIComponent(postId)}`, { baseURL: POST_API_BASE }),
    '게시글 삭제에 실패했습니다.'
  );
}

export async function updatePost(postId, formData) {
  await unwrapRequest(
    client.post(`/${encodeURIComponent(postId)}`, formData, { baseURL: POST_API_BASE }),
    '게시글 수정에 실패했습니다.'
  );
}

export async function createPost(formData) {
  try {
    // 기존 client의 인증 처리를 사용한다.
    // FormData의 Content-Type과 boundary는 브라우저가 설정한다.
    const response = await client.post('', formData, { baseURL: POST_API_BASE });

    if (!response.data?.success) {
      throw new Error(extractErrorMessage(response.data, '게시글 등록에 실패했습니다.'));
    }

    return response.data;
  } catch (error) {
    // 서버가 에러 메시지를 내려줬으면(보통 그렇다) 그대로 쓰고, 응답 바디 자체가 없는 401(세션 만료)만
    // 별도 안내로 바꾼다. unwrapRequest를 쓰지 않는 이유: 이 우선순위(401 고정 문구는 최후 폴백)를 유지하려면
    // 요청 실패 시의 fallbackMessage 자체를 상태 코드로 분기해야 해서, 공용 헬퍼로 묶기보다 여기서 직접 처리한다.
    throw new Error(
      extractErrorMessage(
        error.response?.data,
        error.response?.status === 401
          ? '로그인이 만료되었습니다. 다시 로그인해 주세요.'
          : error.message || '게시글 등록에 실패했습니다.'
      )
    );
  }
}
