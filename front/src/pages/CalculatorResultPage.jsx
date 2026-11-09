// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getTaxCalculationResult, getSavedTaxResult, saveTaxResult } from '../api/taxApi';
import { Button, Icon, Note } from '../components/common/index';
import { SummaryCards } from '../components/calculator/result/SummaryCards';
import { TaxChain } from '../components/calculator/result/TaxChain';
import { MonthlyChart } from '../components/calculator/result/MonthlyChart';
import { CategoryRatioCharts } from '../components/calculator/result/CategoryRatioCharts';
import RegistrationCompare from '../components/calculator/result/RegistrationCompare';
import { ROUTES } from '../utils/routes';
import { useTax } from '../store/useTaxStore';
import { useAuth } from '../store/useAuthStore';
import { useToast } from '../store/useToastStore';

export default function CalcResultPage() {
  const [searchParams] = useSearchParams();
  const { search, state } = useLocation();
  const navigate = useNavigate();
  const taxInputId = Number(searchParams.get('taxInputId'));
  const validTaxInputId = Number.isSafeInteger(taxInputId) && taxInputId > 0;
  const savedResult = searchParams.get('saved') === 'true';
  const { calculated } = useTax();
  const [result, setResult] = useState(savedResult ? null : state?.taxResult ?? null);
  const breakdown = result?.breakdown;
  const [isLoading, setIsLoading] = useState(savedResult || !state?.taxResult);
  const [errorMessage, setErrorMessage] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const autoSaveStarted = useRef(false);
  const toast = useToast();

  // 결과를 한 번 조회하고 산출 근거와 월별 목록을 각 컴포넌트에 나눠 전달한다.
  useEffect(() => {
    // 로그인 후 돌아온 경우 당시 화면 데이터를 유지하고 다시 계산하지 않는다.
    if (!savedResult && state?.taxResult) {
      setResult(state.taxResult);
      setIsLoading(false);
      return;
    }
    if ((!calculated && !savedResult) || !validTaxInputId) return;
    let cancelled = false;
    const controller = new AbortController();

    // 서버의 계산 결과를 받아 표시하며 기존 예시 계산값으로 대체하지 않는다.
    async function loadResult() {
      setIsLoading(true);
      setErrorMessage('');
      setResult(null);
      try {
        // 저장 결과는 DB의 JSON을 그대로 사용하고, 새 분석일 때만 계산 API를 호출한다.
        const response = savedResult
          ? await getSavedTaxResult(taxInputId, { signal: controller.signal })
          : await getTaxCalculationResult(taxInputId, { signal: controller.signal });
        if (!cancelled) setResult(response.data);
      } catch (error) {
        // 소유자가 아닌 저장 결과 URL은 결과를 표시하지 않고 마이페이지로 보낸다.
        if (!cancelled && error.status === 403) { toast('접근할 수 없는 분석입니다.'); navigate(ROUTES.mypage, { replace: true }); return; }
        if (!cancelled) setErrorMessage(error.message || '계산 결과를 불러오지 못했습니다.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }
    loadResult();
    // 화면 이탈·분석번호 변경 후 도착한 이전 응답은 반영하지 않는다.
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [calculated, savedResult, taxInputId, validTaxInputId, state, navigate, toast]);
  const { loggedIn } = useAuth();
  // 페이지의 결과 전체를 저장하며 브라우저의 요약 기록으로 대체하지 않는다.
  const handleSaveResult = useCallback(async () => {
    if (isSaving) return;
    if (!breakdown || isLoading || errorMessage) {
      toast('계산 결과를 확인한 뒤 저장해주세요');
      return;
    }
    if (!loggedIn) {
      navigate(ROUTES.auth, {
        state: {
          returnTo: `${ROUTES.calcResult}${search}`,
          returnState: { taxResult: result, saveAfterLogin: true },
        },
      });
      return;
    }
    setIsSaving(true);
    try {
      await saveTaxResult(taxInputId, result);
      toast('저장되었습니다');
    } catch (error) {
      toast(error.message || '결과를 저장하지 못했습니다');
    } finally {
      setIsSaving(false);
    }
  }, [isSaving, breakdown, isLoading, errorMessage, loggedIn, navigate, search, result, taxInputId, toast]);

  // 비회원이 선택한 저장 동작은 로그인 후 한 번만 이어서 실행하고 현재 결과창에 머문다.
  useEffect(() => {
    if (!state?.saveAfterLogin || !loggedIn || !result || isLoading || autoSaveStarted.current) return;
    autoSaveStarted.current = true;
    void handleSaveResult();
    navigate(`${ROUTES.calcResult}${search}`, { replace: true, state: { taxResult: result } });
  }, [state, loggedIn, result, isLoading, handleSaveResult, navigate, search]);

  if (!validTaxInputId) {
    return <p role="alert">분석번호가 없습니다. 입력 화면에서 분석 결과 보기를 눌러주세요.</p>;
  }

  if (!calculated && !savedResult && !state?.taxResult)
    return <Navigate to={`${ROUTES.calcLoading}${search}`} replace />;

  return (
    <div className="grid">
      <p className="hint">
        총 세금은 기납부세액을 차감하기 전 소득세와 지방소득세의 합계이며, 추가 납부액이나 환급액이 아닙니다. 입력
        조건과 단순화 가정을 적용한 예상 결과이며 실제 신고 세액과 다를 수 있습니다.
      </p>
      <SummaryCards
        result={breakdown}
        actualIncome={result?.actualIncome}
        predictedIncome={result?.predictedIncome}
        actualExpense={result?.actualExpense}
        predictedExpense={result?.predictedExpense}
        settlementType={result?.settlementType}
        settlementAmount={result?.settlementAmount}
        isLoading={isLoading}
        errorMessage={errorMessage}
      />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        {!loggedIn && (
          <span className="small muted" style={{ alignSelf: 'center' }}>
            결과 저장과 시뮬레이션은 회원 전용입니다. 로그인 후 이용해주세요.
          </span>
        )}
        <Button onClick={handleSaveResult} disabled={isSaving}>
          {isSaving ? '저장 중…' : '결과 저장만 하기'}
        </Button>
        <Button
          variant="primary"
          to={loggedIn ? `${ROUTES.calcSim}${search}` : ROUTES.auth}
          state={
            loggedIn
              ? { taxResult: result }
              : {
                  returnTo: `${ROUTES.calcSim}${search}`,
                  returnState: { taxResult: result },
                }
          }
        >
          <Icon name="sliders" size={16} /> 시뮬레이션으로 이동
        </Button>
      </div>
      <div className="grid g2">
        <TaxChain result={breakdown} isLoading={isLoading} errorMessage={errorMessage} />
        <MonthlyChart
          incomes={result?.monthlyIncomes}
          expenses={result?.monthlyExpenses}
          isLoading={isLoading}
          errorMessage={errorMessage}
        >
          {result && (
            <CategoryRatioCharts
              incomeCategories={result.incomeCategories ?? []}
              expenseCategories={result.expenseCategories ?? []}
            />
          )}
        </MonthlyChart>
      </div>
      <RegistrationCompare businessRegistered={result?.businessRegistered} />
      <Note title="안내">
        이 결과는 입력값과 계산 가정에 따른 참고용 추정치이며 세무 조언이 아닙니다. 실제 신고 전에 국세청 자료를
        확인하거나 세무사와 상담하세요.
      </Note>
    </div>
  );
}
