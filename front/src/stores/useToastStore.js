// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { create } from 'zustand';

let timer;

// 화면 아래에 잠깐 보였다 사라지는 안내 문구
export const useToastStore = create((set) => ({
  message: '',
  toast: (msg) => {
    set({ message: msg });
    clearTimeout(timer);
    timer = setTimeout(() => set({ message: '' }), 2200);
  },
}));

/** 사용: const toast = useToast(); toast('저장했습니다') */
export const useToast = () => useToastStore((state) => state.toast);
