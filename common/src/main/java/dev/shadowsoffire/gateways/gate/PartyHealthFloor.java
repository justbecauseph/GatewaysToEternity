package dev.shadowsoffire.gateways.gate;

import dev.shadowsoffire.gateways.Gateways;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Party encounter floor for wave entities.
 * <p>
 * Waves are authored as the {@link #FLOOR_PLAYERS three-player} version of the encounter. When a wave entity spawns, its
 * max health is multiplied by {@code max(FLOOR_PLAYERS, n) / FLOOR_PLAYERS}, capped at {@code CAP_PLAYERS / FLOOR_PLAYERS},
 * where {@code n} is the number of players near the gateway. One to three players face the wave as authored; four and
 * five players face up to 5/3 of its health.
 * <p>
 * The multiplier is a single {@link Operation#ADD_MULTIPLIED_TOTAL} modifier with the stable id {@link #MODIFIER_ID}, so it
 * scales the entity's final max health and leaves every other modifier in place.
 */
public final class PartyHealthFloor {

    /**
     * The id of the max health modifier added to wave entities faced by more than {@link #FLOOR_PLAYERS} players.
     */
    public static final Identifier MODIFIER_ID = Gateways.loc("party_health_floor");

    /**
     * The party size a wave is authored for. Smaller parties face the wave unchanged.
     */
    public static final int FLOOR_PLAYERS = 3;

    /**
     * The party size beyond which additional players no longer add health.
     */
    public static final int CAP_PLAYERS = 5;

    /**
     * The radius, in blocks, around the gateway in which players are counted.
     */
    public static final double PLAYER_RADIUS = 64;

    private PartyHealthFloor() {}

    /**
     * Returns the max health multiplier for a wave entity faced by {@code players} players.
     *
     * @param players The number of counted players.
     * @return {@code max(FLOOR_PLAYERS, min(players, CAP_PLAYERS)) / FLOOR_PLAYERS}.
     */
    public static double factor(int players) {
        return Math.max(FLOOR_PLAYERS, Math.min(players, CAP_PLAYERS)) / (double) FLOOR_PLAYERS;
    }

    /**
     * Counts the players in {@code level} within {@link #PLAYER_RADIUS} blocks of {@code pos} that are alive and not spectating.
     *
     * @param level The level the gateway is in.
     * @param pos   The position of the gateway.
     * @return The number of counted players.
     */
    public static int countPlayers(ServerLevel level, Vec3 pos) {
        double radiusSq = PLAYER_RADIUS * PLAYER_RADIUS;
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(pos) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    /**
     * Applies the max health multiplier for {@code factor} to the entity. A factor of 1 or less adds no modifier.
     * <p>
     * The caller is responsible for refilling the entity's health afterwards.
     *
     * @param entity The wave entity.
     * @param factor The multiplier from {@link #factor(int)}.
     */
    public static void apply(LivingEntity entity, double factor) {
        if (factor <= 1) return;
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER_ID, factor - 1, Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

}
