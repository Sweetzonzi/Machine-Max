package io.github.sweetzonzi.machine_max.util.environment;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLLoader;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 运行环境包装器，自动判断当前运行环境是开发环境（IDE）还是生产环境（JAR 包）。
 * <p>
 * 在开发环境中，允许启用调试功能、详细日志、性能分析等；
 * 在生产环境中，自动禁用所有调试与开发辅助功能。
 * <p>
 * 通过 {@link FMLLoader#isProduction()} 判断运行环境。
 */
public class EnvironmentWrapper {

    /**
     * 运行环境缓存，单例模式 — 类加载时由 {@link FMLLoader#isProduction()} 初始化，避免运行时重复调用。
     */
    private static final boolean IS_PRODUCTION = FMLLoader.isProduction();

    /**
     * 运行环境枚举，用于指定代码应在哪个环境下执行。
     */
    public enum Env {
        /** 开发环境（IDE） */
        DEVELOPMENT,
        /** 生产环境（JAR 包） */
        PRODUCTION,
        /** 所有环境 */
        ALL,
        /** 禁用 */
        CLOSED
        ;


        /**
         * 判断此枚举值是否匹配当前运行环境。
         *
         * @return 当前环境匹配则返回 true
         */
        private boolean matchesCurrent() {
            return switch (this) {
                case ALL -> true;
                case CLOSED -> false;
                case DEVELOPMENT -> !IS_PRODUCTION;
                case PRODUCTION -> IS_PRODUCTION;
            };
        }
    }

    /**
     * 判断当前是否处于开发环境（IDE 中运行，class 文件加载）
     *
     * @return 当前为开发环境则返回 true
     */
    public static boolean isDevelopment() {
        return !IS_PRODUCTION;
    }

    /**
     * 判断当前是否处于生产环境（JAR 包运行）
     *
     * @return 当前为生产环境则返回 true
     */
    public static boolean isProduction() {
        return IS_PRODUCTION;
    }


    /**
     * 判断当前环境是否匹配指定枚举值。
     *
     * @param env 要匹配的环境
     * @return 匹配则返回 true
     */
    public static boolean matches(Env env) {
        return env.matchesCurrent();
    }


    /**
     * 判断当前环境是否匹配指定枚举值。
     *
     * @param env 要匹配的环境枚举值列表
     * @return 匹配则返回 true
     */
    public static boolean matches(Env... env) {
        return Arrays.stream(env).anyMatch(Env::matchesCurrent);
    }


    /**
     * 在指定环境中执行代码块。
     *
     * @param env    指定执行的目标环境
     * @param action 要执行的逻辑
     */
    public static void run(Env env, Runnable action) {
        if (env.matchesCurrent()) {
            action.run();
        }
    }


    /**
     * 在指定环境中执行代码块。
     *
     * @param condition 指定执行的目标环境获取器
     * @param action 要执行的逻辑
     */
    public static void run(Supplier<Env> condition, Runnable action) {
        if (condition.get().matchesCurrent()) {
            action.run();
        }
    }

    /**
     * 根据环境选择执行对应的代码块。
     *
     * @param devAction  开发环境要执行的逻辑
     * @param prodAction 生产环境要执行的逻辑
     */
    public static void run(Runnable devAction, Runnable prodAction) {
        if (isDevelopment()) {
            devAction.run();
        } else {
            prodAction.run();
        }
    }

    /**
     * 根据环境返回不同值。
     *
     * @param devSupplier   开发环境提供的值
     * @param prodSupplier  生产环境提供的值
     * @param <T>           返回值类型
     * @return 当前环境对应的值
     */
    public static <T> T get(Supplier<T> devSupplier, Supplier<T> prodSupplier) {
        if (isDevelopment()) {
            return devSupplier.get();
        }
        return prodSupplier.get();
    }

    /**
     * 仅当匹配指定环境时返回值，否则返回 null。
     *
     * @param env      指定环境
     * @param supplier 匹配时提供的值
     * @param <T>      返回值类型
     * @return 匹配则返回提供的值，否则返回 null
     */
    public static <T> T get(Env env, Supplier<T> supplier) {
        if (env.matchesCurrent()) {
            return supplier.get();
        }
        return null;
    }

    /**
     * 根据环境返回不同的常数值。
     *
     * @param devValue  开发环境使用的值
     * @param prodValue 生产环境使用的值
     * @param <T>       返回值类型
     * @return 当前环境对应的值
     */
    public static <T> T getConstant(T devValue, T prodValue) {
        return isDevelopment() ? devValue : prodValue;
    }


    public static void safeRun(Consumer<Minecraft> action) {
        if (FMLLoader.getDist().isClient()) {
            action.accept(Minecraft.getInstance());
        } else {
            MachineMax.LOGGER.warn("尝试在非客户端环境中执行客户端代码，成功拦截", new Throwable("调用栈"));
        }
    }
}
