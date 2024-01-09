package com.wanda.epc.pojo;

import com.netsdk.lib.NetSDKLib;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * @Title CameraPojo.java
 * @description 设备信息类
 * @time 2023年11月28日 下午3:25:37
 * @author LianYanFei
 **/
public class CameraPojo implements Serializable {
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;
	@NotBlank(message = "账号不能为空")
	private String username;// 设备账号
	private String password;// 设备密码
	private String ip;// 设备ip
	private String channel;// 通道号
	private String port;// 设备端口
	private String starttime;// 回放开始时间
	private String endtime;// 回放结束时间
	private String opentime;// 打开时间
	private String rtmp;// rtmp地址
	private String hls;// hls播放地址
	private String url;// 播放地址
	private String token;// token
	private int count = 0;// 使用人数
	private  NetSDKLib.LLong lHisPlayHandle;// 历史回放播放句柄
	private String downloadpath;// 录像下载路径
	private String m3u8path;// 切片保存路径
	private boolean reHistory = false;// 是否拖动



	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getStarttime() {
		return starttime;
	}

	public void setStarttime(String starttime) {
		this.starttime = starttime;
	}

	public String getEndtime() {
		return endtime;
	}

	public void setEndtime(String endtime) {
		this.endtime = endtime;
	}

	public String getOpentime() {
		return opentime;
	}

	public void setOpentime(String opentime) {
		this.opentime = opentime;
	}

	public String getRtmp() {
		return rtmp;
	}

	public void setRtmp(String rtmp) {
		this.rtmp = rtmp;
	}

	public String getHls() {
		return hls;
	}

	public void setHls(String hls) {
		this.hls = hls;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}


	public  NetSDKLib.LLong getlHisPlayHandle() {
		return lHisPlayHandle;
	}

	public void setlHisPlayHandle( NetSDKLib.LLong lHisPlayHandle) {
		this.lHisPlayHandle = lHisPlayHandle;
	}

	public String getDownloadpath() {
		return downloadpath;
	}

	public void setDownloadpath(String downloadpath) {
		this.downloadpath = downloadpath;
	}

	public String getM3u8path() {
		return m3u8path;
	}

	public void setM3u8path(String m3u8path) {
		this.m3u8path = m3u8path;
	}

	public boolean getReHistory() {
		return reHistory;
	}

	public void setReHistory(boolean reHistory) {
		this.reHistory = reHistory;
	}

	@Override
	public String toString() {
		return "CameraPojo [username=" + username + ", password=" + password + ", ip=" + ip + ", channel=" + channel
				 + ", port=" + port + ", starttime=" + starttime + ", endtime=" + endtime
				+ ", opentime=" + opentime + ", rtmp=" + rtmp + ", hls=" + hls + ", url=" + url + ", token=" + token
				+ ", count=" + count + ", lHisPlayHandle=" + lHisPlayHandle + ", downloadpath=" + downloadpath
				+ ", m3u8path=" + m3u8path  + ", reHistory=" + reHistory + "]";
	}

}
