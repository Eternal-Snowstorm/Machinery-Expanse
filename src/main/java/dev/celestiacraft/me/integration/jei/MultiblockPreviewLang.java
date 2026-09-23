package dev.celestiacraft.me.integration.jei;

import dev.celestiacraft.me.api.lang.MELang;

/**
 * JEI 多方块预览用到的语言键 —— <b>故意不 import 任何 {@code mezz.jei.*}</b>.
 *
 * <p>
 * 为什么要单独放一个类：{@link MultiblockInfoCategory} 引用了 JEI 的类型，只有装了 JEI 才会被装载；
 * 而 datagen 环境里**没有 JEI**(它是 {@code compileOnly})，于是那些语言键永远进不了
 * 生成的 lang JSON，装了 JEI 的玩家就会看到裸键.
 *
 * <p>
 * 把键与文案挪到这个「不碰 JEI」的持有类里之后，{@code MachineryExpanse} 在数据生成之前就能安全地
 * 调用 {@link #initLang()}，键也就正常进 lang 文件了.
 */
public final class MultiblockPreviewLang {

    private MultiblockPreviewLang() {}

    public static final String LANG_TITLE = "me.jei.multiblock.title";
    public static final String LANG_NO_SHAPE = "me.jei.multiblock.no_shape";
    public static final String LANG_SIZE = "me.jei.multiblock.size";
    public static final String LANG_PAGE = "me.jei.multiblock.page";
    public static final String LANG_LAYER_ALL = "me.jei.multiblock.layer.all";
    public static final String LANG_LAYER_FMT = "me.jei.multiblock.layer";
    public static final String LANG_HINT_ROTATE = "me.jei.multiblock.hint.rotate";
    public static final String LANG_HINT_LAYER = "me.jei.multiblock.hint.layer";
    public static final String LANG_HINT_PREV = "me.jei.multiblock.hint.prev";
    public static final String LANG_HINT_NEXT = "me.jei.multiblock.hint.next";

    /** 登记双语文案(幂等，供 {@code MachineryExpanse} 在数据生成之前调用).  */
    public static void initLang() {
        MELang.add(LANG_TITLE, "Multiblock Structure", "多方块结构");
        MELang.add(LANG_NO_SHAPE, "No preview shape is registered for this structure",
                "该结构没有登记预览图案");
        MELang.add(LANG_SIZE, "Size: %sx%sx%s", "尺寸：%s×%s×%s");
        MELang.add(LANG_PAGE, "Shape %s/%s", "图案 %s/%s");
        MELang.add(LANG_LAYER_ALL, "Layer: all", "层：全部");
        MELang.add(LANG_LAYER_FMT, "Layer: %s/%s", "层：%s/%s");
        MELang.add(LANG_HINT_ROTATE, "Scroll or drag to rotate", "滚轮或拖拽旋转");
        MELang.add(LANG_HINT_LAYER, "Click to show layer by layer", "点击逐层查看");
        MELang.add(LANG_HINT_PREV, "Previous shape", "上一份图案");
        MELang.add(LANG_HINT_NEXT, "Next shape", "下一份图案");
    }
}
