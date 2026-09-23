package dev.celestiacraft.me.api.gui.editor;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.editor.data.UIProject;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.ui.MainPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.StringTabContainer;
import com.lowdragmc.lowdraglib.gui.editor.ui.tool.WidgetToolBox;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib.gui.widget.TabButton;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import dev.celestiacraft.me.api.gui.GuiTextures;
import dev.celestiacraft.me.api.gui.RecipeTypeUIWidgets;
import dev.celestiacraft.me.api.recipe.MERecipeType;
import dev.celestiacraft.me.api.recipe.ui.RecipeTypeUI;
import dev.celestiacraft.me.api.registry.MERegistries;
import dev.celestiacraft.me.utils.ResourceLocations;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ME 新写 —— 让 LDLib 的 UI 编辑器能编辑本库的 <b>rtui</b> 工程({@code .rtui} 文件).
 *
 * <p>
 * 逐行对照 GTM 的 {@code com.gregtechceu.gtceu.api.gui.editor.RecipeTypeUIProject} 移植，差异：
 * <ul>
 * <li>{@code GTRecipeType} → {@link MERecipeType}，
 * {@code GTRegistries.RECIPE_TYPES} → {@link MERegistries#RECIPE_TYPES}；</li>
 * <li>默认模板不再调 {@code recipeType.getRecipeUI().createEditableUITemplate(...)}(本库没有这套)，
 * 改成调 {@link RecipeTypeUIWidgets#createDefaultWidget(MERecipeType)}；</li>
 * <li>保存路径仍是 {@code assets/<ns>/ui/recipe_type/<path>.rtui}，保存后调
 * {@link RecipeTypeUI#reloadCustomUI()} 让运行时看到新布局；</li>
 * <li>GTM 的 {@code UIMainPanel} 是 {@code com.gregtechceu.*} 类型(不能 import)，
 * 这里用本文件里的私有静态内部类 {@link MEMainPanel} 代替 —— 行为一致：
 * 在编辑器主面板背景上画一段说明文字 + 一块居中的面板底板. </li>
 * </ul>
 *
 * <p>注册名固定为 {@code rtui}，与 {@code .rtui} 后缀一致({@code IProject#getSuffix()}
 * 会取 {@code getRegisterUI().name()}). 编辑器里通过 {@code template_tab} 菜单
 * 从 {@link MERegistries#RECIPE_TYPES} 里挑一个类型来开模板.
 *
 * <p><b>注意</b>：这个类只在客户端(UI 编辑器)用得到，但类本身不碰客户端类，
 * 所以挂 {@link LDLRegister} 在服务端也不会炸 —— 真正会加载 {@code Minecraft} 的只有
 * {@link MEMainPanel} 里那段绘制代码.
 */
@LDLRegister(name = "rtui", group = "editor.me")
public class MERecipeTypeUIProject extends UIProject {
	/**
	 * 当前工程绑定的配方类型；null = 还没选(此时不给保存菜单项).
	 */
	@Getter
	@Setter
	@Nullable
	protected MERecipeType recipeType;

	/**
	 * LDLib 反射实例化用的无参构造.
	 */
	private MERecipeTypeUIProject() {
		this(null, null);
	}

	public MERecipeTypeUIProject(Resources resources, WidgetGroup root) {
		super(resources, root);
	}

	public MERecipeTypeUIProject(CompoundTag tag) {
		super(tag);
	}

	// ═══════════════════════ 工程生命周期 ═══════════════════════

	@Override
	public UIProject newEmptyProject() {
		return new MERecipeTypeUIProject(Resources.defaultResource(), new WidgetGroup(30, 30, 200, 200));
	}

	@Override
	public UIProject loadProject(File file) {
		try {
			CompoundTag tag = NbtIo.read(file);
			if (tag != null) {
				return new MERecipeTypeUIProject(tag);
			}
		} catch (IOException ignored) {
			// 读不出来就当作「不是本工程的工程」，交回给编辑器处理
		}
		return null;
	}

	@Override
	public CompoundTag serializeNBT() {
		CompoundTag tag = super.serializeNBT();
		if (recipeType != null) {
			tag.putString("recipe_type", recipeType.getRegistryName().toString());
		}
		return tag;
	}

	@Override
	public void deserializeNBT(CompoundTag tag) {
		super.deserializeNBT(tag);
		if (tag.contains("recipe_type")) {
			// 工程文件是手改得动的文本：解析失败就当作「没绑定配方类型」，不要让编辑器崩
			recipeType = MERegistries.RECIPE_TYPES.get(
					ResourceLocations.tryParse(tag.getString("recipe_type"))
			);
		}
	}

	@Override
	public void onLoad(Editor editor) {
		StringTabContainer pages = editor.getTabPages();
		ResourcePanel panel = editor.getResourcePanel();

		panel.loadResource(getResources(), false);
		pages.addTab(new TabButton(50, 16, 60, 14)
				.setTexture(new GuiTextureGroup(
								ColorPattern.T_GREEN.rectTexture().setBottomRadius(10).transform(0, 0.4f),
								new TextTexture("Main")
						),
						new GuiTextureGroup(
								ColorPattern.T_RED.rectTexture().setBottomRadius(10).transform(0, 0.4f),
								new TextTexture("Main")
						)
				), new MEMainPanel(editor, root, recipeType == null ? null : recipeType.getRegistryName().toLanguageKey()));

		for (WidgetToolBox.Default tab : WidgetToolBox.Default.TABS) {
			// 容器类工具箱在 rtui 里没意义(rtui 是平铺布局)，跳过，与 GTM 一致
			if (tab == WidgetToolBox.Default.CONTAINER) {
				continue;
			}
			editor.getToolPanel().addNewToolBox(
					"ldlib.gui.editor.group." + tab.groupName,
					tab.icon,
					tab.createToolBox()
			);
		}
	}

	@Override
	public void attachMenu(Editor editor, String name, TreeBuilder.Menu menu) {
		if (name.equals("file")) {
			if (recipeType == null) {
				menu.remove("ldlib.gui.editor.menu.save");
			} else {
				menu.remove("ldlib.gui.editor.menu.save");
				menu.leaf(Icons.SAVE, "ldlib.gui.editor.menu.save", () -> {
					File path = new File(
							LDLib.getLDLibDir(),
							"assets/%s/ui/recipe_type".formatted(recipeType.getRegistryName().getNamespace()));
					path.mkdirs();
					saveProject(new File(path, recipeType.getRegistryName().getPath() + "." + getRegisterUI().name()));
					// 存完立刻失效缓存，运行时下次打开界面就能看到新布局
					recipeType.getRecipeUI().reloadCustomUI();
				});
			}
		} else if (name.equals("template_tab")) {
			Map<String, List<MERecipeType>> categories = new LinkedHashMap<>();
			for (MERecipeType type : MERegistries.RECIPE_TYPES) {
				String group = type.getGroup();
				if (group == null || group.isEmpty()) {
					group = "me";
				}
				categories.computeIfAbsent(group, g -> new ArrayList<>()).add(type);
			}
			categories.forEach((groupName, types) -> {
				menu.branch(groupName, (builder) -> {
					for (MERecipeType type : types) {
						ItemStack icon = type.getIcon();
						builder.leaf(new ItemStackTexture(icon.isEmpty() ? new ItemStack(Items.BARRIER) : icon),
								type.getRegistryName().toLanguageKey(), () -> {
									root.clearAllWidgets();
									if (type.getRecipeUI().hasCustomUI()) {
										// 有 .rtui 就直接反序列化到工程根上继续编辑
										CompoundTag nbt = type.getRecipeUI().getCustomUI();
										IConfigurableWidget.deserializeNBT(
												root, nbt.getCompound("root"),
												Resources.fromNBT(nbt.getCompound("resources")),
												false
										);
									} else {
										// 没有就用默认布局当起始模板(把子 widget 搬进工程根)
										WidgetGroup widget = RecipeTypeUIWidgets.createDefaultWidget(type);
										root.setSize(widget.getSize());
										for (Widget child : new ArrayList<>(widget.widgets)) {
											root.addWidget(child);
										}
									}
									recipeType = type;
								});
					}
				});
			});
		}
	}

	/**
	 * GTM {@code UIMainPanel} 的 ME 版 —— 只是给编辑器主面板换一张背景：
	 * 左上角用 2 倍字号画配方类型的语言键，中间垫一块面板底板(大小随工程根尺寸走).
	 */
	private static class MEMainPanel extends MainPanel {
		@Nullable
		private final String description;

		public MEMainPanel(Editor editor, WidgetGroup root, @Nullable String description) {
			super(editor, root);
			this.description = description;
			setBackground((graphics, mouseX, mouseY, x, y, width, height) -> {
				if (this.description != null) {
					new TextTexture(this.description)
							.scale(2.0f)
							.draw(
									graphics,
									mouseX,
									mouseY,
									x,
									y,
									width - editor.getConfigPanel().getSize().getWidth(),
									height
							);
				}
				int border = 4;
				IGuiTexture background = GuiTextures.machineBackground();
				Position position = root.getPosition();
				Size size = root.getSize();
				int w = Math.max(size.width + border * 2, 172);
				int h = Math.max(size.height + border * 2, 86);
				background.draw(
						graphics,
						mouseX,
						mouseY,
						position.x - (w - size.width) / 2.0f,
						position.y - (h - size.height) / 2.0f,
						w,
						h
				);
			});
		}
	}
}