import React, { useState } from "react";
import { Empty, Segmented } from "antd";
import ItemCard, { SkeletonCard } from "./ItemCard";
import { sizedImage } from "../media";

export const ITEM_TYPES = [
  { key: "streams", label: "Live", empty: "Nobody is live here right now." },
  { key: "videos", label: "Videos", empty: "No videos yet." },
  { key: "clips", label: "Clips", empty: "No clips yet." },
];

function describe(view, user) {
  if (view.kind === "game") {
    return { eyebrow: "Game", title: view.game.name, subtitle: "Live streams, recent videos and top clips." };
  }
  if (view.personalized) {
    return {
      eyebrow: "For you",
      title: "Recommended for you",
      subtitle: "Picked from the games in your favorites, minus what you already saved.",
    };
  }
  return {
    eyebrow: "Trending",
    title: "Trending now",
    subtitle: user
      ? "From the most-watched games. Favorite a few things and this page will follow your taste."
      : "From the most-watched games. Log in and favorite a few things to get your own picks.",
  };
}

function ItemBrowser({ view, user, resources, loading, isFavorite, pendingIds, onToggleFavorite }) {
  const [type, setType] = useState("streams");
  const { eyebrow, title, subtitle } = describe(view, user);
  const current = ITEM_TYPES.find((itemType) => itemType.key === type);
  const items = resources[type] ?? [];

  return (
    <section className="browser">
      <header className="browser-header">
        {view.kind === "game" && (
          <img className="browser-art" src={sizedImage(view.game.box_art_url, 104, 139)} alt="" />
        )}
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h1 className="browser-title">{title}</h1>
          <p className="browser-subtitle">{subtitle}</p>
        </div>
      </header>

      <Segmented
        className="type-switch"
        value={type}
        onChange={setType}
        options={ITEM_TYPES.map((itemType) => ({
          value: itemType.key,
          label: (
            <span className="type-label">
              {itemType.label}
              {!loading && <span className="type-count">{resources[itemType.key]?.length ?? 0}</span>}
            </span>
          ),
        }))}
      />

      {loading ? (
        <div className="card-grid">
          {Array.from({ length: 8 }, (_, index) => (
            <SkeletonCard key={index} />
          ))}
        </div>
      ) : items.length === 0 ? (
        <Empty className="browser-empty" image={Empty.PRESENTED_IMAGE_SIMPLE} description={current.empty} />
      ) : (
        <div className="card-grid">
          {items.map((item) => (
            <ItemCard
              key={item.twitch_id}
              item={item}
              favorite={isFavorite(item)}
              pending={pendingIds.has(item.twitch_id)}
              onToggleFavorite={onToggleFavorite}
            />
          ))}
        </div>
      )}
    </section>
  );
}

export default ItemBrowser;
