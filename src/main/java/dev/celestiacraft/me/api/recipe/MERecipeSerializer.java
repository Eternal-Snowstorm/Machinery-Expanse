package dev.celestiacraft.me.api.recipe;

import dev.celestiacraft.me.MachineryExpanse;
import dev.celestiacraft.me.api.registry.MERegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link MERecipe} 的序列化器 —— 本库所有配方共用一个(注册名固定 {@code machinery_expanse:generic}).
 *
 * <p>
 * 从 GTM 的 {@code api.recipe.GTRecipeSerializer} 精简拆出来. GTM 要在 JSON/网络里
 * 逐一搬运 capability→Content 的多张映射表、概率逻辑表、条件、研究数据……本库的配方形状是固定的
 * 三件套({@link Content} 列表 + duration/eut/tier)，所以直接用一个
 * {@link RecordCodecBuilder} 描述，不再手写 fromJson.
 *
 * <p>
 * <b>与需求文档的一处偏差</b>：1.20.1 <b>没有</b> {@code StreamCodec}
 * ({@code net.minecraft.network.codec} 是 1.20.5 才进原版的)，因此网络侧只能按该版本的契约
 * 实现 {@link #fromNetwork(ResourceLocation, FriendlyByteBuf)} / {@link #toNetwork(FriendlyByteBuf, MERecipe)};
 * 逻辑侧仍然提供 {@link #CODEC} 供 JSON 与 datagen 使用. 约定：{@code toNetwork}/{@code fromNetwork}
 * 的字段顺序与 {@link #CODEC} 的 JSON 字段一一对应.
 *
 * <p>
 * <b>接入方式</b>(本库的 {@code MachineryExpanse} 主类里加一行即可)：
 *
 * <pre>{@code
 * MERecipeSerializer.register(modBus);
 * }</pre>
 */
public class MERecipeSerializer implements RecipeSerializer<MERecipe> {
	/**
	 * {@code tier} 字段的「未指定」哨兵值：由 {@code |eut|} 反查.
	 */
	public static final int AUTO_TIER = -1;

	/**
	 * 配方编解码器.
	 *
	 * <p>
	 * JSON 形状({@code "type": "machinery_expanse:generic"} 由原版 {@code FinishedRecipe#serializeRecipe()} 补上)：
	 *
	 * <pre>{@code
	 * {
	 *   "type": "machinery_expanse:generic",
	 *   "recipe_type": "machinery_expanse:example",
	 *   "inputs":  [ { "type": "item",  "ingredient": { "item": "minecraft:iron_ingot" }, "count": 2 } ],
	 *   "outputs": [ { "type": "fluid", "fluid": { "FluidName": "minecraft:water", "Amount": 1000 } } ],
	 *   "duration": 200,
	 *   "eut": 32,
	 *   "tier": 1
	 * }
	 * }</pre>
	 */
	public static final Codec<MERecipe> CODEC = RecordCodecBuilder.create((instance) -> {
		return instance.group(MERegistries.RECIPE_TYPES.codec()
						.fieldOf("recipe_type")
						.forGetter((recipe) -> {
							return recipe.getRecipeType();
						}),
				Content.CODEC.listOf().optionalFieldOf("inputs", List.of())
						.forGetter((recipe) -> {
							return recipe.getInputs();
						}),
				Content.CODEC.listOf().optionalFieldOf("outputs", List.of())
						.forGetter((recipe) -> {
							return recipe.getOutputs();
						}),
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("duration", MERecipe.DEFAULT_DURATION)
						.forGetter((recipe) -> {
							return recipe.getDuration();
						}),
				Codec.LONG.optionalFieldOf("eut", 0L)
						.forGetter((recipe) -> {
							return recipe.getEut();
						}),
				Codec.INT.optionalFieldOf("tier", AUTO_TIER)
						.forGetter((recipe) -> {
							return recipe.getTier();
						})
		).apply(instance, (recipeType, inputs, outputs, duration, eut, tier) -> {
			return new MERecipe(
					recipeType,
					null,
					inputs,
					outputs,
					duration,
					eut,
					tier == null || tier == AUTO_TIER ? MERecipe.tierFromEut(eut) : tier
			);
		});
	});

	/**
	 * 配方序列化器的 Forge 注册表入口.
	 */
	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister
			.create(ForgeRegistries.Keys.RECIPE_SERIALIZERS, MachineryExpanse.MODID);

	/**
	 * 全局单例 —— 所有 {@link MERecipe} 都用它.
	 */
	public static final MERecipeSerializer SERIALIZER = new MERecipeSerializer();

	/**
	 * 注册名固定为 {@code machinery_expanse:generic}.
	 */
	public static final RegistryObject<MERecipeSerializer> REGISTERED = SERIALIZERS.register("generic", () -> {
		return SERIALIZER;
	});

	/**
	 * 把 {@link #SERIALIZERS} 挂到 mod 事件总线上(在 {@code MachineryExpanse} 的构造里调用一次).
	 */
	public static void register(IEventBus bus) {
		SERIALIZERS.register(bus);
	}

	/**
	 * 从数据包 JSON 读出配方.
	 *
	 * <p>
	 * 失败时抛 {@link JsonParseException}(而不是 DFU 默认的 {@link RuntimeException})：原版
	 * {@code RecipeManager} 只捕获 {@code JsonParseException} / {@code IllegalArgumentException}，
	 * 抛对类型才能让「一条配方写错」退化成「日志里报一条 + 跳过这条配方」，而不是整个数据包加载崩溃.
	 */
	@Override
	public @NotNull MERecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
		String[] errorHolder = new String[1];
		Optional<MERecipe> parsed = CODEC.parse(JsonOps.INSTANCE, json)
				.resultOrPartial((message) -> {
					errorHolder[0] = message;
				});
		if (parsed.isEmpty()) {
			String message = "[ME] failed to decode recipe %s: %s"
					.formatted(id, errorHolder[0] == null ? "unknown decoding error" : errorHolder[0]);
			MachineryExpanse.LOGGER.error(message);
			throw new JsonParseException(message);
		}
		return parsed.get().setId(id);
	}

	@Override
	public MERecipe fromNetwork(@NotNull ResourceLocation id, FriendlyByteBuf buf) {
		ResourceLocation recipeTypeId = buf.readResourceLocation();
		int duration = buf.readVarInt();
		long eut = buf.readLong();
		int tier = buf.readVarInt();
		List<Content> inputs = buf.readCollection(ArrayList::new, Content::fromNetwork);
		List<Content> outputs = buf.readCollection(ArrayList::new, Content::fromNetwork);

		MERecipeType recipeType = MERegistries.RECIPE_TYPES.get(recipeTypeId);
		if (recipeType == null) {
			String message = "[ME] received recipe %s of unknown recipe type %s (client knows %d types)"
					.formatted(id, recipeTypeId, MERegistries.RECIPE_TYPES.size());
			MachineryExpanse.LOGGER.error(message);
			throw new IllegalStateException(message);
		}
		return new MERecipe(recipeType, id, inputs, outputs, duration, eut, tier);
	}

	@Override
	public void toNetwork(FriendlyByteBuf buf, MERecipe recipe) {
		buf.writeResourceLocation(recipe.getRecipeType().getRegistryName());
		buf.writeVarInt(recipe.getDuration());
		buf.writeLong(recipe.getEut());
		buf.writeVarInt(recipe.getTier());
		buf.writeCollection(recipe.getInputs(), (buffer, content) -> {
			content.toNetwork(buffer);
		});
		buf.writeCollection(recipe.getOutputs(), (buffer, content) -> {
			content.toNetwork(buffer);
		});
	}

	/**
	 * 把配方编码成 JSON(datagen 与调试用)，失败返回 null 并打日志.
	 *
	 * @param recipe 配方；其 {@code id} 不会写进 JSON(由数据包文件名决定)
	 */
	@Nullable
	public static JsonObject toJson(MERecipe recipe) {
		String[] errorHolder = new String[1];
		Optional<JsonElement> encoded = CODEC.encodeStart(JsonOps.INSTANCE, recipe)
				.resultOrPartial((message) -> {
					errorHolder[0] = message;
				});

		if (encoded.isEmpty() || !encoded.get().isJsonObject()) {
			MachineryExpanse.LOGGER.error(
					"[ME] failed to encode recipe {}: {}",
					recipe.getId(),
					errorHolder[0] == null ? "result is not a json object" : errorHolder[0]
			);
			return null;
		}
		return encoded.get().getAsJsonObject();
	}
}