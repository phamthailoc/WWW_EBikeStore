/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        background: "#fbf9f8",
        foreground: "#1b1c1c",
        primary: {
          DEFAULT: "#003b93",
          container: "#0051c3",
          fixed: "#dae2ff",
          dim: "#b1c5ff"
        },
        secondary: {
          DEFAULT: "#5f5e5e",
          container: "#e4e2e1",
          fixed: "#e4e2e1"
        },
        surface: {
          DEFAULT: "#fbf9f8",
          container: "#f0eded",
          "container-low": "#f6f3f2",
          "container-high": "#eae8e7",
          "container-highest": "#e4e2e1",
          "container-lowest": "#ffffff"
        },
        outline: {
          DEFAULT: "#737785",
          variant: "#c3c6d6"
        },
        error: "#d14343"
      },
      fontFamily: {
        headline: ["Space Grotesk", "sans-serif"],
        body: ["Inter", "sans-serif"],
        mono: ["JetBrains Mono", "monospace"],
        display: ["Space Grotesk", "sans-serif"]
      }
    }
  },
  plugins: []
};
