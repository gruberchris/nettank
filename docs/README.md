# NetTank Documentation Index

## Player Documentation

Player-facing documentation uses UPPERCASE filenames:

- **[GAME_CONFIG.md](GAME_CONFIG.md)** - Game configuration and settings
- **[INPUT_CUSTOMIZATION.md](INPUT_CUSTOMIZATION.md)** - Keyboard and control customization

## Technical Documentation

Developer and architecture documentation uses lowercase filenames:

### Terrain System

- **[terrain_system.md](terrain_system/terrain_system.md)** - ⭐ Complete reference: architecture, types, collision, generation (START HERE)
- **[terrain_quick_reference.md](terrain_system/terrain_quick_reference.md)** - Quick integration: setup code, common queries, troubleshooting
- **[terrain_layering_system.md](terrain_system/terrain_layering_system.md)** - Deep dive: three-layer system, rendering order, overlay mechanics
- **[procedural_generation_implementation.md](terrain_system/procedural_generation_implementation.md)** - Algorithm: noise generation, flood fill, profiles
- **[server_client_terrain_protocol.md](terrain_system/server_client_terrain_protocol.md)** - Network protocol: seed synchronization, client-server communication
- **[round_based_regeneration.md](terrain_system/round_based_regeneration.md)** - Round system: automatic terrain regeneration between rounds
- **[dynamic_terrain_system.md](terrain_system/dynamic_terrain_system.md)** - Fire system: ignition, spreading, state transitions

### Game Systems

- **[line_of_sight_system.md](los_system/line_of_sight_system.md)** - Vision blocking and fog of war (future)

## Documentation Organization

### Naming Convention

- **UPPERCASE_WITH_UNDERSCORES.md** - Player-facing documentation
- **lowercase_with_underscores.md** - Technical/developer documentation

### File Locations

- Player docs: `docs/` folder
- Technical docs: `docs/` folder
- README: Repository root

## Quick Links

### For Players
- [Configure Game Settings](GAME_CONFIG.md)
- [Customize Controls](INPUT_CUSTOMIZATION.md)

### For Developers
- [Terrain System Overview](terrain_system/terrain_system.md)
- [Quick Setup Guide](terrain_system/terrain_quick_reference.md)
- [Architecture Details](terrain_system/terrain_layering_system.md)

## Contributing

When adding new documentation:

1. **Player docs**: Use UPPERCASE_WITH_UNDERSCORES.md
2. **Technical docs**: Use lowercase_with_underscores.md
3. **Update this index** with the new document
4. **Add relevant links** in related documents
