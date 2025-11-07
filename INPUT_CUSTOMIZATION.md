# Input Customization Guide

## Overview

NetTank supports both keyboard and gamepad controls, and all inputs are fully customizable through a JSON configuration file.

## Configuration File Location

The input configuration is stored in your home directory:

- **Windows**: `C:\Users\YourName\.nettank\input-config.json`
- **macOS**: `/Users/YourName/.nettank/input-config.json`
- **Linux**: `/home/yourname/.nettank/input-config.json`

The file is automatically created with default settings when you first run the game.

## Default Configuration

```json
{
  "keyboard": {
    "forward": "W",
    "backward": "S",
    "rotateLeft": "A",
    "rotateRight": "D",
    "shoot": "SPACE",
    "exit": "ESCAPE"
  },
  "gamepad": {
    "forward": "RIGHT_TRIGGER",
    "backward": "LEFT_TRIGGER",
    "rotateAxis": "RIGHT_STICK_X",
    "shoot": "BUTTON_A",
    "stickDeadzone": 0.2,
    "triggerThreshold": 0.1,
    "rotationSensitivity": 1.0
  }
}
```

## Customizing Keyboard Controls

### Supported Keys

**Letters**: A-Z  
**Numbers**: 0-9  
**Special Keys**:
- `SPACE`
- `ESCAPE` or `ESC`
- `ENTER` or `RETURN`
- `TAB`
- `SHIFT`
- `CONTROL` or `CTRL`
- `ALT`

**Arrow Keys**:
- `UP`
- `DOWN`
- `LEFT`
- `RIGHT`

### Example: Using Arrow Keys

```json
{
  "keyboard": {
    "forward": "UP",
    "backward": "DOWN",
    "rotateLeft": "LEFT",
    "rotateRight": "RIGHT",
    "shoot": "SPACE",
    "exit": "ESCAPE"
  }
}
```

### Example: ESDF Layout

```json
{
  "keyboard": {
    "forward": "E",
    "backward": "D",
    "rotateLeft": "S",
    "rotateRight": "F",
    "shoot": "SPACE",
    "exit": "ESCAPE"
  }
}
```

## Customizing Gamepad Controls

### Supported Gamepad Axes

- `LEFT_STICK_X` - Left stick horizontal
- `LEFT_STICK_Y` - Left stick vertical
- `RIGHT_STICK_X` - Right stick horizontal
- `RIGHT_STICK_Y` - Right stick vertical
- `LEFT_TRIGGER` - Left trigger (LT/L2)
- `RIGHT_TRIGGER` - Right trigger (RT/R2)

### Supported Gamepad Buttons

- `BUTTON_A` / `A` - A button (Xbox) / X button (PlayStation)
- `BUTTON_B` / `B` - B button (Xbox) / Circle button (PlayStation)
- `BUTTON_X` / `X` - X button (Xbox) / Square button (PlayStation)
- `BUTTON_Y` / `Y` - Y button (Xbox) / Triangle button (PlayStation)
- `LEFT_BUMPER` / `LB` - Left bumper (LB/L1)
- `RIGHT_BUMPER` / `RB` - Right bumper (RB/R1)
- `BACK` / `SELECT` - Back/Select button
- `START` - Start button
- `GUIDE` / `HOME` - Guide/Home button
- `LEFT_THUMB` - Left stick button
- `RIGHT_THUMB` - Right stick button
- `DPAD_UP` - D-pad up
- `DPAD_DOWN` - D-pad down
- `DPAD_LEFT` - D-pad left
- `DPAD_RIGHT` - D-pad right

### Gamepad Sensitivity Settings

**stickDeadzone** (0.0 to 1.0)
- Default: 0.2
- Prevents stick drift by ignoring small movements
- Higher = more deadzone, less sensitive

**triggerThreshold** (0.0 to 1.0)
- Default: 0.1
- How hard you need to press triggers to register
- Higher = need to press triggers harder

**rotationSensitivity** (0.1 to 2.0)
- Default: 1.0
- Multiplier for rotation speed
- Higher = faster rotation with a stick

### Example: Using Left Stick for Movement

```json
{
  "gamepad": {
    "forward": "LEFT_STICK_Y",
    "backward": "LEFT_STICK_Y",
    "rotateAxis": "LEFT_STICK_X",
    "shoot": "RIGHT_TRIGGER",
    "stickDeadzone": 0.15,
    "triggerThreshold": 0.2,
    "rotationSensitivity": 1.2
  }
}
```

### Example: Using Bumpers for Shooting

```json
{
  "gamepad": {
    "forward": "RIGHT_TRIGGER",
    "backward": "LEFT_TRIGGER",
    "rotateAxis": "RIGHT_STICK_X",
    "shoot": "RIGHT_BUMPER",
    "stickDeadzone": 0.2,
    "triggerThreshold": 0.1,
    "rotationSensitivity": 1.0
  }
}
```

## Tips

1. **Edit while the game is closed** - Changes are loaded at startup
2. **Backup your config** - Copy `input-config.json` before experimenting
3. **Delete to reset** - Delete the config file and restart to get default settings
4. **Test incrementally** - Change one control at a time to test
5. **Share configs** - You can share your config file with friends!

## Troubleshooting

**Config not loading?**
- Check JSON syntax (use a JSON validator)
- Make sure quotes are correct
- Verify the file is in the right location

**Invalid key/button names?**
- Check spelling and capitalization
- Refer to the supported keys/buttons lists above
- Game logs will show warnings for unknown keys

**Gamepad not detected?**
- Make sure the gamepad is connected before starting the game
- Check that your gamepad is supported by your OS
- Try reconnecting the gamepad

**Controls feel wrong?**
- Adjust `stickDeadzone` if stick is too sensitive/not responsive
- Adjust `rotationSensitivity` if rotation is too fast/slow
- Adjust `triggerThreshold` if triggers are too sensitive

## Support

For issues or questions, check the game logs at:
- The console output when running the game
- Or look for log files in the game directory

The game will log which configuration was loaded at startup.
