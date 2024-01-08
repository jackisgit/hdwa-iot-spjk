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
        JSONObject cameraJson = JSONObject.parseObject(JSONObject.toJSON(pojo).toString());
//        // 需要校验非空的参数
//        String[] isNullArr = {"ip", "port", "username", "password", "channel", "stream"};
//        // 空值校验
//        if (!Utils.isNullParameters(cameraJson, isNullArr)) {
//            map.put("msg", "输入参数不完整");
//            map.put("code", 1);
//            return map;
//        }
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

    /**
     * @param ip
     * @param port
     * @param username
     * @param password
     * @param channel
     * @param stream
     * @param starttime
     * @param endtime
     * @param token
     * @param opentime
     * @return CameraPojo
     * @Title: openStream
     * @Description:注册设备，拼接rtmp命令
     **/
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
     * @param tokens
     * @return void
     * @Title: closeCamera
     * @Description:关闭视频流
     **/
    @DeleteMapping(value = "/cameras/{tokens}")
    public void closeCamera(@PathVariable("tokens") String tokens) {
        if (null != tokens && !"".equals(tokens)) {
            String[] tokenArr = tokens.split(",");
            for (String token : tokenArr) {
                if (JOBMAP.containsKey(token) && CacheUtil.STREATMAP.containsKey(token)) {
                    if (0 < CacheUtil.STREATMAP.get(token).getCount()) {
                        // 使用人数-1
                        CacheUtil.STREATMAP.get(token).setCount(CacheUtil.STREATMAP.get(token).getCount() - 1);
                        logger.info("关闭成功 当前设备使用人数为" + CacheUtil.STREATMAP.get(token).getCount() + " 设备信息：[ip："
                                + CacheUtil.STREATMAP.get(token).getIp() + " port:"
                                + CacheUtil.STREATMAP.get(token).getPort() + " channel:"
                                + CacheUtil.STREATMAP.get(token).getChannel() + " stream:"
                                + CacheUtil.STREATMAP.get(token).getStream() + " statrtime:"
                                + CacheUtil.STREATMAP.get(token).getStream() + " endtime:"
                                + CacheUtil.STREATMAP.get(token).getEndtime() + " url:"
                                + CacheUtil.STREATMAP.get(token).getUrl() + "]");
                    }
                }
//				CameraController.JOBMAP.get(token).setInterrupted(token);
            }
        }
    }

    /**
     * @return Map<String, CameraPojo>
     * @Title: getCameras
     * @Description:获取视频流
     **/
    @GetMapping(value = "/cameras")
    public Map<String, CameraPojo> getCameras() {
        logger.info("获取视频源信息:" + CacheUtil.STREATMAP.toString());
        return CacheUtil.STREATMAP;
    }

    /**
     * @param tokens
     * @return
     * @Title: keepAlive
     * @Description:视频流保活
     **/
    @PutMapping(value = "/cameras/{tokens}")
    public void keepAlive(@PathVariable("tokens") String tokens) {
        // 校验参数
        if (null != tokens && !"".equals(tokens)) {
            String[] tokenArr = tokens.split(",");
            for (String token : tokenArr) {
                if (null != CacheUtil.STREATMAP.get(token)) {
                    CameraPojo cameraPojo = CacheUtil.STREATMAP.get(token);
                    // 更新当前系统时间
                    cameraPojo.setOpentime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime()));
                    logger.info("保活成功 设备信息：[ip：" + cameraPojo.getIp() + " port:" + cameraPojo.getPort() + " channel:"
                            + cameraPojo.getChannel() + " stream:" + cameraPojo.getStream() + " starttime:"
                            + cameraPojo.getStarttime() + " endtime:" + cameraPojo.getEndtime() + " url:"
                            + cameraPojo.getUrl() + "]");
                }
            }
        }
    }

    /**
     * @return Map<String, Object>
     * @Title: getConfig
     * @Description:获取服务信息
     **/
    @GetMapping(value = "/status")
    public Map<String, Object> getConfig() {
        // 获取当前时间
        long nowtime = new Date().getTime();
        String uptime = (nowtime - CacheUtil.STARTTIME) / (1000 * 60 * 60) + "h"
                + (nowtime - CacheUtil.STARTTIME) % (1000 * 60 * 60) / (1000 * 60) + "m"
                + (nowtime - CacheUtil.STARTTIME) % (1000 * 60 * 60) % (1000 * 60) / (1000) + "s";
        logger.info("获取服务信息:" + config.toString() + ";服务运行时间:" + uptime);
        Map<String, Object> status = new HashMap<>();
        status.put("config", config);
        status.put("uptime", uptime);
        return status;
    }

    /**
     * @param token
     * @Title: playpause
     * @Description: 暂停播放
     * @return: JSONObject
     **/
    @PutMapping(value = "/playpause/{token}")
    public JSONObject playpause(@PathVariable("token") @NotBlank(message = "token不能为空") String token) {
        JSONObject json = new JSONObject(true);
        if (CacheUtil.PUSHRTMPMAP.containsKey(token)) {
            if (CacheUtil.PUSHRTMPMAP.get(token).getPlaySign() == 0) {
                json.put("message", "直播不支持暂停播放功能！");
            } else {
                CacheUtil.PUSHRTMPMAP.get(token).setPausetime(System.currentTimeMillis());
                CacheUtil.PUSHRTMPMAP.get(token).setPlaystatus(true);
                json.put("message", "暂停播放成功 token：" + token);
                logger.info("hcsdk 暂停播放成功 设备信息：[ip:" + CacheUtil.STREATMAP.get(token).getIp() + " port:"
                        + CacheUtil.STREATMAP.get(token).getPort() + " channel:"
                        + CacheUtil.STREATMAP.get(token).getChannel() + " stream:"
                        + CacheUtil.STREATMAP.get(token).getStream() + " starttime:"
                        + CacheUtil.STREATMAP.get(token).getStarttime() + " endtime:"
                        + CacheUtil.STREATMAP.get(token).getEndtime() + " url:"
                        + CacheUtil.STREATMAP.get(token).getUrl() + "]");
            }
        } else {
            json.put("message", "token输入有误！");
        }
        return json;
    }

    /**
     * @param token
     * @Title: playrestart
     * @Description: 恢复播放
     * @return: JSONObject
     **/
    @PutMapping(value = "/playrestart/{token}")
    public JSONObject playrestart(@PathVariable("token") @NotBlank(message = "token不能为空") String token) {
        JSONObject json = new JSONObject(true);
        if (CacheUtil.PUSHRTMPMAP.containsKey(token)) {
            if (CacheUtil.PUSHRTMPMAP.get(token).getPlaySign() == 0) {
                json.put("message", "直播不支持恢复播放功能！");
            } else {
                CacheUtil.PUSHRTMPMAP.get(token).setPlaystatus(false);
                json.put("message", "恢复播放成功 token：" + token);
                logger.info("hcsdk 恢复播放成功 设备信息：[ip:" + CacheUtil.STREATMAP.get(token).getIp() + " port:"
                        + CacheUtil.STREATMAP.get(token).getPort() + " channel:"
                        + CacheUtil.STREATMAP.get(token).getChannel() + " stream:"
                        + CacheUtil.STREATMAP.get(token).getStream() + " starttime:"
                        + CacheUtil.STREATMAP.get(token).getStarttime() + " endtime:"
                        + CacheUtil.STREATMAP.get(token).getEndtime() + " url:"
                        + CacheUtil.STREATMAP.get(token).getUrl() + "]");
            }
        } else {
            json.put("message", "token输入有误！");
        }
        return json;
    }

    /**
     * @param token
     * @param speed 倍速：0.25，0.5， 1.0，2.0，4.0，8.0等
     * @Title: speedplay
     * @Description: 倍速播放
     * @return: JSONObject
     **/
    @PutMapping(value = "/speedplay")
    public JSONObject speedplay(@NotBlank(message = "token不能为空") String token,
                                @NotNull(message = "倍速不能为空") Double speed) {
        JSONObject json = new JSONObject(true);
        if (!CacheUtil.STREATMAP.containsKey(token)) {
            json.put("message", "token输入有误！");
            return json;
        }

        if (null == CacheUtil.STREATMAP.get(token).getStarttime()
                || "".equals(CacheUtil.STREATMAP.get(token).getStarttime())) {
            json.put("message", "直播不支持倍速播放功能！");
            return json;
        }
        // 支持的倍速
        List<Double> speedList = new ArrayList<Double>() {
            {
                this.add(0.125);
                this.add(0.25);
                this.add(0.5);
                this.add(1.0);
                this.add(2.0);
                this.add(4.0);
                this.add(8.0);
                this.add(16.0);
            }
        };
        if (!speedList.contains(speed)) {
            json.put("message", "仅支持0.125, 0.25, 0.5, 1.0, 2.0, 4.0, 8.0倍播放！");
            return json;
        }

        CameraPojo pojo = CacheUtil.STREATMAP.get(token);
        RtmpPush rtmpPush = CacheUtil.PUSHRTMPMAP.get(token);
        rtmpPush.setSpeed(speed);
        json.put("message", speed + "倍速播放成功！ token:" + token);
        logger.info("hcsdk 设备以" + speed + "倍速播放成功 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort() + " username:"
                + pojo.getUsername() + " password:" + pojo.getPassword() + " channel:" + pojo.getChannel() + " stream:"
                + pojo.getStream() + " starttime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + "]");
        return json;
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

    /**
     * @param pojo
     * @Title: download
     * @Description: 下载指定时间录像文件
     * @return: JSONObject
     **/
    @PostMapping(value = "/download")
    public JSONObject download(@RequestBody @Valid CameraPojo pojo) {
        JSONObject json = new JSONObject(true);
        HCLoginSDK login = null;
        // 注册设备
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
                return json;
            }
        }

        NET_DVR_TIME lpStartTime = Utils.getNvrTime(pojo.getStarttime());
        NET_DVR_TIME lpStopTime = Utils.getNvrTime(pojo.getEndtime());

        // 查找录像文件
        NativeLong lFindFileHandle = HCNetSDK.INSTANCE.NET_DVR_FindFile(login.getLUserID(),
                new NativeLong(Integer.valueOf(pojo.getChannel())), 0, lpStartTime, lpStopTime);
        if (lFindFileHandle.intValue() < 0) {
            int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
            logger.error("hcsdk 按时间查找录像文件失败,错误码:" + errorcode);
            json.put("message", "按时间查找录像文件失败 错误码:" + errorcode);
            HCNetSDK.INSTANCE.NET_DVR_FindClose(lFindFileHandle);
            return json;
        }

        NET_DVR_FINDDATA_V30 findData = new NET_DVR_FINDDATA_V30();
        NativeLong lFindFileNextHandle;
        while (true) {
            lFindFileNextHandle = HCNetSDK.INSTANCE.NET_DVR_FindNextFile_V30(lFindFileHandle, findData);
            if (lFindFileNextHandle.intValue() != 1002) {
                break;
            }
        }
        if (lFindFileNextHandle.intValue() != 1000) {
            int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
            logger.error("hcsdk 按时间逐个获取查找到的文件信息失败,错误码:" + errorcode);
            json.put("message", "按时间逐个获取查找到的文件信息失败 错误码:" + errorcode);
            HCNetSDK.INSTANCE.NET_DVR_FindClose(lFindFileHandle);
            return json;
        }

        // 关闭文件查找，释放资源。
        HCNetSDK.INSTANCE.NET_DVR_FindClose(lFindFileHandle);

        // 下载条件结构体
        HCNetSDK.NET_DVR_PLAYCOND pDownloadCond = new HCNetSDK.NET_DVR_PLAYCOND();
        pDownloadCond.dwChannel = Integer.valueOf(pojo.getChannel());
        pDownloadCond.struStartTime = lpStartTime;
        pDownloadCond.struStopTime = lpStopTime;
        pDownloadCond.byDrawFrame = 0;
        pDownloadCond.byStreamType = (byte) ("sub".equals(pojo.getStream()) ? 1 : 0);

        String videopath = null;
        try {
            String starttime = new SimpleDateFormat("yyyyMMddHHmmss")
                    .format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(pojo.getStarttime()));
            String endtime = new SimpleDateFormat("yyyyMMddHHmmss")
                    .format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(pojo.getEndtime()));
            videopath = config.getVideopath() + starttime + "-" + endtime + ".mp4";
        } catch (ParseException e) {
            e.printStackTrace();
        }

        // 检测下载路径是否存在,如果不存在则创建下载路径
        Utils.checkPath(config.getVideopath());

        // 按时间下载录像文件
        NativeLong net_DVR_GetFileByTime_V40 = HCNetSDK.INSTANCE.NET_DVR_GetFileByTime_V40(login.getLUserID(),
                videopath, pDownloadCond);
        if (net_DVR_GetFileByTime_V40.intValue() == -1) {
            int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
            logger.error("hcsdk 按时间下载录像文件失败 错误码:" + errorcode);
            json.put("message", "hcsdk 按时间下载录像文件失败 错误码:" + errorcode);
            return json;
        }
        // 控制录像下载状态 开始下载
        HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(net_DVR_GetFileByTime_V40, HCNetSDK.NET_DVR_PLAYSTART, 0, null);
        HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(net_DVR_GetFileByTime_V40, HCNetSDK.NET_DVR_SETSPEED, 2048, null);
        // 通过key来获取下载句柄和下载路径
        String key = UUID.randomUUID().toString();
        CacheUtil.DWONLOADHANDLE.put(key, net_DVR_GetFileByTime_V40);
        CacheUtil.DWONLOADPATH.put(key, videopath);
        logger.info("hcsdk 开始下载录像文件：" + videopath);
        json.put("message", "开始下载录像文件");
        json.put("key", key);
        return json;
    }

    /**
     * @param key
     * @Title: getDownloadProgress
     * @Description: 获取录像文件下载进度
     * @return: JSONObject
     **/
    @GetMapping(value = "/downprogress")
    public JSONObject getDownloadProgress(@NotBlank(message = "key不能为空") String key) {
        JSONObject json = new JSONObject(true);
        if (!CacheUtil.DWONLOADHANDLE.containsKey(key)) {
            json.put("message", "key输入有误！");
            return json;
        }
        IntByReference LPOutValue = new IntByReference();
        HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(CacheUtil.DWONLOADHANDLE.get(key), HCNetSDK.NET_DVR_PLAYGETPOS, 0,
                LPOutValue);
        if (LPOutValue.getValue() == 100) {
            logger.info("hcsdk 录像文件下载成功 下载路径：" + CacheUtil.DWONLOADPATH.get(key));
            json.put("message", "录像文件下载成功");
            json.put("videopath", CacheUtil.DWONLOADPATH.get(key));
            // 清除缓存
            CacheUtil.DWONLOADHANDLE.remove(key);
            CacheUtil.DWONLOADPATH.remove(key);

            return json;
        }
        json.put("message", "录像文件正在下载");
        json.put("downprogress", LPOutValue.getValue() + "%");
        return json;
    }

    /**
     * @param pojo
     * @Title: playBackCaptureFile
     * @Description: 抓图
     * @return: JSONObject
     **/
    @PostMapping(value = "/playbackcapture")
    public JSONObject playBackCaptureFile(@RequestBody CameraPojo pojo) {
        JSONObject json = new JSONObject(true);
        // 有播放流的接口
        if (null != pojo.getToken() && !"".equals(pojo.getToken())) {
            // 直播流截图
            if (CacheUtil.LIVECALLBACK.containsKey(pojo.getToken())) {
                json = liveCapturePic(pojo.getToken());
            } else if (CacheUtil.HISTORYCALLBACK.containsKey(pojo.getToken())) {
                // 历史流截图
                json = historyCapturePic(pojo.getToken());
            } else {
                json.put("message", "抓图失败，token有误！");
            }

        } else {
            // 无播放流的接口
            // 指定时间点截图
            if (null != pojo.getStarttime() && !"".equals(pojo.getStarttime())) {
                json = timedHistoryCapturePic(pojo);
            } else {
                // 系统时间截图
                json = timedLiveCapturePic(pojo);
            }
        }

        return json;
    }

    /**
     * @param token
     * @Title: liveCapturePic
     * @Description: 直播流截图
     * @return: JSONObject
     **/
    private JSONObject liveCapturePic(String token) {
        JSONObject json = new JSONObject(true);
        if (CacheUtil.LIVECALLBACK.get(token).playbackcapture == false) {
            // 检测下载路径是否存在,如果不存在则创建下载路径
            Utils.checkPath(config.getPicturepath());

            PipedInputStream picInputStream = new PipedInputStream();
            PipedOutputStream picOutputStream = new PipedOutputStream();

            // 将抓图的管道流放到callback中
            CacheUtil.LIVECALLBACK.get(token).setPicOutputStream(picOutputStream);
            PlayBackCapture backCapture = new PlayBackCapture(picInputStream, picOutputStream);

            // 缓存抓图任务
            CacheUtil.PLAYBACKCAPTURE.put(token, backCapture);

            // 构建抓图文件路径
            String picturepath = config.getPicturepath() + UUID.randomUUID().toString() + ".jpg";

            // 保存抓图路径
            backCapture.setPicturePath(picturepath);

            // 管道流开始写入数据
            CacheUtil.LIVECALLBACK.get(token).playbackcapture = true;

            try {
                backCapture.playBackCapture(token);
                json.put("message", "抓图成功");
                json.put("picturepath", picturepath);
            } catch (IOException e) {
                json.put("message", "抓图失败");
                e.printStackTrace();
            } catch (InterruptedException e) {
                json.put("message", "抓图失败");
                e.printStackTrace();
            } finally {
                // 关闭管道流写数据
                CacheUtil.LIVECALLBACK.get(token).playbackcapture = false;
                // 任务结束清除抓图缓存
                CacheUtil.PLAYBACKCAPTURE.remove(token);
            }

        } else {
            // 因为在直播中抓图任务使用的是一个callback，未避免A客户端还在进行抓图任务，B客户端覆盖掉A客户端的管道流正常写入数据产生错误，故而如果该token存在抓图任务，使第一个任务多抓一张图出来
            // 获取主抓图任务
            PlayBackCapture playBackCapture = CacheUtil.PLAYBACKCAPTURE.get(token);
            String picturepath = config.getPicturepath() + UUID.randomUUID().toString() + ".jpg";

            // 使主抓图任务抓图数量+1
            playBackCapture.setPicturePath(picturepath);
            // 异步操作，直接返回
            json.put("message", "抓图成功");
            json.put("picturepath", picturepath);
        }
        return json;
    }

    /**
     * @param token
     * @Title: historyCapturePic
     * @Description: 历史流截图
     * @return: JSONObject
     **/
    private JSONObject historyCapturePic(String token) {
        JSONObject json = new JSONObject(true);
        // 检测下载路径是否存在,如果不存在则创建下载路径
        Utils.checkPath(config.getPicturepath());

        PipedInputStream picInputStream = new PipedInputStream();
        PipedOutputStream picOutputStream = new PipedOutputStream();

        // 将抓图的管道流放入callback中
        CacheUtil.HISTORYCALLBACK.get(token).setPicOutputStream(picOutputStream);

        PlayBackCapture backCapture = new PlayBackCapture(picInputStream, picOutputStream);

        // 缓存抓图任务
        CacheUtil.PLAYBACKCAPTURE.put(token, backCapture);

        // 构建抓图文件路径
        String picturepath = config.getPicturepath() + UUID.randomUUID().toString() + ".jpg";

        // 保存抓图路径
        backCapture.setPicturePath(picturepath);

        // 管道流开始写入数据
        CacheUtil.HISTORYCALLBACK.get(token).playbackcapture = true;

        try {
            backCapture.playBackCapture(token);
            json.put("message", "抓图成功");
            json.put("picturepath", picturepath);
        } catch (IOException e) {
            json.put("message", "抓图失败");
            e.printStackTrace();
        } catch (InterruptedException e) {
            json.put("message", "抓图失败");
            e.printStackTrace();
        } finally {
            // 关闭管道流写数据
            CacheUtil.HISTORYCALLBACK.get(token).playbackcapture = false;
            // 任务结束清除抓图缓存
            CacheUtil.PLAYBACKCAPTURE.remove(token);
        }

        return json;
    }

    /**
     * @param pojo
     * @Title: timedHistoryCapturePic
     * @Description: 定时历史截图
     * @return: JSONObject
     **/
    private JSONObject timedHistoryCapturePic(CameraPojo pojo) {
        JSONObject json = new JSONObject(true);
        HCLoginSDK login = null;
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
                return json;
            }
            NET_DVR_TIME lpStartTime = new NET_DVR_TIME();
            NET_DVR_TIME lpStopTime = new NET_DVR_TIME();
            String starttime;
            try {
                starttime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(pojo.getStarttime()).getTime() - 1000));
                lpStartTime = Utils.getNvrTime(starttime);
                lpStopTime = Utils.getNvrTime(Utils.getEndtime(starttime));
            } catch (ParseException e1) {
                json.put("message", "抓图失败！");
                e1.printStackTrace();
            }
            // 根据时间检测录像文件
            NativeLong lFindFileHandle = HCNetSDK.INSTANCE.NET_DVR_FindFile(login.getLUserID(),
                    new NativeLong(new Integer(pojo.getChannel())), 0, lpStartTime, lpStopTime);
            if (lFindFileHandle.intValue() < 0) {
                int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                json.put("message", "抓图失败！");
                logger.error("hcsdk 抓图失败，按时间查找录像文件失败,错误码:" + errorcode + " 设备信息：[ip:" + pojo.getIp() + " port:"
                        + pojo.getPort() + " channel:" + pojo.getChannel() + " stream:" + pojo.getStream()
                        + " statrtime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + "]");
                return json;
            }
            // 录像文件信息结构体。
            NET_DVR_FINDDATA_V30 findData = new NET_DVR_FINDDATA_V30();
            NativeLong lFindFileNextHandle = null;
            // 异步操作，需要等待sdk给findData赋值
            while (true) {
                lFindFileNextHandle = HCNetSDK.INSTANCE.NET_DVR_FindNextFile_V30(lFindFileHandle, findData);
                if (lFindFileNextHandle.intValue() != 1002) {
                    break;
                }
            }
            if (lFindFileNextHandle.intValue() != 1000) {
                int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                json.put("message", "抓图失败！");
                logger.error("hcsdk 抓图失败，按时间逐个获取查找到的文件信息失败,错误码:" + errorcode + " 设备信息：[ip:" + pojo.getIp() + " port:"
                        + pojo.getPort() + " channel:" + pojo.getChannel() + " stream:" + pojo.getStream()
                        + " statrtime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + "]");
                return json;
            }

            // 关闭文件查找，释放资源。
            HCNetSDK.INSTANCE.NET_DVR_FindClose_V30(lFindFileHandle);
            // 按时间回放录像
            NativeLong lHisPlayHandle = HCNetSDK.INSTANCE.NET_DVR_PlayBackByTime(login.getLUserID(),
                    new NativeLong(new Integer(pojo.getChannel())), lpStartTime, lpStopTime, null);
            if (lHisPlayHandle.longValue() < 0) {
                int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                json.put("message", "抓图失败！");
                logger.error("hcsdk 抓图失败，按时间回放录像文件失败,错误码:" + errorcode + " 设备信息：[ip:" + pojo.getIp() + " port:"
                        + pojo.getPort() + " channel:" + pojo.getChannel() + " stream:" + pojo.getStream()
                        + " statrtime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + "]");
                return json;
            } else {
                PipedInputStream picInputStream = new PipedInputStream();
                PipedOutputStream picOutputStream = new PipedOutputStream();
                PlayDataCallBack fPlayDataCallBack = new PlayDataCallBack(picOutputStream);
                // 注册回调函数
                boolean isCallBack = HCNetSDK.INSTANCE.NET_DVR_SetPlayDataCallBack(lHisPlayHandle, fPlayDataCallBack,
                        0);
                if (!isCallBack) {
                    int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                    json.put("message", "抓图失败！");
                    logger.error("hcsdk 抓图失败，注册回调函数失败,错误码:" + errorcode + " 设备信息：[ip:" + pojo.getIp() + " port:"
                            + pojo.getPort() + " channel:" + pojo.getChannel() + " stream:" + pojo.getStream()
                            + " statrtime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + "]");
                    return json;
                }
                // 控制录像回放状态 开始回放
                boolean isControl = HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(lHisPlayHandle,
                        HCNetSDK.NET_DVR_PLAYSTART, 0, null);
                if (isControl) {
                    PlayBackCapture backCapture = new PlayBackCapture(picInputStream, picOutputStream);
                    // 构建抓图路径
                    String picturepath = config.getPicturepath() + UUID.randomUUID().toString() + ".jpg";
                    // 保存抓图路径
                    backCapture.setPicturePath(picturepath);
                    try {
                        backCapture.playBackCapture("");
                        json.put("message", "抓图成功");
                        json.put("picturepath", picturepath);
                    } catch (IOException e) {
                        json.put("message", "抓图失败");
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        json.put("message", "抓图失败");
                        e.printStackTrace();
                    } finally {
                        // 判断当前设备使用人数,如果人数>1,则-1;否则注销当前设备
                        if (CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() > 1) {
                            CacheUtil.LOGINSDK.get(pojo.getIp())
                                    .setCount(CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() - 1);
                        } else {
                            CacheUtil.LOGINSDK.get(pojo.getIp()).logout();
                            CacheUtil.LOGINSDK.remove(pojo.getIp());
                        }
                    }
                } else {
                    int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                    logger.error("hcsdk 抓图失败，控制录像回放状态失败,错误码:" + errorcode + " 设备信息：[ip:" + pojo.getIp() + " port:"
                            + pojo.getPort() + " channel:" + pojo.getChannel() + " stream:" + pojo.getStream()
                            + " statrtime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + "]");
                    json.put("message", "抓图失败！");
                }
            }
        }
        return json;
    }

    /**
     * @param pojo
     * @Title: timedLiveCapturePic
     * @Description: 定时直播截图
     * @return: JSONObject
     **/
    private JSONObject timedLiveCapturePic(CameraPojo pojo) {
        JSONObject json = new JSONObject(true);
        HCLoginSDK login = null;
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
                return json;
            }
        }
        PipedInputStream picInputStream = new PipedInputStream();
        PipedOutputStream picOutputStream = new PipedOutputStream();
        RealDataCallBack realDataCallBack = new RealDataCallBack(picOutputStream);
        // 实时预览
        NativeLong lRealPlayHandle = HCNetSDK.INSTANCE.NET_DVR_RealPlay_V40(login.getLUserID(), pojo.getPreviewinfo(),
                realDataCallBack, null);
        int errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
        if (lRealPlayHandle.longValue() < 0 && errorcode != 0) {
            json.put("message", "抓图失败！");
            logger.error("hcsdk 抓图失败，实时预览失败,错误码：" + errorcode + " 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort()
                    + " channel:" + pojo.getChannel() + " stream:" + pojo.getStream() + "]");
        } else {
            PlayBackCapture backCapture = new PlayBackCapture(picInputStream, picOutputStream);
            // 构建抓图路径
            String picturepath = config.getPicturepath() + UUID.randomUUID().toString() + ".jpg";
            // 保存抓图路径
            backCapture.setPicturePath(picturepath);
            try {
                backCapture.playBackCapture("");
                json.put("message", "抓图成功");
                json.put("picturepath", picturepath);
            } catch (IOException e) {
                json.put("message", "抓图失败");
                e.printStackTrace();
            } catch (InterruptedException e) {
                json.put("message", "抓图失败");
                e.printStackTrace();
            } finally {
                // 判断当前设备使用人数,如果人数>1,则-1;否则注销当前设备
                if (CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() > 1) {
                    CacheUtil.LOGINSDK.get(pojo.getIp()).setCount(CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() - 1);
                } else {
                    CacheUtil.LOGINSDK.get(pojo.getIp()).logout();
                    CacheUtil.LOGINSDK.remove(pojo.getIp());
                }
            }
        }
        return json;
    }

    /**
     * @param CameraPojo
     * @return Map<String, String>
     * @Title: playHistory
     * @Description:历史回放（hls切片）
     **/
    @PostMapping(value = "/playhistory")
    public Map<String, Object> playHistory(@RequestBody @Valid CameraPojo cameraPojo) {
        // 返回结果
        Map<String, Object> map = new LinkedHashMap<>();

        // 结束时间要大于开始时间
        if (cameraPojo.getStarttime().compareTo(cameraPojo.getEndtime()) >= 0) {
            map.put("msg", "endtime需要大于starttime");
            return map;
        }
        // 获取当前时间
        String opentime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime());
        String token = "";

        boolean isCreatM3u8 = true;// 是否需要创建m3u8文件
        // 生成token
        if (null != cameraPojo.getToken() && !"".equals(cameraPojo.getToken())) {
            token = cameraPojo.getToken();
            isCreatM3u8 = false;
            if (CacheUtil.PUSHHLSMAP.containsKey(token)) {
                // 中断token所属当前的切片任务
                CacheUtil.PUSHHLSMAP.get(token).setExitsign(true);
            }
        } else {
            token = UUID.randomUUID().toString();
        }
        String url = "";
        String Ip = Utils.IpConvert(cameraPojo.getIp());
        String playpath = "";
        HCLoginSDK login = null;// 设备注册信息

        // 播放地址
        playpath = "http://" + Utils.IpConvert(config.getPush_host()) + ":" + config.getM3u8_port() + "/hls/"
                + cameraPojo.getIp() + "/channel" + cameraPojo.getChannel() + "/" + token + "/" + "channel"
                + cameraPojo.getChannel() + ".m3u8";

        if (config.getHost_extra().equals("127.0.0.1")) {
            url = playpath;
        } else {
            url = "http://" + Utils.IpConvert(config.getHost_extra()) + ":" + config.getM3u8_port() + "/hls/"
                    + cameraPojo.getIp() + "/channel" + cameraPojo.getChannel() + "/" + token + "/" + "channel"
                    + cameraPojo.getChannel() + ".m3u8";
        }
        // 创建hls切片路径
        String m3u8path = config.getM3u8_path() + cameraPojo.getIp() + "\\\\channel" + cameraPojo.getChannel() + "\\\\"
                + token + "\\\\";
        // 设置属性
        cameraPojo.setOpentime(opentime);
        cameraPojo.setM3u8path(m3u8path);
        cameraPojo.setHls(playpath);
        cameraPojo.setUrl(url);
        cameraPojo.setToken(token);
        cameraPojo.setCount(1);

        // 将时间重新偏移到整点
        try {
            long startlongtime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(cameraPojo.getStarttime()).getTime();
            long endlongtime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(cameraPojo.getEndtime()).getTime();

            if (isCreatM3u8) {
                startlongtime = ((startlongtime / (1000 * 60))) * 1000 * 60;
            } else {
                startlongtime = ((startlongtime / (1000 * 60)) - 1) * 1000 * 60;
            }
            endlongtime = endlongtime % (1000 * 60) == 0 ? endlongtime : (endlongtime / (1000 * 60) + 1) * 1000 * 60;
            cameraPojo.setStarttime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(startlongtime)));
            cameraPojo.setEndtime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(endlongtime)));
        } catch (ParseException e) {
            e.printStackTrace();
        }

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
                logger.error("hcsdk 设备注册失败  ,错误码:" + login.getErrorCode() + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " username:" + cameraPojo.getUsername() + " password:"
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

        CacheUtil.STREATMAP.put(token, cameraPojo);

        // 如果是拖动则不再生产m3u8文件
        if (isCreatM3u8) {
            // 创建m3u8文件
            Utils.createm3u8(cameraPojo.getStarttime(), cameraPojo.getEndtime(), cameraPojo.getToken(),
                    cameraPojo.getChannel(), cameraPojo.getIp());
        }
        // 启动线程执行切片任务
        CameraThread.MyRunnable job = new CameraThread.MyRunnable(cameraPojo, login);
        CameraThread.MyRunnable.es.execute(job);
        JOBMAP.put(token, job);

        map.put("url", url);
        map.put("token", token);
        map.put("msg", "历史回放成功");
        map.put("code", 0);
        return map;
    }

    /**
     * @param token
     * @Title: closeHistory
     * @Description: 结束hls回放切片任务
     * @return: void
     **/
    @DeleteMapping(value = "/closeHistory/{token}")
    public void closeHistory(@PathVariable("token") @NotBlank(message = "token不能为空") String token) {
        if (JOBMAP.containsKey(token) && CacheUtil.STREATMAP.containsKey(token)
                && CacheUtil.PUSHHLSMAP.containsKey(token)) {
            CacheUtil.PUSHHLSMAP.get(token).setExitsign(true);
            CacheUtil.STREATMAP.remove(token);
            logger.info("结束hls切片任务成功！");
        }
    }


    /**
     * @description 云台控制
     * @author LianYanFei
     * @date 2023/12/1
     */
    @PostMapping(value = "/cloudCtrl")
    public Map<String, Object> cloudCtrl(@RequestBody @Valid CameraPojo cameraPojo) {
        // 返回结果
        Map<String, Object> map = new LinkedHashMap<>();
        if (StrUtil.equals(cameraPojo.getOp(), "UP")) {
            cloudControl(cameraPojo.getIp(), CloudCode.TILT_UP, CloudCode.SPEED_LV6, cameraPojo.getAction());
        } else if (StrUtil.equals(cameraPojo.getOp(), "DOWN")) {
            cloudControl(cameraPojo.getIp(), CloudCode.TILT_DOWN, CloudCode.SPEED_LV6, cameraPojo.getAction());
        } else if (StrUtil.equals(cameraPojo.getOp(), "LEFT")) {
            cloudControl(cameraPojo.getIp(), CloudCode.PAN_LEFT, CloudCode.SPEED_LV6, cameraPojo.getAction());
        } else if (StrUtil.equals(cameraPojo.getOp(), "RIGHT")) {
            cloudControl(cameraPojo.getIp(), CloudCode.PAN_RIGHT, CloudCode.SPEED_LV6, cameraPojo.getAction());
        } else if (StrUtil.equals(cameraPojo.getOp(), "ZOOM_IN")) {
            cloudControl(cameraPojo.getIp(), CloudCode.ZOOM_IN, CloudCode.SPEED_LV6, cameraPojo.getAction());
        } else if (StrUtil.equals(cameraPojo.getOp(), "ZOOM_OUT")) {
            cloudControl(cameraPojo.getIp(), CloudCode.ZOOM_OUT, CloudCode.SPEED_LV6, cameraPojo.getAction());
        }

        return map;
    }

    /**
     * 云台控制<br/>
     * 云台控制的方式为调用该方法摄像头便会一直执行该操作,直到该操作接收到"停止"指令及(iStop)参数
     *
     * @param ip       摄像头ip
     * @param iCommand 控制指令
     * @param iSpeed   云台运行速度
     * @param iStop    是否为停止操作
     * @return
     */
    public static boolean cloudControl(String ip, CloudCode iCommand, CloudCode iSpeed, Integer iStop) {
        logger.info("开始调用云台控制,ip:{},iCommand:{},iSpeed:{},istop:{}", ip, JSON.toJSONString(iCommand), JSON.toJSON(iSpeed), iStop);
        //获取ip对应摄像头的句柄
        NativeLong realHandle = CacheUtil.LOGINSDK.get(ip).getLUserID();
        logger.info("获取预览句柄：{}", JSON.toJSONString(realHandle));
        if (realHandle == null) {
            return false;
        }

        //获取预览句柄
        if (realHandle.intValue() < 0) {
            return false;
        }

        //判断是否为停止操作
        if (iSpeed.getKey() == 1) {
            boolean ptzControl = HCNetSDK.INSTANCE.NET_DVR_PTZControl(realHandle, iCommand.getKey(), iStop);
            logger.info("云台控制ptzControl返回结果集：{}", ptzControl);
        }


        boolean withSpeed = HCNetSDK.INSTANCE.NET_DVR_PTZControlWithSpeed(realHandle, iCommand.getKey(), iStop, iSpeed.getKey());
        logger.info("云台控制withSpeed返回结果集：{}", withSpeed);
        return withSpeed;
    }

    @PostMapping("/playStart")
    public boolean playStart(@RequestBody CameraPojo cameraPojo) {
        NativeLong hkPlaybackHandle = CacheUtil.HISTORYCALLBACKHANDLE.get(cameraPojo.getIp().concat("_HK_PLAYBACK_HANDLE"));
        boolean result = HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(hkPlaybackHandle, HCNetSDK.NET_DVR_PLAYFAST, 0, null);
        return result;
    }

    @PostMapping("/playSlow")
    public boolean playSlow(@RequestBody CameraPojo cameraPojo) {
        NativeLong hkPlaybackHandle = CacheUtil.HISTORYCALLBACKHANDLE.get(cameraPojo.getIp().concat("_HK_PLAYBACK_HANDLE"));
        boolean result = HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(hkPlaybackHandle, HCNetSDK.NET_DVR_PLAYSLOW, 0, null);
        return result;
    }

}
