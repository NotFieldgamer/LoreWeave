/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  async rewrites() {
    // Proxy /api/* to the Spring Boot backend during local dev (optional convenience).
    const api = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
    return [{ source: "/backend/:path*", destination: `${api}/:path*` }];
  },
};
export default nextConfig;
