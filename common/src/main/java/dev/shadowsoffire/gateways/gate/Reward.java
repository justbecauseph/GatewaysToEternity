package dev.shadowsoffire.gateways.gate;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.gateways.Gateways;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.placebo.util.PersistentData;
import dev.shadowsoffire.placebo.codec.CodecMap;
import dev.shadowsoffire.placebo.codec.CodecProvider;
import dev.shadowsoffire.placebo.json.NBTAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * A Reward is provided when a gateway wave is finished.
 */
public interface Reward extends CodecProvider<Reward> {

    public static final CodecMap<Reward> CODEC = new CodecMap<>("Gateway Reward");

    /**
     * Called when this reward is to be granted to the player, either on gate or wave completion.
     *
     * @param level    The level the gateway is in
     * @param gate     The gateway entity
     * @param summoner The summoning player
     * @param list     When generating item rewards, add them to this list instead of directly to the player or the world.
     */
    public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list);

    /**
     * Generates this reward with its quantity multiplied by {@code scale}. Implementations scale the amount they pay out
     * (stack counts, loot rolls, experience) once, and wrappers pass the scale to the reward they wrap. The default calls
     * the four-argument {@link #generateLoot(ServerLevel, GatewayEntity, Player, Consumer)} {@code RewardScale.scaleCount(1, scale, random)}
     * times, which suits rewards that pay out one thing per call.
     *
     * @param level    The level the gateway is in
     * @param gate     The gateway entity
     * @param summoner The summoning player
     * @param list     When generating item rewards, add them to this list instead of directly to the player or the world.
     * @param scale    The factor from {@link RewardScale#of}. 1 generates the reward unchanged. The built-in rewards clamp it as
     *                 {@link RewardScale#scaleCount} does: NaN and negative scales count as 0, and scales above
     *                 {@link RewardScale#MAX_SCALE} count as {@link RewardScale#MAX_SCALE}.
     */
    public default void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
        int times = RewardScale.scaleCount(1, scale, level.getRandom());
        for (int i = 0; i < times; i++) {
            this.generateLoot(level, gate, summoner, list);
        }
    }

    public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list);

    public static void initCodecs() {
        register("stack", StackReward.CODEC);
        register("stack_list", StackListReward.CODEC);
        register("entity_loot", EntityLootReward.CODEC);
        register("loot_table", LootTableReward.CODEC);
        register("chanced", ChancedReward.CODEC);
        register("command", CommandReward.CODEC);
        register("experience", ExperienceReward.CODEC);
        register("summon", SummonReward.CODEC);
        register("counted", CountedReward.CODEC);
    }

    private static void register(String id, Codec<? extends Reward> codec) {
        CODEC.register(Gateways.loc(id), codec);
    }

    /**
     * Builds the template with a count of 1. Building it at its authored count would fail for a count above the max stack
     * size, since {@link ItemStackTemplate#create()} returns {@link ItemStack#EMPTY} for such a count.
     */
    private static ItemStack prototype(ItemStackTemplate template) {
        return template.withCount(1).create();
    }

    /**
     * Passes the template's authored count, scaled, to {@code list} as stacks no larger than the item's max stack size.
     */
    private static void emitStacks(ItemStackTemplate template, double scale, RandomSource random, Consumer<ItemStack> list) {
        ItemStack prototype = prototype(template);
        if (prototype.isEmpty()) return;
        int remaining = RewardScale.scaleCount(template.count(), scale, random);
        int maxStack = prototype.getMaxStackSize();
        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            list.accept(prototype.copyWithCount(count));
            remaining -= count;
        }
    }

    /**
     * Provides a single stack as a reward.
     */
    public static record StackReward(ItemStackTemplate stack, Optional<String> desc) implements Reward {

        public static Codec<StackReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                ItemStackTemplate.CODEC.fieldOf("stack").forGetter(StackReward::stack),
                Codec.STRING.optionalFieldOf("desc").forGetter(StackReward::desc))
            .apply(inst, StackReward::new));

        public StackReward(ItemStackTemplate stack) {
            this(stack, Optional.empty());
        }

        public StackReward(ItemStackTemplate stack, String desc) {
            this(stack, Optional.of(desc));
        }

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            this.generateLoot(level, gate, summoner, list, 1);
        }

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            emitStacks(this.stack, scale, level.getRandom(), list);
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            Component name = this.desc.<Component>map(Component::translatable).orElseGet(() -> prototype(this.stack).getHoverName());
            list.accept(Gateways.lang("tooltip", "with_count", this.stack.count(), name));
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }

    /**
     * Provides a list of stacks as a reward.
     */
    public static record StackListReward(List<ItemStackTemplate> stacks) implements Reward {

        public static Codec<StackListReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                ItemStackTemplate.CODEC.listOf().fieldOf("stacks").forGetter(StackListReward::stacks))
            .apply(inst, StackListReward::new));

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            this.generateLoot(level, gate, summoner, list, 1);
        }

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            for (ItemStackTemplate stack : this.stacks) {
                emitStacks(stack, scale, level.getRandom(), list);
            }
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            for (ItemStackTemplate stack : this.stacks) {
                list.accept(Gateways.lang("tooltip", "with_count", stack.count(), prototype(stack).getHoverName()));
            }
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }

    /**
     * Provides multiple rolls of an entity's loot table as a reward.
     */
    public static record EntityLootReward(EntityType<?> type, @Nullable CompoundTag nbt, int rolls) implements Reward {

        public static Codec<EntityLootReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(EntityLootReward::type),
                NBTAdapter.EITHER_CODEC.optionalFieldOf("nbt").forGetter(r -> Optional.ofNullable(r.nbt)),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rolls", 1).forGetter(EntityLootReward::rolls))
            .apply(inst, (type, nbt, rolls) -> new EntityLootReward(type, nbt.orElse(null), rolls)));

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            this.generateLoot(level, gate, summoner, list, 1);
        }

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            this.rollLoot(level, summoner, list, RewardScale.scaleCount(this.rolls, scale, level.getRandom()));
        }

        /**
         * Creates the entity once, rolls its loot table {@code rolls} times as if the summoner had killed it, and removes the entity.
         */
        private void rollLoot(ServerLevel level, Player summoner, Consumer<ItemStack> list, int rolls) {
            try {
                CompoundTag data = this.nbt != null ? this.nbt.copy() : new CompoundTag();
                data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(this.type).toString());
                Entity entity = EntityType.loadEntityRecursive(data, level, new EntitySpawnRequest(EntitySpawnReason.SPAWNER, false), EntityProcessor.NOP);
                if (entity == null) return;
                PersistentData.of(entity).putBoolean("apoth.no_pinata", true);
                if (entity instanceof LivingEntity living) {
                    Optional<ResourceKey<LootTable>> table = living.getLootTable();
                    for (int i = 0; i < rolls && table.isPresent(); i++) {
                        living.snapTo(summoner.getX(), summoner.getY(), summoner.getZ(), 0, 0);
                        DamageSource src = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC_KILL), summoner);
                        living.hurtServer(level, src, 1);
                        // The five-arg overload hands the loot to a consumer instead of the world, which is
                        // exactly what this reward wants. It replaces a reflected call to the protected
                        // three-arg version wrapped in NeoForge's captureDrops -- an added field, so no
                        // access widener could have reached it. Vanilla just offers the seam directly.
                        living.dropFromLootTable(level, level.damageSources().playerAttack(summoner), true, table.get(), list);
                    }
                }
                entity.remove(RemovalReason.DISCARDED);
            }
            catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            list.accept(Component.translatable("reward.gateways.entity", this.rolls, Component.translatable(this.type.getDescriptionId())));
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }

    /**
     * Provides a roll of a single loot table as a reward.
     */
    public static record LootTableReward(Identifier table, int rolls, String desc) implements Reward {

        public static Codec<LootTableReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                Identifier.CODEC.fieldOf("loot_table").forGetter(LootTableReward::table),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rolls", 1).forGetter(LootTableReward::rolls),
                Codec.STRING.fieldOf("desc").forGetter(LootTableReward::desc))
            .apply(inst, LootTableReward::new));

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            this.generateLoot(level, gate, summoner, list, 1);
        }

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            LootTable realTable = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, this.table));
            int rolls = RewardScale.scaleCount(this.rolls, scale, level.getRandom());
            for (int i = 0; i < rolls; i++) {
                LootParams.Builder ctx = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, gate.getPosition(1));
                ctx.withLuck(summoner.getLuck()).withParameter(LootContextParams.THIS_ENTITY, summoner);
                realTable.getRandomItems(ctx.create(LootContextParamSets.CHEST)).forEach(list);
            }
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            list.accept(Component.translatable("reward.gateways.loot_table", this.rolls, this.desc.isEmpty() ? this.table : Component.translatable(this.desc)));
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }

        public static LootTableReward create(ResourceKey<LootTable> table, int rolls, String desc) {
            return new LootTableReward(table.identifier(), rolls, desc);
        }
    }

    /**
     * Wraps a reward with a random chance applied to it.
     */
    public static record ChancedReward(Reward reward, float chance) implements Reward {

        public static Codec<ChancedReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                Reward.CODEC.fieldOf("reward").forGetter(ChancedReward::reward),
                Codec.FLOAT.fieldOf("chance").forGetter(ChancedReward::chance))
            .apply(inst, ChancedReward::new));

        protected static final DecimalFormat fmt = new DecimalFormat("##.##%");

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            this.generateLoot(level, gate, summoner, list, 1);
        }

        /**
         * Rolls the chance once, then generates the wrapped reward with the scale.
         */
        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            if (level.getRandom().nextFloat() < this.chance) this.reward.generateLoot(level, gate, summoner, list, scale);
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            this.reward.appendHoverText(ctx, c -> {
                list.accept(Component.translatable("reward.gateways.chance", fmt.format(this.chance), c));
            });
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }

    /**
     * Provides a roll of a single loot table as a reward.
     */
    public static record CommandReward(String command, String desc) implements Reward {

        public static Codec<CommandReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                Codec.STRING.fieldOf("command").forGetter(CommandReward::command),
                Codec.STRING.fieldOf("desc").forGetter(CommandReward::desc))
            .apply(inst, CommandReward::new));

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            String realCmd = this.command.replace("<summoner>", summoner.getGameProfile().name());
            level.getServer().getCommands().performPrefixedCommand(gate.createCommandSourceStackForNameResolution((ServerLevel) gate.level()), realCmd);
        }

        /**
         * Commands never scale: the command runs once regardless of the scale.
         */
        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            this.generateLoot(level, gate, summoner, list);
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            list.accept(Component.translatable(this.desc));
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }

    /**
     * Provides a certain amount of XP as a reward, as orbs of {@code orbSize} experience each.
     * <p>
     * An {@code orbSize} below 1 is treated as 1. The codec accepts it rather than rejecting it, so a datapack that sets one
     * still loads; such a reward used to hang the server when it was granted.
     */
    public static record ExperienceReward(int xp, int orbSize) implements Reward {

        public static Codec<ExperienceReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                Codec.INT.fieldOf("experience").forGetter(ExperienceReward::xp),
                Codec.INT.optionalFieldOf("orb_size", 5).forGetter(ExperienceReward::orbSize))
            .apply(inst, ExperienceReward::new));

        public ExperienceReward {
            orbSize = Math.max(1, orbSize);
        }

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            this.generateLoot(level, gate, summoner, list, 1);
        }

        /**
         * Spawns {@code xp / orbSize} orbs, rounded up, with the orb count scaled. Unscaled this is the payout of 7.0.4 and
         * earlier, which rounds the experience up to whole orbs. Scaling the orb count keeps the orbs whole, so the expected
         * experience is that payout times the scale; scaling the experience and then spawning whole orbs would round each
         * scaled payout up to a multiple of {@code orbSize}.
         */
        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            int orbs = RewardScale.scaleCount(this.xp > 0 ? Math.ceilDiv(this.xp, this.orbSize) : 0, scale, level.getRandom());
            for (int i = 0; i < orbs; i++) {
                level.addFreshEntity(new ExperienceOrb(level, gate.getX(), gate.getY(), gate.getZ(), this.orbSize));
            }
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            list.accept(Component.translatable("reward.gateways.experience", this.xp));
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }

    /**
     * Summons a {@link WaveEntity} as a reward.
     */
    public static record SummonReward(WaveEntity entity) implements Reward {

        public static Codec<SummonReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                WaveEntity.CODEC.fieldOf("entity").forGetter(SummonReward::entity))
            .apply(inst, SummonReward::new));

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            for (int i = 0; i < entity.getCount(); i++) {
                Entity ent = entity.createEntity(level, gate);
                if (ent != null) {
                    Vec3 pos = gate.getGateway().spawnAlgo().spawn(level, gate.position(), gate, ent);
                    ent.setPos(pos != null ? pos : gate.position());
                    WaveEntity.positionPassengers(ent);
                    level.addFreshEntityWithPassengers(ent);
                }
            }
        }

        /**
         * Summons are never scaled: the entity count is the authored count regardless of the scale.
         */
        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            this.generateLoot(level, gate, summoner, list);
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            list.accept(Component.translatable("reward.gateways.summon", this.entity.getDescription()));
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }

    /**
     * Rolls the same reward multiple times.
     */
    public static record CountedReward(Reward reward, int count) implements Reward {

        public static Codec<CountedReward> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                Reward.CODEC.fieldOf("reward").forGetter(CountedReward::reward),
                Codec.intRange(1, 1024).fieldOf("count").forGetter(CountedReward::count))
            .apply(inst, CountedReward::new));

        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list) {
            this.generateLoot(level, gate, summoner, list, 1);
        }

        /**
         * Repeats the wrapped reward {@code count} times, and passes the scale to each repetition. Each repetition scales the
         * wrapped reward once, so a count of 3 at scale 2 gives three copies of a 16-stack doubled to 32, or 96 in total. Command
         * and summon rewards ignore the scale, so they still run or summon once per count.
         */
        @Override
        public void generateLoot(ServerLevel level, GatewayEntity gate, Player summoner, Consumer<ItemStack> list, double scale) {
            for (int i = 0; i < this.count; i++) {
                this.reward.generateLoot(level, gate, summoner, list, scale);
            }
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {
            this.reward.appendHoverText(ctx, c -> {
                list.accept(Gateways.lang("tooltip", "with_count", this.count, c));
            });
        }

        @Override
        public Codec<? extends Reward> getCodec() {
            return CODEC;
        }
    }
}
