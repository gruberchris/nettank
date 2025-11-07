# Game Configuration Guide

## Overview

NetTank allows you to customize game settings such as player name, display resolution, and graphics options through a JSON configuration file.

## Configuration File Location

The game configuration is stored in your home directory:

- **Windows**: `C:\Users\YourName\.nettank\game-config.json`
- **macOS**: `/Users/YourName/.nettank/game-config.json`
- **Linux**: `/home/yourname/.nettank/game-config.json`

The file is automatically created with default settings when you first run the game.

## Default Configuration

```json
{
  "playerName": "Player",
  "display": {
    "width": 1920,
    "height": 1080,
    "fullscreen": false,
    "vsync": true
  }
}
```

## Player Name

**playerName** (string)
- Default: "Player" (will be randomized to "PlayerXXX" at runtime if not customized)
- Maximum length: 20 characters
- Allowed characters: Letters, numbers, spaces, hyphens, underscores
- Special characters are automatically removed

**Note:** If you leave the player name as the default "Player" in the config file, the game will automatically generate a random name like "Player123" at runtime. To have a consistent player name, set a custom value in the config.

### Examples:

**Custom player name:**
```json
{
  "playerName": "TankCommander"
}
```

**Name with numbers:**
```json
{
  "playerName": "Player_123"
}
```

## Display Settings

### Resolution

**width** and **height** (integers)
- Default: 1920x1080 (1080p)
- Minimum: 800x600
- Maximum: 3840x2160 (4K)
- Values outside these ranges will be automatically clamped

### Common Resolution Presets:

- **720p**: 1280 x 720
- **1080p**: 1920 x 1080 (Default)
- **1440p**: 2560 x 1440
- **4K**: 3840 x 2160

### Example Resolutions:

**1440p configuration:**
```json
{
  "display": {
    "width": 2560,
    "height": 1440,
    "fullscreen": false,
    "vsync": true
  }
}
```

**4K configuration:**
```json
{
  "display": {
    "width": 3840,
    "height": 2160,
    "fullscreen": true,
    "vsync": true
  }
}
```

### Fullscreen Mode

**fullscreen** (boolean)
- Default: `false`
- `true`: Game runs in fullscreen mode
- `false`: Game runs in windowed mode

**Enable fullscreen:**
```json
{
  "display": {
    "width": 1920,
    "height": 1080,
    "fullscreen": true,
    "vsync": true
  }
}
```

### VSync

**vsync** (boolean)
- Default: `true`
- `true`: Enables vertical sync (prevents screen tearing)
- `false`: Disables vertical sync (may improve performance)

**Disable VSync for maximum FPS:**
```json
{
  "display": {
    "width": 1920,
    "height": 1080,
    "fullscreen": false,
    "vsync": false
  }
}
```

## Complete Configuration Examples

### Competitive Gaming Setup
```json
{
  "playerName": "ProGamer",
  "display": {
    "width": 1920,
    "height": 1080,
    "fullscreen": true,
    "vsync": false
  }
}
```

### High Resolution Setup
```json
{
  "playerName": "UltraPlayer",
  "display": {
    "width": 3840,
    "height": 2160,
    "fullscreen": true,
    "vsync": true
  }
}
```

### Windowed Mode Setup
```json
{
  "playerName": "CasualPlayer",
  "display": {
    "width": 1280,
    "height": 720,
    "fullscreen": false,
    "vsync": true
  }
}
```

## Tips

1. **Edit while game is closed** - Changes are loaded at startup
2. **Backup your config** - Copy `game-config.json` before experimenting
3. **Delete to reset** - Delete the config file and restart to get default settings
4. **Test resolutions** - Try different resolutions to find what works best for your monitor
5. **VSync trade-off** - Disable VSync for higher FPS, but may cause screen tearing

## Troubleshooting

**Config not loading?**
- Check JSON syntax (use a JSON validator)
- Make sure quotes are correct
- Verify the file is in the right location

**Invalid resolution?**
- Resolutions below 800x600 will be reset to 1920x1080
- Resolutions above 4K will be clamped to 3840x2160
- Check your monitor's supported resolutions
- Make sure your graphics card and monitor support the resolution you choose

**Player name not showing?**
- Name must be 1-20 characters
- Special characters are automatically removed
- Empty names default to "Player"

**Game won't start?**
- Try deleting the config file to reset to defaults
- Check that the resolution is supported by your monitor
- Ensure JSON is properly formatted

## Support

For issues or questions, check the game logs at:
- The console output when running the game
- Or look for log files in the game directory

The game will log which configuration was loaded at startup.
