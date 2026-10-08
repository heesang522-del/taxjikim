// mock 응답에 붙이는 300~800ms 지연 (실제 API 호출처럼 로딩 상태를 확인하기 위함)
export const delay = (min = 300, max = 800) =>
  new Promise((resolve) => setTimeout(resolve, min + Math.random() * (max - min)))
