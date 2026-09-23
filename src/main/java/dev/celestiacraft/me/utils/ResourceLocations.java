package dev.celestiacraft.me.utils;

import dev.celestiacraft.me.MachineryExpanse;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@UtilityClass
public class ResourceLocations {
	/**
	 * 按「命名空间 + 路径」构造，等价于 {@code new ResourceLocation(namespace, path)}.
	 *
	 * <p>
	 * 命名空间与路径本身非法时仍然抛 {@link net.minecraft.ResourceLocationException} ——
	 * 这两个参数是代码里写死的常量，写错了属于程序 bug，应该立刻炸而不是悄悄变成别的 id.
	 */
	public ResourceLocation of(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	/**
	 * 解析 {@code "namespace:path"}，没有冒号时按 {@code minecraft:path} 处理.
	 *
	 * <p>
	 * ⚠️ 这个是「宽松解析」：{@code "minecraft:stone"} 与 {@code "stone"} 等价.
	 * 用于内部常量拼接；解析外部数据请用 {@link #tryParse(String)}.
	 */
	public ResourceLocation of(String namespaceOrColonPath) {
		return ResourceLocation.parse(namespaceOrColonPath);
	}

	/**
	 * 解析 {@code "namespace:path"}，<b>非法时返回 {@code null} 而不是抛异常</b>.
	 *
	 * <p>
	 * NBT、网络包、配置文件里读出来的字符串都用它. 调用方本来就该处理「查不到」的情况
	 * (注册表里查不到也是 null)，所以多一个 null 不会增加负担；
	 * 反过来，让一个脏字符串把机器/存档加载炸掉则是实打实的线上事故.
	 */
	@Nullable
	public ResourceLocation tryParse(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return ResourceLocation.tryParse(raw);
		} catch (RuntimeException invalid) {
			// ResourceLocationException 是 RuntimeException 的子类；这里连它的兄弟异常一起兜住，
			// 因为解析失败的原因可能是路径含非法字符、命名空间为空、长度超限等等
			MachineryExpanse.LOGGER.debug("[ME] 忽略非法的 ResourceLocation 字符串 [{}]", raw);
			return null;
		}
	}

	/**
	 * {@link #tryParse(String)} 的 Optional 版本(想写流式代码时用).
	 */
	public Optional<ResourceLocation> parse(@Nullable String raw) {
		return Optional.ofNullable(tryParse(raw));
	}

	/**
	 * 这个字符串是不是合法的 ResourceLocation.
	 */
	public boolean isValid(@Nullable String raw) {
		return tryParse(raw) != null;
	}

	// ═══════════════ 派生 ═══════════════

	/**
	 * 保留命名空间、只换路径.
	 *
	 * <p>
	 * 典型用法：{@code withPath(id, "part/" + id.getPath())}、{@code withPath(uiPath, "ui/machine/x.mui")}.
	 */
	public ResourceLocation withPath(ResourceLocation base, String newPath) {
		return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), newPath);
	}

	/**
	 * 保留路径、只换命名空间(跨命名空间复用同一套贴图/目录时用).
	 */
	public ResourceLocation withNamespace(ResourceLocation base, String newNamespace) {
		return ResourceLocation.fromNamespaceAndPath(newNamespace, base.getPath());
	}

	/**
	 * 在路径前面加一段前缀：{@code prefix(base, "part/")}.
	 */
	public ResourceLocation withPathPrefix(ResourceLocation base, String prefix) {
		return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), prefix + base.getPath());
	}

	/**
	 * 在路径后面加一段后缀：{@code suffix(base, "_ui")}.
	 */
	public ResourceLocation withPathSuffix(ResourceLocation base, String suffix) {
		return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + suffix);
	}

	/**
	 * 取 {@code "namespace:path"} 里的路径部分(语义化包装，省得到处 getPath()).
	 */
	public String pathOf(ResourceLocation id) {
		return id.getPath();
	}

	/**
	 * 取 {@code "namespace:path"} 里的命名空间部分.
	 */
	public String namespaceOf(ResourceLocation id) {
		return id.getNamespace();
	}
}