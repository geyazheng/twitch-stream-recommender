import React, { useState } from "react";
import { Tooltip } from "antd";
import { HeartFilled, HeartOutlined, PlayCircleOutlined } from "@ant-design/icons";
import ChannelAvatar from "./ChannelAvatar";
import { hasRealThumbnail, sizedImage } from "../media";

const BADGES = {
  STREAM: { label: "LIVE", className: "badge badge-live" },
  VIDEO: { label: "VIDEO", className: "badge" },
  CLIP: { label: "CLIP", className: "badge" },
};

// Opens the item on Twitch when it has a URL; renders a plain block otherwise
function ExternalLink({ href, className, children, ...rest }) {
  if (!href) {
    return <div className={className}>{children}</div>;
  }
  return (
    <a className={className} href={href} target="_blank" rel="noopener noreferrer" {...rest}>
      {children}
    </a>
  );
}

function ItemCard({ item, favorite, pending, onToggleFavorite }) {
  const [imageFailed, setImageFailed] = useState(false);
  const thumbnail = hasRealThumbnail(item.thumbnail_url) ? sizedImage(item.thumbnail_url, 440, 248) : null;
  const badge = BADGES[item.item_type];

  return (
    <article className="card">
      <ExternalLink className="card-thumb" href={item.url} tabIndex={-1} aria-hidden="true">
        {thumbnail && !imageFailed ? (
          <img src={thumbnail} alt="" loading="lazy" onError={() => setImageFailed(true)} />
        ) : (
          <span className="thumb-fallback">
            <PlayCircleOutlined />
          </span>
        )}
        {badge && <span className={badge.className}>{badge.label}</span>}
      </ExternalLink>

      <Tooltip title={favorite ? "Remove from favorites" : "Add to favorites"}>
        <button
          type="button"
          className={`fav-button${favorite ? " is-active" : ""}`}
          onClick={() => onToggleFavorite(item)}
          disabled={pending}
          aria-pressed={favorite}
          aria-label={favorite ? "Remove from favorites" : "Add to favorites"}
        >
          {favorite ? <HeartFilled /> : <HeartOutlined />}
        </button>
      </Tooltip>

      <div className="card-body">
        <ChannelAvatar name={item.broadcaster_name} />
        <div className="card-text">
          <ExternalLink className="card-title" href={item.url} title={item.title}>
            {item.title || "Untitled"}
          </ExternalLink>
          <div className="card-channel">{item.broadcaster_name}</div>
        </div>
      </div>
    </article>
  );
}

export function SkeletonCard() {
  return (
    <div className="card is-skeleton" aria-hidden="true">
      <div className="card-thumb shimmer" />
      <div className="card-body">
        <span className="channel-avatar shimmer" />
        <div className="card-text">
          <div className="line shimmer" />
          <div className="line line-short shimmer" />
        </div>
      </div>
    </div>
  );
}

export default ItemCard;
