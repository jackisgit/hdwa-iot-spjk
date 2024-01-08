package com.wanda.epc;

import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.controller.CameraController;
import com.wanda.epc.play.HlsPush;
import com.wanda.epc.play.RealPlay;
import com.wanda.epc.sdk.HCInitSDK;
import com.wanda.epc.sdk.HCNetSDK;
import com.wanda.epc.thread.CameraThread;
import com.wanda.epc.timer.CameraTimer;
import com.wanda.epc.util.LIBPath;
import com.wanda.epc.util.Utils;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.Set;

@SpringBootApplication
public class HcRtmp {

	private final static Logger logger = LoggerFactory.getLogger(HcRtmp.class);

	/*
	 * 初始化sdk
	 */
	@Autowired
	public HCInitSDK init;

	public static void main(String[] args) {
		// 服务启动执行FFmpegFrameGrabber和FFmpegFrameRecorder的tryLoad()，以免导致第一次推流时耗时。
		try {
			FFmpegFrameGrabber.tryLoad();
			FFmpegFrameRecorder.tryLoad();
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 将服务启动时间存入缓存
		CacheUtil.STARTTIME = new Date().getTime();
		final ApplicationContext applicationContext = SpringApplication.run(HcRtmp.class, args);
		LIBPath.setApplicationContext(applicationContext);
		RealPlay.setApplicationContext(applicationContext);
		Utils.setApplicationContext(applicationContext);
		HlsPush.setApplicationContext(applicationContext);
		// 加载动态库，初始化sdk
//		HCNetSDK.tryLoad();
		HCNetSDK.INSTANCE.NET_DVR_Init();
	}

	@PreDestroy
	public void destory() {
		logger.info("服务关闭,开始释放存储空间");
		// 结束正在进行的任务
		Set<String> keys = CameraController.JOBMAP.keySet();
		for (String key : keys) {
			CameraController.JOBMAP.get(key).setInterrupted(key);
		}
		while (true) {
			if (CameraController.JOBMAP.isEmpty()) {
				init.cleanup();
				break;
			}
		}
		// 关闭线程池
		CameraThread.MyRunnable.es.shutdown();
		// 销毁定时器
		CameraTimer.timer.cancel();
	}

}