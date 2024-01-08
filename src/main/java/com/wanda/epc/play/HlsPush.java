package com.wanda.epc.play;

import com.wanda.epc.config.Config;
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
import org.springframework.context.ApplicationContext;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * @ClassName: HlsPush
 * @Description: 历史回放转hls流切片
 * @author: LianYanFei
 * @date: 2023年11月28日
 */
public class HlsPush {

	private final static Logger logger = LoggerFactory.getLogger(HlsPush.class);

	// 配置类
	public static Config config;

	// 通过applicationContext上下文获取Config类
	public static void setApplicationContext(ApplicationContext applicationContext) {
		config = applicationContext.getBean(Config.class);
	}

	private CameraPojo pojo;// 设备信息
	private FFmpegFrameGrabber grabber;// 采集器
	private FFmpegFrameRecorder recorder;// 解码器
	private Map<String, String> videoOption = new HashMap<>();// 设置视频参数
	private int bitrate = 2500000;// 比特率
	private double framerate;// 帧率
	private PipedInputStream inputStream;// 管道输入流
	private PipedOutputStream outputStream;// 管道输出流
	private int err_index = 0;// 推流过程中出现错误的次数
	private NativeLong sdkHandle;// 直播或回放的句柄
	private int playSign;// 回放的标志，用于停止回放释放资源
	private int timebase;// 时钟基
	private long dts = 0, pts = 0;// pkt的dts、pts时间戳
	private int start_index = 0; // 切片的ts文件起始序号
	public long jobStartTime;// 任务开始时间
	private boolean exitsign = false;// 结束任务标志

	public boolean isExitsign() {
		return exitsign;
	}

	public void setExitsign(boolean exitsign) {
		this.exitsign = exitsign;
	}

	public HlsPush(CameraPojo pojo, PipedInputStream inputStream, PipedOutputStream outputStream, NativeLong sdkHandle,
			int playSign) {
		this.pojo = pojo;
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

	public void push() {
		jobStartTime = System.currentTimeMillis();
		try {
			avutil.av_log_set_level(avutil.AV_LOG_QUIET);
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

			// 异常的framerate，强制使用25帧
			if (grabber.getFrameRate() > 0 && grabber.getFrameRate() < 100) {
				framerate = grabber.getFrameRate();
			} else {
				framerate = 25.0;
			}

			bitrate = grabber.getVideoBitrate();// 获取到的比特率 0

			// 获取ts切片的开始序号
			start_index = Utils.getTs_index(pojo.getStarttime());

			recorder = new FFmpegFrameRecorder(pojo.getM3u8path() + "channel" + pojo.getChannel() + "-.m3u8",
					grabber.getImageWidth(), grabber.getImageHeight(), 0);
			recorder.setInterleaved(true);
			recorder.setVideoOptions(videoOption);
			// 设置比特率
			recorder.setVideoBitrate(bitrate);
			// h264编/解码器
			recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
			recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
			// 视频帧率(保证视频质量的情况下最低25，低于25会出现闪屏)
			recorder.setFrameRate(framerate);
			// 关键帧间隔，一般与帧率相同或者是视频帧率的两倍
			recorder.setGopSize((int) framerate);
			// 解码器格式
			recorder.setFormat("hls");
			// 单个切片时长
			recorder.setOption("hls_time", String.valueOf(config.getHls_interval() * 60));
			// HLS播放的列表长度，0标识不做限制
			recorder.setOption("hls_list_size", "0");
			// 设置切片的ts文件序号起始值
			recorder.setOption("start_number", String.valueOf(start_index));

			AVFormatContext fc = null;
			fc = grabber.getFormatContext();
			this.recorder.start(fc);
			logger.debug("hcsdk 开始切片 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " channel:"
					+ pojo.getChannel() + " stream:" + pojo.getStream() + " starttime:" + pojo.getStarttime()
					+ " endtime:" + pojo.getEndtime() + " url:" + pojo.getUrl() + "]");
			// 清空探测时留下的缓存
//			grabber.flush();

			AVPacket pkt = null;

			for (int no_pkt_index = 0; no_pkt_index < 5 && err_index < 5;) {
				// 中断推流任务开关
				if (exitsign) {
					break;
				}
				pkt = grabber.grabPacket();
				if (pkt == null || pkt.size() <= 0 || pkt.data() == null) {
					Thread.sleep(1);
					no_pkt_index++;
					continue;
				}
				// 获取到的pkt的dts，pts异常，将此包丢弃掉。
				if (pkt.dts() == avutil.AV_NOPTS_VALUE && pkt.pts() == avutil.AV_NOPTS_VALUE || pkt.pts() < dts) {
					err_index++;
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

				}
				// pts,dts累加
				timebase = grabber.getFormatContext().streams(pkt.stream_index()).time_base().den();

				pts += (timebase / (int) framerate);
				dts += (timebase / (int) framerate);

				// 将缓存空间的引用计数-1，并将Packet中的其他字段设为初始值。如果引用计数为0，自动的释放缓存空间。
				av_packet_unref(pkt);
			}
		} catch (Exception e) {
			e.printStackTrace();
			release();
			logger.error(e.getMessage());
		} finally {
			release();
			logger.info("hcsdk 切片结束 耗时：" + (System.currentTimeMillis() - jobStartTime) / 1000 + "s 设备信息：[ip:"
					+ pojo.getIp() + " port:" + pojo.getPort() + " channel:" + pojo.getChannel() + " stream:"
					+ pojo.getStream() + " starttime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + " url:"
					+ pojo.getUrl() + "]");
		}
	}

}
