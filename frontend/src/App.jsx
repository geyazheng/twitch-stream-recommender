import React, { useEffect, useMemo, useRef, useState } from "react";
import { App as AntApp, Layout } from "antd";
import AppHeader from "./components/AppHeader";
import GameSidebar from "./components/GameSidebar";
import ItemBrowser from "./components/ItemBrowser";
import FavoritesDrawer from "./components/FavoritesDrawer";
import AuthModal from "./components/AuthModal";
import {
  addFavorite,
  getFavorites,
  getGameItems,
  getRecommendations,
  getTopGames,
  logout,
  removeFavorite,
  searchGames,
} from "./api";

const EMPTY = { streams: [], videos: [], clips: [] };
const USERNAME_KEY = "stream-recommender.username";

const countItems = (groups) => groups.streams.length + groups.videos.length + groups.clips.length;

// The backend has no "who am I" endpoint, so the name shown in the account menu is remembered locally
function readUsername() {
  try {
    return localStorage.getItem(USERNAME_KEY);
  } catch {
    return null;
  }
}

function rememberUsername(username) {
  try {
    if (username) {
      localStorage.setItem(USERNAME_KEY, username);
    } else {
      localStorage.removeItem(USERNAME_KEY);
    }
  } catch {
    // Storage can be unavailable (private mode, blocked site data); the name is only cosmetic
  }
}

