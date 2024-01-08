package com.wanda.epc.play;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.IplImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Date;

/**
 * @ClassName: PlayBackCapture
 * @Description:抓图
 * @author: LianYanFei
 * @date: 2023年11月28日
 */
public class PlayBackCapture {

	private final static Logger logger = LoggerFactory.getLogger(PlayBackCapture.class);

	private PipedInputStream picInputStream;// 抓图输入流
	private PipedOutputStream picOutputStream;// 抓图输出流
	private FFmpegFrameGrabber grabber;// 抓流器
	private ArrayList<String> picturePaths = new ArrayList<>();// 存放抓图地址

	public PlayBackCapture(PipedInputStream picInputStream, PipedOutputStream picOutputStream) {
		this.picInputStream = picInputStream;
		this.picOutputStream = picOutputStream;
	}

	public void setPicturePath(String picturepath) {
		picturePaths.add(picturepath);
	}

	public void playBackCapture(String token) throws IOException, InterruptedException {
		try {
			avutil.av_log_set_level(avutil.AV_LOG_QUIET);
			FFmpegLogCallback.set();
			picInputStream.connect(picOutputStream);
			grabber = new FFmpegFrameGrabber(picInputStream, 0);
			grabber.setFormat("mpeg");
			grabber.setPixelFormat(avutil.AV_PIX_FMT_BGR24);
			grabber.setVideoCodec(avcodec.AV_CODEC_ID_H264);
			grabber.setAudioStream(Integer.MAX_VALUE);
			// 用于检测海康sdk回调函数是否有数据流产生，从而避免没有数据流导致avformat_open_input()函数阻塞
			long stime = new Date().getTime();
			while (true) {
				Thread.sleep(100);
				if (new Date().getTime() - stime > 2000) {
					logger.info("抓图失败！");
					throw new RuntimeException("抓图失败！");
				}
				if (picInputStream.available() == 1024) {
					break;
				}
			}
			grabber.start();
			String rotate = grabber.getVideoMetadata("rotate");// 视频的旋转角度
			Frame frame = null;
			int pictureIndex = 0;
			logger.info("hcsdk 抓图线程开始 token:" + token);
			for (int no_frame_index = 0; no_frame_index < 10;) {
				frame = grabber.grabImage();
				// 空帧
				if (null == frame || null == frame.image) {
					no_frame_index++;
					continue;
				}
				IplImage src = null;
				if (null != rotate && rotate.length() > 1) {
					OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
					src = converter.convert(frame);
					frame = converter.convert(rotate(src, Integer.valueOf(rotate)));
				}
				doExecuteFrame(frame, picturePaths.get(pictureIndex));
				logger.info("hcsdk " + " 抓图完成 保存路径为：" + picturePaths.get(pictureIndex));
				pictureIndex++;
				if (pictureIndex < picturePaths.size()) {
					continue;
				} else {
					break;
				}
			}
		} catch (Exception e) {
			logger.info("hcsdk " + " 抓图失败");
			grabber.stop();
			grabber.close();
			picInputStream.close();
			picOutputStream.close();
			e.printStackTrace();
		} finally {
			logger.info("hcsdk 抓图线程结束 token:" + token);
			grabber.stop();
			grabber.close();
			picInputStream.close();
			picOutputStream.close();
		}

	}

	private void doExecuteFrame(Frame frame, String picturepath) throws IOException {
		if (null == frame || null == frame.image) {
			return;
		}
		Java2DFrameConverter converter = new Java2DFrameConverter();
		BufferedImage bi = converter.getBufferedImage(frame);

		File output = new File(picturepath);
		try {
			ImageIO.write(bi, "jpg", output);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private IplImage rotate(IplImage src, Integer angle) {
		IplImage img = IplImage.create(src.height(), src.width(), src.depth(), src.nChannels());
		opencv_core.cvTranspose(src, img);
		opencv_core.cvFlip(img, img, angle);
		return img;
	}
}
