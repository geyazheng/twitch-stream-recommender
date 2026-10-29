import React from "react";
import { Badge, Button, Dropdown, Input, Layout } from "antd";
import { HeartOutlined, LogoutOutlined } from "@ant-design/icons";
import Logo from "./Logo";
import ChannelAvatar from "./ChannelAvatar";

function AppHeader({ user, favoriteCount, searching, onSearch, onHome, onLogin, onSignup, onLogout, onOpenFavorites }) {
  const accountMenu = {
    items: [
      { key: "who", label: user?.username ? `Signed in as ${user.username}` : "Signed in", disabled: true },
      { type: "divider" },
      { key: "logout", label: "Log out", icon: <LogoutOutlined /> },
    ],
    onClick: ({ key }) => key === "logout" && onLogout(),
  };

  return (
    <Layout.Header className="app-header">
      <button type="button" className="brand" onClick={onHome}>
        <Logo />
        <span className="brand-name">Stream Recommender</span>
      </button>

      <Input.Search
        className="header-search"
        placeholder="Search a game by its exact name"
        allowClear
        loading={searching}
        onSearch={(value, _event, info) => info?.source !== "clear" && onSearch(value)}
      />

      <div className="header-actions">
        {user ? (
          <>
            <Badge count={favoriteCount} size="small" color="#8b5cf6" overflowCount={99}>
              <Button icon={<HeartOutlined />} onClick={onOpenFavorites}>
                <span className="hide-narrow">Favorites</span>
              </Button>
            </Badge>
            <Dropdown menu={accountMenu} trigger={["click"]} placement="bottomRight">
              <button type="button" className="account-button" aria-label="Account menu">
                <ChannelAvatar name={user.username || "?"} size={34} />
              </button>
            </Dropdown>
          </>
        ) : (
          <>
            <Button type="text" onClick={onLogin}>
              Log in
            </Button>
            <Button type="primary" onClick={onSignup}>
              Sign up
            </Button>
          </>
        )}
      </div>
    </Layout.Header>
  );
}

export default AppHeader;
