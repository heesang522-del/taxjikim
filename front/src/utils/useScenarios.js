'use no memo';

import { useEffect } from 'react';
import { create } from 'zustand';
import { useAuth } from '../stores/useAuthStore';
import { getSimulationScenarios, saveSimulationScenario, deleteSimulationScenario } from '../api/taxApi';

// DB 조회 결과를 화면끼리 공유하는 메모리 상태다. persist나 브라우저 저장소는 사용하지 않는다.
const useScenarioStore = create((set, get) => ({
  userId: null,
  taxInputId: null,
  requestId: 0,
  scenarios: [],
  isLoading: false,
  isSaving: false,
  deletingId: null,
  errorMessage: '',

  // 회원 또는 분석이 바뀌면 해당 분석의 목록만 조회한다.
  load: async (userId, taxInputId) => {
    // 결과 교체 후 다시 진입하면 삭제된 시나리오가 남지 않도록 DB 목록을 새로 읽는다.
    if (get().userId === userId && get().taxInputId === taxInputId && get().isLoading) return;
    const requestId = get().requestId + 1;
    set({ userId, taxInputId, requestId, scenarios: [], isLoading: true, errorMessage: '' });
    try {
      const response = await getSimulationScenarios(taxInputId);
      // 분석을 빠르게 바꿨을 때 늦게 도착한 이전 응답은 반영하지 않는다.
      if (get().requestId === requestId) set({ scenarios: response.data });
    } catch (error) {
      if (get().requestId === requestId) set({ errorMessage: error.message || '시나리오를 불러오지 못했습니다.' });
    } finally {
      if (get().requestId === requestId) set({ isLoading: false });
    }
  },

  // DB 저장이 성공한 응답만 목록에 추가한다.
  save: async (userId, scenario) => {
    if (!userId) return 'login';
    if (get().isSaving) return 'busy';
    set({ isSaving: true });
    try {
      const response = await saveSimulationScenario(scenario);
      if (get().userId === userId && get().taxInputId === scenario.taxInputId) {
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
  remove: async (userId, taxInputId, simulationId) => {
    if (!userId || get().deletingId !== null) return false;
    set({ deletingId: simulationId });
    try {
      await deleteSimulationScenario(simulationId);
      if (get().userId === userId && get().taxInputId === taxInputId) {
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
  clear: () => set((state) => ({ userId: null, taxInputId: null, requestId: state.requestId + 1, scenarios: [], isLoading: false, errorMessage: '' })),
}));

// 같은 분석의 입력 영역과 저장 목록이 DB 응답을 메모리에서 공유한다.
export function useScenarios(taxInputId) {
  const { user } = useAuth();
  const userId = user?.userId ?? user?.id ?? null;
  const state = useScenarioStore();
  const matches = state.userId === userId && state.taxInputId === taxInputId;
  useEffect(() => {
    if (userId) useScenarioStore.getState().load(userId, taxInputId);
    else useScenarioStore.getState().clear();
  }, [userId, taxInputId]);

  return {
    scenarios: matches ? state.scenarios : [],
    isLoading: Boolean(userId && (!matches || state.isLoading)),
    isSaving: state.isSaving,
    deletingId: state.deletingId,
    errorMessage: matches ? state.errorMessage : '',
    save: (scenario) => state.save(userId, { ...scenario, taxInputId }),
    remove: (simulationId) => state.remove(userId, taxInputId, simulationId),
  };
}
