package dev.celestiacraft.me.api.addon;

import dev.celestiacraft.me.MachineryExpanse;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * {@code @MEAddon} 扫描器. 
 *
 * <p>
 * 与 GTM 的 {@code AddonFinder} 同思路：不靠 ServiceLoader、也不靠 forge 的 {@code mods.toml}
 * 声明，而是直接读 Forge 已经解析好的注解扫描数据({@link ModFileScanData})，
 * 找出所有标了 {@link MEAddon} 的类并实例化. 
 *
 * <p>
 * 好处是 addon 侧零配置：只要类上有注解、有无参构造，就会被发现. 
 */
public class AddonFinder {
	private static @Nullable List<IMEAddon> cache = null;

	/**
	 * 全部 addon(按 {@link IMEAddon#priority()} 升序，同优先级按类名排序保证确定性). 
	 */
	public static List<IMEAddon> getAddons() {
		if (cache == null) {
			List<IMEAddon> addons = new ArrayList<>(getInstances(MEAddon.class, IMEAddon.class));
			addons.sort(Comparator.comparingInt(IMEAddon::priority)
					.thenComparing((addon) -> {
						return addon.getClass().getName();
					}));
			// 同名 modId 冲突时保留先出现的(优先级高的那个)
			Map<String, IMEAddon> byModId = new LinkedHashMap<>();
			for (IMEAddon addon : addons) {
				IMEAddon previous = byModId.putIfAbsent(addon.addonModId(), addon);
				if (previous != null) {
					MachineryExpanse.LOGGER.error(
							"Duplicate ME addon for mod id '{}': {} is ignored, keeping {}",
							addon.addonModId(),
							addon.getClass().getName(),
							previous.getClass().getName()
					);
				}
			}
			cache = List.copyOf(byModId.values());
		}
		return cache;
	}

	/**
	 * 按 mod id 取 addon. 
	 */
	public static IMEAddon getAddon(String modId) {
		for (IMEAddon addon : getAddons()) {
			if (addon.addonModId().equals(modId)) return addon;
		}
		return null;
	}

	/**
	 * 清空缓存(仅调试/重载用). 
	 */
	public static void invalidate() {
		cache = null;
	}

	private static <T> List<T> getInstances(Class<?> annotationClass, Class<T> instanceClass) {
		Type annotationType = Type.getType(annotationClass);
		Set<String> classNames = new LinkedHashSet<>();

		for (ModFileScanData scanData : ModList.get().getAllScanData()) {
			for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
				if (Objects.equals(data.annotationType(), annotationType)) {
					classNames.add(data.memberName());
				}
			}
		}

		List<T> instances = new ArrayList<>();
		for (String className : classNames) {
			try {
				Class<?> asmClass = Class.forName(className);
				Class<? extends T> impl = asmClass.asSubclass(instanceClass);
				Constructor<? extends T> ctor = impl.getDeclaredConstructor();
				ctor.setAccessible(true);
				instances.add(ctor.newInstance());
			} catch (ReflectiveOperationException | LinkageError e) {
				MachineryExpanse.LOGGER.error("Failed to load ME addon: {}", className, e);
			}
		}
		return instances;
	}
}