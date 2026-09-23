package dev.celestiacraft.me.modular;

import dev.celestiacraft.me.api.machine.trait.RecipeLogic;
import dev.celestiacraft.me.api.MEValues;
import dev.celestiacraft.me.api.recipe.MERecipe;
import dev.celestiacraft.me.api.recipe.RecipeHelper;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 模块化单元的配方逻辑：把「一句话机器逻辑」({@link ModuleMachine#recipeRequirement()})
 * 与主机等级当作<b>内容匹配</b>的一部分.
 *
 * <p>
 * Java 重写自 GTEternalTime 的 {@code ETModuleRecipeLogic}.
 *
 * <p>
 * 同样拦在 {@code matchRecipe} 这一层：拒绝时带上原因文本，机器面板与 Jade 都能看到
 * 「为什么这条配方不跑」(主机没接上 / 等级不够 / 电不够).
 */
public class ModuleRecipeLogic extends RecipeLogic {
	private final ModuleMachine module;

	public ModuleRecipeLogic(ModuleMachine module) {
		super(module);
		this.module = module;
	}

	@Override
	protected boolean matchRecipe(MERecipe recipe) {
		Component reason = module.recipeRequirement();
		if (reason != null) {
			setFailureReason(reason);
			return false;
		}

		int cap = module.recipeTier();
		int recipeTier = RecipeHelper.getRecipeEUtTier(recipe);
		if (recipeTier > cap) {
			setFailureReason(Component.translatable(ModuleMachine.LANG_TIER_TOO_LOW, MEValues.tierNameRaw(cap)));
			return false;
		}

		return super.matchRecipe(recipe);
	}

	@Override
	public void addDisplayText(List<Component> textList) {
		super.addDisplayText(textList);

		ModuleHostMachine host = module.getHost();
		if (host == null) {
			textList.add(Component.translatable(ModuleMachine.LANG_NO_HOST, ModuleMachine.hostRange()));
		} else {
			int distance = (int) Math.sqrt(host.getPos().distSqr(module.getPos()));
			textList.add(Component.translatable(ModuleMachine.LANG_HOST_LINKED, distance));
			textList.add(Component.translatable(ModuleMachine.LANG_TIER_SPLIT,
					MEValues.tierNameRaw(module.processingTier()),
					MEValues.tierNameRaw(module.recipeTier())));
		}
	}
}
