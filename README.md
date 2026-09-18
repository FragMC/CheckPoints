# CheckPoints Plugin

[![Hangar](https://img.shields.io/badge/Hangar-FragMC%2FCheckPoints-2a2a2a?logo=papermc)](https://hangar.papermc.io/FragMC/CheckPoints) [![Modrinth](https://img.shields.io/modrinth/dt/fragmc-checkpoints?logo=modrinth&label=Modrinth)](https://modrinth.com/plugin/fragmc-checkpoints)

## 🎯 Overview
A lightweight and powerful checkpoint system for Paper servers. Set personal checkpoints and teleport back to them instantly!

## ✨ Features

### Core Commands
- **`/cp set`** - Set your current location as a checkpoint
  - You can use /cp set [Co-Ordinates]
  - Saves your exact position, including pitch and yaw
  - Displays coordinates confirmation
  
- **`/cp go`** - Teleport back to your saved checkpoint
  - Instant teleportation with no delay
  - Helpful error messages if no checkpoint is set

- **`/cp tp <player>`** - Teleport a player to their checkpoint
  - Supports player names: `/cp tp Steve`
  - Supports selectors: `/cp tp @a` (all players)
  - Supports tag filtering: `/cp tp @a[tag=ready]`
  - Requires `checkpoint.tp` permission (OP by default)

### Advanced Integration
- **Full `/execute` support** - All commands work seamlessly with command blocks and functions
  - Example: `/execute as @a[tag=racing] run cp go`
  - Example: `/execute as PlayerName run cp set`

### User Experience
- Color-coded feedback messages (green for success, red for errors, yellow for warnings)
- Informative coordinate display when setting checkpoints
- Bulk teleport feedback showing success/failure counts
- Tab completion for all commands and player names

## 🔧 Technical Details

- **Minecraft Version:** Paper
- **Java Version:** 21 (minimum: 17)
- **Dependencies:** None
- **Storage:** In-memory (per-session)

## 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `checkpoint.use` | Access to basic checkpoint commands | All players |
| `checkpoint.tp` | Teleport other players to their checkpoints | OP only |

## 📦 Installation

1. Download `checkpoints-[VERSION].jar`
2. Place in your server's `plugins/` folder
3. Restart your server
4. Done! No configuration needed

## 🚀 Usage Examples
```
/cp set                           # Set your checkpoint
/cp go                            # Go to your checkpoint
/cp tp Notch                      # TP Notch to his checkpoint
/cp tp @a[tag=minigame]          # TP all tagged players to their checkpoints
/execute as @a run cp set         # All players set checkpoints simultaneously
```

## 📝 Notes

- Checkpoints are **personal** - each player has their own
- Checkpoints are **temporary** - they reset on server restart
- No world restrictions - checkpoints work across dimensions
- Lightweight with no performance impact

## 🐛 Known Issues

None! This is a stable initial release.

## 💬 Feedback & Support

Found a bug or have a feature request? Let us know!

---

Made for the FragMC minecraft server.
