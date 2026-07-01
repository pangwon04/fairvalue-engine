/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // API base URL 은 NEXT_PUBLIC_API_BASE_URL(.env.local)로 분리. 기본 http://localhost:8080.
};
export default nextConfig;
