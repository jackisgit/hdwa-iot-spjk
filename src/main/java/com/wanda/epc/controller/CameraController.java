package com.wanda.epc.controller;

import com.alibaba.fastjson.JSONObject;


import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.config.Config;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.DHLoginSDK;
import com.wanda.epc.thread.CameraThread;
import com.wanda.epc.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Title CameraController.java
 * @description controller
 * @time 2020年3月16日 下午3:43:29
 * @author wuguodong
 **/
@RestController
@Validated
public class CameraController {

	private final static Logger logger = LoggerFactory.getLogger(CameraController.class);

	/*
	 * 配置文件bean
	 */
	@Autowired
	public Config config;

	/*
	 * 存放任务线程
	 */
	public static Map<String, CameraThread.MyRunnable> JOBMAP = new ConcurrentHashMap<>();

	/**
	 * @Title: openCamera
	 * @Description:开启视频流
	 * @return Map<String,String>
	 **/
	@PostMapping(value = "/cameras")
	public Map<String, Object> openCamera(@RequestParam String channel ) {
		CameraPojo pojo = new CameraPojo();
		pojo.setIp("10.20.8.231");
		pojo.setChannel(channel);
		pojo.setPassword("admin");
		pojo.setUsername("admin");
		pojo.setPort("37777");
		// 返回结果
		Map<String, Object> map = new LinkedHashMap<>();
		// openStream返回结果
		Map<String, Object> openMap = new HashMap<>();
		JSONObject cameraJson = JSONObject.parseObject(JSONObject.toJSON(pojo).toString());
		// 需要校验非空的参数
//		String[] isNullArr = { "ip", "port", "username", "password", "channel"};
//		// 空值校验
//		if (!Utils.isNullParameters(cameraJson, isNullArr)) {
//			map.put("msg", "输入参数不完整");
//			map.put("code", 1);
//			return map;
//		}
//		// ip格式校验
//		if (!Utils.isTrueIp(pojo.getIp())) {
//			map.put("msg", "ip格式输入错误");
//			map.put("code", 2);
//			return map;
//		}
//		if (null != pojo.getStarttime() || "".equals(pojo.getStarttime())) {
//			// 开始时间校验
//			if (!Utils.isTrueTime(pojo.getStarttime())) {
//				map.put("msg", "starttime格式输入错误");
//				map.put("code", 3);
//				return map;
//			}
//			if (null != pojo.getEndtime() || "".equals(pojo.getEndtime())) {
//				if (!Utils.isTrueTime(pojo.getEndtime())) {
//					map.put("msg", "endtime格式输入错误");
//					map.put("code", 4);
//					return map;
//				}
//				// 结束时间要大于开始时间
//				try {
//					long starttime = new SimpleDateFormat("yyyy-MM-dd HH:ss:mm").parse(pojo.getStarttime()).getTime();
//					long endtime = new SimpleDateFormat("yyyy-MM-dd HH:ss:mm").parse(pojo.getEndtime()).getTime();
//					if (pojo.getStarttime().compareTo(pojo.getEndtime()) >= 0) {
//						map.put("msg", "endtime需要大于starttime");
//						map.put("code", 5);
//						return map;
//					}
//				} catch (ParseException e) {
//					logger.error(e.getMessage());
//				}
//			}
//		}

		Set<String> keys = CacheUtil.STREATMAP.keySet();
		CameraPojo cameraPojo = new CameraPojo();
		// 获取当前时间
		String opentime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime());
		// 判断缓存是否为空
		if (0 == keys.size()) {
			openMap = openStream(pojo.getIp(), pojo.getPort(), pojo.getUsername(), pojo.getPassword(),
					pojo.getChannel(), pojo.getStarttime(), pojo.getEndtime(), pojo.getToken(),
					opentime);
			if (Integer.parseInt(openMap.get("errorcode").toString()) == 0) {
				map.put("url", ((CameraPojo) openMap.get("pojo")).getUrl());
				map.put("token", ((CameraPojo) openMap.get("pojo")).getToken());
				map.put("msg", "打开视频流成功");
				map.put("code", 0);
			} else {
				map.put("msg", openMap.get("message"));
				map.put("code", openMap.get("errorcode"));
			}
		} else {
			boolean sign = false;// 是否存在的标志,true:存在;false:不存在
			if (null == pojo.getStarttime()) {// 直播流
				for (String key : keys) {
					if (pojo.getIp().equals(CacheUtil.STREATMAP.get(key).getIp())
							&& pojo.getChannel().equals(CacheUtil.STREATMAP.get(key).getChannel())
							&& null == CacheUtil.STREATMAP.get(key).getStarttime()) {// 存在直播流
						sign = true;
						cameraPojo = CacheUtil.STREATMAP.get(key);
						break;
					}
				}
				if (sign) {// 存在
					cameraPojo.setCount(cameraPojo.getCount() + 1);
					cameraPojo.setOpentime(opentime);
					map.put("url", cameraPojo.getUrl());
					map.put("token", cameraPojo.getToken());
					map.put("msg", "打开视频流成功");
					map.put("code", 0);
				} else {// 不存在
					openMap = openStream(pojo.getIp(), pojo.getPort(), pojo.getUsername(), pojo.getPassword(),
							pojo.getChannel(),  pojo.getStarttime(), pojo.getEndtime(),
							pojo.getToken(), opentime);
					if (Integer.parseInt(openMap.get("errorcode").toString()) == 0) {
						map.put("url", ((CameraPojo) openMap.get("pojo")).getUrl());
						map.put("token", ((CameraPojo) openMap.get("pojo")).getToken());
						map.put("msg", "打开视频流成功");
						map.put("code", 0);
					} else {
						map.put("msg", openMap.get("message"));
						map.put("code", openMap.get("errorcode"));
					}
				}
			} else {// 历史流
				openMap = openStream(pojo.getIp(), pojo.getPort(), pojo.getUsername(), pojo.getPassword(),
						pojo.getChannel(), pojo.getStarttime(), pojo.getEndtime(), pojo.getToken(),
						opentime);
				if (Integer.parseInt(openMap.get("errorcode").toString()) == 0) {
					map.put("url", ((CameraPojo) openMap.get("pojo")).getUrl());
					map.put("token", ((CameraPojo) openMap.get("pojo")).getToken());
					map.put("msg", "打开视频流成功");
					map.put("code", 0);
				} else {
					map.put("msg", openMap.get("message"));
					map.put("code", openMap.get("errorcode"));
				}
			}
		}

