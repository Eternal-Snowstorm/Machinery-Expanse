package dev.celestiacraft.me.modular;

import dev.celestiacraft.me.api.energy.IEnergyContainer;
import dev.celestiacraft.me.api.lang.MELang;
import dev.celestiacraft.me.api.machine.IMachineBlockEntity;
import dev.celestiacraft.me.api.machine.multiblock.IMultiPart;
import dev.celestiacraft.me.api.machine.multiblock.WorkableMultiblockMachine;
import dev.celestiacraft.me.api.MEValues;
import dev.celestiacraft.me.config.MEConfig;
import dev.celestiacraft.me.utils.Formatting;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模块化多方块的<b>主机(核心)</b>.
 *
 * <p>
 * Java 重写自 GTEternalTime 的 {@code ETModuleHostMachine}.
 *
 * <p>
 * 一句话机器逻辑：<b>主机只负责「供电」与「等级」两件事</b> —— 单元能不能跑那条配方，
 * 由单元自己按「主机等级 + 主机电量」判断.
 *
 * <p>
 * 供电走本库的能量系统(需求 7)：主机成型时把自己的 {@link IEnergyContainer} 仓室
 * (能源仓 Part)全部找出来，单元借电时从这些仓室里扣.
 */
public abstract class ModuleHostMachine extends WorkableMultiblockMachine {
	/**
	 * 挂在主机上的单元.
	 */
	private final Set<ModuleMachine> modules = new LinkedHashSet<>();

	/**
	 * 缓存的能源仓(结构成型时刷新).
	 */
	private final Set<IEnergyContainer> energyContainers = new LinkedHashSet<>();

	public ModuleHostMachine(IMachineBlockEntity holder) {
		super(holder);
	}

	/**
	 * 主机等级(成型时由能源仓算出)：<b>单元的配方等级以它为准</b>.
	 *
	 * <p>
	 * 默认实现取结构里最高一档能源仓的档位；没有能源仓则退化成自己注册时的 tier.
	 */
	public int hostTier() {
		int best = getTier();
		for (IEnergyContainer container : energyContainers) {
			if (container instanceof dev.celestiacraft.me.api.machine.MetaMachine machine) {
				best = Math.max(best, machine.getTier());
			}
		}
		return best;
	}

	/**
	 * 主机当前可用能量(FE). 单元借电前先看这个值就知道够不够.
	 */
	public long availableEu() {
		long sum = 0;
		for (IEnergyContainer container : energyContainers) {
			sum += container.getEnergyStored();
		}
		return sum;
	}

	/**
	 * 从主机的能源仓里共扣 {@code amount}(FE)；返回<b>实际扣到</b>的量.
	 *
	 * <p>
	 * 逐个仓扣，扣不满就少扣 —— 永远不会扣成负数，调用方按返回值决定是否开工.
	 * 单元侧的统一入口是 {@link ModuleMachine#consumeEu(long)}.
	 */
	public long consumeHostEu(long amount) {
		if (amount <= 0) return 0;
		long remaining = amount;
		long consumed = 0;
		for (IEnergyContainer container : energyContainers) {
			if (remaining <= 0) break;
			long got = container.extractEnergy(remaining);
			consumed += got;
			remaining -= got;
		}
		return consumed;
	}

	/**
	 * 刷新能源仓缓存：结构成型/失效时调用.
	 */
	protected void refreshEnergyContainers() {
		energyContainers.clear();
		for (IMultiPart part : getParts()) {
			if (part instanceof IEnergyContainer container) {
				energyContainers.add(container);
			}
		}
	}

	public void attachModule(ModuleMachine module) {
		if (modules.add(module)) onModulesChanged();
	}

	public void detachModule(ModuleMachine module) {
		if (modules.remove(module)) onModulesChanged();
	}

	/**
	 * 当前挂着的单元(只读).
	 */
	public Set<ModuleMachine> modules() {
		return Collections.unmodifiableSet(modules);
	}

	/**
	 * 单元表变了：重判配方 + 唤醒 tick.
	 *
	 * <p>
	 * ⚠️ 一定要 {@code updateTickSubscription()}：配方逻辑没活干时<b>会自己退订 tick</b>，
	 * 新挂上来的单元可能正躺在退订状态里，不唤醒它就永远不会开始跑.
	 */
	protected void onModulesChanged() {
		getRecipeLogic().markLastRecipeDirty();
		getRecipeLogic().updateTickSubscription();
	}

	@Override
	public void onStructureFormed() {
		super.onStructureFormed();
		refreshEnergyContainers();
		ModuleNetwork.addHost(this);
	}

	@Override
	public void onStructureInvalid() {
		ModuleNetwork.removeHost(this);
		energyContainers.clear();
		super.onStructureInvalid();
	}

	@Override
	public void onUnload() {
		ModuleNetwork.removeHost(this);
		super.onUnload();
	}

	@Override
	public void addDisplayText(List<Component> textList) {
		super.addDisplayText(textList);
		long formed = modules.stream().filter(ModuleMachine::isFormed).count();
		textList.add(Component.translatable(LANG_MODULES, modules.size(), formed));
		textList.add(Component.translatable(LANG_HOST_TIER, MEValues.tierNameRaw(hostTier())));
		textList.add(Component.translatable(LANG_AVAILABLE_EU, Formatting.formatNumber(availableEu())));
	}

	/**
	 * 「单元 N 台(已成型 M)」——参数是两个数量.
	 */
	public static final String LANG_MODULES = "me.machine.host.modules";
	/**
	 * 主机等级(参数 = 档位名).
	 */
	public static final String LANG_HOST_TIER = "me.machine.host.tier";
	/**
	 * 主机可用能量(参数 = 已格式化的数值).
	 */
	public static final String LANG_AVAILABLE_EU = "me.machine.host.available_energy";

	/**
	 * 登记本基类用到的语言键；必须在数据生成之前调用.
	 */
	public static void initLang() {
		MELang.add(LANG_MODULES, "Modules %s (%s formed)", "单元 %s 台(已成型 %s)");
		MELang.add(LANG_HOST_TIER, "Host tier %s (unit recipe tier follows it)",
				"主机等级 %s(单元的配方等级以它为准)");
		MELang.add(LANG_AVAILABLE_EU, "Host buffer %s (units may draw from it)",
				"主机缓存 %s(单元可以从这里取电)");
	}

	/**
	 * 默认主机搜索半径(读 {@code MEConfig} 的 getter；配置未加载时它自己会回落到默认值).
	 */
	protected static int hostRange() {
		return MEConfig.getDefaultHostRange();
	}
}