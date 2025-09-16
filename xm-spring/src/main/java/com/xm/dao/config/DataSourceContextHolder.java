package com.xm.dao.config;

/**
 * 动态数据源上下文
 *
 * @author XM
 * @date 2025/9/11
 */
public class DataSourceContextHolder {

    /**
     * 用 ThreadLocal 保存当前线程的数据源 key
     */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置数据源
     *
     * @param dataSourceType
     */
    public static void setDataSourceType(String dataSourceType) {
        CONTEXT_HOLDER.set(dataSourceType);
    }

    /**
     * 获取数据源
     *
     * @return
     */
    public static String getDataSourceType() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清空数据源
     */
    public static void clearDataSourceType() {
        CONTEXT_HOLDER.remove();
    }
}