		return map;
	}

	/**
	 * @Title: openStream
	 * @Description:注册设备，拼接rtmp命令
	 * @param ip
	 * @param port
	 * @param username
	 * @param password
	 * @param channel
	 * @param starttime
	 * @param endtime
	 * @param opentime
	 * @return CameraPojo
	 **/
	private Map<String, Object> openStream(String ip, String port, String username, String password, String channel,
			 String starttime, String endtime, String histoken, String opentime) {
		Map<String, Object> map = new HashMap<>();
		CameraPojo cameraPojo = new CameraPojo();
		// 生成token
		String token = UUID.randomUUID().toString();
		String url = "";
		String Ip = Utils.IpConvert(ip);
		String rtmp = "";
		DHLoginSDK login = null;// 设备注册信息

		if (null != starttime && !"".equals(starttime)) {// 回放
			if (null != endtime && !"".equals(endtime)) {// 存在结束时间
				cameraPojo.setStarttime(starttime);
				cameraPojo.setEndtime(endtime);
			} else {
				cameraPojo.setStarttime(Utils.getStarttime(starttime));
				cameraPojo.setEndtime(Utils.getEndtime(starttime));
			}
			if (null != histoken && "" != histoken && CameraController.JOBMAP.containsKey(histoken)) {
				CameraPojo hisCameraPojo = CacheUtil.STREATMAP.get(histoken);
				hisCameraPojo.setStarttime(cameraPojo.getStarttime());
				hisCameraPojo.setEndtime(cameraPojo.getEndtime());
				hisCameraPojo.setReHistory(true);
				CacheUtil.STREATMAP.put(histoken, hisCameraPojo);
				// 记录拖动前的解码时间戳
				long dts = CacheUtil.PUSHRTMPMAP.get(histoken).getDts();
				CacheUtil.DTSMAP.put(histoken, dts);
				CameraController.JOBMAP.get(histoken).setInterrupted(histoken);
				map.put("pojo", hisCameraPojo);
				map.put("errorcode", 0);
				map.put("message", "打开视频流成功");
				return map;
			}
			rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + "/history/"
					+ token;
			if (config.getHost_extra().equals("127.0.0.1")) {
				url = rtmp;
			} else {
				url = "rtmp://" + Utils.IpConvert(config.getHost_extra())  + "/history/"
						+ token;
			}
		} else {// 直播
			rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + "/live/" + token;
			if (config.getHost_extra().equals("127.0.0.1")) {
				url = rtmp;
			} else {
				url = "rtmp://" + Utils.IpConvert(config.getHost_extra()) + "/live/"
						+ token;
			}
		}

		cameraPojo.setUsername(username);
		cameraPojo.setPassword(password);
		cameraPojo.setIp(Ip);
		cameraPojo.setPort(port);
		cameraPojo.setChannel(channel);
		cameraPojo.setRtmp(rtmp);
		cameraPojo.setUrl(url);
		cameraPojo.setOpentime(opentime);
		cameraPojo.setCount(1);
		cameraPojo.setToken(token);

		Socket rtmpSocket = new Socket();
		try {
			rtmpSocket.connect(new InetSocketAddress(Utils.IpConvert(config.getPush_host()),
					Integer.parseInt(config.getPush_port())), 1000);
			rtmpSocket.close();

			// 注册设备
			if (CacheUtil.LOGINSDK.containsKey(Ip)) {
				// 设备已经注册过
				// 使用人数+1
				CacheUtil.LOGINSDK.get(Ip).setCount(CacheUtil.LOGINSDK.get(Ip).getCount() + 1);
				login = CacheUtil.LOGINSDK.get(Ip);
			} else {
				login = new DHLoginSDK();
				login.login(cameraPojo);
				if (login.getIsLogin()) {
					// 设备注册成功
					logger.info("hcsdk 设备注册成功 设备信息：[ip:" + cameraPojo.getIp() + " port:" + cameraPojo.getPort()
							+ " username:" + cameraPojo.getUsername() + " password:" + cameraPojo.getPassword()
							+ " channel:" + cameraPojo.getChannel() + "]");
					// 使用人数+1
					login.setCount(login.getCount() + 1);
					CacheUtil.LOGINSDK.put(Ip, login);
				} else {
					logger.error("hcsdk 设备注册失败  ,错误码:" + login.getErrorCode() + " 设备信息：[ip:" + cameraPojo.getIp()
							+ " port:" + cameraPojo.getPort() + " username:" + cameraPojo.getUsername() + " password:"
							+ cameraPojo.getPassword() + " channel:" + cameraPojo.getChannel() + " stream:" + "]");
					map.put("pojo", cameraPojo);
					if (login.getErrorCode() == 7) {
						map.put("message", "连接设备失败,设备不在线或网络原因引起的连接超时等");
						map.put("errorcode", 7);
					} else {
						map.put("message", "其他错误");
						map.put("errorcode", 6);
					}
					return map;
				}
			}
			// 执行推流任务
			CameraThread.MyRunnable job = new CameraThread.MyRunnable(cameraPojo, login);
			CameraThread.MyRunnable.es.execute(job);
			JOBMAP.put(token, job);

			map.put("pojo", cameraPojo);
			map.put("errorcode", login.getErrorCode());
			map.put("message", "打开视频流成功");
		} catch (Exception e) {
			logger.error("与推流IP:" + config.getPush_host() + " 端口: " + config.getPush_port() + " 建立连接失败,请检查nginx服务");
			map.put("pojo", cameraPojo);
			map.put("errorcode", 8);
			map.put("message",
					"与推流IP:" + config.getPush_host() + " 端口: " + config.getPush_port() + " 建立连接失败,请检查nginx服务");
			try {
				rtmpSocket.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		return map;
	}

	/**
	 * @Title: closeCamera
	 * @Description:关闭视频流
	 * @param tokens
	 * @return void
	 **/
	@DeleteMapping(value = "/cameras/{tokens}")
	public void closeCamera(@PathVariable("tokens") String tokens) {
		if (null != tokens && !"".equals(tokens)) {
			String[] tokenArr = tokens.split(",");
			for (String token : tokenArr) {
				if (JOBMAP.containsKey(token) && CacheUtil.STREATMAP.containsKey(token)) {
					if (0 < CacheUtil.STREATMAP.get(token).getCount()) {
						// 使用人数-1
						CacheUtil.STREATMAP.get(token).setCount(CacheUtil.STREATMAP.get(token).getCount() - 1);
						logger.info("关闭成功 当前设备使用人数为" + CacheUtil.STREATMAP.get(token).getCount() + " 设备信息：[ip："
								+ CacheUtil.STREATMAP.get(token).getIp() + " port:"
								+ CacheUtil.STREATMAP.get(token).getPort() + " channel:"
								+ CacheUtil.STREATMAP.get(token).getChannel()  + " statrtime:"
								+ CacheUtil.STREATMAP.get(token).getStarttime() + " endtime:"
								+ CacheUtil.STREATMAP.get(token).getEndtime() + " url:"
								+ CacheUtil.STREATMAP.get(token).getUrl() + "]");
					}
				}
//				CameraController.JOBMAP.get(token).setInterrupted(token);
			}
		}
	}

	/**
	 * @Title: getCameras
	 * @Description:获取视频流
	 * @return Map<String,CameraPojo>
	 **/
	@GetMapping(value = "/cameras")
	public Map<String, CameraPojo> getCameras() {
		logger.info("获取视频源信息:" + CacheUtil.STREATMAP.toString());
		return CacheUtil.STREATMAP;
	}

	/**
	 * @Title: keepAlive
	 * @Description:视频流保活
	 * @param tokens
	 * @return
	 **/
	@PutMapping(value = "/cameras/{tokens}")
	public void keepAlive(@PathVariable("tokens") String tokens) {
		// 校验参数
		if (null != tokens && !"".equals(tokens)) {
			String[] tokenArr = tokens.split(",");
			for (String token : tokenArr) {
				if (null != CacheUtil.STREATMAP.get(token)) {
					CameraPojo cameraPojo = CacheUtil.STREATMAP.get(token);
					// 更新当前系统时间
					cameraPojo.setOpentime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime()));
					logger.info("保活成功 设备信息：[ip：" + cameraPojo.getIp() + " port:" + cameraPojo.getPort() + " channel:"
							+ cameraPojo.getChannel()  + " starttime:"
							+ cameraPojo.getStarttime() + " endtime:" + cameraPojo.getEndtime() + " url:"
							+ cameraPojo.getUrl() + "]");
				}
			}
		}
	}

	/**
	 * @Title: getConfig
	 * @Description:获取服务信息
	 * @return Map<String,Object>
	 **/
	@GetMapping(value = "/status")
	public Map<String, Object> getConfig() {
		// 获取当前时间
		long nowtime = new Date().getTime();
		String uptime = (nowtime - CacheUtil.STARTTIME) / (1000 * 60 * 60) + "h"
				+ (nowtime - CacheUtil.STARTTIME) % (1000 * 60 * 60) / (1000 * 60) + "m"
				+ (nowtime - CacheUtil.STARTTIME) % (1000 * 60 * 60) % (1000 * 60) / (1000) + "s";
		logger.info("获取服务信息:" + config.toString() + ";服务运行时间:" + uptime);
		Map<String, Object> status = new HashMap<>();
		status.put("config", config);
		status.put("uptime", uptime);
		return status;
	}





}
