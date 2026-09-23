package dev.celestiacraft.me.test;

import dev.celestiacraft.me.MachineryExpanse;
import dev.celestiacraft.me.api.addon.AbstractMEAddon;
import dev.celestiacraft.me.api.addon.AddonBootstrap;
import dev.celestiacraft.me.api.addon.MEAddon;
import dev.celestiacraft.me.api.energy.EnergyHatchSizes;
import dev.celestiacraft.me.api.machine.MachineDefinition;
import dev.celestiacraft.me.api.machine.MultiblockMachineDefinition;
import dev.celestiacraft.me.api.recipe.MERecipe;
import dev.celestiacraft.me.api.recipe.RecipeBuilder;
import dev.celestiacraft.me.api.registry.MERegisterEvent;
import dev.celestiacraft.me.api.recipe.MERecipeType;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.function.Consumer;

/**
 * 测试包的 addon 入口 —— 同时是 {@code @MEAddon} 的<b>参考实现</b>.
 *
 * <p>
 * 它把「一个 addon 该长什么样」演示全了：继承 {@link AbstractMEAddon} 拿到注册器，
 * 然后按固定阶段覆写自己关心的那几步(顺序见 {@code IMEAddon} 类注释)：
 * <ol>
 * <li>{@link #initialize()} —— 把注册器挂到 mod 事件总线上；</li>
 * <li>{@link #registerPartAbilities()} —— 仓室能力；</li>
 * <li>{@link #registerRecipeTypes(MERegisterEvent.RL)} —— 配方类型；</li>
 * <li>{@link #registerMachines(MERegisterEvent.RL)} / {@link #registerMultiblocks(MERegisterEvent.RL)} —— 机器；</li>
 * <li>{@link #initLang()} —— 数据生成之前登记语言键；</li>
 * <li>{@link #registerRecipes(Consumer)} —— 数据生成期写配方 JSON. </li>
 * </ol>
 *
 * <p>
 * 注意 {@link TestRegistrations#registerMachineDefinitions()} 里同时做了「多方块」和「仓室」两类注册，
 * 所以它被放在 {@code registerMachines} 阶段调用(多方块注册必须在机器之后，
 * 因为 {@code autoAbilities} 要读已经登记好的能力表).
 */
@MEAddon(modId = MachineryExpanse.MODID, priority = 2000)
public class TestAddon extends AbstractMEAddon {
	public TestAddon() {
		// priority 2000：排在普通 addon 后面，让别人先注册
		super(MachineryExpanse.MODID, 2000);
	}

	@Override
	public void initialize() {
		// ⚠️ 挂的必须是 TestRegistrations 自己那个 REGISTRAR.
		//    基类的 registrar() 会 new 一个**不同的**实例；挂错了就会出现
		//    「机器注册进了本库的表，但方块从没进 Forge 注册表」——
		//    编译期毫无提示，进游戏/跑 datagen 才炸成 Registry Object not present.
		attach(TestRegistrations.REGISTRAR);
		TestRegistrations.registerCreativeTab();
		MachineryExpanse.LOGGER.info("ME test pack: registrar attached");
	}

	@Override
	public void registerPartAbilities() {
		TestRegistrations.registerAbilities();
	}

	@Override
	public void registerRecipeTypes(MERegisterEvent.RL<MERecipeType> event) {
		// 配方类型在 TestRegistrations 的静态字段里**已经进表**了(那里直接调了 MERecipeType.register)，
		// 所以这里只触碰一下类、确保静态初始化跑过 —— 再 event.register(...) 一次会因为重名抛 IllegalStateException.
		MachineryExpanse.LOGGER.debug(
				"ME test pack: recipe types ready ({}, {})",
				TestRegistrations.TEST_RECIPES.getRegistryName(),
				TestRegistrations.TEST_GENERATOR_RECIPES.getRegistryName()
		);
	}

	@Override
	public void registerMachines(MERegisterEvent.RL<MachineDefinition> event) {
		// 仓室 + 多方块一起登记(见类注释：多方块的 autoAbilities 依赖仓室能力表已就绪)
		TestRegistrations.registerAll();
	}

	@Override
	public void registerMultiblocks(MERegisterEvent.RL<MultiblockMachineDefinition> event) {
		// 实际注册在 registerAll() 里完成了；这里只是演示 addon 阶段可以把已注册的定义再报一遍
		if (TestRegistrations.TEST_MULTIBLOCK != null) {
			MachineryExpanse.LOGGER.info(
					"ME test pack: multiblock {} registered",
					TestRegistrations.TEST_MULTIBLOCK.getId()
			);
		}
	}

	@Override
	public void initLang() {
		// TestRegistrations.registerAll() 里已经顺带登记过；这里补上库自己的那几组，
		// 保证 runData 产出的 lang 里库文案也在(addon 不调的话库文案就得靠库自己初始化).
		EnergyHatchSizes.initLang();
	}

	@Override
	public void registerRecipes(Consumer<FinishedRecipe> provider) {
		// 运行时那份已经在 TestRegistrations.registerRecipes() 里进 ALL_RECIPES 了，
		// 这里把**已有的那几条**直接写成 JSON.
		// (不要在这里再 recipeBuilder(...).save(provider) 一遍：那会二次写运行时的配方表，
		//   日志里会出现 "recipe ... was overwritten in the runtime recipe table" 警告. )
		for (String path : new String[] {"iron_to_gold", "cobble_to_stone", "sand_to_glass", "energy_charge",
				"coal_generator"}) {
			MERecipe recipe = RecipeBuilder.ALL_RECIPES.get(MachineryExpanse.loadResource(path));
			if (recipe != null) {
				provider.accept(new RecipeBuilder.BuiltRecipe(recipe));
			}
		}
	}

	@Override
	public void setup(FMLCommonSetupEvent event) {
		TestRegistrations.logSummary();
	}

	/**
	 * 供 JEI / 调试用的入口(当前没用到，留个口子).
	 */
	public static String modBusState() {
		return AddonBootstrap.isInitialised() ? "initialised" : "not initialised";
	}
}