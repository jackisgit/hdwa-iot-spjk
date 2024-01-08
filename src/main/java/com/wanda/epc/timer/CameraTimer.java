package com.wanda.epc.timer;

import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.config.Config;
import com.wanda.epc.controller.CameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @Title CameraTimer.java
 * @description 定时任务
 * @time 2023年11月28日 下午3:11:36
 * @author LianYanFei
 **/
@Component
public class CameraTimer implements CommandLineRunner {

	private final static Logger logger = LoggerFactory.getLogger(CameraTimer.class);

	@Autowired
	private Config config;// 配置文件bean

	public static Timer timer;

	@Override
	public void run(String... args) throws Exception {
		timer = new Timer("timeTimer");
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				logger.info("定时任务  当前有" + CameraController.JOBMAP.size() + "个推流任务正在进行推流");
				// 管理缓存
				if (null != CacheUtil.STREATMAP && 0 != CacheUtil.STREATMAP.size()) {
					Set<String> keys = CacheUtil.STREATMAP.keySet();
					for (String key : keys) {
						try {
							// 最后打开时间
							long opentime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
									.parse(CacheUtil.STREATMAP.get(key).getOpentime()).getTime();

							// 当前系统时间
							long nowtime = new Date().getTime();
							// 如果通道使用人数为0.则关闭推流
							if (CacheUtil.STREATMAP.get(key).getCount() == 0) {
								// 结束线程
								CameraController.JOBMAP.get(key).setInterrupted(key);
								logger.info("定时任务 当前设备使用人数为0结束推流 设备信息：[ip：" + CacheUtil.STREATMAP.get(key).getIp()
										+ " port:" + CacheUtil.STREATMAP.get(key).getPort() + " channel:"
										+ CacheUtil.STREATMAP.get(key).getChannel() + " stream:"
										+ CacheUtil.STREATMAP.get(key).getStream() + " starttime:"
										+ CacheUtil.STREATMAP.get(key).getStarttime() + " endtime:"
										+ CacheUtil.STREATMAP.get(key).getEndtime() + " url:"
										+ CacheUtil.STREATMAP.get(key).getUrl() + "]");
							} else if (null == CacheUtil.STREATMAP.get(key).getStarttime()
									&& (nowtime - opentime) / 1000 / 60 >= config.getKeepalive()) {
								// 结束线程
								CameraController.JOBMAP.get(key).setInterrupted(key);
								logger.info("定时任务 当前设备使用时间超时结束推流 设备信息：[ip:" + CacheUtil.STREATMAP.get(key).getIp()
										+ " port:" + CacheUtil.STREATMAP.get(key).getPort() + " channel:"
										+ CacheUtil.STREATMAP.get(key).getChannel() + " stream:"
										+ CacheUtil.STREATMAP.get(key).getStream() + " starttime:"
										+ CacheUtil.STREATMAP.get(key).getStarttime() + " endtime:"
										+ CacheUtil.STREATMAP.get(key).getEndtime() + " url:"
										+ CacheUtil.STREATMAP.get(key).getUrl() + "]");
							} else if (null != CacheUtil.STREATMAP.get(key).getM3u8path()
									&& !"".equals(CacheUtil.STREATMAP.get(key).getM3u8path())
									&& (nowtime - opentime) / 1000 / 60 >= config.getKeepalive()) {
								// hls切片超时删除
//								deleteDir(CacheUtil.STREATMAP.get(key).getM3u8path());
//								logger.info("定时任务 当前设备使用时间超时清除播放文件 设备信息：[ip:" + CacheUtil.STREATMAP.get(key).getIp()
//										+ " port:" + CacheUtil.STREATMAP.get(key).getPort() + " channel:"
//										+ CacheUtil.STREATMAP.get(key).getChannel() + " stream:"
//										+ CacheUtil.STREATMAP.get(key).getStream() + " starttime:"
//										+ CacheUtil.STREATMAP.get(key).getStarttime() + " endtime:"
//										+ CacheUtil.STREATMAP.get(key).getEndtime() + " url:"
//										+ CacheUtil.STREATMAP.get(key).getUrl() + "]");
//								CacheUtil.STREATMAP.remove(key);
							}
						} catch (ParseException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}, 1, 1000 * 60);
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
		file.delete();
		return true;
	}
}
