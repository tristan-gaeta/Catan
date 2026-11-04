# Server

The Ktor [`server`](../server/src/main/kotlin) holds every online game. It runs the real `Game`, and each client only
ever sees a `PlayerView`, so a modified client can't learn another player's cards or the upcoming dice. See
[what may leave the server](rules-engine.md#what-may-leave-the-server).

## Running it

For local testing, with no Google project needed:

```
SETTLEDOWN_DEV_AUTH=true ./gradlew :server:run
```

Dev auth accepts any `dev:<name>` token as that identity, so anyone can sign in as anyone. Never turn it on anywhere
reachable by other people.

| Variable | Default | Meaning |
|---|---|---|
| `SETTLEDOWN_GOOGLE_CLIENT_IDS` | none | Comma-separated OAuth client IDs that ID tokens may be issued to. Required unless dev auth is on |
| `SETTLEDOWN_DEV_AUTH` | off | `true` accepts unverified `dev:<name>` tokens |
| `SETTLEDOWN_DB` | `settledown.db` | Path of the SQLite database |
| `SETTLEDOWN_HOST`, `SETTLEDOWN_PORT` | `0.0.0.0`, `8080` | Where to listen |
| `SETTLEDOWN_TRUST_PROXY` | off | `true` takes the client's address from the last `X-Forwarded-For` entry |

To play against it from the apps, online play only appears when an app knows the server's address. On desktop:

```
SETTLEDOWN_SERVER_URL=http://localhost:8080 SETTLEDOWN_DEV_USER=alice ./gradlew :app:desktopApp:run
```

`SETTLEDOWN_DEV_USER` signs in as `dev:<name>` without Google. The session token is kept in `~/.settledown`, so run each
extra player as a different user or delete that token between runs.

On Android, pass the same details as Gradle properties, and forward the port on a USB phone. A debug build is allowed
plain HTTP only to `localhost`:

```
adb reverse tcp:8080 tcp:8080
./gradlew :app:androidApp:assembleDebug -Psettledown.serverUrl=http://localhost:8080 -Psettledown.devUser=alice
```

For real sign-in, set `SETTLEDOWN_GOOGLE_CLIENT_ID` and `SETTLEDOWN_GOOGLE_CLIENT_SECRET` on desktop instead of the dev
user, or `-Psettledown.googleClientId=<Web client ID>` on Android.

## Deploying it

The server speaks plain HTTP and expects a reverse proxy in front of it for HTTPS. A deployment needs:

* **TLS at the proxy**, with WebSocket upgrades passed through. Session tokens travel in the `Authorization` header.
* **`SETTLEDOWN_TRUST_PROXY=true`** behind exactly one proxy that appends to `X-Forwarded-For`, with the server not
  reachable directly. Otherwise every player shares one sign-in rate limit, because they all appear to come from the
  proxy. With no proxy, leave it off, or clients can pick their own address.
* **Google OAuth clients.** The Android app signs in through a *Web* client ID (its `serverClientId`), with an Android
  client registered for the package name and signing certificate. Desktop has its own *Desktop* client ID. List the
  Web and Desktop IDs in `SETTLEDOWN_GOOGLE_CLIENT_IDS`, since those are the audiences of the tokens the server sees.
* **Backups of the database.** It holds the seeds of running games, which reveal every future roll, so treat backups as
  secret too. Run one server per database: SQLite and the in-memory rooms and games don't span several instances.

**A rules or format change ends running games.** A record only replays on the version that made it, so at startup
any game from another `Game.RULES_VERSION` or `FORMAT_VERSION` is marked unavailable. Deploy those changes when few
games are running.

## Requests

Every request except `GET /health` sends `X-Settledown-Protocol: <WireCodec.PROTOCOL_VERSION>`, and a mismatch gets a
`426` asking the player to update. Except for signing in, requests also need `Authorization: Bearer <token>`.

| Request | Does |
|---|---|
| `POST /auth/google` | Exchanges a Google ID token for a session token |
| `DELETE /auth/session` | Signs out |
| `GET`, `PUT`, `DELETE /account` | Reads, renames or deletes the account |
| `POST /rooms` | Creates a room for 3 or 4 players |
| `GET /rooms/{code}` | Shows a room to its members; clients poll this while waiting |
| `POST /rooms/{code}/join`, `/leave` | Takes or gives up a seat; taking the last one starts the game |
| `GET /me/room`, `GET /me/games` | The room you're waiting in, and your games |
| `GET /games/{id}/ws` | The live game socket |

Bodies are the JSON types in `com.tgaetaprojects.settledown.api`. Socket frames are binary `WireCodec` messages; see
[live-game messages](serialization.md#live-game-messages).

## Accounts

Google's token is only checked once, at sign-in, for its signature, issuer, audience and expiry. The server then
issues its own opaque token and stores only its hash, so a copy of the database can't be used to sign in. Accounts are
keyed on Google's `sub`, and the email address is never stored.

Deleting an account removes its name from past games. A running game carries on, and the timer plays that seat.

## Rooms

A room lives only in memory until it fills, so a restart loses waiting rooms and nothing else. Codes are 6 characters
without look-alikes such as `0` and `O`, joining is rate limited per account, and a room nobody has touched for 30
minutes expires. When the last seat is taken, seats, colors and both seeds are drawn from `SecureRandom`.

## Live games

One `GameHost` coroutine owns each game and reads a queue of commands, so the mutable `Game` is only touched from one
place. An accepted action is saved first, then sent to each seat as its own `viewFor` and `eventsFor`. If saving fails
the game stops, and it resumes from the last saved action on the next restart. Until then, clients are told to keep
trying.

A seat has one live connection, and a new one replaces the old. Connecting sends the whole current view, so
reconnecting needs nothing special. The event log restarts, since events aren't kept for replay to clients.

### Timers

When time runs out the server plays a simple legal move, chosen by `AutoMove`. These are ordinary actions, so records
still replay and `RULES_VERSION` doesn't change.

| Phase | Time | Move on expiry |
|---|---|---|
| Rolling, including the opening roll-off | 30 s | Roll |
| Set-up | 60 s | The settlement spot with the most pips, then the first legal road |
| Discard | 45 s, shared | The first cards owed, for each seat still owing |
| Moving the robber, Road Building | 30 s | The first legal hex and victim, or the first legal road |
| Trading and building | 120 s | End the turn |

The clock restarts when the phase, current player or turn changes, so a Knight's robber move gives a fresh 120 s
afterward. Building doesn't restart it, and neither does an open trade offer, so offers can't hold up the game.

Once more than 24 timeouts have passed in a row with no player acting, about three rounds of a 4-player game, the game
is abandoned.

### Close codes

| Code | Meaning | Reconnect? |
|---|---|---|
| 4001 `CLOSE_GAME_UNAVAILABLE` | The game ended, or you have no seat at it | No |
| 4002 `CLOSE_REPLACED` | This seat connected from somewhere else | No |
| 4003 `CLOSE_PROTOCOL_ERROR` | Something undecodable, or more than 20 actions a second | No |
| anything else | The network, a restart, a client too far behind, or a sign-out on this account | Yes |

A player who connects after their game finished gets the final view, then 4001, so they can still see who won.
