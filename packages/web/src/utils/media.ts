import type { SyntheticEvent } from "react";

export const resolveProductImage = (url?: string | null) => {
  if (!url) {
    return "";
  }

  try {
    return new URL(url).toString();
  } catch {
    return url;
  }
};

const encodeSvg = (svg: string) =>
  `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;

export const createProductImageFallback = (label = "Kinetic E-Bike") =>
  encodeSvg(`
    <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="800" viewBox="0 0 1200 800">
      <defs>
        <linearGradient id="bg" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0%" stop-color="#0f172a" />
          <stop offset="50%" stop-color="#1d4ed8" />
          <stop offset="100%" stop-color="#38bdf8" />
        </linearGradient>
      </defs>
      <rect width="1200" height="800" fill="url(#bg)" />
      <circle cx="960" cy="170" r="170" fill="rgba(255,255,255,0.12)" />
      <circle cx="180" cy="640" r="220" fill="rgba(255,255,255,0.08)" />
      <text x="90" y="150" fill="white" font-family="Arial, sans-serif" font-size="34" font-weight="700" opacity="0.9">KINETIC</text>
      <text x="90" y="580" fill="white" font-family="Arial, sans-serif" font-size="68" font-weight="700">${escapeSvgText(label)}</text>
      <text x="90" y="640" fill="rgba(255,255,255,0.82)" font-family="Arial, sans-serif" font-size="28">Premium electric mobility</text>
    </svg>
  `);

export const attachImageFallback = (
  event: SyntheticEvent<HTMLImageElement, Event>,
  label?: string,
  fallbackSrc?: string
) => {
  const image = event.currentTarget;
  image.onerror = null;
  image.src = fallbackSrc || createProductImageFallback(label);
};

const escapeSvgText = (value: string) =>
  value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
