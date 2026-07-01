import type { Config } from "tailwindcss";

// 디자인 토큰: #090946(네이비 900) + Gray 중립 + 기능 상태색. Pretendard.
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        navy: {
          50: "#f2f3f9", 100: "#e3e4f1", 200: "#c2c4e0", 300: "#9a9ccb",
          400: "#6d70b0", 500: "#474a96", 600: "#2f327a", 700: "#20235e",
          800: "#141647", 900: "#090946",
        },
        success: "#16a34a",
        danger: "#dc2626",
        warning: "#d97706",
      },
      fontFamily: {
        sans: ["Pretendard", "Pretendard Variable", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};
export default config;
