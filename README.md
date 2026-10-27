# Twitch Stream Recommender

A Spring Boot REST API that wraps the [Twitch Helix API](https://dev.twitch.tv/docs/api/) and adds user accounts, favorites, and personalized recommendations on top of it.

## What it does

- Search Twitch games, and list streams / videos / clips for a game
- Register, log in (session-based), and save favorite items
- Recommend streams, videos and clips based on the games a user has favorited (falls back to top games for anonymous users)
- Caches Twitch responses in memory so repeated requests do not hit the Twitch rate limit

## Architecture

```
Client ──▶ Spring Boot (8080)
             ├─ Spring Security      form login, session auth, BCrypt via DelegatingPasswordEncoder
             ├─ Controllers          /game /search /register /favorite /recommendation
             ├─ Services             TwitchService, FavoriteService, RecommendationService
             ├─ OpenFeign client ──▶ Twitch Helix API (OAuth2 client-credentials, token fetched automatically)
             ├─ Caffeine cache       expireAfterWrite = 1m
             └─ Spring Data JDBC ──▶ MySQL 8 (users, authorities, items, favorite_records)
```

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Security · Spring Cloud OpenFeign · Spring Data JDBC · MySQL 8 · Caffeine · Gradle · Docker

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/game` | no | Top games. With `?game_name=` searches games by name |
| GET | `/search?game_id=` | no | Streams, videos and clips for a game, grouped by type |
| GET | `/recommendation` | optional | Recommendations. Personalized if logged in, otherwise based on top games |
| POST | `/register` | no | Body: `{username, password, first_name, last_name}` |
| POST | `/login` | no | Form fields `username`, `password`. Returns 204 and a session cookie |
| POST | `/logout` | yes | Returns 204 |
| GET | `/favorite` | yes | Current user's favorites, grouped by type |
| POST | `/favorite` | yes | Body: `{favorite: {twitch_id, title, url, thumbnail_url, broadcaster_name, game_id, item_type}}` |
| DELETE | `/favorite` | yes | Same body as POST. Removes the favorite |

Responses use `snake_case` and omit null fields.

### Recommendation logic

1. Collect the user's favorited items. Take the distinct `game_id`s as seeds (max 3). If none, use Twitch's top games.
2. For each seed game, fetch streams, videos and clips from Twitch (20 items split across seeds).
3. Drop anything the user has already favorited.
4. Cache the result for one minute per user.

## Getting started

### Prerequisites

- Java 21
- Docker (for MySQL), or a local MySQL 8
- A Twitch application: create one at <https://dev.twitch.tv/console/apps> to get a Client ID and Client Secret

### 1. Configure

```bash
cp .env.example .env
# fill in TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET
```

Secrets are read from environment variables only. Nothing sensitive is checked in.

### 2. Run

**Option A: everything in Docker**

```bash
docker compose up --build
```

**Option B: MySQL in Docker, app locally**

```bash
docker compose up db -d
set -a; source .env; set +a
./gradlew bootRun
```

The API is at <http://localhost:8080>. On first start the schema is created from `src/main/resources/database-init.sql` (`DATABASE_INIT=always` drops and recreates tables; set it to `never` once you have data you want to keep).

### 3. Try it

```bash
curl 'localhost:8080/game'
curl 'localhost:8080/search?game_id=33214'

curl -X POST localhost:8080/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"pass1234","first_name":"Alice","last_name":"Lee"}'

curl -c cookies.txt -X POST localhost:8080/login -d 'username=alice&password=pass1234'
curl -b cookies.txt localhost:8080/recommendation
```

## Tests

```bash
./gradlew test --tests '*FavoriteServiceTests'
```

`FavoriteServiceTests` covers the favorite/unfavorite logic with Mockito and needs nothing else running.

`TwitchApplicationTests` starts the full application context, so it needs MySQL up and the Twitch variables set before running the whole suite:

```bash
docker compose up db -d
set -a; source .env; set +a
./gradlew test
```

## Project layout

```
src/main/java/com/miazheng/twitch
├── AppConfig.java              Spring Security filter chain, JDBC user store, password encoder
├── external/                   Feign client + Twitch response models, TwitchService, GameController
├── item/                       /search
├── favorite/                   /favorite
├── recommendation/             /recommendation
├── user/                       /register
├── db/                         Spring Data JDBC repositories and entities
└── model/                      Request/response DTOs
src/main/resources
├── application.yml
└── database-init.sql
```
