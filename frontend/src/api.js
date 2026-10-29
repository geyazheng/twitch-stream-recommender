export class ApiError extends Error {
  constructor(message, status, code = null) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

// The backend's own errors carry a readable `message` and an `error` code such as
// "duplicate_favorite"; Spring's default error body has neither
async function readError(response) {
  try {
    const body = await response.json();
    return {
      message: typeof body.message === "string" ? body.message : null,
      code: typeof body.error === "string" ? body.error : null,
    };
  } catch {
    return { message: null, code: null };
  }
}

async function call(url, { expect = 200, error, ...options } = {}) {
  const response = await fetch(url, { credentials: "include", ...options });
  if (response.status !== expect) {
    const { message, code } = await readError(response);
    throw new ApiError(message ?? error, response.status, code);
  }
  return response;
}

const jsonBody = (body) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const login = ({ username, password }) => {
  const form = new FormData();
  form.append("username", username);
  form.append("password", password);
  return call("/login", { method: "POST", body: form, expect: 204, error: "Wrong username or password" });
};

export const register = ({ username, password, first_name, last_name }) =>
  call("/register", {
    method: "POST",
    ...jsonBody({ username, password, first_name, last_name }),
    error: "Could not create the account. Try a different username.",
  });

export const logout = () => call("/logout", { method: "POST", expect: 204, error: "Could not log out" });

export const getTopGames = () =>
  call("/game", { error: "Could not load top games" }).then((response) => response.json());

// Twitch only matches the exact game name
export const searchGames = (name) =>
  call(`/game?game_name=${encodeURIComponent(name)}`, { error: "Search failed" }).then((response) => response.json());

export const getGameItems = (gameId) =>
  call(`/search?game_id=${encodeURIComponent(gameId)}`, { error: "Could not load this game" }).then((response) =>
    response.json()
  );

export const getRecommendations = () =>
  call("/recommendation", { error: "Could not load recommendations" }).then((response) => response.json());

// Also tells whether the session cookie is still logged in: 401 means it is not
export const getFavorites = () =>
  call("/favorite", { error: "Could not load favorites" }).then((response) => response.json());

export const addFavorite = (item) =>
  call("/favorite", { method: "POST", ...jsonBody({ favorite: item }), error: "Could not save the favorite" });

export const removeFavorite = (item) =>
  call("/favorite", { method: "DELETE", ...jsonBody({ favorite: item }), error: "Could not remove the favorite" });
