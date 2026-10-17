## 7.1.0
* Added `RewardScale`, a hook for other mods to scale gateway rewards. Gateways itself does not compute a scale.
  * Register a `RewardScale.Provider` with `RewardScale.register`. Each provider returns a factor for a gateway's rewards, and the factors multiply. The product is clamped to `RewardScale.MAX_SCALE`, which is 16. With no providers the scale is 1, so rewards are unchanged.
  * Registering a provider that is already registered does nothing, so its factor applies once.
  * A provider returning NaN, infinity or a negative value is ignored. A provider that throws a `RuntimeException` or `LinkageError` counts as 1. Gateways logs a warning once per provider class, so a broken provider cannot make a gate lose its rewards.
* Gateway rewards scale when a wave or a gateway completes. Wave rewards, completion rewards and endless modifier rewards all apply the scale. Each reward scales once, and fractional amounts round up with a probability equal to their fraction, so the average is the authored amount times the scale.
  * Stack and stack list rewards: the item count scales. Stacks are split to the item's max stack size.
  * Entity loot and loot table rewards: the number of rolls scales.
  * Experience rewards: the experience scales.
  * Chanced rewards keep their chance, and pass the scale to the reward they wrap.
  * Counted rewards: the wrapped reward is repeated the authored count of times, and each repetition gets the scale.
  * Command and summon rewards never scale. A counted command runs once per count, and a counted summon summons once per count.
  * Tooltips still show the authored amounts.
* Added a five-argument `Reward.generateLoot` overload that takes the scale. Its default generates the reward a whole number of times, rounded so the expected count is the scale. This suits rewards that pay out one thing per call.
* Fixed summon rewards not adding the summoned entity's passengers to the level. They now join the level riding their vehicle, as summon failures already did.
* Fixed `gateways:loot_table` rewards throwing 'Parameters not allowed in this parameter set' on 26.2 because they passed a tool to the chest parameter set; Hellish Fortress and Overworldian Nights completion rewards could not generate.
* Fixed stack rewards whose count is above their item's max stack size, such as 32 ender pearls, paying out nothing. They now pay out in full, split into max-size stacks.
  * Their tooltips showed a count of 0 and logged a warning each time they were drawn. They now show the authored count and item.

## 7.0.4
* Wave entities' passengers are now placed on their vehicle before `GateEvents.WAVE_ENTITY_SPAWNED` fires and the entity joins the level. This covers riders from the wave entity's NBT and riders that wave modifiers attach, such as Apotheosis's `apotheosis:passenger`.
  * Previously they joined the level at their load position, usually (0, 0, 0). A rider in an unloaded chunk never moved onto its vehicle and could not be found by UUID.
  * Addons that spawn wave entities themselves can call `WaveEntity.positionPassengers(Entity)` before adding the entity to the level.

## 7.0.3
* Summon failures now fire `GateEvents.FAILURE_ENTITY_SPAWNED` for each summoned entity, just before it and its passengers join the level, so listeners can record state that entity-add processing reads.
  * Failure summons do not fire `WAVE_ENTITY_SPAWNED`: they are not wave members, and are summoned while the gateway fails.
* Summon failures now add the summoned entity's passengers to the level, placed on their vehicle. Previously only the entity itself joined the level.
* Corrected the documentation of `GateEvents.WAVE_ENTITY_SPAWNED`: it fires just before the entity and its passengers are added to the level, not after.

## 7.0.2
* Added a party health floor for wave entities. Waves are authored as the three-player version of the encounter.
  * When a wave entity spawns, its max health is multiplied by `max(3, n) / 3`, capped at `5 / 3`, where `n` is the number of players within 64 blocks of the gateway that are alive and not spectating.
  * One to three players face the wave as authored; four players face 4/3 of its health, and five or more face 5/3.
  * Applies to normal and endless gateways, and to living passengers. The multiplier is a separate `ADD_MULTIPLIED_TOTAL` max health modifier with the id `gateways:party_health_floor`; other wave modifiers are unchanged.
* Added Fabric GameTests (`:fabric:runGameTest`, run by `build`).
* Requires Placebo 11.0.1 and Apothic Attributes 4.0.1, the minimums since 7.0.1. The generated NeoForge metadata now states them too.

## 7.0.0
* Ported to Minecraft 26.2.
* Updated to Placebo 11.0.0 and Apothic Attributes 4.0.0.
* The generated crafting recipes no longer carry a `category` field, since 26.2 removed the recipe book category from crafting recipes.

## 6.0.2
* Updated to Placebo 10.0.2.
* Fixed endless gateways crashing when an attribute wave modifier was applied multiple times in one wave. Fixes Apotheosis#1768.
  * Attribute wave modifiers now use generated (ephemeral) modifier ids. The `modifier_id` field is no longer used, and is ignored if present.

