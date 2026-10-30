/**
 * Tailwind config — نوا ادمین (پنل کارکنان نوبت‌یار)
 * توکن‌ها به سبک shadcn/ui (متغیرهای HSL) و مقیاس تایپوگرافی/فاصله‌ی Vercel Geist.
 * منحنی‌های حرکت بر پایه‌ی Apple HIG و Material 3 Motion.
 * @type {import('tailwindcss').Config}
 */
const token = (name) => `hsl(var(--${name}) / <alpha-value>)`;

module.exports = {
  // Paths are relative to backend/ (run the build from there, see README.md).
  content: ['./templates/staffpanel/**/*.html', './staffpanel/**/*.py', './assets/nava/js/**/*.js'],
  // Tone classes built in templates as bg-{{ tone }}/10 etc. are invisible to
  // the content scan, so they are listed here.
  safelist: [{ pattern: /^(bg|text)-(accent|info|danger|warning|primary)(\/(10|15))?$/ }],
  darkMode: 'class',
  theme: {
    container: {
      center: true,
      padding: '1rem',
    },
    extend: {
      fontFamily: {
        sans: ['Vazirmatn', 'system-ui', 'Tahoma', 'sans-serif'],
      },
      colors: {
        background: token('background'),
        foreground: token('foreground'),
        card: { DEFAULT: token('card'), foreground: token('card-foreground') },
        popover: { DEFAULT: token('popover'), foreground: token('popover-foreground') },
        muted: { DEFAULT: token('muted'), foreground: token('muted-foreground') },
        border: token('border'),
        input: token('input'),
        ring: token('ring'),
        primary: { DEFAULT: token('primary'), foreground: token('primary-foreground') },
        accent: { DEFAULT: token('accent'), foreground: token('accent-foreground') },
        info: token('info'),
        warning: token('warning'),
        danger: token('danger'),
        chart: {
          1: token('chart-1'),
          2: token('chart-2'),
          3: token('chart-3'),
          4: token('chart-4'),
          5: token('chart-5'),
        },
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
        xl: 'calc(var(--radius) + 4px)',
        '2xl': 'calc(var(--radius) + 8px)',
      },
      boxShadow: {
        // ارتفاع‌ها (Elevation) به سبک Material 3 / Geist
        'elevation-1': '0 1px 2px 0 rgb(0 0 0 / .04), 0 1px 3px 0 rgb(0 0 0 / .06)',
        'elevation-2': '0 4px 12px -2px rgb(0 0 0 / .08), 0 2px 4px -2px rgb(0 0 0 / .05)',
        'elevation-3': '0 16px 40px -8px rgb(0 0 0 / .18), 0 4px 12px -4px rgb(0 0 0 / .08)',
        glow: '0 0 0 1px hsl(var(--primary) / .25), 0 8px 30px -6px hsl(var(--primary) / .45)',
      },
      transitionTimingFunction: {
        // Apple HIG spring-like & Material 3 emphasized
        spring: 'cubic-bezier(.34, 1.56, .64, 1)',
        emphasized: 'cubic-bezier(.2, 0, 0, 1)',
        'out-expo': 'cubic-bezier(.16, 1, .3, 1)',
      },
      keyframes: {
        'fade-up': {
          from: { opacity: '0', transform: 'translateY(16px) scale(.98)', filter: 'blur(6px)' },
          to: { opacity: '1', transform: 'none', filter: 'blur(0)' },
        },
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'scale-in': {
          from: { opacity: '0', transform: 'scale(.95) translateY(-4px)' },
          to: { opacity: '1', transform: 'none' },
        },
        shimmer: { '100%': { transform: 'translateX(-100%)' } },
        float: {
          '0%, 100%': { transform: 'translate3d(0,0,0)' },
          '50%': { transform: 'translate3d(0,-10px,0)' },
        },
        'pulse-ring': {
          '0%': { transform: 'scale(.8)', opacity: '.8' },
          '100%': { transform: 'scale(2.2)', opacity: '0' },
        },
        aurora: {
          '0%, 100%': { transform: 'translate(0,0) rotate(0deg)' },
          '33%': { transform: 'translate(4%,-6%) rotate(8deg)' },
          '66%': { transform: 'translate(-5%,4%) rotate(-6deg)' },
        },
      },
      animation: {
        'fade-up': 'fade-up .7s cubic-bezier(.16,1,.3,1) both',
        'fade-in': 'fade-in .4s ease both',
        'scale-in': 'scale-in .22s cubic-bezier(.16,1,.3,1) both',
        shimmer: 'shimmer 1.6s infinite',
        float: 'float 6s ease-in-out infinite',
        'pulse-ring': 'pulse-ring 1.6s cubic-bezier(.2,0,0,1) infinite',
        aurora: 'aurora 18s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
