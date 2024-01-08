package com.wanda.epc.timer;

import com.wanda.epc.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;

/**
 * @ClassName: DownloadTimer
 * @Description: 定时任务 删除服务器录像下载文件与抓图文件
 * @author: LianYanFei
 * @date: 2023-11-28
 */
@Configuration
@EnableScheduling
public class DownloadTimer {

	private final static Logger logger = LoggerFactory.getLogger(DownloadTimer.class);

	@Autowired
	public Config config;

	/**
	 * @Title: configureTasks
	 * @Description: 定时删除录像文件
	 * @return: void
	 **/
	@Scheduled(cron = "${config.deletevideocron}")
	public void deleteVideoTasks() {
		logger.info("定时任务 开始清除 " + config.getVideopath() + " 路径下的录像文件");
		deleteDir(config.getVideopath());
		logger.info("定时任务 清除完成 " + config.getVideopath() + " 路径下的录像文件");
	}

	/**
	 * @Title: deletePictureTasks
	 * @Description: 定时删除抓图文件
	 * @return: void
	 **/
	@Scheduled(cron = "${config.deletepicturecron}")
	public void deletePictureTasks() {
		logger.info("定时任务 开始清除 " + config.getPicturepath() + " 路径下的抓图文件");
		deleteDir(config.getPicturepath());
		logger.info("定时任务 清除完成 " + config.getVideopath() + " 路径下的抓图文件");
	}

	/**
	 * @Title: deleteDir
	 * @Description: 删除文件夹下的所有内容
	 * @param path
	 * @return: boolean
	 **/
	private boolean deleteDir(String path) {
		File file = new File(path);
		if (!file.exists()) {// 判断待删除目录是否存在
			logger.error(path + " The dir are not exists!");
			return false;
		}
		String[] content = file.list();// 取得当前目录下所有文件和文件夹
		for (String name : content) {
			File temp = new File(path, name);
			if (temp.isDirectory()) {// 判断是否是目录
				deleteDir(temp.getAbsolutePath());// 递归调用，删除目录里的内容
				temp.delete();// 删除空目录
			} else {
				temp.delete();
			}
		}
		return true;
	}

}
