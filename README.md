# Nettank

A multiplayer top-down 2D tank battle game where players control tanks and try to destroy each other.

![img_1.png](docs/images/img_1.png)

## Requirements

- **Java**: OpenJDK 21 (Java 21) or newer
- **Operating System**: Windows, macOS, or Linux
- **Graphics**: OpenGL 3.3 compatible graphics card

## Controls

### Keyboard (Default)

- **W** - Move forward
- **S** - Move backward
- **A** - Rotate left
- **D** - Rotate right
- **Space** - Fire weapon
- **Escape** - Exit game

### Gamepad (Default)

NetTank supports any standard gamepad including:
- Xbox controllers (Xbox One, Xbox Series X/S, Xbox 360)
- PlayStation controllers (DualShock 4, DualSense)
- Nintendo Switch Pro Controller
- Generic USB gamepads

**Default Gamepad Mapping:**
- **Right Trigger (RT/R2)** - Move forward
- **Left Trigger (LT/L2)** - Move backward
- **Right Stick (horizontal)** - Rotate left/right
- **A button (Xbox) / X button (PlayStation)** - Fire weapon

### Customizing Controls

All keyboard keys and gamepad buttons are fully customizable! See [INPUT_CUSTOMIZATION.md](docs/INPUT_CUSTOMIZATION.md) for:
- How to rebind keys and buttons
- Adjusting gamepad sensitivity and deadzones
- Example configurations (Arrow keys, ESDF layout, etc.)
- Troubleshooting tips

## Game Configuration

Players can customize additional game settings by editing `~/.nettank/game-config.json`:

- **Player Name** - Set your display name (max 20 characters)
- **Display Resolution** - Default: 1920x1080 (1080p)
- **Fullscreen Mode** - Toggle fullscreen/windowed mode
- **VSync** - Enable/disable vertical sync

See [GAME_CONFIG.md](docs/GAME_CONFIG.md) for complete configuration documentation, including:
- All supported resolutions (800x600 up to 4K)
- Configuration examples
- Troubleshooting tips

## Launching The Game Client

Note: OpenJDK 21 (Java 21) Runtime is required to run the game client or the server.

The game client is a universal jar file that can be run on any platform with Java installed. To run the game client, use the following command:

```shell
java -jar nettank-client.jar
```

macOS users may need to use the following command to run the game client:

```shell
java -XstartOnFirstThread -jar nettank-client.jar
```

The game client accesses the game server at `localhost:5555` by default. If you want to connect to a different server, you can specify the server address and port as command line arguments:

```shell
java -jar nettank-client.jar <server_address> <server_port> <player_name>
```

## Launching The Game Server

The gamer server can be deployed using Docker. The following command will build the Docker image and run the server:

To build the Docker image:

```shell
docker build -t nettank-server .
```

To run the Docker container using interactive mode:

```shell
docker run -p 5555:5555 nettank-server
```

To run the Docker container in detached mode (background):

```shell
docker run -d -p 5555:5555 --restart unless-stopped nettank-server
```