function App() {
  const { message } = AntApp.useApp();
  const [user, setUser] = useState(null);
  const [favorites, setFavorites] = useState(EMPTY);
  const [topGames, setTopGames] = useState([]);
  const [gamesLoading, setGamesLoading] = useState(true);
  const [view, setView] = useState({ kind: "recommendation", personalized: false });
  const [resources, setResources] = useState(EMPTY);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [pendingIds, setPendingIds] = useState(() => new Set());
  const [favoritesOpen, setFavoritesOpen] = useState(false);
  const [auth, setAuth] = useState({ open: false, mode: "login", notice: null });
  const [narrow, setNarrow] = useState(false);
  const [siderCollapsed, setSiderCollapsed] = useState(false);
  const latestRequest = useRef(0);

  const favoriteIds = useMemo(
    () => new Set([...favorites.streams, ...favorites.videos, ...favorites.clips].map((item) => item.twitch_id)),
    [favorites]
  );

  const showError = (err) => message.error(err.message);

  // Only the latest request may update the page, so a slow response cannot overwrite a newer view
  const loadView = (nextView) => {
    const requestId = ++latestRequest.current;
    setView(nextView);
    setLoading(true);
    if (narrow) {
      setSiderCollapsed(true);
    }
    const request = nextView.kind === "game" ? getGameItems(nextView.game.id) : getRecommendations();
    request
      .then((data) => requestId === latestRequest.current && setResources(data))
      .catch((err) => {
        if (requestId === latestRequest.current) {
          setResources(EMPTY);
          showError(err);
        }
      })
      .finally(() => requestId === latestRequest.current && setLoading(false));
  };

  const showRecommendations = (favoriteGroups = favorites, loggedIn = Boolean(user)) =>
    loadView({ kind: "recommendation", personalized: loggedIn && countItems(favoriteGroups) > 0 });

  // Logging in or out changes what recommendations should show, but the requests involved finish
  // later. Reload only if the user is still on the same recommendations by then.
  const snapshotView = () => ({ request: latestRequest.current, onRecommendations: view.kind === "recommendation" });
  const stillOnRecommendations = (snapshot) => snapshot.onRecommendations && latestRequest.current === snapshot.request;

  useEffect(() => {
    getTopGames()
      .then(setTopGames)
      .catch(showError)
      .finally(() => setGamesLoading(false));

    getFavorites()
      .then((groups) => {
        setUser({ username: readUsername() });
        setFavorites(groups);
        showRecommendations(groups, true);
      })
      .catch((err) => {
        if (err.status !== 401) {
          showError(err);
        }
        showRecommendations(EMPTY, false);
      });
    // Runs once on load: restores the session from the cookie, then shows recommendations
  }, []);

  const refreshFavorites = () =>
    getFavorites()
      .then((groups) => {
        setFavorites(groups);
        return groups;
      })
      .catch((err) => {
        if (err.status === 401) {
          setUser(null);
          setFavorites(EMPTY);
        } else {
          showError(err);
        }
        return EMPTY;
      });

  const openAuth = (mode, notice = null) => setAuth({ open: true, mode, notice });

  const handleAuthenticated = (username) => {
    rememberUsername(username);
    setUser({ username });
    setAuth((current) => ({ ...current, open: false }));
    message.success(`Signed in as ${username}`);
    const snapshot = snapshotView();
    refreshFavorites().then((groups) => stillOnRecommendations(snapshot) && showRecommendations(groups, true));
  };

  const handleLogout = () => {
    const snapshot = snapshotView();
    logout()
      .then(() => {
        rememberUsername(null);
        setUser(null);
        setFavorites(EMPTY);
        setFavoritesOpen(false);
        message.success("Logged out");
        if (stillOnRecommendations(snapshot)) {
          showRecommendations(EMPTY, false);
        }
      })
      .catch(showError);
  };

  const handleSearch = (rawName) => {
    const name = rawName.trim();
    if (!name) {
      return;
    }
    setSearching(true);
    searchGames(name)
      .then((games) => {
        if (games.length === 0) {
          message.info(`No game is called "${name}" on Twitch. It needs the exact name, like "Grand Theft Auto V".`);
          return;
        }
        loadView({ kind: "game", game: games[0] });
      })
      .catch(showError)
      .finally(() => setSearching(false));
  };

  const setPending = (twitchId, isPending) =>
    setPendingIds((current) => {
      const next = new Set(current);
      if (isPending) {
        next.add(twitchId);
      } else {
        next.delete(twitchId);
      }
      return next;
    });

  const toggleFavorite = async (item) => {
    if (!user) {
      openAuth("login", "Log in to save favorites and get recommendations from them.");
      return;
    }
    setPending(item.twitch_id, true);
    try {
      if (favoriteIds.has(item.twitch_id)) {
        await removeFavorite(item);
      } else {
        await addFavorite(item);
      }
    } catch (err) {
      if (err.status === 401) {
        setUser(null);
        openAuth("login", "Your session ended. Log in again to keep saving favorites.");
      } else if (err.code !== "duplicate_favorite") {
        // A duplicate just means it was already saved (e.g. a double click); the refresh below shows the real state
        showError(err);
      }
    } finally {
      await refreshFavorites();
      setPending(item.twitch_id, false);
    }
  };

  const selectedKey = view.kind === "game" ? view.game.id : "for-you";

  return (
    <Layout className="app">
      <AppHeader
        user={user}
        favoriteCount={favoriteIds.size}
        searching={searching}
        onSearch={handleSearch}
        onHome={() => showRecommendations()}
        onLogin={() => openAuth("login")}
        onSignup={() => openAuth("register")}
        onLogout={handleLogout}
        onOpenFavorites={() => setFavoritesOpen(true)}
      />
      <Layout>
        <Layout.Sider
          className={`app-sider${narrow ? " is-narrow" : ""}`}
          width={264}
          breakpoint="lg"
          collapsedWidth={0}
          collapsed={siderCollapsed}
          onCollapse={setSiderCollapsed}
          onBreakpoint={(broken) => {
            setNarrow(broken);
            setSiderCollapsed(broken);
          }}
        >
          <GameSidebar
            games={topGames}
            loading={gamesLoading}
            selectedKey={selectedKey}
            onSelectRecommendations={() => showRecommendations()}
            onSelectGame={(game) => loadView({ kind: "game", game })}
          />
        </Layout.Sider>
        <Layout.Content className="app-content">
          <ItemBrowser
            view={view}
            user={user}
            resources={resources}
            loading={loading}
            isFavorite={(item) => favoriteIds.has(item.twitch_id)}
            pendingIds={pendingIds}
            onToggleFavorite={toggleFavorite}
          />
        </Layout.Content>
      </Layout>

      <FavoritesDrawer
        open={favoritesOpen}
        favorites={favorites}
        pendingIds={pendingIds}
        onClose={() => setFavoritesOpen(false)}
        onRemove={toggleFavorite}
      />
      <AuthModal
        open={auth.open}
        mode={auth.mode}
        notice={auth.notice}
        onModeChange={(mode) => setAuth({ open: true, mode, notice: null })}
        onClose={() => setAuth((current) => ({ ...current, open: false }))}
        onAuthenticated={handleAuthenticated}
      />
    </Layout>
  );
}

export default App;
