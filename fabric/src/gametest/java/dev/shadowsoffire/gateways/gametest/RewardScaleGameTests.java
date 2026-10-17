package dev.shadowsoffire.gateways.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;

import dev.shadowsoffire.gateways.GatewayObjects;
import dev.shadowsoffire.gateways.entity.EndlessGatewayEntity;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.gateways.entity.NormalGatewayEntity;
import dev.shadowsoffire.gateways.gate.BossEventSettings;
import dev.shadowsoffire.gateways.gate.GateRules;
import dev.shadowsoffire.gateways.gate.Gateway;
import dev.shadowsoffire.gateways.gate.Reward;
import dev.shadowsoffire.gateways.gate.Reward.ChancedReward;
import dev.shadowsoffire.gateways.gate.Reward.CommandReward;
import dev.shadowsoffire.gateways.gate.Reward.CountedReward;
import dev.shadowsoffire.gateways.gate.Reward.EntityLootReward;
import dev.shadowsoffire.gateways.gate.Reward.ExperienceReward;
import dev.shadowsoffire.gateways.gate.Reward.LootTableReward;
import dev.shadowsoffire.gateways.gate.Reward.StackListReward;
import dev.shadowsoffire.gateways.gate.Reward.StackReward;
import dev.shadowsoffire.gateways.gate.Reward.SummonReward;
import dev.shadowsoffire.gateways.gate.RewardScale;
import dev.shadowsoffire.gateways.gate.StandardWaveEntity;
import dev.shadowsoffire.gateways.gate.Wave;
import dev.shadowsoffire.gateways.gate.endless.ApplicationMode;
import dev.shadowsoffire.gateways.gate.endless.EndlessGateway;
import dev.shadowsoffire.gateways.gate.endless.EndlessModifier;
import dev.shadowsoffire.gateways.gate.normal.NormalGateway;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Covers reward scaling. Each reward scales its quantity once, command and summon rewards never scale, and
 * {@link RewardScale#scaleCount} rounds stochastically. The reward tests pass the scale to the reward directly, so they do
 * not depend on providers. The provider tests register and unregister their providers inside one test method, because
 * providers are global.
 * <p>
 * The summoner is a player that is never added to the level, so it is not a nearby player for other tests to count.
 */
public final class RewardScaleGameTests {

    /** The gate's position, relative to the test. */
    private static final Vec3 GATE_OFFSET = new Vec3(1.5D, 1, 1.5D);
    /** Where summons appear, relative to the test. */
    private static final Vec3 SPAWN_OFFSET = new Vec3(4.5D, 1, 1.5D);
    /** Entities a summon reward summons. */
    private static final int SUMMON_COUNT = 2;
    /** How far a summoned entity or rider may sit from the summon's position: a spider plus a skeleton's riding height. */
    private static final double RIDER_DISTANCE = 3;
    /** The tag that the command reward's marker carries, so the test can find the marker it summoned. */
    private static final String COMMAND_TAG = "gateways_reward_scale";

    @GameTest
    public void stackRewardScalesItemCount(GameTestHelper helper) {
        List<ItemStack> items = generate(helper, new StackReward(stack(Items.DIAMOND, 16)), 3);
        helper.assertValueEqual(total(items), 48, "diamonds from a 16-count stack at scale 3");
        helper.succeed();
    }

    @GameTest
    public void stackRewardSplitsAtMaxStackSize(GameTestHelper helper) {
        List<ItemStack> items = generate(helper, new StackReward(stack(Items.DIAMOND, 64)), 2);
        helper.assertValueEqual(total(items), 128, "diamonds from a 64-count stack at scale 2");
        helper.assertValueEqual(items.size(), 2, "stacks of diamonds at scale 2");
        for (ItemStack stack : items) {
            helper.assertTrue(stack.getCount() <= stack.getMaxStackSize(), "a stack of " + stack.getCount() + " exceeds the max stack size " + stack.getMaxStackSize());
        }
        helper.succeed();
    }

    @GameTest
    public void oversizedStackRewardPaysOut(GameTestHelper helper) {
        // Ender pearls stack to 16, and a 32-count template is above that. The codec allows it, and create() would return EMPTY.
        StackReward reward = new StackReward(stack(Items.ENDER_PEARL, 32));
        List<ItemStack> one = generate(helper, reward, 1);
        helper.assertValueEqual(countOf(one, Items.ENDER_PEARL), 32, "ender pearls from a 32-count stack at scale 1");
        helper.assertValueEqual(one.size(), 2, "stacks of ender pearls at scale 1");
        for (ItemStack stack : one) {
            helper.assertTrue(stack.getCount() <= stack.getMaxStackSize(), "a stack of " + stack.getCount() + " exceeds the max stack size " + stack.getMaxStackSize());
        }
        helper.assertValueEqual(countOf(generate(helper, reward, 2), Items.ENDER_PEARL), 64, "ender pearls from a 32-count stack at scale 2");
        // Splitting must copy the prototype, never change the shared empty stack.
        helper.assertTrue(ItemStack.EMPTY.isEmpty() && ItemStack.EMPTY.getCount() == 0, "ItemStack.EMPTY was modified");
        helper.succeed();
    }

    @GameTest
    public void oversizedStackTooltipShowsAuthoredCount(GameTestHelper helper) {
        // Tooltips show the authored amount, including for a count above the max stack size, where create() would give 0 air.
        List<MutableComponent> single = tooltip(helper, new StackReward(stack(Items.ENDER_PEARL, 32)));
        helper.assertValueEqual(single.size(), 1, "tooltip lines of a stack reward");
        assertCountLine(helper, single.get(0), 32, Items.ENDER_PEARL);

        List<MutableComponent> list = tooltip(helper, new StackListReward(List.of(stack(Items.ENDER_PEARL, 32), stack(Items.EMERALD, 4))));
        helper.assertValueEqual(list.size(), 2, "tooltip lines of a stack list reward");
        assertCountLine(helper, list.get(0), 32, Items.ENDER_PEARL);
        assertCountLine(helper, list.get(1), 4, Items.EMERALD);
        helper.succeed();
    }

    @GameTest
    public void stackListRewardScalesEachEntry(GameTestHelper helper) {
        StackListReward reward = new StackListReward(List.of(stack(Items.DIAMOND, 16), stack(Items.EMERALD, 4)));
        List<ItemStack> items = generate(helper, reward, 2);
        helper.assertValueEqual(countOf(items, Items.DIAMOND), 32, "diamonds from a 16-count entry at scale 2");
        helper.assertValueEqual(countOf(items, Items.EMERALD), 8, "emeralds from a 4-count entry at scale 2");
        helper.succeed();
    }

    @GameTest
    public void experienceRewardScalesTotalXp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 gatePos = helper.absoluteVec(GATE_OFFSET);
        AABB box = new AABB(gatePos, gatePos).inflate(3);
        try {
            generate(helper, new ExperienceReward(10, 5), 2);
            List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box);
            helper.assertValueEqual(orbs.size(), 4, "experience orbs at scale 2");
            helper.assertValueEqual(orbs.stream().mapToInt(ExperienceOrb::getValue).sum(), 20, "experience at scale 2");
        }
        finally {
            level.getEntitiesOfClass(ExperienceOrb.class, box).forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest
    public void lootTableRewardRollsScale(GameTestHelper helper) {
        // The trial key table has one pool that rolls once and drops one trial key, so the key count is the roll count.
        LootTableReward reward = new LootTableReward(Identifier.fromNamespaceAndPath("minecraft", "spawners/trial_chamber/key"), 1, "Trial key");
        helper.assertValueEqual(countOf(generate(helper, reward, 1), Items.TRIAL_KEY), 1, "trial keys rolled at scale 1");
        helper.assertValueEqual(countOf(generate(helper, reward, 2), Items.TRIAL_KEY), 2, "trial keys rolled at scale 2");
        helper.succeed();
    }

    @GameTest
    public void lootTableRewardGeneratesVanillaChest(GameTestHelper helper) {
        // The simple dungeon chest is the table Overworldian Nights rewards with. Every entry is an item, so one roll gives at least one item.
        LootTableReward reward = new LootTableReward(Identifier.fromNamespaceAndPath("minecraft", "chests/simple_dungeon"), 1, "Simple dungeon");
        helper.assertTrue(total(generate(helper, reward, 1)) >= 1, "items from one roll of the simple dungeon chest");
        helper.succeed();
    }

    @GameTest
    public void entityLootRewardRollsScale(GameTestHelper helper) {
        // A white sheep's loot table has one pool that rolls once and drops one white wool, so the wool count is the roll count.
        EntityLootReward reward = new EntityLootReward(EntityTypes.SHEEP, null, 1);
        helper.assertValueEqual(countOf(generate(helper, reward, 1), Items.WOOL.white()), 1, "white wool rolled at scale 1");
        helper.assertValueEqual(countOf(generate(helper, reward, 2), Items.WOOL.white()), 2, "white wool rolled at scale 2");
        helper.succeed();
    }

    @GameTest
    public void chancedRewardKeepsItsChance(GameTestHelper helper) {
        StackReward stack = new StackReward(stack(Items.DIAMOND, 16));
        helper.assertValueEqual(total(generate(helper, new ChancedReward(stack, 1F), 2)), 32, "diamonds from a certain chanced reward at scale 2");
        helper.assertValueEqual(total(generate(helper, new ChancedReward(stack, 0F), 2)), 0, "diamonds from an impossible chanced reward at scale 2");
        helper.succeed();
    }

    @GameTest
    public void countedRewardScalesCountOnce(GameTestHelper helper) {
        // Three copies of a 16-stack scaled to 32 are 96 diamonds. Scaling the count of 3 as well would give 6 * 32 = 192.
        CountedReward reward = new CountedReward(new StackReward(stack(Items.DIAMOND, 16)), 3);
        helper.assertValueEqual(total(generate(helper, reward, 2)), 96, "diamonds from a counted reward at scale 2");
        helper.succeed();
    }

    @GameTest
    public void commandRewardRunsOnceAtScale(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 gatePos = helper.absoluteVec(GATE_OFFSET);
        AABB box = new AABB(gatePos, gatePos).inflate(3);
        CommandReward reward = new CommandReward(markerCommand(), "Marker");
        try {
            generate(helper, reward, 3);
            helper.assertValueEqual(commandMarkers(level, box).size(), 1, "markers summoned by a command reward at scale 3");
        }
        finally {
            commandMarkers(level, box).forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest
    public void countedCommandRunsOncePerCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 gatePos = helper.absoluteVec(GATE_OFFSET);
        AABB box = new AABB(gatePos, gatePos).inflate(3);
        CountedReward reward = new CountedReward(new CommandReward(markerCommand(), "Marker"), 2);
        try {
            generate(helper, reward, 3);
            helper.assertValueEqual(commandMarkers(level, box).size(), 2, "markers summoned by a counted command reward of 2 at scale 3");
        }
        finally {
            commandMarkers(level, box).forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest
    public void summonRewardSummonsCountAtScale(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 spawnPos = helper.absoluteVec(SPAWN_OFFSET);
        AABB box = new AABB(spawnPos, spawnPos).inflate(RIDER_DISTANCE);
        try {
            generate(helper, new SummonReward(spiderJockey(SUMMON_COUNT)), 3);

            List<Mob> spiders = mobsOfType(level, box, EntityTypes.SPIDER);
            helper.assertValueEqual(spiders.size(), SUMMON_COUNT, "spiders summoned at scale 3");
            for (Entity spider : spiders) {
                helper.assertValueEqual(spider.getPassengers().stream().map(Entity::getType).toList(), List.of(EntityTypes.SKELETON), "riders on a summoned spider");
                for (Entity rider : spider.getPassengers()) {
                    helper.assertTrue(level.getEntity(rider.getUUID()) == rider, "a summoned skeleton is not in the level");
                    helper.assertTrue(rider.getVehicle() == spider, "a summoned skeleton is not riding its spider");
                    helper.assertTrue(rider.position().distanceTo(spawnPos) < RIDER_DISTANCE, "a summoned skeleton is at " + rider.position() + ", not on its spider");
                }
            }
        }
        finally {
            level.getEntitiesOfClass(Mob.class, box).forEach(e -> e.getPassengersAndSelf().forEach(Entity::discard));
        }
        helper.succeed();
    }

    @GameTest
    public void countedSummonSummonsOncePerCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 spawnPos = helper.absoluteVec(SPAWN_OFFSET);
        AABB box = new AABB(spawnPos, spawnPos).inflate(RIDER_DISTANCE);
        try {
            generate(helper, new CountedReward(new SummonReward(spiderJockey(1)), 2), 3);
            helper.assertValueEqual(mobsOfType(level, box, EntityTypes.SPIDER).size(), 2, "spiders summoned by a counted summon reward of 2 at scale 3");
        }
        finally {
            level.getEntitiesOfClass(Mob.class, box).forEach(e -> e.getPassengersAndSelf().forEach(Entity::discard));
        }
        helper.succeed();
    }

    @GameTest
    public void defaultOverloadRepeatsFourArgumentRewards(GameTestHelper helper) {
        // A reward that only implements the four-argument method, as rewards written before 7.1.0 do, pays out one diamond per call.
        Reward legacy = new LegacyReward();
        helper.assertValueEqual(total(generate(helper, legacy, 1)), 1, "calls of a four-argument reward at scale 1");
        helper.assertValueEqual(total(generate(helper, legacy, 3)), 3, "calls of a four-argument reward at scale 3");
        helper.assertValueEqual(total(generate(helper, legacy, 0)), 0, "calls of a four-argument reward at scale 0");
        // Wrappers pass the scale through to it: two counted repetitions at scale 3, and a certain chance at scale 3.
        helper.assertValueEqual(total(generate(helper, new CountedReward(legacy, 2), 3)), 6, "calls of a counted four-argument reward of 2 at scale 3");
        helper.assertValueEqual(total(generate(helper, new ChancedReward(legacy, 1F), 3)), 3, "calls of a certain chanced four-argument reward at scale 3");

        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        int trials = 2000;
        long sum = 0;
        for (int i = 0; i < trials; i++) {
            List<ItemStack> items = new ArrayList<>();
            legacy.generateLoot(helper.getLevel(), gate, summoner, items::add, 2.5D);
            int calls = total(items);
            helper.assertTrue(calls == 2 || calls == 3, "a four-argument reward at scale 2.5 was called " + calls + " times");
            sum += calls;
        }
        double mean = (double) sum / trials;
        helper.assertTrue(Math.abs(mean - 2.5D) < 0.05D, "mean calls of a four-argument reward at scale 2.5 over " + trials + " trials was " + mean);
        helper.succeed();
    }

    @GameTest
    public void scaleCountMatchesExpectedValue(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7L);
        helper.assertValueEqual(RewardScale.scaleCount(7, 1, random), 7, "7 at scale 1");
        // Scale 1 must not consume randomness, so the next draw matches a fresh source with the same seed.
        helper.assertValueEqual(random.nextInt(), RandomSource.create(7L).nextInt(), "randomness consumed at scale 1");
        helper.assertValueEqual(RewardScale.scaleCount(2, 2.5D, random), 5, "2 at scale 2.5");

        RandomSource seeded = RandomSource.create(42L);
        int trials = 2000;
        long sum = 0;
        for (int i = 0; i < trials; i++) {
            int result = RewardScale.scaleCount(1, 1.5D, seeded);
            helper.assertTrue(result == 1 || result == 2, "1 at scale 1.5 gave " + result);
            sum += result;
        }
        double mean = (double) sum / trials;
        helper.assertTrue(Math.abs(mean - 1.5D) < 0.05D, "mean of 1 at scale 1.5 over " + trials + " trials was " + mean);
        helper.succeed();
    }

    @GameTest
    public void providerScalesWaveRewards(GameTestHelper helper) {
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        List<GatewayEntity> seenGates = new ArrayList<>();
        List<Player> seenSummoners = new ArrayList<>();
        RewardScale.Provider provider = (g, p) -> {
            seenGates.add(g);
            seenSummoners.add(p);
            return 3;
        };

        RewardScale.register(provider);
        try {
            List<ItemStack> items = diamondWave().spawnRewards(helper.getLevel(), gate, summoner);
            helper.assertValueEqual(total(items), 48, "diamonds from a wave at a provider scale of 3");
            helper.assertValueEqual(seenGates.size(), 1, "provider calls per wave reward grant");
            helper.assertTrue(seenGates.get(0) == gate && seenSummoners.get(0) == summoner, "provider did not receive the gate and summoner");
        }
        finally {
            RewardScale.unregister(provider);
        }
        helper.succeed();
    }

    @GameTest
    public void providerFactorsMultiply(GameTestHelper helper) {
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        RewardScale.Provider two = (g, p) -> 2;
        RewardScale.Provider three = (g, p) -> 3;

        RewardScale.register(two);
        RewardScale.register(three);
        try {
            helper.assertValueEqual(RewardScale.of(gate, summoner), 6D, "combined scale of providers 2 and 3");
            helper.assertValueEqual(total(diamondWave().spawnRewards(helper.getLevel(), gate, summoner)), 96, "diamonds from a wave at a combined scale of 6");
        }
        finally {
            RewardScale.unregister(two);
            RewardScale.unregister(three);
        }
        helper.succeed();
    }

    @GameTest
    public void invalidProviderFactorsAreIgnored(GameTestHelper helper) {
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        RewardScale.Provider nan = (g, p) -> Double.NaN;
        RewardScale.Provider negative = (g, p) -> -1;
        RewardScale.Provider infinite = (g, p) -> Double.POSITIVE_INFINITY;
        RewardScale.Provider three = (g, p) -> 3;

        RewardScale.register(nan);
        RewardScale.register(negative);
        RewardScale.register(infinite);
        RewardScale.register(three);
        try {
            helper.assertValueEqual(RewardScale.of(gate, summoner), 3D, "combined scale with NaN, negative and infinite providers");
        }
        finally {
            RewardScale.unregister(nan);
            RewardScale.unregister(negative);
            RewardScale.unregister(infinite);
            RewardScale.unregister(three);
        }
        helper.succeed();
    }

    @GameTest
    public void throwingProviderCountsAsOne(GameTestHelper helper) {
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        RewardScale.Provider broken = (g, p) -> {
            throw new IllegalStateException("broken provider");
        };
        RewardScale.Provider two = (g, p) -> 2;
        RewardScale.Provider three = (g, p) -> 3;

        RewardScale.register(broken);
        RewardScale.register(two);
        RewardScale.register(three);
        try {
            helper.assertValueEqual(RewardScale.of(gate, summoner), 6D, "combined scale of a throwing provider with providers 2 and 3");
            // The gate still grants its rewards: a 16-stack of diamonds at scale 6.
            helper.assertValueEqual(total(diamondWave().spawnRewards(helper.getLevel(), gate, summoner)), 96, "diamonds from a wave beside a throwing provider");
        }
        finally {
            RewardScale.unregister(broken);
            RewardScale.unregister(two);
            RewardScale.unregister(three);
        }
        helper.succeed();
    }

    @GameTest
    public void linkageErrorCountsAsOne(GameTestHelper helper) {
        // A provider that references a class or field the runtime lacks fails with a LinkageError, not a RuntimeException.
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        RewardScale.Provider missing = (g, p) -> {
            throw new NoSuchFieldError("missing");
        };
        RewardScale.Provider three = (g, p) -> 3;

        RewardScale.register(missing);
        RewardScale.register(three);
        try {
            helper.assertValueEqual(RewardScale.of(gate, summoner), 3D, "combined scale of a provider throwing NoSuchFieldError and a provider of 3");
        }
        finally {
            RewardScale.unregister(missing);
            RewardScale.unregister(three);
        }
        helper.succeed();
    }

    @GameTest
    public void duplicateRegistrationCountsOnce(GameTestHelper helper) {
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        RewardScale.Provider three = (g, p) -> 3;

        RewardScale.register(three);
        RewardScale.register(three);
        try {
            helper.assertValueEqual(RewardScale.of(gate, summoner), 3D, "combined scale of a provider registered twice");
        }
        finally {
            RewardScale.unregister(three);
        }
        // One unregister removes the only registration, so nothing is left behind to apply.
        helper.assertValueEqual(RewardScale.of(gate, summoner), 1D, "combined scale after the provider was unregistered");
        helper.succeed();
    }

    @GameTest
    public void combinedScaleClampsToMaxScale(GameTestHelper helper) {
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        RewardScale.Provider four = (g, p) -> 4;
        RewardScale.Provider eight = (g, p) -> 8;

        RewardScale.register(four);
        RewardScale.register(eight);
        try {
            helper.assertValueEqual(RewardScale.of(gate, summoner), 16D, "combined scale of 4 and 8");
        }
        finally {
            RewardScale.unregister(four);
            RewardScale.unregister(eight);
        }
        helper.succeed();
    }

    @GameTest
    public void zeroFactorOutweighsOverflow(GameTestHelper helper) {
        GatewayEntity gate = gate(helper);
        ServerPlayer summoner = summoner(helper);
        // The first two factors overflow the product to infinity. The zero factor must still give 0, not NaN.
        RewardScale.Provider huge = (g, p) -> 1.0E200;
        RewardScale.Provider alsoHuge = (g, p) -> 1.0E200;
        RewardScale.Provider zero = (g, p) -> 0;

        RewardScale.register(huge);
        RewardScale.register(alsoHuge);
        RewardScale.register(zero);
        try {
            helper.assertValueEqual(RewardScale.of(gate, summoner), 0D, "combined scale with a zero factor and overflowing factors");
        }
        finally {
            RewardScale.unregister(huge);
            RewardScale.unregister(alsoHuge);
            RewardScale.unregister(zero);
        }
        helper.succeed();
    }

    @GameTest
    public void completionRewardsApplyScale(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 gatePos = helper.absoluteVec(GATE_OFFSET);
        AABB box = new AABB(gatePos, gatePos).inflate(3);
        CompletionFixture gate = new CompletionFixture(level, normalGateway(helper, List.of(new StackReward(stack(Items.DIAMOND, 16)))), summoner(helper));
        RewardScale.Provider three = (g, p) -> 3;

        RewardScale.register(three);
        try {
            gate.complete();
            helper.assertValueEqual(itemEntityCount(level, box, Items.DIAMOND), 48, "diamonds from a completion reward at a provider scale of 3");
        }
        finally {
            RewardScale.unregister(three);
            level.getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest
    public void endlessModifierRewardsApplyScale(GameTestHelper helper) {
        EndlessModifier modifier = EndlessModifier.builder()
            .applicationMode(new ApplicationMode.OnlyOnWave(1))
            .reward(new StackReward(stack(Items.DIAMOND, 16)))
            .build();
        EndlessCompletionFixture gate = new EndlessCompletionFixture(helper.getLevel(), endlessGateway(helper, modifier), summoner(helper));
        RewardScale.Provider three = (g, p) -> 3;

        RewardScale.register(three);
        try {
            gate.complete();
            helper.assertValueEqual(countOf(gate.undropped(), Items.DIAMOND), 48, "diamonds from an endless modifier reward at a provider scale of 3");
        }
        finally {
            RewardScale.unregister(three);
        }
        helper.succeed();
    }

    /**
     * Generates {@code reward} at {@code scale} for a gate at {@link #GATE_OFFSET} and a summoner there, and returns the items.
     */
    private static List<ItemStack> generate(GameTestHelper helper, Reward reward, double scale) {
        List<ItemStack> items = new ArrayList<>();
        reward.generateLoot(helper.getLevel(), gate(helper), summoner(helper), items::add, scale);
        return items;
    }

    private static List<MutableComponent> tooltip(GameTestHelper helper, Reward reward) {
        List<MutableComponent> lines = new ArrayList<>();
        reward.appendHoverText(Item.TooltipContext.of(helper.getLevel()), lines::add);
        return lines;
    }

    /**
     * Asserts that a tooltip line is the {@code tooltip.gateways.with_count} line for {@code count} of {@code item}.
     */
    private static void assertCountLine(GameTestHelper helper, MutableComponent line, int count, Item item) {
        String expectedName = new ItemStack(item).getHoverName().getString();
        helper.assertTrue(line.getContents() instanceof TranslatableContents t && "tooltip.gateways.with_count".equals(t.getKey()),
            "expected a with_count tooltip line for " + expectedName + ", got " + line);
        Object[] args = ((TranslatableContents) line.getContents()).getArgs();
        helper.assertValueEqual(args.length, 2, "arguments of the tooltip line for " + expectedName);
        helper.assertValueEqual(args[0], count, "count in the tooltip line for " + expectedName);
        String name = args[1] instanceof Component c ? c.getString() : String.valueOf(args[1]);
        helper.assertValueEqual(name, expectedName, "item name in the tooltip line for " + expectedName);
    }

    private static ItemStackTemplate stack(Item item, int count) {
        return new ItemStackTemplate(item, count);
    }

    private static int total(List<ItemStack> items) {
        return items.stream().mapToInt(ItemStack::getCount).sum();
    }

    private static int countOf(List<ItemStack> items, Item item) {
        return items.stream().filter(s -> s.getItem() == item).mapToInt(ItemStack::getCount).sum();
    }

    /** A wave whose only reward is a 16-count stack of diamonds. */
    private static Wave diamondWave() {
        return Wave.builder()
            .entity(zombie())
            .reward(new StackReward(stack(Items.DIAMOND, 16)))
            .maxWaveTime(200)
            .setupTime(0)
            .build();
    }

    private static StandardWaveEntity zombie() {
        return new StandardWaveEntity.Builder(EntityTypes.ZOMBIE).build();
    }

    /** A spider carrying a skeleton, with the skeleton written in NBT the way a datapack would. */
    private static StandardWaveEntity spiderJockey(int count) {
        CompoundTag skeleton = rider(EntityTypes.SKELETON);
        return new StandardWaveEntity.Builder(EntityTypes.SPIDER)
            .nbt(tag -> {
                ListTag passengers = new ListTag();
                passengers.add(skeleton);
                tag.put("Passengers", passengers);
                return tag;
            })
            .count(count)
            .build();
    }

    private static CompoundTag rider(EntityType<?> type) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", EntityType.getKey(type).toString());
        return tag;
    }

    private static List<Entity> commandMarkers(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(Entity.class, box, e -> e.getType() == EntityTypes.MARKER && e.entityTags().contains(COMMAND_TAG));
    }

    private static List<Mob> mobsOfType(ServerLevel level, AABB box, EntityType<?> type) {
        return level.getEntitiesOfClass(Mob.class, box, m -> m.getType() == type);
    }

    /** A gate at {@link #GATE_OFFSET} with no completion rewards. */
    private static RewardFixture gate(GameTestHelper helper) {
        return new RewardFixture(helper.getLevel(), normalGateway(helper, List.of()), helper.absoluteVec(GATE_OFFSET));
    }

    /** A normal gateway with the given completion rewards, whose summons appear at {@link #SPAWN_OFFSET}. */
    private static NormalGateway normalGateway(GameTestHelper helper, List<Reward> completionRewards) {
        Vec3 spawnPos = helper.absoluteVec(SPAWN_OFFSET);
        Wave wave = Wave.builder().entity(zombie()).maxWaveTime(200).setupTime(0).build();
        return new NormalGateway(Gateway.Size.SMALL, TextColor.fromRgb(0xFFFFFF), List.of(wave), completionRewards, List.of(),
            (level, pos, gate, toSpawn) -> spawnPos, GateRules.DEFAULT, BossEventSettings.DEFAULT);
    }

    /** An endless gateway with one modifier and no failures, whose summons appear at {@link #SPAWN_OFFSET}. */
    private static EndlessGateway endlessGateway(GameTestHelper helper, EndlessModifier modifier) {
        Vec3 spawnPos = helper.absoluteVec(SPAWN_OFFSET);
        Wave wave = Wave.builder().entity(zombie()).maxWaveTime(200).setupTime(0).build();
        return new EndlessGateway(Gateway.Size.SMALL, TextColor.fromRgb(0xFFFFFF), wave, List.of(modifier), List.of(),
            (level, pos, gate, toSpawn) -> spawnPos, GateRules.DEFAULT, BossEventSettings.DEFAULT);
    }

    /** A player at {@link #GATE_OFFSET} that is never added to the level, so it is not a nearby player for other tests to count. */
    private static ServerPlayer summoner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "gateways_test"), ClientInformation.createDefault());
        player.setPos(helper.absoluteVec(GATE_OFFSET));
        return player;
    }

    private static String markerCommand() {
        return "summon minecraft:marker ~ ~ ~ {Tags:[\"" + COMMAND_TAG + "\"]}";
    }

    private static int itemEntityCount(ServerLevel level, AABB box, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, box).stream().map(ItemEntity::getItem).filter(s -> s.getItem() == item).mapToInt(ItemStack::getCount).sum();
    }

    /**
     * A reward that implements only the four-argument {@link Reward#generateLoot}, as rewards written before 7.1.0 do. Each call
     * pays out one diamond, so the diamond count is the call count.
     */
    private static final class LegacyReward implements Reward {

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            list.accept(new ItemStack(Items.DIAMOND));
        }

        @Override
        public void appendHoverText(Item.TooltipContext ctx, Consumer<MutableComponent> list) {}

        @Override
        public Codec<? extends Reward> getCodec() {
            throw new UnsupportedOperationException("LegacyReward is never serialized");
        }
    }

    /**
     * A normal gateway that serves a gateway definition without the registry. It is never added to the level, so it never ticks.
     */
    private static final class RewardFixture extends NormalGatewayEntity {

        private final NormalGateway gateway;

        private RewardFixture(ServerLevel level, NormalGateway gateway, Vec3 pos) {
            super(GatewayObjects.NORMAL_GATEWAY.get(), level);
            this.gateway = gateway;
            this.setPos(pos);
        }

        @Override
        public NormalGateway getGateway() {
            return this.gateway;
        }
    }

    /**
     * A normal gateway that completes for a fixed summoner, without the registry. It is never added to the level, so it never ticks.
     * The summoner is returned directly, so completion does not look up a player in the level or create a fake one.
     */
    private static final class CompletionFixture extends NormalGatewayEntity {

        private final NormalGateway gateway;
        private final Player summoner;

        private CompletionFixture(ServerLevel level, NormalGateway gateway, ServerPlayer summoner) {
            super(GatewayObjects.NORMAL_GATEWAY.get(), level);
            this.gateway = gateway;
            this.summoner = summoner;
            this.setPos(summoner.position());
        }

        @Override
        public NormalGateway getGateway() {
            return this.gateway;
        }

        @Override
        public Player summonerOrClosest() {
            return this.summoner;
        }

        private void complete() {
            this.completeGateway();
        }
    }

    /**
     * An endless gateway that completes a wave for a fixed summoner, without the registry. It is never added to the level, so it never ticks.
     */
    private static final class EndlessCompletionFixture extends EndlessGatewayEntity {

        private final EndlessGateway gateway;
        private final Player summoner;

        private EndlessCompletionFixture(ServerLevel level, EndlessGateway gateway, ServerPlayer summoner) {
            super(GatewayObjects.ENDLESS_GATEWAY.get(), level);
            this.gateway = gateway;
            this.summoner = summoner;
            this.setPos(summoner.position());
        }

        @Override
        public EndlessGateway getGateway() {
            return this.gateway;
        }

        @Override
        public Player summonerOrClosest() {
            return this.summoner;
        }

        private void complete() {
            this.completeWave();
        }

        private List<ItemStack> undropped() {
            return List.copyOf(this.undroppedItems);
        }
    }

}
