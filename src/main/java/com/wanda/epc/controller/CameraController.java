package com.wanda.epc.controller;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.wanda.epc.entity.DeviceInfo;
import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.callback.PlayDataCallBack;
import com.wanda.epc.callback.RealDataCallBack;
import com.wanda.epc.config.Config;
import com.wanda.epc.ctrl.Control;
import com.wanda.epc.mapper.DeviceInfoMapper;
import com.wanda.epc.play.CloudCode;
import com.wanda.epc.play.PlayBackCapture;
import com.wanda.epc.play.RtmpPush;
import com.wanda.epc.pojo.*;
import com.wanda.epc.request.HikvisionControllingRequest;
import com.wanda.epc.request.HikvisionPlaybackRequest;
import com.wanda.epc.sdk.HCLoginSDK;
import com.wanda.epc.sdk.HCNetSDK;
import com.wanda.epc.sdk.HCNetSDK.NET_DVR_FINDDATA_V30;
import com.wanda.epc.sdk.HCNetSDK.NET_DVR_TIME;
import com.wanda.epc.thread.CameraThread;
import com.wanda.epc.util.ResultUtil;
import com.wanda.epc.util.Utils;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author LianYanFei
 * @Title CameraController.java
 * @description controller
 * @time 2023年11月28日 下午3:43:29
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

    @Value("${projectIp}")
    private String projectIp;

    @Autowired
    private DeviceInfoMapper deviceInfoMapper;

    /*
     * 存放任务线程
     */
    public static Map<String, CameraThread.MyRunnable> JOBMAP = new ConcurrentHashMap<>();


    @PostMapping("/preview")
    public ResultUtil<List<HikvisionDevicePojo>> preview(@RequestBody List<String> eqIds) {
        if (!CollectionUtils.isEmpty(eqIds)) {
            List<HikvisionDevicePojo> hikvisionDeviceDtoList = eqIds.stream().map(eqId -> {
                LambdaQueryWrapper<DeviceInfo> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(DeviceInfo::getEqId, eqId);
                DeviceInfo deviceInfo = deviceInfoMapper.selectOne(queryWrapper);
                HikvisionDevicePojo hikvisionDeviceDto = new HikvisionDevicePojo();
                logger.info("预览查询数据库结果：{}", JSON.toJSONString(deviceInfo));
                if (Objects.nonNull(deviceInfo)) {
                    CameraPojo cameraPojo = new CameraPojo();
                    cameraPojo.setIp(deviceInfo.getIp());
                    cameraPojo.setPassword(deviceInfo.getPassword());
                    cameraPojo.setUsername(deviceInfo.getAccount());
                    cameraPojo.setPort(deviceInfo.getPort());
                    cameraPojo.setChannel(deviceInfo.getChannel());
                    Map<String, Object> restulMap = openCamera(cameraPojo);
                    Integer code = (Integer) restulMap.get("code");
                    if (code == 0) {
                        String url = (String) restulMap.get("url");
                        hikvisionDeviceDto.setUrl(url);
                        hikvisionDeviceDto.setName(deviceInfo.getCameraName());
                        hikvisionDeviceDto.setEqId(deviceInfo.getEqId());
                    }
                }
                return hikvisionDeviceDto;
            }).collect(Collectors.toList());
            return ResultUtil.success(hikvisionDeviceDtoList);
        }
        return null;
    }


    @PostMapping("/playback")
    public ResultUtil<HikvisionDevicePojo> playback(@RequestBody HikvisionPlaybackRequest request) {
        LambdaQueryWrapper<DeviceInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceInfo::getEqId, request.getEqId());
        DeviceInfo deviceInfo = deviceInfoMapper.selectOne(queryWrapper);
        logger.info("回放查询数据库结果：{}", JSON.toJSONString(deviceInfo));
        if (Objects.nonNull(deviceInfo)) {
            CameraPojo cameraPojo = new CameraPojo();
            cameraPojo.setIp(deviceInfo.getIp());
            cameraPojo.setPassword(deviceInfo.getPassword());
            cameraPojo.setUsername(deviceInfo.getAccount());
            cameraPojo.setPort(deviceInfo.getPort());
            cameraPojo.setChannel(deviceInfo.getChannel());
            cameraPojo.setToken(deviceInfo.getEqId());
            cameraPojo.setStarttime(request.getBeginTime());
            cameraPojo.setEndtime(request.getEndTime());
            Map<String, Object> resultMap = openCamera(cameraPojo);
            Integer code = (Integer) resultMap.get("code");
            if (code == 0) {
                HikvisionDevicePojo hikvisionDeviceDto = new HikvisionDevicePojo();
                String url = (String) resultMap.get("url");
                hikvisionDeviceDto.setUrl(url);
                hikvisionDeviceDto.setUrl(url);
                hikvisionDeviceDto.setName(deviceInfo.getCameraName());
                hikvisionDeviceDto.setEqId(deviceInfo.getEqId());
                return ResultUtil.success(hikvisionDeviceDto);
            } else {
                String message = (String) resultMap.get("message");
                return ResultUtil.fail(message, null);
            }
        }
        return null;
    }

    @PostMapping(value = "/hisList")
    public ResultUtil<List<HikvisionPlayBackLisPojo>> historyList(@RequestBody HikvisionPlaybackRequest request) throws InterruptedException {
        LambdaQueryWrapper<DeviceInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceInfo::getEqId, request.getEqId());
        DeviceInfo deviceInfo = deviceInfoMapper.selectOne(queryWrapper);
        if (Objects.nonNull(deviceInfo)) {
            CameraPojo cameraPojo = new CameraPojo();
            cameraPojo.setIp(deviceInfo.getIp());
            cameraPojo.setPassword(deviceInfo.getPassword());
            cameraPojo.setUsername(deviceInfo.getAccount());
            cameraPojo.setPort(deviceInfo.getPort());
            cameraPojo.setChannel(deviceInfo.getChannel());
            cameraPojo.setToken(deviceInfo.getEqId());
            cameraPojo.setStarttime(request.getBeginTime());
            cameraPojo.setEndtime(request.getEndTime());
            List<HikvisionPlayBackLisPojo> historyList = historyList(cameraPojo);
            return ResultUtil.success(historyList);
        }
        return null;
    }

    @PostMapping("/controlling")
    public ResultUtil<HikvisionCameraPojo> controlling(@RequestBody HikvisionControllingRequest request) {
        LambdaQueryWrapper<DeviceInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceInfo::getEqId, request.getEqId());
        DeviceInfo deviceInfo = deviceInfoMapper.selectOne(queryWrapper);
        if (Objects.nonNull(deviceInfo)) {
            HCLoginSDK loginSDK = CacheUtil.LOGINSDK.get(deviceInfo.getIp());
            if (loginSDK.getIsLogin()) {
                if (StrUtil.equals(request.getCommand(), "UP")) {
                    Control.cloudControl(deviceInfo.getIp(), com.wanda.epc.ctrl.CloudCode.TILT_UP, com.wanda.epc.ctrl.CloudCode.SPEED_LV6, request.getAction(), loginSDK.getLUserID());
                } else if (StrUtil.equals(request.getCommand(), "DOWN")) {
                    Control.cloudControl(deviceInfo.getIp(), com.wanda.epc.ctrl.CloudCode.TILT_DOWN, com.wanda.epc.ctrl.CloudCode.SPEED_LV6, request.getAction(), loginSDK.getLUserID());
                } else if (StrUtil.equals(request.getCommand(), "LEFT")) {
                    Control.cloudControl(deviceInfo.getIp(), com.wanda.epc.ctrl.CloudCode.PAN_LEFT, com.wanda.epc.ctrl.CloudCode.SPEED_LV6, request.getAction(), loginSDK.getLUserID());
                } else if (StrUtil.equals(request.getCommand(), "RIGHT")) {
                    Control.cloudControl(deviceInfo.getIp(), com.wanda.epc.ctrl.CloudCode.PAN_RIGHT, com.wanda.epc.ctrl.CloudCode.SPEED_LV6, request.getAction(), loginSDK.getLUserID());
                } else if (StrUtil.equals(request.getCommand(), "ZOOM_IN")) {
                    Control.cloudControl(deviceInfo.getIp(), com.wanda.epc.ctrl.CloudCode.ZOOM_IN, com.wanda.epc.ctrl.CloudCode.SPEED_LV6, request.getAction(), loginSDK.getLUserID());
                } else if (StrUtil.equals(request.getCommand(), "ZOOM_OUT")) {
                    Control.cloudControl(deviceInfo.getIp(), com.wanda.epc.ctrl.CloudCode.ZOOM_OUT, com.wanda.epc.ctrl.CloudCode.SPEED_LV6, request.getAction(), loginSDK.getLUserID());
                }
            }

        }
        return ResultUtil.success();
    }

    /**
     * @param pojo
     * @return Map<String, String>
     * @Title: openCamera
     * @Description:开启视频流
     **/
    public Map<String, Object> openCamera( CameraPojo pojo) {
        // 返回结果
        Map<String, Object> map = new LinkedHashMap<>();
        // openStream返回结果
        Map<String, Object> openMap = new HashMap<>();
        // ip格式校验
        if (!Utils.isTrueIp(pojo.getIp())) {
            map.put("msg", "ip格式输入错误");
            map.put("code", 2);
            return map;
        }
        if (null != pojo.getStarttime() || "".equals(pojo.getStarttime())) {
            // 开始时间校验
            if (!Utils.isTrueTime(pojo.getStarttime())) {
                map.put("msg", "starttime格式输入错误");
                map.put("code", 3);
                return map;
            }
            if (null != pojo.getEndtime() || "".equals(pojo.getEndtime())) {
                if (!Utils.isTrueTime(pojo.getEndtime())) {
                    map.put("msg", "endtime格式输入错误");
                    map.put("code", 4);
                    return map;
                }
                // 结束时间要大于开始时间
                try {
                    long starttime = new SimpleDateFormat("yyyy-MM-dd HH:ss:mm").parse(pojo.getStarttime()).getTime();
                    long endtime = new SimpleDateFormat("yyyy-MM-dd HH:ss:mm").parse(pojo.getEndtime()).getTime();
                    if (pojo.getStarttime().compareTo(pojo.getEndtime()) >= 0) {
                        map.put("msg", "endtime需要大于starttime");
                        map.put("code", 5);
                        return map;
                    }
                } catch (ParseException e) {
                    logger.error(e.getMessage());
                }
            }
        }

        Set<String> keys = CacheUtil.STREATMAP.keySet();
        CameraPojo cameraPojo = new CameraPojo();
        // 获取当前时间
        String opentime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime());
        // 判断缓存是否为空
        if (0 == keys.size()) {
            openMap = openStream(pojo.getIp(), pojo.getPort(), pojo.getUsername(), pojo.getPassword(),
                    pojo.getChannel(), pojo.getStream(), pojo.getStarttime(), pojo.getEndtime(), pojo.getToken(),
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
                            pojo.getChannel(), pojo.getStream(), pojo.getStarttime(), pojo.getEndtime(),
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
                        pojo.getChannel(), pojo.getStream(), pojo.getStarttime(), pojo.getEndtime(), pojo.getToken(),
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


    private Map<String, Object> openStream(String ip, String port, String username, String password, String channel,
                                           String stream, String starttime, String endtime, String histoken, String opentime) {
        Map<String, Object> map = new HashMap<>();
        CameraPojo cameraPojo = new CameraPojo();
        // 生成token
        String token = UUID.randomUUID().toString();
        String url = "";
        String Ip = Utils.IpConvert(ip);
        String rtmp = "";
        HCLoginSDK login = null;// 设备注册信息

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
            rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + ":" + config.getPush_port() + "/history/"
                    + token;
            if (config.getHost_extra().equals("127.0.0.1")) {
                url = "http://" + projectIp + ":8080/history/" + token + ".m3u8";
            } else {
                url = "http://" + projectIp + ":8080/history/" + token + ".m3u8";
            }
        } else {// 直播
            rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + ":" + config.getPush_port() + "/live/" + token;
            if (config.getHost_extra().equals("127.0.0.1")) {
                url = "http://" + projectIp + ":8080/live/" + token + ".m3u8";
            } else {
                url = "http://" + projectIp + ":8080/live/" + token + ".m3u8";
            }
        }

        cameraPojo.setUsername(username);
        cameraPojo.setPassword(password);
        cameraPojo.setIp(Ip);
        cameraPojo.setPort(port);
        cameraPojo.setChannel(channel);
        cameraPojo.setStream(stream);
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
                login = new HCLoginSDK();
                login.login(cameraPojo);
                if (login.getIsLogin()) {
                    // 设备注册成功
                    logger.info("hcsdk 设备注册成功 设备信息：[ip:" + cameraPojo.getIp() + " port:" + cameraPojo.getPort()
                            + " username:" + cameraPojo.getUsername() + " password:" + cameraPojo.getPassword()
                            + " channel:" + cameraPojo.getChannel() + " stream:" + cameraPojo.getStream() + "]");
                    // 使用人数+1
                    login.setCount(login.getCount() + 1);
                    CacheUtil.LOGINSDK.put(Ip, login);
                } else {
                    logger.error("hcsdk 设备注册失败  ,错误码:" + login.getErrorCode() + " 设备信息：[ip:" + cameraPojo.getIp()
                            + " port:" + cameraPojo.getPort() + " username:" + cameraPojo.getUsername() + " password:"
                            + cameraPojo.getPassword() + " channel:" + cameraPojo.getChannel() + " stream:"
                            + cameraPojo.getStream() + "]");
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
     * @throws InterruptedException
     * @Title: historyList
     * @Description: 获取指定时间内的视频列表
     * @return: void
     **/
    public List<HikvisionPlayBackLisPojo> historyList(CameraPojo pojo) throws InterruptedException {
        HCLoginSDK login = null;// 注册设备
        JSONObject json = new JSONObject(true);
        if (CacheUtil.LOGINSDK.containsKey(pojo.getIp())) {
            // 设备已经注册过
            // 使用人数+1
            CacheUtil.LOGINSDK.get(pojo.getIp()).setCount(CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() + 1);
            login = CacheUtil.LOGINSDK.get(pojo.getIp());
        } else {
            login = new HCLoginSDK();
            login.login(pojo);
            if (login.getIsLogin()) {
                // 设备注册成功
                logger.info("hcsdk 设备注册成功 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " username:"
                        + pojo.getUsername() + " password:" + pojo.getPassword() + " channel:" + pojo.getChannel()
                        + " stream:" + pojo.getStream() + "]");
                // 使用人数+1
                login.setCount(login.getCount() + 1);
                CacheUtil.LOGINSDK.put(pojo.getIp(), login);
            } else {
                logger.error("hcsdk 设备注册失败  ,错误码:" + login.getErrorCode() + " 设备信息：[ip:" + pojo.getIp() + " port:"
                        + pojo.getPort() + " username:" + pojo.getUsername() + " password:" + pojo.getPassword()
                        + " channel:" + pojo.getChannel() + " stream:" + pojo.getStream() + "]");
                if (login.getErrorCode() == 7) {
                    json.put("message", "连接设备失败,设备不在线或网络原因引起的连接超时等");
                    json.put("errorcode", 7);
                } else {
                    json.put("message", "其他错误");
                    json.put("errorcode", 6);
                }
            }
        }

        NET_DVR_TIME lpStartTime = Utils.getNvrTime(pojo.getStarttime());
        NET_DVR_TIME lpStopTime = Utils.getNvrTime(pojo.getEndtime());

        // 根据时间查找设备录像文件。

        NativeLong lFindHandle = HCNetSDK.INSTANCE.NET_DVR_FindFile(login.getLUserID(),
                new NativeLong(Integer.valueOf(pojo.getChannel())), 0, lpStartTime, lpStopTime);
        if (lFindHandle.intValue() < 0) {
            int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
            logger.error("hcsdk 按时间查找录像文件失败,错误码:" + errorcode);
            json.put("message", "按时间查找录像文件失败 错误码:" + errorcode);
            HCNetSDK.INSTANCE.NET_DVR_FindClose(lFindHandle);
            return null;
        }
        // 文件查找结果信息结构体。
        HCNetSDK.NET_DVR_FINDDATA_V40 lpFindData = new HCNetSDK.NET_DVR_FINDDATA_V40();
        NativeLong lFindNextFile_V40;

        List<HikvisionPlayBackLisPojo> list = new ArrayList<>();

        // 文件列表序号
        int videoindex = 1;
        while (true) {
            // 逐个获取查找到的文件信息
            lFindNextFile_V40 = HCNetSDK.INSTANCE.NET_DVR_FindNextFile_V40(lFindHandle, lpFindData);
            // 正在查找请等待
            if (lFindNextFile_V40.intValue() == 1002) {
                continue;
            }
            // 获取文件信息成功
            if (lFindNextFile_V40.intValue() == 1000) {
                HikvisionPlayBackLisPojo hisList = new HikvisionPlayBackLisPojo();
                hisList.setBeginTime(Utils.sdkTimeToStr(lpFindData.struStartTime));
                hisList.setEndTime(Utils.sdkTimeToStr(lpFindData.struStopTime));
                list.add(hisList);
                videoindex++;
                continue;
            }
            if (lFindNextFile_V40.intValue() == 1003) {
                logger.debug("hcsdk 没有更多的文件，查找结束");
                break;
            }
        }
        // 结束查找 释放资源
        HCNetSDK.INSTANCE.NET_DVR_FindClose(lFindHandle);
        // 判断当前设备使用人数,如果人数>1,则-1;否则注销当前设备
        if (CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() > 1) {
            CacheUtil.LOGINSDK.get(pojo.getIp()).setCount(CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() - 1);
        } else {
            CacheUtil.LOGINSDK.get(pojo.getIp()).logout();
            CacheUtil.LOGINSDK.remove(pojo.getIp());
        }
        return list;
    }


}
