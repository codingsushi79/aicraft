**Aicraft** adds private AI chat to your Paper server — in-game and in the browser. Talk to Ollama, Unsloth, vLLM, or any OpenAI-compatible API. Conversations are saved per player, with a web UI for history and linking your Minecraft account.

**v1.1.0** adds the web UI, SQLite/Postgres chat history, per-player chat IDs, account linking, and LuckPerms-friendly daily chat limits.

---

<details>
<summary><strong>Requirements</strong></summary>

- **Paper** 1.21 or newer, including **26.x** (Spigot is not supported)
- **Java 21** for Paper 1.21.x servers, **Java 25** for Paper 26.x servers
- An **OpenAI-compatible** chat API — local or cloud
- **Optional:** open port **8765** (or your chosen port) if you want the web UI reachable from outside the host

Works with:
- [Ollama](https://ollama.com/) on `localhost`
- Unsloth / vLLM / LM Studio and other `/v1/chat/completions` endpoints
- OpenAI, Groq, OpenRouter, etc.

</details>

<details>
<summary><strong>Installation</strong></summary>

1. Download the latest **Aicraft** `.jar` from the **Versions** tab on this page.
2. Drop it into your server's `plugins/` folder.
3. Start or restart the server.
4. On first run, Aicraft creates:
   - `plugins/Aicraft/config.yml` — AI endpoint settings
   - `plugins/Aicraft/web.yml` — web UI, database, and rate limits
   - `plugins/Aicraft/aicraft.db` — SQLite database (created automatically)
5. Edit `config.yml` before players use the plugin (see **Configuration** below).
6. Restart after changing config (safest). Reloading may not restart the web server or database.

</details>

<details>
<summary><strong>AI configuration</strong> (`config.yml`)</summary>

```yaml
endpoint: "http://localhost:11434/v1/chat/completions"
api-key: "ollama"
model: "llama3.2"
system-prompt: "You are a helpful assistant inside a Minecraft server. Keep replies concise and game-friendly."
timeout-seconds: 120
max-history-messages: 20
```

| Option | Description |
|--------|-------------|
| `endpoint` | Full URL to the chat completions API (`/v1/chat/completions`) |
| `api-key` | API key sent as `Authorization: Bearer …`. Use any placeholder (e.g. `ollama`) if your server ignores auth |
| `model` | Model name your provider expects |
| `system-prompt` | Prepended to every new chat |
| `timeout-seconds` | HTTP timeout for AI requests |
| `max-history-messages` | Max user + assistant messages kept in the AI context window |

### Ollama (local, recommended)

On the **same machine** as Minecraft:

```bash
ollama pull llama3.2
ollama serve
```

```yaml
endpoint: "http://localhost:11434/v1/chat/completions"
api-key: "ollama"
model: "llama3.2"
```

If Ollama runs on another host, replace `localhost` with that machine's IP or hostname.

### OpenAI (cloud)

```yaml
endpoint: "https://api.openai.com/v1/chat/completions"
api-key: "sk-your-key-here"
model: "gpt-4o-mini"
```

### Other local providers

Point `endpoint` at whatever `/v1/chat/completions` URL your stack exposes. `localhost` and private LAN addresses are supported.

</details>

<details>
<summary><strong>Web & database configuration</strong> (`web.yml`)</summary>

```yaml
enabled: true
port: 8765
bind-address: "0.0.0.0"

database:
  type: sqlite
  sqlite-file: "aicraft.db"
  postgres:
    host: "localhost"
    port: 5432
    database: "aicraft"
    username: "aicraft"
    password: ""

rate-limits:
  default-chats-per-day: 50

linking:
  link-code-expiry-minutes: 2
  web-session-days: 30
```

| Option | Description |
|--------|-------------|
| `enabled` | Turn the web UI on or off |
| `port` / `bind-address` | Where the web server listens (`0.0.0.0` = all interfaces) |
| `database.type` | `sqlite` (default) or `postgres` |
| `database.sqlite-file` | SQLite file inside `plugins/Aicraft/` — **created automatically** |
| `database.postgres.*` | Postgres connection settings (database must exist beforehand) |
| `rate-limits.default-chats-per-day` | Max **new** chats per player per 24h when no permission overrides it |
| `linking.link-code-expiry-minutes` | How long an `/ailink` code stays valid |
| `linking.web-session-days` | How long a linked web session lasts |

**Database notes:**
- SQLite is zero-setup — the `.db` file and tables are created on first startup.
- Migrations run automatically; you never need to run SQL by hand.
- The database is always used (even with `enabled: false`) so in-game chats are saved.
- For Postgres, create the database first, then set `type: postgres` and fill in credentials.

</details>

<details>
<summary><strong>How to use in-game</strong></summary>

| Command | What it does |
|---------|----------------|
| `/newchat` | Start a new private chat — you get **Chat #N** (your personal ID) |
| `/ai <message>` | Send a message in your active chat |
| `/endchat` | End your active chat (history is still saved) |
| `/reopenchat <id>` | Reopen one of **your** saved chats (e.g. `/reopenchat 1`) |
| `/ailink` | Link your account to the web UI (after entering your username on the web) |

**Chat IDs are per-player.** If you run `/reopenchat 1`, that is *your* chat #1 — not another player's.

**Example session:**

```
/newchat
→ Chat #3 started. Only you see this conversation.
/ai what should I build in survival?
/ai give me a compact starter base layout
/endchat
/reopenchat 3
/ai add a storage room to that plan
/endchat
```

- `/ai` only works while a chat is active (`/newchat` or `/reopenchat`).
- Other players cannot see your messages or the AI's replies.

</details>

<details>
<summary><strong>Web UI</strong></summary>

Open `http://<server-ip>:8765` in a browser (use your `port` from `web.yml`).

**Link your account:**

1. Enter your Minecraft username on the web page.
2. Join the server and run `/ailink`.
3. Enter the code shown **only to you** in-game on the web page.

**What you can do on the web:**

- Browse your full chat history
- Start new chats from the browser
- Continue old conversations
- See the same per-player chat numbers as in-game (`Chat #1` is always yours)

**Tips for server admins:**

- Forward/open the web port in your firewall if players connect remotely.
- Set `bind-address: "127.0.0.1"` and use a reverse proxy if you want HTTPS or external access without exposing the port directly.
- Disable the web UI with `enabled: false` — in-game chat and database persistence still work.

</details>

<details>
<summary><strong>Permissions</strong></summary>

| Permission | Default | Description |
|------------|---------|-------------|
| `aicraft.use` | `true` (everyone) | `/newchat`, `/ai`, `/endchat`, `/reopenchat` |
| `aicraft.web` | `true` (everyone) | `/ailink` for the web UI |
| `aicraft.chats.unlimited` | `op` | No daily cap on new chats |
| `aicraft.chats.<n>` | — | e.g. `aicraft.chats.50` = 50 new chats per 24h |

**Rate limits** count **new chats created** in a rolling 24-hour window — not individual messages.

- If a player has `aicraft.chats.50`, they get 50 new chats per day.
- If they also have `aicraft.chats.unlimited`, unlimited wins.
- Otherwise the default from `web.yml` applies (`default-chats-per-day`).

**LuckPerms** works out of the box. Grant nodes to groups as usual. Players creating chats from the web while offline use LuckPerms cached permissions if LuckPerms is installed.

**Example (LuckPerms):**
```
/lp group vip permission set aicraft.chats.100 true
/lp group default permission set aicraft.chats.10 true
```

</details>

<details>
<summary><strong>Troubleshooting</strong></summary>

**"No active chat. Start one with /newchat."**  
Run `/newchat` or `/reopenchat <id>` before `/ai`.

**"Daily chat limit reached"**  
The player has hit their 24h new-chat cap. Grant a higher `aicraft.chats.<n>` permission, `aicraft.chats.unlimited`, or raise `default-chats-per-day` in `web.yml`.

**"Failed to reach AI endpoint" / connection errors**  
- Confirm your AI server is running (`ollama serve`, etc.).
- Check `endpoint` in `config.yml` — include the full path (`/v1/chat/completions`).
- If the model runs on another machine, use that host's IP instead of `localhost`.
- Ensure the Minecraft server can reach that host and port (firewall, Docker networking).

**HTTP 404 / model not found**  
- Run `ollama pull <model>` (or equivalent) for the name in `model`.
- Match `model` exactly to what your provider lists.

**Slow or timed-out replies**  
- Increase `timeout-seconds` in `config.yml`.
- Use a smaller/faster model locally.

**Web UI won't load**  
- Check `enabled: true` in `web.yml`.
- Confirm the port is open in your firewall / hosting panel.
- Check the server log for `Aicraft web UI running on http://...`.

**"/ailink" says no pending web link**  
Enter your username on the web UI first, then run `/ailink` within the expiry window (`link-code-expiry-minutes`).

**Link code expired**  
Start over: enter your username on the web again, then run `/ailink` in-game.

**Postgres connection errors**  
- Make sure the database exists and credentials in `web.yml` are correct.
- Ensure the Minecraft server can reach the Postgres host.

</details>

---

Made by **SushiMC**. Bug reports and feature requests welcome on the project issue tracker.
