package com.wanda.epc.play;

import com.netsdk.lib.NetSDKLib;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.util.Utils;
import com.sun.jna.NativeLong;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_clone;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * @Title RtmpPush.java
 * @description javacv推数据帧
 * @time 2023年11月28日 下午2:32:42
 * @author LianYanFei
 **/
public class RtmpPush {
	private final static Logger logger = LoggerFactory.getLogger(RtmpPush.class);

	private AVPacket avPacket = null;

	private Frame frame;
	private CameraPojo pojo;// 设备信息
	private FFmpegFrameRecorder recorder;// 解码器
	private FFmpegFrameGrabber grabber;// 采集器
	private Map<String, String> videoOption = new HashMap<>();// 设置视频参数
	private int bitrate = 2500000;// 比特率
	private double framerate;// 帧率
	private PipedInputStream inputStream;// 管道输入流
	private PipedOutputStream outputStream;// 管道输出流
	private int err_index = 0;// 推流过程中出现错误的次数
	private NetSDKLib.LLong sdkHandle;// 直播或回放的句柄
	private int playSign;// 直播或回放的标志，用于停止预览或回放;0-直播；1-回放
	private int exitcode = 0;// 退出状态码：0-正常退出;1-手动中断;2-异常情况需要重连
	private int timebase;// 时钟基
	private double speed = 1;// 倍速
	private boolean playstatus = false;// 暂停回放标志
	private long dts = 0, pts = 0;// pkt的dts、pts时间戳
	private long pausetime;

	public void setExitcode(int exitcode) {
		this.exitcode = exitcode;
	}

	public int getExitcode() {
		return exitcode;
	}

	public void setPlaystatus(boolean playstatus) {
		this.playstatus = playstatus;
	}

	public int getPlaySign() {
		return playSign;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}

	public long getDts() {
		return dts;
	}

	public void setDts(long dts) {
		this.dts = dts;
		this.pts = dts;
	}

	public long getPausetime() {
		return pausetime;
	}

	public void setPausetime(long pausetime) {
		this.pausetime = pausetime;
	}

	public RtmpPush(CameraPojo cameraPojo, PipedInputStream inputStream, PipedOutputStream outputStream,
			NetSDKLib.LLong sdkHandle, int playSign) {
		this.pojo = cameraPojo;
		this.inputStream = inputStream;
		this.outputStream = outputStream;
		this.playSign = playSign;
		this.sdkHandle = sdkHandle;
	}

	/**
	 * @Title: release
	 * @Description:资源释放
	 * @return void
	 **/
	public void release() {
		try {
			inputStream.close();
			outputStream.close();
			Utils.sdkRelease(sdkHandle, playSign);
			grabber.stop();
			grabber.close();
			if (recorder != null) {
				recorder.stop();
				recorder.release();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @Title: push
	 * @Description:推送视频流数据包
	 * @return void
	 **/
	public void push() {
		try {
			// 恢复拖动状态
			pojo.setReHistory(false);
			FFmpegLogCallback.setLevel(avutil.AV_LOG_QUIET);
			grabber = new FFmpegFrameGrabber(inputStream, 0);
			//有些码率什么可以自己设置、不过没有必要
			grabber.setVideoCodec(avcodec.AV_CODEC_ID_H264);
			// 设置读取的最大数据，单位字节 为了加快首播速度
			grabber.setOption("probesize", "8192");
			// 设置分析的最长时间，单位微秒 为了加快首播速度
			grabber.setOption("analyzeduration", "1000000");
			// 5秒超时 单位微秒
			grabber.setOption("stimeout", "5000000");
			// 5秒超时 单位微秒
			grabber.setOption("rw_timeout", "5000000");
			// 设置缓存大小，提高画质、减少卡顿花屏
			grabber.setOption("buffer_size", "1024000");
			// 用于检测海康sdk回调函数是否有数据流产生，从而避免没有数据流导致avformat_open_input()函数阻塞
			long stime = new Date().getTime();
			while (true) {
				Thread.sleep(100);
				if (new Date().getTime() - stime > 2000) {
					logger.info("hcsdk 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
							+ pojo.getChannel() + " starttime:" + pojo.getStarttime()
							+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "] 无视频流数据");
					return;
				}
				if (inputStream.available() == 1024) {
					break;
				}
			}
			grabber.start();
			if (grabber.getFrameRate() > 0 && grabber.getFrameRate() < 100) {
				framerate = grabber.getFrameRate();
			} else {
				framerate = 25.0;
			}
			recorder = new FFmpegFrameRecorder(pojo.getRtmp(), grabber.getImageWidth(), grabber.getImageHeight());
			recorder.setFormat("flv");
			recorder.setInterleaved(true);
			recorder.setVideoOption("preset", "ultrafast");
			recorder.setVideoOption("tune", "zerolatency");
			recorder.setVideoOption("crf", "25");
			recorder.setSampleRate(grabber.getSampleRate());
			recorder.setFrameRate(framerate);
			recorder.setVideoBitrate(bitrate);
			logger.debug("dahua 开始推流 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
					+ pojo.getChannel() + " starttime:" + pojo.getStarttime()
					+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "]");
			// 清空探测时留下的缓存
			//h264只需要转封装
			if (grabber.getVideoCodec() == avcodec.AV_CODEC_ID_H264) {
				if (grabber.getAudioChannels() > 0) {
					recorder.setAudioChannels(grabber.getAudioChannels());
					recorder.setAudioBitrate(grabber.getAudioBitrate());
					recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
				}
				recorder.start(grabber.getFormatContext());
				while ((avPacket = grabber.grabPacket()) != null) {
					recorder.recordPacket(avPacket);
				}
			} else {
				if (grabber.getAudioChannels() > 0) {
					recorder.setAudioChannels(grabber.getAudioChannels());
					recorder.setAudioBitrate(grabber.getAudioBitrate());
					recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
				}
				recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
				//使用libx264加速解码 注意需要使用gpl版的 不然还是默认是思科的h264编解码器
				recorder.setVideoCodecName("libx264");
				recorder.start();
				while ((frame = grabber.grab()) != null) {
					recorder.record(frame);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			release();
			logger.error(e.getMessage());
		} finally {
			release();
			if (exitcode == 2) {
				logger.info("dahua 开始重连 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
						+ pojo.getChannel()  + " starttime:" + pojo.getStarttime()
						+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "]");
			} else {
				logger.info("hcsdk 推流结束 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
						+ pojo.getChannel()  + " starttime:" + pojo.getStarttime()
						+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "]");
			}
		}
	}
}