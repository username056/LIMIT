/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        bg: '#FAFBFC',
        surface: '#FFFFFF',
        primary: {
          DEFAULT: '#6C8DFF',
          dark: '#5B7FFF',
          deep: '#6366F1',
        },
        accent: '#EFF5FF',
        border: '#E2E8F5',
        text: {
          main: '#1F2937',
          sub: '#64748B',
        },
      },
      backgroundImage: {
        'primary-gradient': 'linear-gradient(90deg, #6366F1 0%, #93C5FD 100%)',
        'success-gradient': 'linear-gradient(90deg, #15803D 0%, #22C55E 100%)',
      },
      borderRadius: {
        sm: '8px',
        md: '12px',
        lg: '16px',
        // tokens.css의 --radius-pill과 같은 값입니다. 이게 없으면 rounded-pill이 무시돼
        // 알약 모양으로 만든 검색창·배지가 각지게 보입니다.
        pill: '999px',
      },
      boxShadow: {
        card: '0 1px 2px rgba(16, 24, 40, 0.04)',
        elevated: '0 4px 16px rgba(76, 100, 200, 0.08)',
      },
      fontFamily: {
        main: ['Pretendard', '-apple-system', 'BlinkMacSystemFont', 'Malgun Gothic', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
