// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';

/** 인증번호 유효시간(초) */
export const CODE_TTL = 180;

export const formatClock = (sec) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;

/**
 * 이메일 인증번호 발급·검증. 프로토타입이라 메일을 보내는 대신 번호를 화면 안내(토스트)로 알려준다.
 * send()는 새 번호를 발급하며 이전 번호와 타이머를 대체한다.
 * verify(input)은 'ok' | 'wrong' | 'expired' | 'none'을 돌려준다.
 */
export function useVerifyCode() {
  const [issued, setIssued] = useState(null); // { code, at }
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!issued) return undefined;
    setNow(Date.now());
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [issued]);

  const send = useCallback(() => {
    const code = String(Math.floor(100000 + Math.random() * 900000));
    setIssued({ code, at: Date.now() });
    return code;
  }, []);

  const secondsLeft = issued ? Math.max(0, CODE_TTL - Math.floor((now - issued.at) / 1000)) : 0;
  const expired = Boolean(issued) && secondsLeft === 0;

  const verify = useCallback((input) => {
    if (!issued) return 'none';
    if (Date.now() - issued.at >= CODE_TTL * 1000) return 'expired';
    return input.trim() === issued.code ? 'ok' : 'wrong';
  }, [issued]);

  return { sent: Boolean(issued), secondsLeft, expired, send, verify };
}
