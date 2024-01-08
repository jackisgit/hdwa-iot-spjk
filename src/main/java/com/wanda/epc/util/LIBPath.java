package com.wanda.epc.util;

import com.wanda.epc.config.Config;
import org.springframework.context.ApplicationContext;

/**
 * @Title LIBPath.java
 * @description 获取sdk库路径
 * @time 2023年11月28日 上午10:42:30
 * @author LianYanFei
 **/
public class LIBPath {

	// 配置类
	public static Config config;

	// 通过applicationContext上下文获取Config类
	public static void setApplicationContext(ApplicationContext applicationContext) {
		config = applicationContext.getBean(Config.class);
	}

}
