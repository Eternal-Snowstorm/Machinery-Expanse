package dev.celestiacraft.me.integration.jei;

import dev.celestiacraft.me.MachineryExpanse;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 本库的 JEI 插件入口(需求 6：多方块预览界面).
 *
 * <p>
 * <b>类装载隔离：</b>本类(以及它引用的 {@link MultiblockInfoCategory} / {@link StructurePreviewDrawable})
 * 是<b>唯一</b>会碰到 {@code mezz.jei.*} 的地方，而且它<b>没有</b>被 {@code dev.celestiacraft.me.MachineryExpanse}
 * 或任何其它非 JEI 代码 import / 引用 —— JEI 自己会用注解扫描发现 {@link JeiPlugin} 标注的类.
 * 于是：
 * <ul>
 * <li>没装 JEI 时，没有任何代码路径会去装载本类，也就不会触发 {@code NoClassDefFoundError}；</li>
 * <li>JEI 是 {@code compileOnly} + {@code runtimeOnly} 依赖，正式包里玩家不需要装它
 * ({@code mods.toml} 里应把 JEI 标成 optional，而不是 required). </li>
 * </ul>
 *
 * <p>
 * <b>为什么不实现 {@code IModPlugin} 的全部钩子：</b>只做两件事 —— 注册分类、注册配方.
 * 物品子类型、配方转移、GUI 处理器这些本库都用不上(本库不含任何配方与容器).
 */
@JeiPlugin
public class MEJeiPlugin implements IModPlugin {
	@Override
	public @NotNull ResourceLocation getPluginUid() {
		return MachineryExpanse.loadResource("jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new MultiblockInfoCategory(registration.getJeiHelpers()));
	}

	@Override
	public void registerRecipes(@NotNull IRecipeRegistration registration) {
		MultiblockInfoCategory.registerRecipes(registration);
	}
}