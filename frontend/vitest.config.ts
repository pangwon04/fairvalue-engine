import { defineConfig } from 'vitest/config';

// FairValue frontend 계약 검증용 vitest 설정.
// 런타임 앱 없음 — 폼 스키마/검증기 테스트만 실행한다.
export default defineConfig({
  test: {
    environment: 'node',
    include: [
      'src/**/*.test.ts',
      'scripts/**/*.test.ts',
    ],
    globals: false,
  },
});
