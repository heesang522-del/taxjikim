// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { SimSliders } from '../components/calculator/SimSliders';
import { SimCompare } from '../components/calculator/SimCompare';
import { getTaxCalculationResult, getSavedTaxResult, simulateTax } from '../api/taxApi';
import { useTax } from '../store/useTaxStore';
import { ScenarioTable } from '../components/calculator/ScenarioTable';
import { useAuth } from '../store/useAuthStore';
import { ROUTES } from '../utils/routes';

export function CalculatorSimPage() {
  const { loggedIn, authStatus } = useAuth();
  const { state } = useLocation();
  const [params] = useSearchParams();
  const taxInputId = Number(params.get('taxInputId'));
  const saved = params.get('saved') === 'true';
  const { sim, patchSim } = useTax();
  const [result, setResult] = useState(state?.taxResult ?? null);
  const [scenario, setScenario] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  // 결과창에서 받은 데이터를 우선 사용하고, 직접 접근한 경우에만 기준 결과를 조회한다.
  useEffect(() => {
    if (!loggedIn || authStatus === 'checking') return;
    const controller = new AbortController();
    setErrorMessage('');
    if (state?.taxResult) {
      setResult(state.taxResult);
      return () => controller.abort();
    }
    setResult(null);
    if (!Number.isSafeInteger(taxInputId) || taxInputId <= 0) {
      setErrorMessage('결과 화면에서 시뮬레이션으로 이동해주세요.');
      setIsLoading(false);
      return;
    }
    async function loadResult() {
      try {
        const response = saved
          ? await getSavedTaxResult(taxInputId, { signal: controller.signal })
          : await getTaxCalculationResult(taxInputId, { signal: controller.signal });
        if (!controller.signal.aborted) setResult(response.data);
      } catch (error) {
        if (!controller.signal.aborted) {
          setErrorMessage(error.message || '기준 결과를 불러오지 못했습니다.');
          setIsLoading(false);
        }
      }
    }
    loadResult();
    return () => controller.abort();
  }, [state, taxInputId, saved, loggedIn, authStatus]);

  // 추가 금액만 서버에 전달한다. 입력을 멈춘 뒤 요청하고 이전 응답은 반영하지 않는다.
  useEffect(() => {
    if (!loggedIn || authStatus === 'checking' || !result?.breakdown) return;
    const controller = new AbortController();
    setIsLoading(true);
    setErrorMessage('');
    setScenario(null);
    const timer = setTimeout(async () => {
      const base = result.breakdown;
      const input = {
        taxYear: base.taxYear,
        dependentCount: base.dependentCount,
        businessRegistered: result.businessRegistered,
        totalIncome: base.totalIncome,
        totalExpense: base.totalExpense,
        specialDonationAmount: base.specialDonationAmount,
        generalDonationAmount: base.generalDonationAmount,
      };
      try {
        const response = await simulateTax(
          input,
          {
            extraInc: sim.extraInc,
            extraExp: sim.extraExp,
            extraDonation: sim.extraDonation ?? 0,
          },
          { signal: controller.signal }
        );
        if (!controller.signal.aborted) setScenario(response.data);
      } catch (error) {
        if (!controller.signal.aborted) setErrorMessage(error.message || '시뮬레이션 계산에 실패했습니다.');
      } finally {
        if (!controller.signal.aborted) setIsLoading(false);
      }
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [result, sim.extraInc, sim.extraExp, sim.extraDonation, loggedIn, authStatus]);

  // 저장한 추가 가정을 입력칸에 적용하면 기존 재계산 Effect가 실행된다.
  function handleLoadScenario(savedScenario) {
    patchSim({
      extraInc: savedScenario.extraInc,
      extraExp: savedScenario.extraExp,
      extraDonation: savedScenario.extraDonation,
    });
  }

  // 새로고침 시 인증 복원이 끝난 후 판단하며, 비회원은 계산 화면을 표시하지 않는다.
  if (authStatus === 'checking') return <p>로그인 상태를 확인하고 있습니다.</p>;
  if (!loggedIn) return <Navigate to={ROUTES.auth} replace />;

  return (
    <div className="grid g-side">
      <div className="grid" style={{ minWidth: 0 }}>
        <SimSliders
          taxInputId={taxInputId}
          scenario={scenario}
          predictedIncome={result?.predictedIncome}
          predictedExpense={result?.predictedExpense}
          disabled={isLoading || Boolean(errorMessage)}
        />
        <ScenarioTable onLoad={handleLoadScenario} />
      </div>
      <SimCompare base={result?.breakdown} scenario={scenario} isLoading={isLoading} errorMessage={errorMessage} />
    </div>
  );
}
