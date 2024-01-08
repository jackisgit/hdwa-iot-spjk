package com.wanda.epc.play;

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

	private CameraPojo pojo;// 设备信息
	private FFmpegFrameRecorder recorder;// 解码器
	private FFmpegFrameGrabber grabber;// 采集器
	private Map<String, String> videoOption = new HashMap<>();// 设置视频参数
	private int bitrate = 2500000;// 比特率
	private double framerate;// 帧率
	private PipedInputStream inputStream;// 管道输入流
	private PipedOutputStream outputStream;// 管道输出流
	private int err_index = 0;// 推流过程中出现错误的次数
	private NativeLong sdkHandle;// 直播或回放的句柄
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
			NativeLong sdkHandle, int playSign) {
		this.pojo = cameraPojo;
		this.inputStream = inputStream;
		this.outputStream = outputStream;
		this.playSign = playSign;
		this.sdkHandle = sdkHandle;
		// 视频参数设置
		// 该参数用于降低延迟
		this.videoOption.put("tune", "zerolatency");
		/**
		 ** 权衡quality(视频质量)和encode speed(编码速度) values(值)： *
		 * ultrafast(终极快),superfast(超级快), veryfast(非常快), faster(很快), fast(快), *
		 * medium(中等), slow(慢), slower(很慢), veryslow(非常慢) *
		 * ultrafast(终极快)提供最少的压缩（低编码器CPU）和最大的视频流大小；而veryslow(非常慢)提供最佳的压缩（高编码器CPU）的同时降低视频流的大小
		 */
		this.videoOption.put("preset", "ultrafast");
		// 画面质量参数，0~51；18~28是一个合理范围
		this.videoOption.put("crf", "25");
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
			avutil.av_log_set_level(avutil.AV_LOG_ERROR);
			FFmpegLogCallback.set();
			grabber = new FFmpegFrameGrabber(inputStream, 0);
			grabber.setVideoOption("vcodec", "copy");
			grabber.setFormat("mpeg");
			grabber.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
			grabber.setVideoCodec(avcodec.AV_CODEC_ID_H264);
			grabber.setAudioStream(Integer.MAX_VALUE);


			// 用于检测海康sdk回调函数是否有数据流产生，从而避免没有数据流导致avformat_open_input()函数阻塞
			long stime = new Date().getTime();
			while (true) {
				Thread.sleep(100);
				if (new Date().getTime() - stime > 2000) {
					logger.info("hcsdk 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
							+ pojo.getChannel() + " stream:" + pojo.getStream() + " starttime:" + pojo.getStarttime()
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

			bitrate = grabber.getVideoBitrate();// 获取到的比特率 0
			recorder = new FFmpegFrameRecorder(pojo.getRtmp(), grabber.getImageWidth(), grabber.getImageHeight(), 0);
			recorder.setInterleaved(true);
			recorder.setVideoOptions(this.videoOption);
			// 设置比特率
			recorder.setVideoBitrate(bitrate);
			// h264编/解码器
			recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
			// 封装flv格式
			recorder.setFormat("flv");
			recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
			// 视频帧率(保证视频质量的情况下最低25，低于25会出现闪屏)
			recorder.setFrameRate(framerate);
			// 关键帧间隔，一般与帧率相同或者是视频帧率的两倍
			recorder.setGopSize(50);

			AVFormatContext fc = null;
			fc = grabber.getFormatContext();
			this.recorder.start(fc);
			logger.debug("hcsdk 开始推流 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
					+ pojo.getChannel() + " stream:" + pojo.getStream() + " starttime:" + pojo.getStarttime()
					+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "]");
			// 清空探测时留下的缓存
			grabber.flush();

			AVPacket pkt = null;
			AVPacket oldpkt = null;
			long lasttime = System.currentTimeMillis();
			int pktindex = 0;
			for (int no_pkt_index = 0; no_pkt_index < 5 && err_index < 5;) {
				// 中断推流任务开关
				if (exitcode == 1) {
					break;
				}
				// 回放暂停开关
				if (playstatus) {
					// 暂停超过2分钟，结束推流
					if (System.currentTimeMillis() - pausetime <= 1000 * 60 * 2) {
						// 将暂停前推得最后一个AVPacket拷贝给 要推送的pkt，循环推送最后一帧
						pkt = av_packet_clone(oldpkt);
					} else {
						logger.info("hcsdk 回放暂停超时，结束推流！");
						break;
					}
				} else {
					// 获取AVPacket进行推送
					pkt = grabber.grabPacket();
					// copy一份AVPacket缓存起来
					oldpkt = pkt == null || pkt.size() <= 0 || pkt.data() == null ? null : av_packet_clone(pkt);
				}
				if (pkt == null || pkt.size() <= 0 || pkt.data() == null) {
					if (playSign == 0) {
						// 空包记录次数跳过
						logger.warn("JavaCV 出现空包 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
								+ pojo.getChannel() + " stream:" + pojo.getStream() + " starttime:"
								+ pojo.getStarttime() + " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl()
								+ "]");
						no_pkt_index++;
						exitcode = 2;
						continue;
					} else {
						logger.warn("JavaCV 出现空包 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
								+ pojo.getChannel() + " stream:" + pojo.getStream() + " starttime:"
								+ pojo.getStarttime() + " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl()
								+ "]");
						no_pkt_index++;
						continue;
					}
				}
				// 获取到的pkt的dts，pts异常，将此包丢弃掉。
				if (pkt.dts() == avutil.AV_NOPTS_VALUE && pkt.pts() == avutil.AV_NOPTS_VALUE || pkt.pts() < dts) {
					err_index++;
					exitcode = 2;
					av_packet_unref(pkt);
					continue;
				}

				// 过滤音频
				if (pkt.stream_index() == 1) {
					av_packet_unref(pkt);
					continue;
				} else {
					// 矫正sdk回调数据的dts，pts每次不从0开始累加所导致的播放器无法续播问题
					pkt.pts(pts);
					pkt.dts(dts);
					err_index += (recorder.recordPacket(pkt) ? 0 : 1);
					// 如果是回放，进行流控
					if (playSign == 1) {
						pktindex++;
						if (pktindex >= framerate * speed) {
							long nowtime = System.currentTimeMillis();
							if (nowtime - lasttime < 1000) {
								Thread.sleep(1000 - (nowtime - lasttime));
							}
							lasttime = System.currentTimeMillis();
							pktindex = 0;
						}
					}

				}
				// pts,dts累加
				timebase = grabber.getFormatContext().streams(pkt.stream_index()).time_base().den();

				pts += (timebase / (int) framerate) / speed;
				dts += (timebase / (int) framerate) / speed;

				// 将缓存空间的引用计数-1，并将Packet中的其他字段设为初始值。如果引用计数为0，自动的释放缓存空间。
				av_packet_unref(pkt);
			}
		} catch (Exception e) {
			e.printStackTrace();
			release();
			logger.error(e.getMessage());
		} finally {
			release();
			if (exitcode == 2) {
				logger.info("hcsdk 开始重连 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
						+ pojo.getChannel() + " stream:" + pojo.getStream() + " starttime:" + pojo.getStarttime()
						+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "]");
			} else {
				logger.info("hcsdk 推流结束 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
						+ pojo.getChannel() + " stream:" + pojo.getStream() + " starttime:" + pojo.getStarttime()
						+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "]");
			}
		}
	}
}