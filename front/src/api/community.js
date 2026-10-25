import data from '../mocks/community.json';
import { delay } from './delay';

// MOCK: 실제 API로 교체할 때도 같은 시그니처(async, 반환 형태)를 유지한다
export async function getCommunity() {
  await delay();
  const posts = await listAnonymousPosts();
  const boards = { INFO: 'info', TIP: 'tips', QNA: 'qna' };
  return {
    tabs: [...data.tabs, { id: 'qna', label: '문의하기' }],
    posts: [
      ...posts.map((post) => ({
        id: post.postId,
        postId: post.postId,
        board: boards[post.boardType] || 'info',
        tag: post.boardType,
        tone: 'primary',
        title: post.title,
        author: '익명',
        views: 0,
        comments: 0,
        time: new Date(post.createdAt).toLocaleDateString('ko-KR'),
      })),
      ...data.posts.map((post) => ({ ...post, author: '익명' })),
    ],
  };
}
