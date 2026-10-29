import React, { useId } from "react";

function Logo({ size = 28 }) {
  const gradientId = useId();
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#a78bfa" />
          <stop offset="1" stopColor="#6d28d9" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="9" fill={`url(#${gradientId})`} />
      <path d="M12.5 10.2v11.6l9.3-5.8z" fill="#fff" />
    </svg>
  );
}

export default Logo;
