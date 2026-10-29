// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useMemo } from 'react';
import { useTax } from '../store/useTaxStore';
import { calcTax, calcSimple } from './tax';

/** 현재 입력 기준 계산 결과. opts를 넘기면 시뮬레이션 가정값을 반영한다. */
export function useTaxResult(opts) {
  const { rows, profile } = useTax();
  const key = opts ? `${opts.extraExp}|${opts.extraInc}|${opts.yellow}|${opts.excludeWithholding}` : 'base';
  // 총 세금 화면은 기납부세액을 차감하지 않고, 기존 시뮬레이션은 종전 계산을 유지한다.
  const auto33 = opts?.excludeWithholding ? false : profile.auto33;
  return useMemo(() => calcTax(rows, auto33, opts), [rows, auto33, key]); // eslint-disable-line react-hooks/exhaustive-deps
}

export function useSimpleResult({ excludeWithholding = false } = {}) {
  const { rows, profile } = useTax();
  const auto33 = excludeWithholding ? false : profile.auto33;
  return useMemo(() => calcSimple(rows, auto33), [rows, auto33]);
}
