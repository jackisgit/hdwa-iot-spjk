package com.wanda.epc.play;

import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.util.Utils;
import com.sun.jna.NativeLong;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
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
 * @author LianYanFei
 * @Title RtmpPush.java
 * @description javacv推数据帧
 * @time 2023年11月28日 下午2:32:42
 **/
public class RtmpPush {
    private final static Logger logger = LoggerFactory.getLogger(RtmpPush.class);
    private AVPacket avPacket = null;
    private CameraPojo pojo;// 设备信息
    private FFmpegFrameRecorder recorder;// 解码器
    private FFmpegFrameGrabber grabber;// 采集器
    private Map<String, String> videoOption = new HashMap<>();// 设置视频参数
    private int bitrate = 2500000;// 比特率
    private double framerate;// 帧率

    private Frame frame;
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
    }

    /**
     * @return void
     * @Title: release
     * @Description:资源释放
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
     * @return void
     * @Title: push
     * @Description:推送视频流数据包
     **/
    public void push() {
        try {
            // 恢复拖动状态
            pojo.setReHistory(false);
            FFmpegLogCallback.setLevel(avutil.AV_LOG_QUIET);
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
            //h264只需要转封装
            if (grabber.getVideoCodec() == avcodec.AV_CODEC_ID_H264) {
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