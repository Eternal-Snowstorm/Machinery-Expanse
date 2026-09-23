package dev.celestiacraft.me.api.addon;

import dev.celestiacraft.me.MachineryExpanse;
import dev.celestiacraft.me.config.MEConfig;
import dev.celestiacraft.me.api.machine.MachineDefinition;
import dev.celestiacraft.me.api.machine.MultiblockMachineDefinition;
import dev.celestiacraft.me.api.registry.MERegisterEvent;
import dev.celestiacraft.me.api.registry.MERegistries;
import dev.celestiacraft.me.api.recipe.MERecipeType;
import dev.celestiacraft.me.data.MELangProvider;
import dev.celestiacraft.me.data.MEMachineModelProvider;
import dev.celestiacraft.me.data.MERecipeProvider;

import lombok.Getter;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * addon 生命周期驱动 —— 注册阶段的调度器.
 *
 * <p>
 * 做四件事：
 * <ol>
 * <li>扫描并实例化所有 {@link MEAddon}(见 {@link AddonFinder})；</li>
 * <li>在 <b>mod 构造期</b> 按固定顺序跑完注册阶段(顺序见 {@link IMEAddon} 类注释).
 * 必须放在构造期而不是 {@code FMLCommonSetupEvent}：注册阶段要创建方块/物品的
 * {@code DeferredRegister} 条目，而 Forge 的 {@code RegisterEvent} 在 setup 之前就已经结束了；</li>
 * <li>跑完 {@link MERegistries#freezeAll()} 冻结注册表 —— 之后再注册直接抛异常，
 * 免得「机器静默没注册上」拖到进游戏才发现；</li>
 * <li>把 {@code FMLCommonSetupEvent} / {@code GatherDataEvent} 转发给 addon. </li>
 * </ol>
 *
 * <p>
 * 每个阶段除了直接回调 addon，还会把对应的 {@link MERegisterEvent} 发到 mod 事件总线上，
 * 这样「不是 addon、但也想挂一笔」的 mod 可以 {@code addGenericListener} 监听.
 */
public class AddonBootstrap {
	@Getter
	private static List<IMEAddon> addons = List.of();
	@Getter
	private static boolean initialised = false;
	private static IEventBus modBus;

	/**
	 * 本库的 mod 事件总线({@link #init(IEventBus)} 之后可用).
	 *
	 * <p>
	 * addon 在 {@code initialize()} 里要用它来 {@code DeferredRegister.register(...)}，
	 * 例如 {@code MyRegistrar.REGISTRAR.attach(AddonBootstrap.modBus())}.
	 */
	public static IEventBus modBus() {
		if (modBus == null) {
			throw new IllegalStateException("AddonBootstrap 还没初始化：请确认 ME 是本 addon 的依赖之一");
		}
		return modBus;
	}

	public static IMEAddon getAddon(String modId) {
		for (IMEAddon addon : addons) {
			if (addon.addonModId().equals(modId)) return addon;
		}
		return null;
	}

	/**
	 * 在 mod 构造函数里调用一次.
	 */
	public static void init(IEventBus modBus) {
		if (initialised) {
			return;
		}
		initialised = true;
		AddonBootstrap.modBus = modBus;

		addons = AddonFinder.getAddons();
		if (addons.isEmpty()) {
			MachineryExpanse.LOGGER.info("[ME] 没有发现 @MEAddon；库已加载但处于空闲状态");
		} else if (logDiscovery()) {
			MachineryExpanse.LOGGER.info(
					"[ME] 发现 {} 个 addon: {}",
					addons.size(),
					addons.stream().map(IMEAddon::addonModId).toList()
			);
		}

		// ① initialize —— 建注册器、把 DeferredRegister 挂上总线
		forEach("initialize", IMEAddon::initialize);

		// ② 仓室能力必须最先(多方块的 abilities(...) 依赖它)
		forEach("registerPartAbilities", IMEAddon::registerPartAbilities);

		// ③ 配方类型
		MERegisterEvent.RL<MERecipeType> recipeTypeEvent = new MERegisterEvent.RL<>(
				MERegistries.RECIPE_TYPES,
				MERecipeType.class
		);
		forEach("registerRecipeTypes", (addon) -> {
			addon.registerRecipeTypes(recipeTypeEvent);
		});
		modBus.post(recipeTypeEvent);

		// ④ 单方块机器与仓室
		MERegisterEvent.RL<MachineDefinition> machineEvent = new MERegisterEvent.RL<>(
				MERegistries.MACHINES, MachineDefinition.class);
		forEach("registerMachines", a -> a.registerMachines(machineEvent));
		modBus.post(machineEvent);

		// ⑤ 多方块
		MERegisterEvent.RL<MultiblockMachineDefinition> multiblockEvent = new MERegisterEvent.RL<>(
				MERegistries.MULTIBLOCKS,
				MultiblockMachineDefinition.class
		);
		forEach("registerMultiblocks", (addon) -> {
			addon.registerMultiblocks(multiblockEvent);
		});
		modBus.post(multiblockEvent);

		// ⑥ UI / 语言
		forEach("registerUI", IMEAddon::registerUI);
		forEach("initLang", IMEAddon::initLang);

		// ⑦ 冻结
		MERegistries.freezeAll();
		MachineryExpanse.LOGGER.info(
				"[ME] 注册表已冻结({} 台机器 / {} 个配方类型)",
				MERegistries.MACHINES.size(),
				MERegistries.RECIPE_TYPES.size()
		);

		// ⑧ 收尾与数据生成
		modBus.addListener(AddonBootstrap::onCommonSetup);
		modBus.addListener(AddonBootstrap::onGatherData);
	}

	private static void forEach(String phase, Consumer<IMEAddon> action) {
		for (IMEAddon addon : addons) {
			try {
				action.accept(addon);
			} catch (Throwable throwable) {
				MachineryExpanse.LOGGER.error(
						"[ME] addon {} 在 {}() 阶段抛异常",
						addon.addonModId(),
						phase,
						throwable
				);
			}
		}
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			forEach("setup", (addon) -> {
				addon.setup(event);
			});
		});
	}

	private static void onGatherData(GatherDataEvent event) {
		DataGenerator generator = event.getGenerator();
		boolean server = event.includeServer();
		boolean client = event.includeClient();

		for (IMEAddon addon : addons) {
			// 语言：把 MELang 里登记的键写成 addon 自己命名空间的 lang json
			if (client) {
				MELangProvider.register(event, addon.addonModId());
				// 机器/仓室的 blockstate + 模型 JSON：按注册表里的 MachineDefinition 自动产出
				generator.addProvider(true, new MEMachineModelProvider(
						generator.getPackOutput(),
						addon.addonModId(),
						event.getExistingFileHelper())
				);
			}
			// 配方：收进一个 provider，再由 provider 统一写出
			if (server) {
				List<FinishedRecipe> collected = new ArrayList<>();
				try {
					addon.registerRecipes(collected::add);
				} catch (Throwable t) {
					MachineryExpanse.LOGGER.error(
							"[ME] addon {} 在 registerRecipes() 阶段抛异常",
							addon.addonModId(),
							t
					);
				}
				if (!collected.isEmpty()) {
					// RecipeProvider 收 PackOutput(1.20.1 的 DataGenerator#getPackOutput)，不是 DataGenerator 本身
					generator.addProvider(true, new MERecipeProvider(
							generator.getPackOutput(),
							addon.addonModId(),
							collected
					));
				}
			}
		}
	}

	private static boolean logDiscovery() {
		return MEConfig.isLogAddonDiscovery();
	}
}