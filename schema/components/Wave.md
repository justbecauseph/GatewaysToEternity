# Description
A Wave contains all the information needed for a Gateway to spawn a single wave of mobs, including the entities, modifiers, rewards, and timers.

# Dependencies
This object references the following objects:
1. [WaveEntity](./WaveEntity.md)
2. [WaveModifier](./WaveModifier.md)
3. [Reward](./Reward.md)

# Schema
```js
{
    "entities": [             // [Mandatory] || The list of entities that will be spawned as part of this wave. May not be empty.
        WaveEntity
    ],              
    "modifiers": [            // [Optional]  || A list of wave modifiers to be applied to all spawned entities.
        WaveModifier
    ],
    "rewards": [              // [Optional]  || A list of rewards that will be provided when this wave is completed.
        Reward
    ],
    "max_wave_time": integer  // [Mandatory] || The maximum time (in ticks) that a player may take to complete this wave.
    "setup_time": integer     // [Mandatory] || The setup time (in ticks) before this wave begins, after the last wave was completed.
}
```

# Party Health Floor
Waves are authored as the three-player version of the encounter. When a wave entity spawns, its max health is multiplied by `max(3, n) / 3`, capped at `5 / 3`, where `n` is the number of players within 64 blocks of the gateway that are alive and not spectating. One to three players face the wave as authored; four players face 4/3 of its health, and five or more face 5/3.

The multiplier is applied after the wave's `modifiers` and before the entity's health is filled, as a separate `add_multiplied_total` max health modifier with the id `gateways:party_health_floor` (added only when the multiplier is above 1). Because it multiplies the total, it also scales max health added later, such as by endless gateway modifiers. It applies to living passengers, and to normal and endless gateways. Players are counted once per entity, when that entity spawns. The result is still clamped to the max health attribute's range (1024 in vanilla, unless a mod such as AttributeFix raises it).

# Examples
A simple wave spawning two blazes and providing the loot of three when completed.
```json
{
    "entities": [{
        "entity": "minecraft:blaze",
        "count": 2
    }],
    "rewards": [{
        "type": "gateways:entity_loot",
        "entity": "minecraft:blaze",
        "rolls": 3
    }],
    "max_wave_time": 750,
    "setup_time": 150
}
```