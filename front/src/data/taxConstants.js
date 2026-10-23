// 세율·공제 한도는 예시값입니다. 실제 사용 전 국세청 자료로 확인하세요.
// 기준 연도는 페이지를 열 때 현재 날짜에서 가져온다.
export const YEAR = new Date().getFullYear();
export const MONTH_NOW = 9; // 1~9월 입력, 10~12월 예측
export const FORECAST_WINDOW = 6; // 예측에 쓰는 최근 개월 수
export const BASIC_DEDUCTION = 1_500_000;
export const STANDARD_CREDIT = 70_000;
export const SIMPLE_EXPENSE_RATE = 0.641;
export const WITHHOLDING_RATE = 0.03;
export const LOCAL_TAX_RATE = 0.1; // 지방소득세 = 소득세 결정세액의 10%

// [과세표준 상한, 세율, 누진공제]
export const BRACKETS = [
  [14e6, 0.06, 0],
  [50e6, 0.15, 1_260_000],
  [88e6, 0.24, 5_760_000],
  [150e6, 0.35, 15_440_000],
  [300e6, 0.38, 19_940_000],
  [500e6, 0.4, 25_940_000],
  [1e9, 0.42, 35_940_000],
  [Infinity, 0.45, 65_940_000],
];
