/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // ★ Phase 6-1: Docker 배포용 자립 실행 산출물(.next/standalone/server.js).
  output: "standalone",
  // API base URL 은 NEXT_PUBLIC_API_BASE_URL(.env.local·빌드 ARG)로 분리. 기본 http://localhost:8080.
  // 데모 배너는 NEXT_PUBLIC_DEMO_BANNER(빌드 시점 인라인)로 on/off.
};
export default nextConfig;