## 6.0.1
* Updated to Placebo 10.0.1.
  * This means that various systems using `RandomAttributeModifier` now have a mandatory `modifier_id` field. This field should be unique in-context, but does not need to be globally unique.

## 6.0.0
* Ported to Minecraft 26.1.2.
* Added two new Iron Gateways.

## 5.1.0
* A stock datapack is now generated with each Gateways release.
* Added two new fields to `GateRules` - `lives` and `requires_nearby_player`. These allow setting more restrictions on how players must interact with a gateway.
  * `lives` specifies the number of times that nearby players may die before the gateway fails.
  * `requires_nearby_player` forces players to stay within the leash range of the gateway, or the gate will fail.
* `WaveModifier#apply` now has context of the `GatewayEntity`.
* Added `CountedReward` (type `gateways:counted`) which allows repeating any other reward multiple times.
* Added an optional `desc` field to `StackReward`.
* Fixed an issue that caused gateway particles to flicker on item or xp orb pickup.
* Added the `finish_gateway` criteria trigger.
  * This can be used to make advancements that complete when the player finishes a specific gateway.
  * Previously this was possible through command rewards, but that was a bit ugly.
* Fixed an issue where Endless Gateways could fail on world reload.

## 5.0.4
* Updated to Placebo 9.9.0.

## 5.0.3
* Toblerone0508: Updated recipes to use more tags.
* nageih: Updated Chinese Translation.
* Fixed subtitles being untranslated.
* Updated errored gate pearl text to simply state the broken gateway name.
  * The previous text mostly resulted in incorrect bug reports, so it wasn't serving the intended purpose.
* Started working on Datagen for Gateways. Only implemented the basic blaze gateway for now, but this should ideally make it easier to make new gateways in the future.

## 5.0.2
* Added the active gateway entity as context to `WaveEntity#createEntity`.

## 5.0.1
* Updated to NeoForge 21.1.61 and Apothic Attributes 2.4.0.

## 5.0.0
* Updated to Minecraft 1.21.1.
* Added a workaround for Jade not displaying properly when Gateway boss bars were shown.

## 4.2.6
* Quarkrus: Added russian translation.

## 4.2.5
* Made gate pearls report the creator modid instead of always reporting `gateways`.

## 4.2.4
* Fixed the way that spawn algorithms operate when in a void world.

## 4.2.3
* Fixed the `/open_gateway` command not being registered.

## 4.2.2
* Fixed a bug where attack targets could be set to a fake player, causing mobs to lock onto nothing.
* Fixed the Gateway of Overworldian Nights not allowing discards.
  * This flag should typically be raised for any gate that spawns a mob that can delete itself.

## 4.2.1
* Added the ability for Endless Modifiers to adjust the wave time and setup time.

## 4.2.0
* This update brings a substantial refactor to the Gateway json format - you will need to update your files accordingly!
  * Many new controls have been added, and some old controls have been adjusted. The default values of things have also changed.
  * The new schema files are located [here](./schema/).
* Gateways now depends on Apothic Attributes.
* Removed all of the original single-mob Gateways.
* Added the following Gateways:
  * Gate of the Emerald Grove.
  * Gate of Overworldian Nights.
  * Gate of the Hellish Fortress.
  * Single-mob Gateways for Blazes, Endermen, and Slimes.
  * Endless Blaze Gateway.
  * More to come in future updates.
* Jared: Added native CraftTweaker support, including bindings for the gateway entity and all of the gateway events.
* Added an event handler to prevent Wave entities from despawning while the gate is alive.
  * Wave Entities always have `Mob#setPersistenceRequired` called, but mobs could bypass this via event.
* Removed the 25 block exclusion radius for Gateways.
  * This was replaced with a check that a Gateway's bounding box may not overlap with another Gateway.
  * Additionally, a Gateway may now declare a `spacing` value to prevent it from being placed near other Gateways.
* Updated the default value for `allow_discarding` from `false` to `true`.
* Wave entities may now be declared with a `count` so that multiple copies do not have to be made for the same entity.
* Removed `completion_xp` and added the `experience` reward type.
* Wave entities may now specify a `desc` key to change what is shown in the tooltip.
* Top-level `rewards` will now be displayed as `Key Rewards` and are always shown on the pearl.
  * Most rewards should be in the wave, but noteworthy completion rewards should be placed in this section.
* Rewards and Failures now enforce usage of the namespace in their type keys. The namespace for all the defaults is `gateways`.
* Redid Gate Pearl tooltips.
* Gate Pearls can now stack to 64.
* Oh, and as a side effect, it is now possible for addons to be created which register custom gateway types.
* The CurseForge Page has been updated to reflect these changes.

