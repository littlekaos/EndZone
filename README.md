# EndZone

Discord bot for the **EndZone** community server, with support for a separate **CourtZone** ban-appeal server. Built with [JDA](https://github.com/DV8FromTheWorld/JDA) (Java 21), it handles staff moderation, strikes and appeals, demotion tracking, event tooling, voice channels, logging, and scheduled server maintenance.

Use `/help` in Discord for a full, permission-aware command list (EndZone vs CourtZone layouts differ).

## Features

### Staff strikes and appeals
- Issue, view, remove, clear, and edit staff strikes
- Users can appeal strikes; staff review pending appeals
- Role restoration and demotion protection for temporary demotions
- Demotion list management (add, bulk add/remove, scheduled sync)
- Admin tools: database backup, appeal scanner, strike log import (`dbscan`)

### Moderation
- Warn, mute, unmute, timeout, untimeout, kick, ban, unban, purge
- Ban reason lookup across main and court guilds
- Global blacklist management
- Channel content restrictions (`/restrict`, `/unrestrict`, `/restrict-setup`)
- Mute role configuration and timed mute handling

### Events and community
- Event name submission and checking (`/eventname`)
- Event pings (drafting / countdown channels)
- Void checker for reaction-based message validation
- Reaction roles, AFK status, custom emoji steal
- Interactive forms with periodic database export

### Voice channels
- Per-server voice channel manager (`/setup`)
- Create, delete, lock, unlock, rename, and limit user-created channels
- Stats and admin database info commands

### Automation and logging
- Multi-channel audit logging (moderation, voice, messages, join/leave, names, events)
- Ban sync between configured guilds
- Ticket Tool integration for support workflows
- Scheduled tasks (form auto-export, weekly Winners VC/role reset)
- Staff ping and access-help embeds on startup

## Requirements

- **Java 21**
- **Maven 3.6+**
- A [Discord application](https://discord.com/developers/applications) with bot token
- **Privileged intents** enabled in the Developer Portal where needed:
  - Server Members Intent
  - Message Content Intent

The bot uses member chunking and voice-state caching for moderation and logging.

## Setup

### 1. Clone the repository

```bash
git clone <your-repo-url>
cd EndZone
```

### 2. Environment variables

Create a `.env` file in the project root (it is gitignored). The bot searches several directories; placing `.env` next to `pom.xml` is the simplest approach.

| Variable | Required | Description |
|----------|----------|-------------|
| `BOT_TOKEN` | Yes | Discord bot token (also accepts `TOKEN` from the environment) |
| `DATABASE_PATH` | No | JDBC URL (default: `jdbc:sqlite:endzone.db`) |
| `GUILD_ID` | No | Primary guild ID override (default is configured in `BotConfig`) |
| `BOT_STATUS` | No | Streaming activity title (default: `🌍 Watching EZ!`) |
| `BOT_STATUS_URL` | No | Twitch/stream URL for activity (default in config) |
| `BOT_ONLINE_STATUS` | No | `ONLINE`, `IDLE`, `DO_NOT_DISTURB`, or `INVISIBLE` |
| `ENV_FILE_PATH` | No | Explicit path to a `.env` file |
| `ACCESS_HELP_CHANNEL_ID` | No | Override for the access-help channel |

Example:

```env
BOT_TOKEN=your_bot_token_here
DATABASE_PATH=jdbc:sqlite:endzone.db
BOT_STATUS=🌍 Watching EZ!
BOT_ONLINE_STATUS=ONLINE
```

Do not commit `.env` or your bot token.

### 3. Build

```bash
mvn compile
```

### 4. Run

**Main class:** `EndZone.EndZone`

Run from your IDE or after compiling:

```bash
mvn compile
# Run EndZone.main with runtime classpath (IDE run configuration recommended)
```

On startup the bot initializes the database (HikariCP), registers slash commands, syncs bans, starts background services (mutes, demotion sync, schedulers), and posts configured embeds.

## Permissions and roles

Command access is driven by role tiers defined in `BotConfig` (moderator, admin, Alpha Beta+, staff, court roles, and others). Many commands are restricted to specific roles; `/help` shows what your account can use in the current server.

Server-specific channel and role IDs live in `src/main/java/EndZone/config/BotConfig.java`. Adjust those constants (or env overrides where supported) when deploying to a different guild.

## Project structure

```
EndZone/
├── pom.xml
├── .env                          # Local secrets (not committed)
└── src/main/java/EndZone/
    ├── EndZone.java              # Entry point
    ├── config/                   # BotConfig, guild/role/channel IDs
    ├── commands/                 # Slash command handlers
    ├── events/                   # Interaction and guild event listeners
    ├── services/                 # Strikes, appeals, mutes, bans, voice, etc.
    ├── database/                 # HikariCP + SQLite/PostgreSQL
    ├── listeners/
    ├── embeds/
    ├── forms/
    ├── schedulers/
    └── utils/
```

## Tech stack

- Java 21, Maven
- JDA 5.1.1
- SQLite (default) with optional PostgreSQL driver
- HikariCP, Gson, Logback, dotenv-java

## License

Private project — add a license file here if you plan to distribute the bot.
