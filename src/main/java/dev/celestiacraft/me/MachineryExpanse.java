package dev.celestiacraft.me;

import dev.celestiacraft.me.api.addon.AddonBootstrap;
import dev.celestiacraft.me.api.energy.EnergyHatchPartMachine;
import dev.celestiacraft.me.api.energy.EnergyHatchSizes;
import dev.celestiacraft.me.api.energy.EnergyTypes;
import dev.celestiacraft.me.api.gui.factory.MachineUIFactory;
import dev.celestiacraft.me.api.item.MachineItem;
import dev.celestiacraft.me.api.lang.MELang;
import dev.celestiacraft.me.api.machine.trait.RecipeLogic;
import dev.celestiacraft.me.api.recipe.MERecipeSerializer;
import dev.celestiacraft.me.api.recipe.content.ContentKinds;
import dev.celestiacraft.me.api.registry.MERegistries;
import dev.celestiacraft.me.config.MEConfig;
import dev.celestiacraft.me.integration.jei.MultiblockPreviewLang;
import dev.celestiacraft.me.modular.ModularMachine;
import dev.celestiacraft.me.modular.ModuleHostMachine;
import dev.celestiacraft.me.modular.ModuleMachine;
import dev.celestiacraft.me.threading.ThreadedHatches;
import dev.celestiacraft.me.threading.ThreadedRecipeStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MachineryExpanse.MODID)
public class MachineryExpanse {
	public static final String MODID = "machinery_expanse";
	public static final String NAME = "Machinery Expanse";
	public static final Logger LOGGER = LogManager.getLogger(NAME);

	public static ResourceLocation loadResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public MachineryExpanse(FMLJavaModLoadingContext context) {
		IEventBus bus = context.getModEventBus();

		MERegistries.init(bus);
		MELang.add("me.tooltip.library", "Registered by Machinery-Expanse", "由 Machinery-Expanse 注册");
		MachineUIFactory.register();
		MERecipeSerializer.register(bus);
		AddonBootstrap.init(bus);
		initLibraryLang();

		bus.addListener(this::commonSetup);

		MinecraftForge.EVENT_BUS.register(this);

		context.registerConfig(ModConfig.Type.COMMON, MEConfig.SPEC);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			LOGGER.info("[ME] {} addon(s) discovered", AddonBootstrap.getAddons().size());
		});
	}

	private static void initLibraryLang() {
		// 配方 / 线程
		RecipeLogic.initLang();
		ThreadedHatches.initLang(); // 内部含 ThreadedRecipeLogic / ThreadHatchPartMachine / ThreadedMultiblockMachine
		ThreadedRecipeStatus.initLang();

		// 配方内容种类(内置物品/流体 + 第三方注册进来的)
		ContentKinds.initLang();

		// 能量
		EnergyTypes.initLang();
		EnergyHatchPartMachine.initLang();
		EnergyHatchSizes.initLang();

		// 模块化
		ModuleMachine.initLang();
		ModuleHostMachine.initLang();
		ModularMachine.initLang();

		// 物品 / UI
		MachineItem.initLang();
		MultiblockPreviewLang.initLang(); // JEI 预览(这个类不碰 JEI，所以 datagen 环境也能调)
	}
}