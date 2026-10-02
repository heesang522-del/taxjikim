import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // 5173이 사용 중이면 다른 포트로 넘어가지 않고 오류를 내서, 예전 서버를 보는 혼동을 막는다
  server: { port: 5173, strictPort: true },
})
