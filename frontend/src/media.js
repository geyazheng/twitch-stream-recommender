// Twitch thumbnail URLs carry size placeholders: {width}x{height} for streams and box art,
// %{width}x%{height} for videos. Clip thumbnails are fixed URLs.
export function sizedImage(url, width, height) {
  if (!url) {
    return null;
  }
  return url
    .replaceAll("%{width}", width)
    .replaceAll("%{height}", height)
    .replaceAll("{width}", width)
    .replaceAll("{height}", height);
}

// Videos that are still processing point at Twitch's placeholder, which only exists in a few sizes
export const hasRealThumbnail = (url) => Boolean(url) && !url.includes("/_404/");

// A stable color per channel, so the same streamer always gets the same avatar
export function channelColor(name = "") {
  let hash = 0;
  for (const char of name) {
    hash = (hash * 31 + char.codePointAt(0)) % 360;
  }
  return `hsl(${hash} 55% 42%)`;
}

export function initial(name = "") {
  const [first = "?"] = Array.from(name.trim());
  return first.toUpperCase();
}
