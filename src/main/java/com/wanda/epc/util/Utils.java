package com.wanda.epc.util;

import com.alibaba.fastjson.JSONObject;
import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.ToolKits;
import com.wanda.epc.config.Config;
import com.sun.jna.NativeLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * @author LianYanFei
 * @Title Utils.java
 * @description 工具类
 * @time 2023年11月28日 下午3:30:02
 **/
public class Utils {

    private final static Logger logger = LoggerFactory.getLogger(Utils.class);

    // 配置类
    public static Config config;

    // 通过applicationContext上下文获取Config类
    public static void setApplicationContext(ApplicationContext applicationContext) {
        config = applicationContext.getBean(Config.class);
    }

    /**
     * @param domainName
     * @return ip
     * @Title: IpConvert
     * @Description:域名转ip
     **/
    public static String IpConvert(String domainName) {
        String ip = domainName;
        try {
            ip = InetAddress.getByName(domainName).getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return domainName;
        }
        return ip;
    }


    /**
     * @return starttime
     * @Title: getStarttime
     * @Description:获取回放开始时间
     **/
    public static String getStarttime(String time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String starttime = null;
        try {
            starttime = sdf.format(sdf.parse(time).getTime() - 60 * 1000);
        } catch (Exception e) {
            logger.error("时间格式化错误");
            e.printStackTrace();
        }
        return starttime;
    }

    /**
     * @return endString
     * @Title: getEndtime
     * @Description:获取回放结束时间
     **/
    public static String getEndtime(String time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String endString = null;
        try {
            endString = sdf.format(sdf.parse(time).getTime() + 60 * 1000);
        } catch (Exception e) {
            logger.error("时间格式化错误");
            e.printStackTrace();
        }
        return endString;
    }

    /**
     * @param playHandle
     * @param playSign:  0:直播;1:回放
     * @return void
     * @Title: sdkRelease
     * @Description:sdk资源释放
     **/
    public static void sdkRelease(NetSDKLib.LLong playHandle, int playSign) {
        logger.info("释放资源：{},{}", playHandle.longValue(), playHandle);
        if (playSign == 0) {
            if (!NetSDKLib.NETSDK_INSTANCE.CLIENT_StopRealPlay(playHandle)) {// 停止预览
                logger.error("dhsdk NET_DVR_StopRealPlay error,code:" + ToolKits.getErrorCode());
            }
        } else {
            if (!NetSDKLib.NETSDK_INSTANCE.CLIENT_StopPlayBack(playHandle)) {// 停止回放
                logger.error("dhsdk NET_DVR_StopPlayBack error,code:" + ToolKits.getErrorCode());
            }
        }
    }


    /**
     * @param ip
     * @return boolean
     * @Title: isTrueIp
     * @Description:接口参数ip格式校验
     **/
    public static boolean isTrueIp(String ip) {
        return ip.matches("([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}");
    }

    /**
     * @param time
     * @return boolean
     * @Title: isTrueTime
     * @Description:接口参数时间格式校验
     **/
    public static boolean isTrueTime(String time) {
        try {
            new SimpleDateFormat("yyyy-MM-dd HH:ss:mm").parse(time);
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
    }


    /**
     * @param path
     * @Title: mkdirs
     * @Description: 检测是否存在路径，不存在则创建
     * @return: void
     **/
    public static void checkPath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * @Title: getStart_index
     * @Description: 根据传入时间获取ts切片的起始序号（时间点处于当天的第几分钟）
     * @return: int
     **/
    public static int getTs_index(String starttime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        int start_index = 0;
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(starttime));
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            start_index = hour * 60 + minute;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return start_index;
    }

    /**
     * @param starttime 开始时间
     * @param endtime   结束时间
     * @param token     令牌
     * @param channel   通道号
     * @param ip
     * @Title: createm3u8
     * @Description: 创建m3u8文件
     * @return: void
     **/
    public static void createm3u8(String starttime, String endtime, String token, String channel, String ip) {
        // 生成索引文件路径
        String m3u8path = config.getM3u8_path() + ip + "\\\\channel" + channel + "\\\\" + token + "\\\\";
        // 检查该路径是否存在，不存在则创建
        Utils.checkPath(m3u8path);
        // 生成的m3u8文件路径（包含文件名）
        m3u8path += "channel" + channel + ".m3u8";
        try {
            // 获取该m3u8文件开始的切片序号
            int strat_index = getTs_index(starttime);
            File file = new File(m3u8path);
            file.createNewFile();
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file), "utf-8");
            // 生成m3u8文件内容
            // m3u文件头
            osw.write("#EXTM3U\n");
            // 版本号
            osw.write("#EXT-X-VERSION:3\n");
            // 每一个media URI 在 PlayList中只有唯一的序号，相邻之间序号+1, 一个media URI并不是必须要包含的，如果没有，默认为0
            osw.write("#EXT-X-MEDIA-SEQUENCE:" + strat_index + "\n");
            // 是否允许做cache
            osw.write("#EXT-X-ALLOW-CACHE:YES\n");
            // 指定最大的媒体段时间长（秒）
            osw.write("#EXT-X-TARGETDURATION:60\n");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long startdate = sdf.parse(starttime).getTime();
            long enddate = sdf.parse(endtime).getTime();
            long dataDifference = (enddate - startdate) / 1000;

            // 切片的数量
            int tsnum = (int) (dataDifference % (config.getHls_interval() * 60) == 0
                    ? dataDifference / (config.getHls_interval() * 60)
                    : dataDifference / (config.getHls_interval() * 60) + 1);

            for (int index = 0; index < tsnum; index++) {
                // duration 指定每个媒体段(ts)的持续时间（秒），仅对其后面的URI有效
                osw.write("#EXTINF:" + config.getHls_interval() * 60 + ".000000,\n");
                // ts片的名称
                osw.write("channel" + channel + "-" + strat_index + ".ts\n");
                strat_index++;
            }

            // 表示PlayList的末尾
            osw.write("#EXT-X-ENDLIST");
            osw.flush();
            osw.close();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("生成：" + m3u8path + " 文件失败" + e.getMessage());
        }
    }

}
