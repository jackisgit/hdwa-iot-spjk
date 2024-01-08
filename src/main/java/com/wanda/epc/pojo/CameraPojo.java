package com.wanda.epc.pojo;

import com.wanda.epc.sdk.HCNetSDK.NET_DVR_PREVIEWINFO;
import com.sun.jna.NativeLong;

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
	@NotBlank(message = "密码不能为空")
	private String password;// 设备密码
	@NotBlank(message = "ip不能为空")
	@Pattern(regexp = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$", message = "ip格式输入错误")
	private String ip;// 设备ip
	@NotBlank(message = "通道号不能为空")
	private String channel;// 通道号
	@NotBlank(message = "码流类型不能为空")
	private String stream;// 设备码流
	@NotBlank(message = "设备端口不能为空")
	private String port;// 设备端口
	@NotBlank(message = "开始时间不能为空")
	@Pattern(regexp = "^(((20[0-3][0-9]-(0[13578]|1[02])-(0[1-9]|[12][0-9]|3[01]))|(20[0-3][0-9]-(0[2469]|11)-(0[1-9]|[12][0-9]|30))) (20|21|22|23|[0-1][0-9]):[0-5][0-9]:[0-5][0-9])$", message = "开始时间格式错误")
	private String starttime;// 回放开始时间
	@NotBlank(message = "结束时间不能为空")
	@Pattern(regexp = "^(((20[0-3][0-9]-(0[13578]|1[02])-(0[1-9]|[12][0-9]|3[01]))|(20[0-3][0-9]-(0[2469]|11)-(0[1-9]|[12][0-9]|30))) (20|21|22|23|[0-1][0-9]):[0-5][0-9]:[0-5][0-9])$", message = "结束时间格式错误")
	private String endtime;// 回放结束时间
	private String opentime;// 打开时间
	private String rtmp;// rtmp地址
	private String hls;// hls播放地址
	private String url;// 播放地址
	private String token;// token
	private int count = 0;// 使用人数
	private NativeLong lHisPlayHandle;// 历史回放播放句柄
	private String downloadpath;// 录像下载路径
	private String m3u8path;// 切片保存路径
	private NET_DVR_PREVIEWINFO previewinfo = new NET_DVR_PREVIEWINFO();// 预览参数
	private boolean reHistory = false;// 是否拖动
	private String op;
	private Integer action;


	public CameraPojo() {
		this.previewinfo.hPlayWnd = null;// 播放窗口的句柄，为NULL表示不解码显示。
		this.previewinfo.bBlocked = true;// false- 非阻塞取流，true- 阻塞取流
		this.previewinfo.dwLinkMode = 0;// 连接方式：0- TCP方式，1- UDP方式，2- 多播方式，3- RTP方式，4- RTP/RTSP，5- RTP/HTTP，6-
										// HRUDP（可靠传输）
	}

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
		if (!"".equals(channel) && null != channel) {
			this.previewinfo.lChannel = 0xffffffff;
			this.previewinfo.byStreamID  = channel.getBytes();
		}
	}

	public String getStream() {
		return stream;
	}

	public void setStream(String stream) {
		this.stream = stream;
		// 码流类型：0-主码流，1-子码流，2-三码流，3-虚拟码流，以此类推
		if ("sub".equals(stream)) {// 子码流
			this.previewinfo.dwStreamType = 1;
		} else {// 主码流
			this.previewinfo.dwStreamType = 0;
		}
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

	public NET_DVR_PREVIEWINFO getPreviewinfo() {
		return previewinfo;
	}

	public void setPreviewinfo(NET_DVR_PREVIEWINFO previewinfo) {
		this.previewinfo = previewinfo;
	}

	public NativeLong getlHisPlayHandle() {
		return lHisPlayHandle;
	}

	public void setlHisPlayHandle(NativeLong lHisPlayHandle) {
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
				+ ", stream=" + stream + ", port=" + port + ", starttime=" + starttime + ", endtime=" + endtime
				+ ", opentime=" + opentime + ", rtmp=" + rtmp + ", hls=" + hls + ", url=" + url + ", token=" + token
				+ ", count=" + count + ", lHisPlayHandle=" + lHisPlayHandle + ", downloadpath=" + downloadpath
				+ ", m3u8path=" + m3u8path + ", previewinfo=" + previewinfo + ", reHistory=" + reHistory + "]";
	}

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public Integer getAction() {
		return action;
	}

	public void setAction(Integer action) {
		this.action = action;
	}
}
