package com.wanda.epc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


/**
 * @author LianYanFei
 * @version 1.0
 * @project iot_epc_spdj
 * @description 读取配置文件的bean
 * @date 2023/10/18 16:32:42
 */
@Component
@ConfigurationProperties(prefix = "config")
public class Config {
	private Integer keepalive;// 保活时长（分钟）
	private String push_host;// 推送地址
	private String host_extra;// 额外地址
	private String push_port;// 推送端口
	private String m3u8_port;// m3u8端口
	private String m3u8_path;// 切片保存路径
	private Integer hls_interval; // 分段切片间隔
	private Integer bitrate;// 设备码率上限
	private String libpath;// sdk引用库位置
	private String videopath;// 录像下载路径
	private String picturepath;// 抓图保存路径
	private String deletevideocron;// 删除录像文件周期
	private String deletepicturecron;// 删除抓图文件周期
	private String version;// 版本信息

	public Integer getKeepalive() {
		return keepalive;
	}

	public void setKeepalive(Integer keepalive) {
		this.keepalive = keepalive;
	}

	public String getPush_host() {
		return push_host;
	}

	public void setPush_host(String push_host) {
		this.push_host = push_host;
	}

	public String getHost_extra() {
		return host_extra;
	}

	public void setHost_extra(String host_extra) {
		this.host_extra = host_extra;
	}

	public String getPush_port() {
		return push_port;
	}

	public void setPush_port(String push_port) {
		this.push_port = push_port;
	}

	public String getM3u8_port() {
		return m3u8_port;
	}

	public void setM3u8_port(String m3u8_port) {
		this.m3u8_port = m3u8_port;
	}

	public String getM3u8_path() {
		return m3u8_path;
	}

	public void setM3u8_path(String m3u8_path) {
		this.m3u8_path = m3u8_path;
	}

	public Integer getHls_interval() {
		return hls_interval;
	}

	public void setHls_interval(Integer hls_interval) {
		this.hls_interval = hls_interval;
	}

	public Integer getBitrate() {
		return bitrate;
	}

	public void setBitrate(Integer bitrate) {
		this.bitrate = bitrate;
	}

	public String getLibpath() {
		return libpath;
	}

	public void setLibpath(String libpath) {
		this.libpath = libpath;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getVideopath() {
		return videopath;
	}

	public void setVideopath(String videopath) {
		this.videopath = videopath;
	}

	public String getPicturepath() {
		return picturepath;
	}

	public void setPicturepath(String picturepath) {
		this.picturepath = picturepath;
	}

	public String getDeletevideocron() {
		return deletevideocron;
	}

	public void setDeletevideocron(String deletevideocron) {
		this.deletevideocron = deletevideocron;
	}

	public String getDeletepicturecron() {
		return deletepicturecron;
	}

	public void setDeletepicturecron(String deletepicturecron) {
		this.deletepicturecron = deletepicturecron;
	}

	@Override
	public String toString() {
		return "Config [keepalive=" + keepalive + ", push_host=" + push_host + ", host_extra=" + host_extra
				+ ", push_port=" + push_port + ", m3u8_port=" + m3u8_port + ", m3u8_path=" + m3u8_path
				+ ", hls_interval=" + hls_interval + ", bitrate=" + bitrate + ", libpath=" + libpath + ", videopath="
				+ videopath + ", picturepath=" + picturepath + ", deletevideocron=" + deletevideocron
				+ ", deletepicturecron=" + deletepicturecron + ", version=" + version + "]";
	}

}
