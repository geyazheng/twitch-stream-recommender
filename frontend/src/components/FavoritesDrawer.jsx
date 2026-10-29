import React, { useState } from "react";
import { Button, Drawer, Empty, Segmented, Tooltip } from "antd";
import { DeleteOutlined } from "@ant-design/icons";
import { ITEM_TYPES } from "./ItemBrowser";
import { hasRealThumbnail, sizedImage } from "../media";

function FavoritesDrawer({ open, favorites, pendingIds, onClose, onRemove }) {
  // Until the user picks a tab, show the first type that has favorites
  const [chosenType, setChosenType] = useState(null);
  const firstNonEmpty = ITEM_TYPES.find((itemType) => favorites[itemType.key]?.length > 0)?.key;
  const type = chosenType ?? firstNonEmpty ?? "streams";
  const items = favorites[type] ?? [];

  return (
    <Drawer
      title="Favorites"
      placement="right"
      size={440}
      open={open}
      onClose={onClose}
      afterOpenChange={(isOpen) => !isOpen && setChosenType(null)}
    >
      <Segmented
        block
        value={type}
        onChange={setChosenType}
        options={ITEM_TYPES.map((itemType) => ({
          value: itemType.key,
          label: `${itemType.label} · ${favorites[itemType.key]?.length ?? 0}`,
        }))}
      />

      {items.length === 0 ? (
        <Empty
          className="drawer-empty"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Nothing saved here yet. Tap the heart on any card."
        />
      ) : (
        <ul className="fav-list">
          {items.map((item) => (
            <li key={item.twitch_id} className="fav-row">
              <a className="fav-thumb" href={item.url ?? undefined} target="_blank" rel="noopener noreferrer">
                {hasRealThumbnail(item.thumbnail_url) && (
                  <img src={sizedImage(item.thumbnail_url, 192, 108)} alt="" loading="lazy" />
                )}
              </a>
              <div className="fav-text">
                <a className="fav-title" href={item.url ?? undefined} target="_blank" rel="noopener noreferrer" title={item.title}>
                  {item.title || "Untitled"}
                </a>
                <span className="fav-channel">{item.broadcaster_name}</span>
              </div>
              <Tooltip title="Remove">
                <Button
                  type="text"
                  icon={<DeleteOutlined />}
                  loading={pendingIds.has(item.twitch_id)}
                  onClick={() => onRemove(item)}
                  aria-label={`Remove ${item.title} from favorites`}
                />
              </Tooltip>
            </li>
          ))}
        </ul>
      )}
    </Drawer>
  );
}

export default FavoritesDrawer;
