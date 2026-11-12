/** 상담 시간: 평일 오전 9시 ~ 오후 6시 */
export const isBusinessHours = (d = new Date()) => {
  const day = d.getDay();
  return day >= 1 && day <= 5 && d.getHours() >= 9 && d.getHours() < 18;
};

export const BUSINESS_HOURS_TEXT = '평일 오전 9시 ~ 오후 6시';

const pad = (n) => String(n).padStart(2, '0');
export const formatClockTime = (at) => { const d = new Date(at); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
export const formatDateTime = (at) => { const d = new Date(at); return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${formatClockTime(at)}`; };

/** 대기 시간 표시 (예: 방금, 5분, 3시간, 2일). since는 ISO 8601 문자열 또는 epoch ms를 받는다. */
export const formatWaiting = (since, now = Date.now()) => {
  const sinceMs = typeof since === 'string' ? new Date(since).getTime() : since;
  const min = Math.floor((now - sinceMs) / 60000);
  if (min < 1) return '방금';
  if (min < 60) return `${min}분`;
  if (min < 60 * 24) return `${Math.floor(min / 60)}시간`;
  return `${Math.floor(min / 1440)}일`;
};
