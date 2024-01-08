## 视频监控对接厂家SDK

#### 海康威视厂商对接分支说明
**1.spjk_hikvision_sdk:适配海康威视NVR,DVR设备,视频编码为h.264,h.265的存储设备。**
**2.spjk_hikvision_cvr_sdk:适配海康威视CVR设备,视频编码为h.264,h.265的存储设备。**

#### 大华厂商对接分支说明
**1.spjk_dahua_sdk:适配大华股份设备,视频编码为h.264,h.265的存储设备。**

## 所需核心maven依赖
**javacv版本、ffmpeg版本。**
```java
        <dependency>
            <groupId>org.bytedeco</groupId>
            <artifactId>javacv</artifactId>
            <version>1.5.9</version>
        </dependency>
        <dependency>
            <groupId>org.bytedeco</groupId>
            <artifactId>ffmpeg</artifactId>
            <version>6.0-1.5.9</version>
            <classifier>windows-x86_64-gpl</classifier>
        </dependency>
```
**海康威视dll文件加载所需依赖版本(将master根目录下的/hikvision/lib文件拷贝至所在开发分支根目录即可)**
```java
        <dependency>
            <groupId>com.sun</groupId>  <!--自定义 -->
            <artifactId>jna</artifactId>    <!--自定义 -->
            <version>3.3.0</version> <!--自定义 -->
            <scope>system</scope> <!--system，类似provided，需要显式提供依赖的jar以后，Maven就不会在Repository中查找它 -->
            <systemPath>${basedir}/lib/jna-3.3.0.jar</systemPath> <!--项目根目录下的lib文件夹下 -->
        </dependency>

        <dependency>
            <groupId>com.sencha.gxt</groupId>  <!--自定义 -->
            <artifactId>examples</artifactId>    <!--自定义 -->
            <version>3.0.9</version> <!--自定义 -->
            <scope>system</scope> <!--system，类似provided，需要显式提供依赖的jar以后，Maven就不会在Repository中查找它 -->
            <systemPath>${basedir}/lib/examples-3.0.9 b0.jar</systemPath> <!--项目根目录下的lib文件夹下 -->
        </dependency>

```

**大华dll文件加载所需依赖版本(将master根目录下的lib文件拷贝至所在开发分支根目录即可)**
```java
         <!--大华股份-->
        <dependency>
            <groupId>com.dahuatech.icc</groupId>
            <artifactId>java-sdk-oauth</artifactId>
            <version>1.0.9</version>
        </dependency>
        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna</artifactId>
            <version>5.4.0</version>
        </dependency>

```
**流媒体服务器为SRS http://www.ossrs.net/lts/zh-cn/**
- 1. SRS-Windows-x86_64-5.0-b7-setup.exe
- 2. 说明：SRS的作用是将javacv推送的流封装成各种为FLV,M3U8等格式，并生成对应的播放地址，SRS是本项目的核心(其中M3U8格式可以使用播放器自带的播放速度控件进行倍速播放，无需后段开发进行接口提供),SRS使用master分支的文件[SRS-Windows-x86_64-5.0-b7-setup.exe](SRS-Windows-x86_64-5.0-b7-setup.exe)即可。

**备注:**
- 1.上述分支可适配海康及大华多种存储设备类型可以满足90%以上硬件设备。
- 2.流媒体服务器为exe文件需要执行安装,安装成功后在桌面会生成快捷方式,使用快捷方式打开开启后不能关闭CMD运行窗口,访问地址为http://localhost:8080/。
- 3.海康威视服务后端服务部署时需要将/hikvision/下的sdk目录拷贝到与jar包同级别目录下,要不无法加载到该dll文件。
- 4.大华股份后端服务部署时需要将/dahua/下的[win64](dahua%2Fwin64)目录,[dynamic-lib-load.xml](dahua%2Fdynamic-lib-load.xml)文件拷贝到所在分支项目的resources目录下。
- 5.前端播放器使用dplayer进行播发 https://dplayer.diygod.dev/zh/guide.html#api。
