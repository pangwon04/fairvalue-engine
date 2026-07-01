import type { Metadata } from "next";
import "./globals.css";
import { QueryProvider } from "@/lib/QueryProvider";

export const metadata: Metadata = {
  title: "FairValue Engine",
  description: "복합금융상품 공정가치 평가 플랫폼",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
