# PlayerWarpsPlus

A Paper plugin that adds cinematic countdown and transition effects to [PlayerWarps](https://www.spigotmc.org/resources/115286/) teleportation.

## Features

- Configurable countdown timer (3, 2, 1...) with title/subtitle messages
- Cinematic zoom-out effect using FOV manipulation
- Smooth blindness and darkness transitions
- Movement and damage cancellation during countdown
- Customizable sounds for countdown, teleport, and arrival
- Full MiniMessage formatting support with custom `<smallcaps>` tag
- Bypass permission for instant teleports
- Configurable warp command alias

## Requirements

- Paper 1.21+ (or compatible fork)
- [PlayerWarps](https://www.spigotmc.org/resources/115286/) 7.9.0+
- Java 21+

## Installation

1. Download the latest release from [Releases](../../releases)
2. Place the JAR in your server's `plugins` folder
3. Ensure PlayerWarps is installed and enabled
4. Restart your server
5. Configure `plugins/PlayerWarpsPlus/config.yml` to your liking

**Important:** Set `teleport-wait: -1` in PlayerWarps config to avoid conflicts with PlayerWarpsPlus countdown.

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `playerwarpsplus.bypass` | Bypass countdown and teleport instantly | op |
| `playerwarpsplus.reload` | Reload the plugin configuration | op |

## Commands

| Command | Description |
|---------|-------------|
| `/playerwarpsplus reload` | Reload the configuration |
| `/pwplus reload` | Alias for reload |
| `/pwp reload` | Alias for reload |

## Configuration

```yaml
countdown:
  # Duration in seconds (how long to show 3, 2, 1)
  duration: 3

  # Countdown messages (supports MiniMessage formatting)
  title: "<#f9e59d>ᴡᴀʀᴘɪɴɢ ɪɴ %seconds%"
  subtitle: "<white>ᴅᴏ ɴᴏᴛ ᴍᴏᴠᴇ"

  # Final message (at teleport)
  final-title: "<#7f91fd>ᴡᴀʀᴘɪɴɢ ᴛᴏ"
  final-subtitle: "<white><sc>%warp%</sc>"

  # Cancelled message (when player moves)
  cancelled-title: "<red>ᴄᴀɴᴄᴇʟʟᴇᴅ"
  cancelled-subtitle: ""

  # Zoom effect settings
  zoom-duration: 5            # How long the zoom effect lasts (ticks)
  zoom-speed-amplifier: 4     # Speed potion level (0-10, higher = wider FOV)

  # Black screen duration after zoom
  black-duration: 15          # Ticks before teleport

  # Sound effects, blindness, darkness settings...
  # See config.yml for full options

# Change if you've customized the /pw command in PlayerWarps
warp-command: "pw"

# Debug mode
debug: false
```

### Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%warp%` | The warp name |
| `%seconds%` | Seconds remaining (countdown only) |

### Custom Tags

| Tag | Description |
|-----|-------------|
| `<smallcaps>text</smallcaps>` | Converts text to Unicode small caps (ᴛᴇxᴛ) |
| `<sc>text</sc>` | Short alias for smallcaps |

## How It Works

1. When a player warps, the plugin intercepts the teleport event
2. A countdown is displayed (3, 2, 1...)
3. If the player moves or takes damage, the warp is cancelled
4. At the end of the countdown, the player mounts an invisible bat
5. The bat flies backwards while a Speed effect widens the FOV (zoom-out effect)
6. Blindness/darkness effects create a smooth transition
7. The player is teleported to the warp destination
8. Effects are cleaned up and the player arrives

## Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## License

[MIT License](LICENSE)

## Credits

- Developed by Loralon
- Uses [PlayerWarps API](https://www.spigotmc.org/resources/115286/) by Olzie
