package dev.shadowsoffire.gateways.gate;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.shadowsoffire.gateways.Gateways;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Scales the quantities that gateway rewards pay out. Other mods register {@link Provider}s, and the factors they return
 * multiply together. With no providers, every reward is generated unchanged.
 */
public final class RewardScale {

    /** Upper bound on the combined scale, so a misbehaving provider cannot spawn unbounded rewards. */
    public static final double MAX_SCALE = 16;

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
    /** The provider classes that have already been warned about, so each class logs at most one warning. */
    private static final Set<Class<?>> WARNED = ConcurrentHashMap.newKeySet();

    private RewardScale() {}

    /**
     * Supplies the reward scale of a gateway.
     */
    @FunctionalInterface
    public interface Provider {

        /**
         * Returns the factor for rewards this gate grants to the summoner; 1 means unchanged.
         *
         * @param gate     The gateway granting the rewards.
         * @param summoner The summoning player.
         * @return The factor. NaN, infinite and negative factors are ignored, and a provider that throws a {@link RuntimeException} or
         *         {@link LinkageError} counts as 1.
         */
        double scale(GatewayEntity gate, Player summoner);
    }

    /**
     * Registers a provider. Its factor multiplies the factors of all other providers. Registering a provider that is already
     * registered does nothing, so its factor applies once.
     * <p>
     * Synchronized so the check and the add are one step: two threads registering the same provider, as NeoForge's parallel
     * mod loading can, still add it once.
     *
     * @param provider The provider to register.
     */
    public static synchronized void register(Provider provider) {
        Objects.requireNonNull(provider);
        if (PROVIDERS.stream().noneMatch(p -> p == provider)) {
            PROVIDERS.add(provider);
        }
    }

    /**
     * Removes a provider added with {@link #register(Provider)}.
     *
     * @param provider The provider to remove.
     */
    public static void unregister(Provider provider) {
        PROVIDERS.removeIf(p -> p == provider);
    }

    /**
     * Computes the combined scale of the rewards a gateway grants to a summoner.
     *
     * @param gate     The gateway granting the rewards.
     * @param summoner The summoning player.
     * @return The product of all providers' factors, clamped to [0, {@link #MAX_SCALE}]. Ignored factors count as 1.
     */
    public static double of(GatewayEntity gate, Player summoner) {
        double scale = 1;
        boolean zero = false;
        for (Provider provider : PROVIDERS) {
            double factor;
            try {
                factor = provider.scale(gate, summoner);
            }
            catch (RuntimeException | LinkageError e) {
                // A broken provider counts as 1, so it cannot make a gate lose the rewards that the other providers scale.
                if (WARNED.add(provider.getClass())) {
                    Gateways.LOGGER.warn("Reward scale provider {} threw an exception. Its factor is treated as 1.", provider.getClass().getName(), e);
                }
                continue;
            }
            if (Double.isNaN(factor) || Double.isInfinite(factor) || factor < 0) {
                warnInvalid(provider, factor);
                continue;
            }
            // Tracked apart from the product, because an overflowed product times zero would be NaN.
            zero |= factor == 0;
            scale *= factor;
        }
        return zero ? 0 : Math.min(scale, MAX_SCALE);
    }

    /**
     * Scales a whole-number quantity. The fractional part of {@code n * scale} is rounded up with a probability equal to
     * itself, so the expected result is exactly {@code n * scale}.
     * <p>
     * A scale of 1 returns {@code n} without consuming randomness, and so does a product that is already whole.
     *
     * @param n      The authored quantity.
     * @param scale  The factor, such as one returned by {@link #of(GatewayEntity, Player)}.
     * @param random The source of the rounding roll.
     * @return {@code floor(n * scale)}, plus one with probability equal to the fractional part of {@code n * scale}.
     */
    public static int scaleCount(int n, double scale, RandomSource random) {
        if (scale == 1) return n;
        double scaled = n * scale;
        if (scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        int whole = (int) Math.floor(scaled);
        double fraction = scaled - whole;
        return fraction > 0 && random.nextDouble() < fraction ? whole + 1 : whole;
    }

    private static void warnInvalid(Provider provider, double factor) {
        if (WARNED.add(provider.getClass())) {
            Gateways.LOGGER.warn("Ignoring reward scale provider {}, which returned {}. Factors must be finite and not negative.", provider.getClass().getName(), factor);
        }
    }
}
