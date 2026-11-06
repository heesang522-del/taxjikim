'use no memo';

import { useEffect } from 'react';
import { create } from 'zustand';
import { useAuth } from '../store/useAuthStore';
import { getSimulationScenarios, saveSimulationScenario, deleteSimulationScenario } from '../api/taxApi';

// DB 조회 결과를 화면끼리 공유하는 메모리 상태다. persist나 브라우저 저장소는 사용하지 않는다.
const useScenarioStore = create((set, get) => ({
  userId: null,
  scenarios: [],
  isLoading: false,
  isSaving: false,
  deletingId: null,
  loaded: false,
  errorMessage: '',

  // 로그인한 회원이 바뀌면 이전 목록을 비우고 서버에서 본인 목록을 가져온다.
  load: async (userId) => {
    if (get().userId === userId && (get().isLoading || get().loaded)) return;
    set({ userId, scenarios: [], isLoading: true, loaded: false, errorMessage: '' });
    try {
      const response = await getSimulationScenarios();
      if (get().userId === userId) set({ scenarios: response.data, loaded: true });
    } catch (error) {
      if (get().userId === userId) set({ errorMessage: error.message || '시나리오를 불러오지 못했습니다.' });
    } finally {
      if (get().userId === userId) set({ isLoading: false });
    }
  },

  // DB 저장이 성공한 응답만 목록에 추가한다.
  save: async (userId, scenario) => {
    if (!userId) return 'login';
    if (get().isSaving) return 'busy';
    set({ isSaving: true });
    try {
      const response = await saveSimulationScenario(scenario);
      if (get().userId === userId) {
        set({ scenarios: [...get().scenarios, response.data], errorMessage: '' });
      }
      return 'ok';
    } catch (error) {
      return error.code === 'SCENARIO_LIMIT_REACHED' ? 'max' : 'error';
    } finally {
      set({ isSaving: false });
    }
  },

  // 삭제 요청이 성공한 뒤에만 화면에서 해당 시나리오를 제거한다.
  remove: async (userId, simulationId) => {
    if (!userId || get().deletingId !== null) return false;
    set({ deletingId: simulationId });
    try {
      await deleteSimulationScenario(simulationId);
      if (get().userId === userId) {
        set({ scenarios: get().scenarios.filter((row) => row.simulationId !== simulationId) });
      }
      return true;
    } catch {
      return false;
    } finally {
      set({ deletingId: null });
    }
  },

  // 로그아웃 시 화면 메모리만 정리한다. 저장소나 쿠키를 읽거나 삭제하지 않는다.
  clear: () => set({ userId: null, scenarios: [], loaded: false, isLoading: false, errorMessage: '' }),
}));

// 마이페이지와 시뮬레이션 화면은 같은 DB 목록과 저장·삭제 동작을 사용한다.
export function useScenarios() {
  const { user } = useAuth();
  const userId = user?.userId ?? user?.id ?? null;
  const state = useScenarioStore();
  useEffect(() => {
    if (userId) useScenarioStore.getState().load(userId);
    else useScenarioStore.getState().clear();
  }, [userId]);

  return {
    scenarios: state.userId === userId ? state.scenarios : [],
    isLoading: state.isLoading,
    isSaving: state.isSaving,
    deletingId: state.deletingId,
    errorMessage: state.userId === userId ? state.errorMessage : '',
    save: (scenario) => state.save(userId, scenario),
    remove: (simulationId) => state.remove(userId, simulationId),
  };
}
