package com.wanda.epc.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.ToolKits;
import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.config.Config;
import com.wanda.epc.entity.DeviceInfo;
import com.wanda.epc.mapper.DeviceInfoMapper;
import com.wanda.epc.play.OperationControl;
import com.wanda.epc.play.RtmpPush;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.pojo.DahuaCameraDto;
import com.wanda.epc.pojo.DahuaDeviceDto;
import com.wanda.epc.pojo.DahuaPlayBackListDto;
import com.wanda.epc.request.DahuaControllingRequest;
import com.wanda.epc.request.DahuaPlaybackRequest;
import com.wanda.epc.sdk.DHInitSDK;
import com.wanda.epc.sdk.DHLoginSDK;
import com.wanda.epc.thread.CameraThread;
import com.wanda.epc.util.ResultUtil;
import com.wanda.epc.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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

    @Autowired
    private DeviceInfoMapper deviceInfoMapper;


    @Value("${ip}")
    private String ip;

    /*
     * 初始化sdk
     */
    @Autowired
    public DHInitSDK init;

    @Value("${projectIp}")
    private String projectIp;

    @PostConstruct
    public void init() {
        init.init();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * 存放任务线程
     */
    public static Map<String, CameraThread.MyRunnable> JOBMAP = new ConcurrentHashMap<>();

    @PostMapping("/preview")
    public ResultUtil<List<DahuaDeviceDto>> preview(@RequestBody List<String> eqIds) {
        if (!CollectionUtils.isEmpty(eqIds)) {
            List<DahuaDeviceDto> dahuaDeviceDtoList = eqIds.stream().map(eqId -> {
                LambdaQueryWrapper<DeviceInfo> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(DeviceInfo::getEqId, eqId);
                DeviceInfo deviceInfo = deviceInfoMapper.selectOne(queryWrapper);
                DahuaDeviceDto dahuaDeviceDto = new DahuaDeviceDto();
                if (Objects.nonNull(deviceInfo)) {
                    CameraPojo cameraPojo = new CameraPojo();
                    cameraPojo.setIp(deviceInfo.getIp());
                    cameraPojo.setPassword(deviceInfo.getPassword());
                    cameraPojo.setUsername(deviceInfo.getAccount());
                    cameraPojo.setPort(deviceInfo.getPort());
                    cameraPojo.setChannel(deviceInfo.getChannel());
                    cameraPojo.setToken(deviceInfo.getEqId());
                    cameraPojo.setToken(eqId);
                    Map<String, Object> result = openRealCamera(cameraPojo);
                    Integer code = (Integer) result.get("code");
                    if (code == 0) {
                        String url = (String) result.get("url");
                        dahuaDeviceDto.setUrl(url);
                        dahuaDeviceDto.setName(deviceInfo.getCameraName());
                        dahuaDeviceDto.setEqId(deviceInfo.getEqId());
                    }
                }
                return dahuaDeviceDto;
            }).collect(Collectors.toList());
            return ResultUtil.success(dahuaDeviceDtoList);
        }
        return null;
    }


    @PostMapping("/playback")
    public ResultUtil<DahuaDeviceDto> playback(@RequestBody DahuaPlaybackRequest request) {
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
            Map<String, Object> resultMap = openPlaybackCamera(cameraPojo);
            Integer code = (Integer) resultMap.get("code");
            if (code == 0) {
                String url = (String) resultMap.get("url");
                DahuaDeviceDto dahuaDeviceDto = new DahuaDeviceDto();
                dahuaDeviceDto.setUrl(url);
                dahuaDeviceDto.setName(deviceInfo.getCameraName());
                dahuaDeviceDto.setEqId(deviceInfo.getEqId());
                return ResultUtil.success(dahuaDeviceDto);
            }
        }
        return null;
    }


    /**
     * @throws InterruptedException
     * @Title: historyList
     * @Description: 获取指定时间内的视频列表
     * @return: void
     **/
    @PostMapping(value = "/hisList")
    public ResultUtil<List<DahuaPlayBackListDto>> historyList(@RequestBody DahuaPlaybackRequest request) throws
            InterruptedException {
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
            OperationControl operationControl = new OperationControl();
            List<DahuaPlayBackListDto> list = operationControl.findReplayDate(cameraPojo);
            return ResultUtil.success(list);
        }
        return null;
    }


    @PostMapping("/controlling")
    public ResultUtil<DahuaCameraDto> controlling(@RequestBody DahuaControllingRequest request) {
        LambdaQueryWrapper<DeviceInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceInfo::getEqId, request.getEqId());
        DeviceInfo deviceInfo = deviceInfoMapper.selectOne(queryWrapper);
        if (Objects.nonNull(deviceInfo)) {
            OperationControl operationControl = new OperationControl();
            DHLoginSDK dhLoginSDK = CacheUtil.LOGINSDK.get(deviceInfo.getIp());
            CameraPojo cameraPojo = new CameraPojo();
            cameraPojo.setIp(deviceInfo.getIp());
            cameraPojo.setUsername(deviceInfo.getAccount());
            cameraPojo.setUsername(cameraPojo.getUsername());
            cameraPojo.setPort(deviceInfo.getPort());
            cameraPojo.setChannel(deviceInfo.getChannel());
            NetSDKLib.LLong realPlayLogin = null;
            if (dhLoginSDK.getIsLogin()) {
                realPlayLogin = dhLoginSDK.getLUserID();
            } else {
                dhLoginSDK = new DHLoginSDK();
                dhLoginSDK.login(cameraPojo);
                if (dhLoginSDK.getIsLogin()) {
                    realPlayLogin = dhLoginSDK.getLUserID();
                }
            }
            if (StrUtil.equals(request.getCommand(), "UP")) {
                if (request.getAction() == 0) {
                    operationControl.ptzControlUpStart(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()), 0, 5);
                } else {
                    operationControl.ptzControlUpEnd(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()));
                }
            } else if (StrUtil.equals(request.getCommand(), "DOWN")) {
                if (request.getAction() == 0) {
                    operationControl.ptzControlDownStart(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()), 0, 5);
                } else {
                    operationControl.ptzControlDownEnd(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()));
                }
            } else if (StrUtil.equals(request.getCommand(), "LEFT")) {
                if (request.getAction() == 0) {
                    operationControl.ptzControlLeftStart(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()), 0, 5);
                } else {
                    operationControl.ptzControlLeftEnd(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()));
                }
            } else if (StrUtil.equals(request.getCommand(), "RIGHT")) {
                if (request.getAction() == 0) {
                    operationControl.ptzControlRightStart(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()), 0, 5);
                } else {
                    operationControl.ptzControlRightEnd(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()));
                }
            } else if (StrUtil.equals(request.getCommand(), "ZOOM_IN")) {
                if (request.getAction() == 0) {
                    operationControl.ptzControlFocusAddStart(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()), 5);
                } else {
                    operationControl.ptzControlFocusAddEnd(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()));
                }

            } else if (StrUtil.equals(request.getCommand(), "ZOOM_OUT")) {
                if (request.getAction() == 0) {
                    operationControl.ptzControlFocusDecStart(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()), 5);
                } else {
                    operationControl.ptzControlFocusDecEnd(realPlayLogin, Integer.parseInt(deviceInfo.getChannel()));
                }
            }
        }
        return ResultUtil.success();
    }

    /**
     * @return Map<String, String>
     * @Title: openCamera
     * @Description:开启视频流
     **/
    public Map<String, Object> openRealCamera(CameraPojo pojo) {
        DHLoginSDK login = null;// 设备注册信息
        // 返回结果
        Map<String, Object> map = new LinkedHashMap<>();
        // openStream返回结果
        logger.info("请求参数：{}", JSON.toJSONString(pojo));
        // 获取当前时间
        String opentime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime());
        String streamId = pojo.getIp() + pojo.getChannel();
        Boolean media = getMediaList(streamId);
        pojo.setToken(streamId);
        if (media) {
            logger.info("预览视频流已存在,直接获取");
            String hlsUrl = "http://".concat(projectIp).concat(":8081/live/").concat(streamId).concat("/hls.m3u8");
            map.put("url", hlsUrl);
            map.put("token", streamId);
            map.put("msg", "视频流已注册打开成功");
            map.put("code", 0);
        } else {
            String rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + ":" + config.getPush_port() + "/live/" + streamId;
            String url = "http://".concat(projectIp).concat(":8081/live/").concat(streamId).concat("/hls.m3u8");
            pojo.setOpentime(opentime);
            pojo.setRtmp(rtmp);
            pojo.setUrl(url);
            // 注册设备
            if (CacheUtil.LOGINSDK.containsKey(pojo.getIp())) {
                // 设备已经注册过
                // 使用人数+1
                CacheUtil.LOGINSDK.get(pojo.getIp()).setCount(CacheUtil.LOGINSDK.get(pojo.getIp()).getCount() + 1);
                login = CacheUtil.LOGINSDK.get(pojo.getIp());
                // 执行推流任务
                CameraThread.MyRunnable job = new CameraThread.MyRunnable(pojo, login);
                CameraThread.MyRunnable.es.execute(job);
                JOBMAP.put(streamId, job);
                map.put("pojo", pojo);
                map.put("code", 0);
                map.put("url", url);
                map.put("message", "打开视频流成功");
            } else {
                login = new DHLoginSDK();
                login.login(pojo);
                if (login.getIsLogin()) {
                    // 设备注册成功
                    logger.info("hcsdk 设备注册成功 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort()
                            + " username:" + pojo.getUsername() + " password:" + pojo.getPassword()
                            + " channel:" + pojo.getChannel() + "]");
                    // 使用人数+1
                    login.setCount(login.getCount() + 1);
                    CacheUtil.LOGINSDK.put(pojo.getIp(), login);
                } else {
                    logger.error("大华预览设备注册失败  ,错误码:" + login.getErrorCode() + " 设备信息：[ip:" + pojo.getIp()
                            + " port:" + pojo.getPort() + " username:" + pojo.getUsername() + " password:"
                            + pojo.getPassword() + " channel:" + pojo.getChannel() + " stream:" + "]");
                    map.put("pojo", pojo);
                    if (login.getErrorCode() == 7) {
                        map.put("message", "连接设备失败,设备不在线或网络原因引起的连接超时等");
                        map.put("errorcode", 7);
                    } else {
                        map.put("message", "其他错误");
                        map.put("errorcode", 6);
                    }
                    return map;
                }
                // 执行推流任务
                CameraThread.MyRunnable job = new CameraThread.MyRunnable(pojo, login);
                CameraThread.MyRunnable.es.execute(job);
                JOBMAP.put(streamId, job);
                map.put("pojo", pojo);
                map.put("code", 0);
                map.put("url", url);
                map.put("message", "打开视频流成功");
            }
        }
        return map;
    }

    public Map<String, Object> openPlaybackCamera(CameraPojo pojo) {
        DHLoginSDK login = null;// 设备注册信息
        Map<String, Object> map = new LinkedHashMap<>();
        String date = date(pojo.getStarttime());
        String opentime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime());
        String streamId = pojo.getIp().concat(date).concat("_").concat(pojo.getChannel());
        Boolean media = getMediaList(streamId);
        pojo.setToken(streamId);
        if (media) {
            logger.info("预览视频流已存在,直接获取");
            String hlsUrl = "http://".concat(projectIp).concat(":8081/history/").concat(streamId).concat("/hls.m3u8");
            map.put("url", hlsUrl);
            map.put("token", streamId);
            map.put("msg", "视频流已注册打开成功");
            map.put("code", 0);
        } else {
            String rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + ":" + config.getPush_port() + "/history/" + streamId;
            String url = "http://".concat(projectIp).concat(":8081/history/").concat(streamId).concat("/hls.m3u8");
            pojo.setOpentime(opentime);
            pojo.setRtmp(rtmp);
            pojo.setUrl(url);
            // 注册设备
            if (CacheUtil.PLAY_BACK_LOGIN_MODULE.containsKey(pojo.getIp())) {
                //关闭回放
                NetSDKLib.LLong lHistoryHandle = CacheUtil.PLAY_BACK_PLAY_HANDLE.get(pojo.getIp());
                Utils.sdkRelease(lHistoryHandle, 1);
                //注销该IP前期登录句柄
                login = CacheUtil.PLAY_BACK_LOGIN_MODULE.get(pojo.getIp());
                NetSDKLib.LLong lUserID = login.getLUserID();
                boolean result = login.logoutBack(lUserID);
                logger.info("回放注销登录句柄结果：{}", result);
                CacheUtil.PLAY_BACK_LOGIN_MODULE.remove(pojo.getIp());
            }
            login = new DHLoginSDK();
            login.login(pojo);
            if (login.getIsLogin()) {
                // 设备注册成功
                logger.info("大华回放设备注册成功 设备信息：[ip:" + pojo.getIp() + " port:" + pojo.getPort()
                        + " username:" + pojo.getUsername() + " password:" + pojo.getPassword()
                        + " channel:" + pojo.getChannel() + "]");
                // 使用人数+1
                login.setCount(login.getCount() + 1);
                CacheUtil.PLAY_BACK_LOGIN_MODULE.put(pojo.getIp(), login);
            } else {
                logger.error("大华回放设备注册失败  ,错误码:" + login.getErrorCode() + " 设备信息：[ip:" + pojo.getIp()
                        + " port:" + pojo.getPort() + " username:" + pojo.getUsername() + " password:"
                        + pojo.getPassword() + " channel:" + pojo.getChannel() + " stream:" + "]");
                map.put("pojo", pojo);
                if (login.getErrorCode() == 7) {
                    map.put("message", "连接设备失败,设备不在线或网络原因引起的连接超时等");
                    map.put("code", 7);
                } else {
                    map.put("message", "其他错误");
                    map.put("code", 6);
                }
                return map;
            }
            // 执行推流任务
            CameraThread.MyRunnable job = new CameraThread.MyRunnable(pojo, login);
            CameraThread.MyRunnable.es.execute(job);
            JOBMAP.put(streamId, job);
            map.put("pojo", pojo);
            map.put("code", 0);
            map.put("url", url);
            map.put("message", "打开视频流成功");
        }
        return map;
    }


    public String date(String timeString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date date = dateFormat.parse(timeString);
            long timeInMilliseconds = date.getTime();
            System.out.println("时间字符串转换为数字表示: " + timeInMilliseconds);
            return String.valueOf(timeInMilliseconds);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Boolean getMediaList(String streamId) {
        // 获取流媒体信息接口地址
        String httpUrl = "http://127.0.0.1:8081/index/api/getMediaList";
        boolean flag = true;
        Map<String, Object> params = new HashMap<>(16);
        params.put("stream", streamId);
        params.put("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc");
        try {
            String result = HttpUtil.get(httpUrl, params);
            logger.info("获取流媒体服务列表结果：{}", result);
            JSONObject jsonObject = JSONObject.parseObject(result);
            String data = String.valueOf(jsonObject.get("data"));
            // 未注册
            if ("null".equals(data)) {
                flag = false;
            }
            // 已经注册，获取实时观看人数
            else {
                flag = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * @param ip
     * @param port
     * @param username
     * @param password
     * @param channel
     * @param starttime
     * @param endtime
     * @param opentime
     * @return CameraPojo
     * @Title: openStream
     * @Description:注册设备，拼接rtmp命令
     **/
//    private Map<String, Object> openStream(String ip, String port, String username, String password, String
//            channel, String starttime, String endtime, String opentime, String token) {
//        Map<String, Object> map = new HashMap<>();
//        CameraPojo cameraPojo = new CameraPojo();
//        String url = "";
//        String Ip = Utils.IpConvert(ip);
//        String rtmp = "";
//        DHLoginSDK login = null;// 设备注册信息
//        if (null != starttime && !"".equals(starttime)) {// 回放
//            if (null != endtime && !"".equals(endtime)) {// 存在结束时间
//                cameraPojo.setStarttime(starttime);
//                cameraPojo.setEndtime(endtime);
//            } else {
//                cameraPojo.setStarttime(Utils.getStarttime(starttime));
//                cameraPojo.setEndtime(Utils.getEndtime(starttime));
//            }
//            rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + ":" + config.getPush_port() + "/history/"
//                    + token;
//            if (config.getHost_extra().equals("127.0.0.1")) {
//                url = "http://".concat(projectIp).concat(":8081/history/").concat(token).concat("/hls.m3u8");
//            } else {
//                url = "http://".concat(projectIp).concat(":8081/history/").concat(token).concat("/hls.m3u8");
//            }
//        } else {// 直播
//            rtmp = "rtmp://" + Utils.IpConvert(config.getPush_host()) + ":" + config.getPush_port() + "/live/" + token;
//            if (config.getHost_extra().equals("127.0.0.1")) {
//                url = "http://".concat(projectIp).concat(":8081/live/").concat(token).concat("/hls.m3u8");
//            } else {
//                url = "http://".concat(projectIp).concat(":8081/live/").concat(token).concat("/hls.m3u8");
//            }
//        }
//        cameraPojo.setUsername(username);
//        cameraPojo.setPassword(password);
//        cameraPojo.setIp(Ip);
//        cameraPojo.setPort(port);
//        cameraPojo.setChannel(channel);
//        cameraPojo.setRtmp(rtmp);
//        cameraPojo.setUrl(url);
//        cameraPojo.setOpentime(opentime);
//        cameraPojo.setCount(1);
//        cameraPojo.setToken(token);
//        try {
//            // 注册设备
//            if (CacheUtil.LOGINSDK.containsKey(Ip)) {
//                // 设备已经注册过
//                // 使用人数+1
//                CacheUtil.LOGINSDK.get(Ip).setCount(CacheUtil.LOGINSDK.get(Ip).getCount() + 1);
//                login = CacheUtil.LOGINSDK.get(Ip);
//            } else {
//                login = new DHLoginSDK();
//                login.login(cameraPojo);
//                if (login.getIsLogin()) {
//                    // 设备注册成功
//                    logger.info("hcsdk 设备注册成功 设备信息：[ip:" + cameraPojo.getIp() + " port:" + cameraPojo.getPort()
//                            + " username:" + cameraPojo.getUsername() + " password:" + cameraPojo.getPassword()
//                            + " channel:" + cameraPojo.getChannel() + "]");
//                    // 使用人数+1
//                    login.setCount(login.getCount() + 1);
//                    CacheUtil.LOGINSDK.put(Ip, login);
//                } else {
//                    logger.error("hcsdk 设备注册失败  ,错误码:" + login.getErrorCode() + " 设备信息：[ip:" + cameraPojo.getIp()
//                            + " port:" + cameraPojo.getPort() + " username:" + cameraPojo.getUsername() + " password:"
//                            + cameraPojo.getPassword() + " channel:" + cameraPojo.getChannel() + " stream:" + "]");
//                    map.put("pojo", cameraPojo);
//                    if (login.getErrorCode() == 7) {
//                        map.put("message", "连接设备失败,设备不在线或网络原因引起的连接超时等");
//                        map.put("errorcode", 7);
//                    } else {
//                        map.put("message", "其他错误");
//                        map.put("errorcode", 6);
//                    }
//                    return map;
//                }
//            }
//            // 执行推流任务
//            CameraThread.MyRunnable job = new CameraThread.MyRunnable(cameraPojo, login);
//            CameraThread.MyRunnable.es.execute(job);
//            JOBMAP.put(token, job);
//
//            map.put("pojo", cameraPojo);
//            map.put("errorcode", login.getErrorCode());
//            map.put("message", "打开视频流成功");
//        } catch (Exception e) {
//            logger.error("与推流IP:" + config.getPush_host() + " 端口: " + config.getPush_port() + " 建立连接失败,请检查zlm服务");
//            map.put("pojo", cameraPojo);
//            map.put("errorcode", 8);
//            map.put("message",
//                    "与推流IP:" + config.getPush_host() + " 端口: " + config.getPush_port() + " 建立连接失败,请检查zlm服务");
//        }
//        return map;
//    }

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
                                + CacheUtil.STREATMAP.get(token).getStarttime() + " statrtime:"
                                + CacheUtil.STREATMAP.get(token).getEndtime() + " endtime:"
                                + CacheUtil.STREATMAP.get(token).getEndtime() + " url:"
                                + CacheUtil.STREATMAP.get(token).getUrl() + "]");
                    }
                }
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
                            + cameraPojo.getChannel() + " starttime:"
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
                        + CacheUtil.STREATMAP.get(token).getStarttime() + " starttime:"
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
                        + CacheUtil.STREATMAP.get(token).getStarttime() + " starttime:"
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
                + pojo.getUsername() + " password:" + pojo.getPassword() + " channel:" + pojo.getChannel() + " starttime:" + pojo.getStarttime() + " endtime:" + pojo.getEndtime() + "]");
        return json;
    }


}
