# LifeStealCore

**A Lifesteal plugin for Paper and Folia.** When a player kills another player, one heart moves from the loser to the winner — nothing is created, nothing is paid, the total on the server stays the same. Hearts are stored per player and survive death, logout and server restarts.

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Folia-green.svg)](https://papermc.io)
[![Version](https://img.shields.io/badge/version-1.0.2-lightgrey.svg)](https://github.com/riftfox/lifestealcore)

---

## Why another lifesteal plugin?

Because the hard part of lifesteal is not "take a heart on kill" — that is ten lines of code. The hard part is that **a heart per kill is worth far more than a coin per kill**, so two friends (or one person with two accounts) can quietly trade kills until one of them has twenty hearts and has fought nobody.

LifeStealCore ships with the rules that make the feature survive real players: pair cooldowns, daily caps, a minimum playtime before a victim can lose anything, and a same-address check. The defaults are strict on purpose. Everything is configurable, and you can suspend the whole system with one flag without unloading the plugin and losing everybody's heart count.

---

## Features

- **Conserved hearts** — the killer gains exactly what the victim loses, so the server economy of hearts never inflates.
- **Anti-farm protection** — pair cooldown, per-pair daily limit, global daily cap, minimum victim playtime, same-IP blocking.
- **Elimination** — run out of hearts and you are `ban`ned (timed or permanent), `kick`ed with a custom disconnect screen, or left as a `spectator` until staff revive you.
- **World control** — blacklist or whitelist the worlds where hearts can move (hide your spawn or duel arena).
- **Heart item** — a holdable, tradable heart (`NETHER_STAR` by default) with its own name, lore, glow and custom model data. Right-click to use it.
- **Three storage backends** — plain YAML, SQLite, or MySQL shared across a network of servers. Falls back to YAML with a console warning if the database is unreachable.
- **PlaceholderAPI support** — hearts, cooldowns, daily limits, leaderboards, server-wide values.
- **Folia support** — `folia-supported: true`, no ticking-thread assumptions.
- **English and German** out of the box (`language: EN` in `config.yml`).
- **Everything editable** — every message, sound, prefix and broadcast can be changed or switched off by leaving it empty.
- **Safe upgrades** — settings are re-merged on update, but your `heart-item` section is never overwritten: keys you delete stay deleted.

---

## Requirements

| | |
|---|---|
| **Server** | Paper (or a Paper-derived fork) **26.1+**, or Folia |
| **Java** | **JDK 25** or newer |
| **Required plugins** | none |
| **Optional** | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholders, Vault for economy hooks |

The plugin disables gracefully: without PlaceholderAPI the placeholders simply don't exist, without Vault the economy hook logs one warning and stays dormant.

---

## Installation

1. Download `LifeStealCore-<version>.jar` from the [Releases](https://github.com/riftfox/lifestealcore/releases) page.
2. Drop it into your server's `plugins/` folder.
3. Start (or restart) the server. The plugin creates:

   ```
   plugins/LifeStealCore/
   ├── config.yml            ← language + menu options
   └── lang/
       ├── EN/settings.yml   ← behaviour and all text
       └── DE/settings.yml
   ```

4. Edit `plugins/LifeStealCore/lang/EN/settings.yml` — at minimum read the `abuse:` section.
5. Run `/lifesteal reload` (or restart) and you are live.

> **Choosing a language:** set `language: EN` (or `DE`) in `config.yml`. It picks which `lang/<code>/` folder is read. Switching it later creates the other folder **from scratch** — your edits do not carry over — so decide before you configure.

---

## How hearts work

| Setting | Default | Meaning |
|---|---|---|
| `hearts.default` | `10` | What a new player starts with, and what an eliminated player resets to. |
| `hearts.minimum` | `1` | Below this, the next death **eliminates** instead of dropping them further. |
| `hearts.maximum` | `20` | The ceiling (20 ≈ twice the vanilla bar; above that hearts cover the hotbar). |
| `hearts.per-kill` | `1` | How many hearts one kill moves. Victim loses N, killer gains N. |
| `hearts.victim-loses-when-killer-is-full` | `true` | At the ceiling, `true` destroys the heart (dying stays expensive), `false` does nothing. |
| `elimination.mode` | `ban` | `ban`, `kick`, or `spectator`. The stored count always resets to `hearts.default`. |
| `elimination.ban.minutes` | `1440` | Ban length. `0` = forever (a true one-life world). |
| `cooldown.minutes` | `1.0` | Plain throttle between two steals by the same killer. |

### Anti-farm rules

A kill refused by **any** of these moves no heart in either direction — the killer doesn't gain, so a farmer can never delete hearts from the server either.

| Rule | Default | What it stops |
|---|---|---|
| `abuse.pair-cooldown-minutes` | `720` | The same two accounts only trade one heart per 12 h, however often they duel. |
| `abuse.pair-limit-per-day` | `1` | A second kill on the same victim today. |
| `abuse.steals-per-day` | `0` (off) | A global daily cap across all victims. |
| `abuse.minimum-victim-playtime-minutes` | `30` | Fresh alts — a new account must be genuinely played before it is worth killing. |
| `abuse.block-same-address` | `true` | Two clients on one connection (the shape of a farm, not a fight). |

Two of the refusal messages (`refused-playtime`, `refused-address`) are **empty by default**: an honest pair is not told they look suspicious, and a farmer is not told which rule caught them. Fill them in if your server prefers to be open about it.

---

## Commands

`/lifesteal` — aliases: `/hearts`, `/lifestealcore`

| Command | Description |
|---|---|
| `/lifesteal` | Your own status: hearts, today's steals, all-time total, next cooldown. |
| `/lifesteal check <player>` | The same for any player (online or not). |
| `/lifesteal give <player> <amount>` | Hand out heart items. |
| `/lifesteal set <player> <hearts>` | Set a player's heart count — also how you revive someone. |
| `/lifesteal reload` | Re-read `config.yml` and the language file. |
| `/lifesteal help` | Usage. |

### Permissions

| Permission | Default | Grants |
|---|---|---|
| `lifestealcore.use` | everyone | `/lifesteal` for yourself |
| `lifestealcore.steal` | everyone | Actually take a heart on kill (revoke from staff who test PvP) |
| `lifestealcore.admin` | op | `check`, `give`, `set`, `reload` |
| `lifestealcore.mute` | nobody | Hide the server-wide steal announcement |
| `lifestealcore.exempt` | nobody | Never lose a heart, never yield one |

All five node names can be renamed in `settings.yml` under `permissions:` — an empty string means "no permission required".

---

## PlaceholderAPI

Available with PlaceholderAPI installed. Player placeholders also work for **logged-out** players, so leaderboards can ask about anybody.

| Placeholder | Value |
|---|---|
| `%lifestealcore_hearts%` | Hearts they own |
| `%lifestealcore_health%` | The same in half-hearts, as Minecraft counts them |
| `%lifestealcore_today%` | Hearts stolen today |
| `%lifestealcore_total%` | Hearts stolen, all time |
| `%lifestealcore_cooldown%` | Seconds until they may steal again |
| `%lifestealcore_cooldown_text%` | The same written out — empty while ready |
| `%lifestealcore_eliminated%` / `%lifestealcore_alive%` | Elimination state, both ways round |
| `%lifestealcore_enabled%` | Is lifesteal switched on? |
| `%lifestealcore_minimum%` / `%lifestealcore_maximum%` | The floor and the cap |
| `%lifestealcore_start%` | What a new player begins with |
| `%lifestealcore_per_kill%` | What one kill is worth |
| `%lifestealcore_limit%` | Steals per day (`0` = no limit) |
| `%lifestealcore_players%` | Players with a heart count on file |

Counts answer `0` rather than nothing, and a player never seen answers `hearts.default` — so a scoreboard can keep doing arithmetic.

---

## Configuration reference

Everything lives in `plugins/LifeStealCore/lang/<lang>/settings.yml` and is heavily commented — the comments explain *why* a default is what it is, not just what it does. `/lifesteal reload` applies changes.

| Section | Controls |
|---|---|
| `prefix`, `enabled` | Chat prefix, and the master on/off switch for the whole feature. |
| `permissions` | Rename the permission nodes. |
| `worlds` | `blacklist`/`whitelist` of worlds where hearts move. |
| `hearts`, `elimination`, `cooldown` | See the tables above. |
| `abuse` | The anti-farm rules. |
| `heart-item` | Material, worth, name, lore, glow, custom model data. **Never overwritten on update.** |
| `announce` | Server-wide steal/elimination broadcasts, victim DM, console audit log. |
| `data` | Save interval, forget a default-count player after N days, timezone for "today". |
| `storage` | `yaml`, `sqlite` or `mysql` (+ connection details and table name). |
| `sounds` | Volume/pitch per event; clear `name` to silence one. |
| `messages` | Every line, each with its placeholders listed above it. Empty string = off. |

### Storage

```yaml
storage:
  type: yaml      # one readable file, fixable by hand — the default
  # type: sqlite  # one database file, better with thousands of players
  # type: mysql   # several servers share hearts and cooldowns
```

SQLite and MySQL drivers already ship with Bukkit/Paper/Folia — nothing extra to install. MySQL is worth it on a network: otherwise a player has eighteen hearts on one server and ten on the next, and a farm can just hop to the server where the cooldowns are blank.

Heart counts are written the moment they change, again every `save-interval-seconds` (min 30), and again on shutdown.

---

## Building from source

```bash
git clone https://github.com/riftfox/lifestealcore.git
cd lifestealcore
mvn -DskipTests clean package
```

The jar lands in `target/LifeStealCore-<version>.jar`. The build needs **JDK 25** and pulls its dependencies from the PaperMC, PlaceholderAPI and JitPack repositories — no local jars are required (`target/` and IDE files are git-ignored).

---

## FAQ

**Do I have to configure anything before going live?**
You can run the defaults, but read the `abuse:` section first — it is what makes the feature survive contact with real players.

**Someone reported a heart missing — how do I check?**
Every steal and elimination is written to the console when `announce.log-to-console` is on. Search the log for the two names.

**Can I suspend lifesteal for an event?**
Set `enabled: false` and `/lifesteal reload`. Kills stop moving hearts; the command and everyone's count keep working.

**How do I revive an eliminated player?**
`/lifesteal set <player> <hearts>` clears the elimination state on their next login.

**Are my settings safe on update?**
Settings are re-merged on update so you get new keys and new comments. The `heart-item` section is the exception — it describes an object you design, so keys you deleted stay deleted.

---

## Contributing

Issues and pull requests are welcome. Please keep the style of the existing code (including the SPDX header on new files) and run `mvn clean package` before submitting.

## License

LifeStealCore is released under the [GNU General Public License v3.0 only](LICENSE).

You may run, study, share and modify it — derivative work must stay under the same license and carry the same terms. The full text ships in [`LICENSE`](LICENSE); a plain-language summary is at [gnu.org/licenses/gpl-3.0](https://www.gnu.org/licenses/gpl-3.0.html).

```
SPDX-License-Identifier: GPL-3.0-only
```