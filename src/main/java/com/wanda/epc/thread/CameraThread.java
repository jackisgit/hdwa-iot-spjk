package com.wanda.epc.thread;

import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.controller.CameraController;
import com.wanda.epc.play.RealPlay;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.DHLoginSDK;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Title CameraThread.java
 * @description 线程池类
 * @time 2023年11月28日 上午11:42:50
 * @author LianYanFei
 **/
public class CameraThread {

	public static class MyRunnable implements Runnable {

		// 创建线程池
		public static ExecutorService es = Executors.newCachedThreadPool();

		private CameraPojo cameraPojo;
		private DHLoginSDK login;

		public MyRunnable(CameraPojo cameraPojo, DHLoginSDK login) {
			this.cameraPojo = cameraPojo;
			this.login = login;
		}

		// 中断线程
		public void setInterrupted(String key) {
			// 结束推流线程
			CacheUtil.PUSHRTMPMAP.get(key).setExitcode(1);
			if (CacheUtil.PUSHHLSMAP.containsKey(key)) {
				CacheUtil.PUSHHLSMAP.get(key).setExitsign(true);
			}
		}

		@Override
		public void run() {
			// 获取当前线程
			CacheUtil.STREATMAP.put(cameraPojo.getToken(), cameraPojo);

			RealPlay play = new RealPlay(cameraPojo, login);
			play.play();

			// 判断是否因rtmp拖动导致的结束
			if (CacheUtil.STREATMAP.containsKey(cameraPojo.getToken())
					&& CacheUtil.STREATMAP.get(cameraPojo.getToken()).getReHistory()) {
				// 执行新任务
				MyRunnable job = new MyRunnable(cameraPojo, login);
				MyRunnable.es.execute(job);
				CameraController.JOBMAP.put(cameraPojo.getToken(), job);
			} else {
				// 清除缓存
				if (null == cameraPojo.getM3u8path() || "".equals(cameraPojo.getM3u8path())) {
					// hls回放不清除此缓存
					CacheUtil.STREATMAP.remove(cameraPojo.getToken());
				}
				CacheUtil.PUSHRTMPMAP.remove(cameraPojo.getToken());
				CacheUtil.HISTORYCALLBACK.remove(cameraPojo.getToken());
				CacheUtil.LIVECALLBACK.remove(cameraPojo.getToken());
				CameraController.JOBMAP.remove(cameraPojo.getToken());
				// 判断当前设备使用人数,如果人数>1,则-1;否则注销当前设备
				if (CacheUtil.LOGINSDK.get(cameraPojo.getIp()).getCount() > 1) {
					CacheUtil.LOGINSDK.get(cameraPojo.getIp())
							.setCount(CacheUtil.LOGINSDK.get(cameraPojo.getIp()).getCount() - 1);
				} else {
					CacheUtil.LOGINSDK.get(cameraPojo.getIp()).logout();
					CacheUtil.LOGINSDK.remove(cameraPojo.getIp());
				}
			}
		}
	}
}
