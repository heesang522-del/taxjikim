import { create } from 'zustand';
import { getMyRoom } from '../api/chat';

export const useMemberRoomStore = create((set, get) => {
  let pendingRequest = null;

  return {
    memberId: null,
    room: null,
    roomLoading: false,
    roomError: '',
    loaded: false,

    setRoom: (update) =>
      set((state) => ({
        room: typeof update === 'function' ? update(state.room) : update,
      })),

    resetRoom: () => {
      // 로그아웃하면 이전 회원의 상담 상태와 요청 공유를 해제한다.
      pendingRequest = null;
      set({ memberId: null, room: null, roomLoading: false, roomError: '', loaded: false });
    },

    loadRoom: (memberId, refresh = false) => {
      if (!memberId) return Promise.resolve();
      if (get().memberId !== memberId) {
        get().resetRoom();
        set({ memberId });
      }
      // 두 화면의 동시 조회와 개발 모드 재실행은 같은 요청을 기다린다.
      if (pendingRequest) return pendingRequest;
      if (get().loaded && !refresh) return Promise.resolve();

      set({ roomLoading: true, roomError: '' });
      const request = getMyRoom()
        .then((result) => {
          // 계정이 바뀐 뒤 도착한 응답으로 다른 회원의 상태를 덮어쓰지 않는다.
          if (pendingRequest === request) set({ room: result.data, loaded: true });
        })
        .catch((error) => {
          if (pendingRequest === request) {
            set({ roomError: error.status && error.message ? error.message : '진행 중인 상담을 확인하지 못했습니다.' });
          }
        })
        .finally(() => {
          if (pendingRequest === request) {
            pendingRequest = null;
            set({ roomLoading: false });
          }
        });
      pendingRequest = request;
      return request;
    },
  };
});
