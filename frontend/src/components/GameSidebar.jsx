import React from "react";
import { FireOutlined, ThunderboltFilled } from "@ant-design/icons";
import { sizedImage } from "../media";

function GameSidebar({ games, loading, selectedKey, onSelectRecommendations, onSelectGame }) {
  return (
    <nav className="sidebar" aria-label="Browse">
      <button
        type="button"
        className={`nav-item${selectedKey === "for-you" ? " is-active" : ""}`}
        onClick={onSelectRecommendations}
      >
        <span className="nav-icon">
          <ThunderboltFilled />
        </span>
        For you
      </button>

      <div className="sidebar-heading">
        <FireOutlined /> Top games
      </div>

      {loading
        ? Array.from({ length: 10 }, (_, index) => (
            <div key={index} className="game-item is-skeleton">
              <span className="game-art shimmer" />
              <span className="line shimmer" />
            </div>
          ))
        : games.map((game, index) => (
            <button
              type="button"
              key={game.id}
              className={`game-item${selectedKey === game.id ? " is-active" : ""}`}
              onClick={() => onSelectGame(game)}
              title={game.name}
            >
              <img className="game-art" src={sizedImage(game.box_art_url, 72, 96)} alt="" loading="lazy" />
              <span className="game-name">{game.name}</span>
              <span className="game-rank">{index + 1}</span>
            </button>
          ))}
    </nav>
  );
}

export default GameSidebar;
