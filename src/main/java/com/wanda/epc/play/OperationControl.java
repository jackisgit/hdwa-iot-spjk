package com.wanda.epc.play;

import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.ToolKits;
import com.sun.jna.ptr.IntByReference;
import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.pojo.DahuaPlayBackListDto;
import com.wanda.epc.sdk.DHLoginSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot_epc_spdj
 * @description 操作控制类
 * @date 2023/12/23 19:56:18
 */
public class OperationControl {

    private final static Logger logger = LoggerFactory.getLogger(OperationControl.class);


    /**
     * 查询录像文件
     *
     * @return
     */
    public List<DahuaPlayBackListDto> findReplayDate(CameraPojo pojo) {
        List<DahuaPlayBackListDto> list = new ArrayList<>();
        String[] begin = pojo.getStarttime().split(" ");
        NetSDKLib.NET_TIME start_time = handleDate(begin[0], begin[1]);
        String[] end = pojo.getEndtime().split(" ");
        NetSDKLib.NET_TIME end_time = handleDate(end[0], end[1]);

        int nFileCount = 50; //每次查询的最大文件个数
        NetSDKLib.NET_RECORDFILE_INFO[] numberFile = (NetSDKLib.NET_RECORDFILE_INFO[]) new NetSDKLib.NET_RECORDFILE_INFO().toArray(nFileCount);
        int maxlen = nFileCount * numberFile[0].size();
        IntByReference outFileCoutReference = new IntByReference(0);

        DHLoginSDK loginSDK = new DHLoginSDK();
        loginSDK.login(pojo);
        if (loginSDK.getIsLogin()) {
            boolean cRet = NetSDKLib.NETSDK_INSTANCE.CLIENT_QueryRecordFile(loginSDK.getLUserID(), Integer.parseInt(pojo.getChannel()), 0, start_time, end_time, null, numberFile, maxlen, outFileCoutReference, 5000, false);
            if (cRet) {
                CacheUtil.FIND_FILE_LOGIN_MODULE.put(pojo.getIp().concat("_findFileLogin"), loginSDK.getLUserID());
                logger.info("QueryRecordFile  Succeed! " + "查询到的视频个数：{}, 码流类型：{}", outFileCoutReference.getValue(), numberFile[0].bRecType);
                for (int i = 0; i < outFileCoutReference.getValue(); i++) {
                    DahuaPlayBackListDto dahuaPlayBackListDto = new DahuaPlayBackListDto();
                    NetSDKLib.NET_TIME starttime = numberFile[i].starttime;
                    NetSDKLib.NET_TIME endtime = numberFile[i].endtime;
                    dahuaPlayBackListDto.setBeginTime(operationTime(starttime.dwYear + "-" + starttime.dwMonth + "-" + starttime.dwDay + " " + starttime.dwHour + ":" + starttime.dwMinute + ":" + starttime.dwSecond));
                    dahuaPlayBackListDto.setEndTime(operationTime(endtime.dwYear + "-" + endtime.dwMonth + "-" + endtime.dwDay + " " + endtime.dwHour + ":" + endtime.dwMinute + ":" + endtime.dwSecond));
                    dahuaPlayBackListDto.setSize(numberFile[i].size);
                    list.add(dahuaPlayBackListDto);
                }
            } else {
                logger.error("查询文件失败{}", ToolKits.getErrorCode());
            }
        }
        return list;
    }

    public String operationTime(String irregularDate) {
        String desiredFormat = "yyyy-MM-dd HH:mm:ss"; // 目标格式

        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat outputFormat = new SimpleDateFormat(desiredFormat);
        try {
            Date date = inputFormat.parse(irregularDate);
            String formattedDate = outputFormat.format(date);
           return formattedDate;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * @description 格式化时间
     * @author LianYanFei
     * @date 2023/12/20
     */
    public NetSDKLib.NET_TIME handleDate(String odate, String otime) {
        NetSDKLib.NET_TIME net_time = new NetSDKLib.NET_TIME();
        String[] odates = odate.split("-");
        int year = Integer.parseInt(odates[0]);
        int month = Integer.parseInt(odates[1]);
        int day = Integer.parseInt(odates[2]);

        String[] otbegins = otime.split(":");
        net_time.dwYear = year;
        net_time.dwMonth = month;
        net_time.dwDay = day;
        net_time.dwHour = Integer.parseInt(otbegins[0]);
        net_time.dwMinute = Integer.parseInt(otbegins[1]);
        net_time.dwSecond = Integer.parseInt(otbegins[2]);

        return net_time;
    }


    /********************************************************************************
     * 									云台功能                  							*
     ********************************************************************************/

    /**
     * 变焦+
     */
    public boolean ptzControlFocusAddStart(NetSDKLib.LLong m_hLoginHandle, int nChannelID, int lParam2) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_FOCUS_ADD_CONTROL,
                0, lParam2, 0, 0);
    }

    public boolean ptzControlFocusAddEnd(NetSDKLib.LLong m_hLoginHandle, int nChannelID) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_FOCUS_ADD_CONTROL,
                0, 0, 0, 1);
    }

    /**
     * 变焦-
     */
    public  boolean ptzControlFocusDecStart(NetSDKLib.LLong m_hLoginHandle, int nChannelID, int lParam2) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_FOCUS_DEC_CONTROL,
                0, lParam2, 0, 0);
    }

    public  boolean ptzControlFocusDecEnd(NetSDKLib.LLong m_hLoginHandle, int nChannelID) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_FOCUS_DEC_CONTROL,
                0, 0, 0, 1);
    }

    /**
     * 向上
     */
    public  boolean ptzControlUpStart(NetSDKLib.LLong m_hLoginHandle, int nChannelID, int lParam1, int lParam2) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_UP_CONTROL,
                lParam1, lParam2, 0, 0);
    }

    public  boolean ptzControlUpEnd(NetSDKLib.LLong m_hLoginHandle, int nChannelID) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_UP_CONTROL,
                0, 0, 0, 1);
    }

    /**
     * 向下
     */
    public  boolean ptzControlDownStart(NetSDKLib.LLong m_hLoginHandle, int nChannelID, int lParam1, int lParam2) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_DOWN_CONTROL,
                lParam1, lParam2, 0, 0);
    }

    public  boolean ptzControlDownEnd(NetSDKLib.LLong m_hLoginHandle, int nChannelID) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_DOWN_CONTROL,
                0, 0, 0, 1);
    }

    /**
     * 向左
     */
    public  boolean ptzControlLeftStart(NetSDKLib.LLong m_hLoginHandle, int nChannelID, int lParam1, int lParam2) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_LEFT_CONTROL,
                lParam1, lParam2, 0, 0);
    }

    public  boolean ptzControlLeftEnd(NetSDKLib.LLong m_hLoginHandle, int nChannelID) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_LEFT_CONTROL,
                0, 0, 0, 1);
    }

    /**
     * 向右
     */
    public  boolean ptzControlRightStart(NetSDKLib.LLong m_hLoginHandle, int nChannelID, int lParam1, int lParam2) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_RIGHT_CONTROL,
                lParam1, lParam2, 0, 0);
    }

    public  boolean ptzControlRightEnd(NetSDKLib.LLong m_hLoginHandle, int nChannelID) {
        return NetSDKLib.NETSDK_INSTANCE.CLIENT_DHPTZControlEx(m_hLoginHandle, nChannelID,
                NetSDKLib.NET_PTZ_ControlType.NET_PTZ_RIGHT_CONTROL,
                0, 0, 0, 1);
    }
}