## 4.1.1
* Removed forge dependency line from the mods.toml and marked as Forge and NeoForge for CF.
  * The dependency will be added back and the Forge marker will be removed once CF supports Neo correctly.
* Reduced default leash range from 32 to 24.

## 4.1.0
* Updated to Placebo 8.3.0 and refactored code to use DynamicHolder instead of keeping live Gateway references.

## 4.0.2
* Updated to Placebo 8.2.2.

## 4.0.1
* Updated to Placebo 8.2.0.

## 4.0.0
* Updated to 1.20.1.

## 3.2.2
* Added the `remove_mobs_on_failure` flag to allow keeping the mobs around if the gate is failed.

## 3.2.1
* Fixed GateEvent.WaveEnd and GateEvent.WaveEntitySpawned not firing.
  * Somehow these were lost between 1.18 and 1.19.

## 3.2.0
* Updated to Placebo 7.2.0
* Added some more color to the tooltips.
* The "Hold Alt to show Failures" text will no longer show if the Gateway has no failure penalties.
* Gateways will now properly fail (instead of stalling) when a mob is removed but not killed.
* Gateways now have three new fields: "spawn_algorithm", "player_damage_only", and "allow_discarding".
  * "spawn_algorithm" allows for selecting how Wave Entity spawn positions are selected.
  * "player_damage_only" can be used to enforce that the Wave Entities are only hurt by player damage (excluding fake players).
  * "allow_discarding" can be used to enable discards (entity removals that are not kills) to count as kills. This is enabled by default for the creeper gates.
  * See the schema for more information.
* The "allow_discarding" and "player_damage_only" properties will be reflected in the gate tooltip. The "spawn_algorithm" property will not.
* The JSON Schema has been updated to reflect 3.2.0, and is available [here](https://gist.github.com/Shadows-of-Fire/a45a2742b7a0842c50738d3df3ce8148).

## 3.1.1
* Forward port of 2.2.1

## 3.1.0
* Fixed an issue where gateways would fail to spawn wave entities or would spawn them too far away.
* Revamped Gate Pearl tooltips! Waves can now be scrolled through manually, and the display for elements has been adjusted.
* Added Failures, actions that trigger when the gateway is failed. These allow pack makers to increase the risk/reward ratio.
* Changed how command rewards/failures are displayed on gate tooltips. Instead of showing the raw command, a translation key must be provided.
* Gate entities that teleport will no longer be able to teleport outside of the gateway boundary.

## 3.0.2
* Forward-ported all changes from 2.1.5

## 3.0.1
* Added forge events that fire on various gateway actions.

## 3.0.0
* Updated to MC 1.19.2

## 2.2.1
* Increased the rate at which items are dropped if the number of dropped items is very high.
* Added a potential fix for infinite fog.
* Gateways will now properly fail if a gate entity is teleported to another dimension.
* Stack List Rewards will no longer crash.
* Chanced Rewards will no longer display incorrectly.

## 2.2.0
* Backported all changes from 3.1.0

## 2.1.5
* alin529: Added chinese translation
* Wave entities will now always attempt to spawn on the surface, and will not spawn midair.
* Waves will no longer be skipped if the gate is unloaded.
* Gateway particles will now show through walls to indicate the position of wave entities.

## 2.1.4
* Added forge events that fire on various gateway actions. (backport of 3.0.1)

## 2.1.3
* Added the "command" reward type, which allows for execution of a command as a reward.
  * Details are in the JSON schema document.
* Made the /open_gateway command accept an entity selector instead of a blockpos for the target location.

## 2.1.2
* Fixed spawned entities reporting their spawn reason as natural instead of spawner.

## 2.1.1
* Updated to Placebo 6.6.2

## 2.1.0
* Fixed passengers in wave nbt data.
* Added support for other mods to add sub-serializers to the wave entity types.
* Added an optional leash_range param to the Gateway definition.

## 2.0.2
* Added a "Gates Defeated" statistic.
* Fixed /open_gateway not accepting decimal positions.
* Darkosto: Fixed sounds being in stereo.
* Ignored empty stacks in reward processing, so the sound doesn't play erroneously.

## 2.0.1
* Fixed LootTableReward not supporting more than 1 roll.

## 2.0.0
* Rewrote the entire mod, updated all the textures.  Basically a brand new mod.
* Updated to 1.18.2.

## 1.1.0
* Added the /open_gateway command which allows spawning a gateway at a position.
* Fixed a bug where a gateway would crash if the summoner ID was unset.