# Material Pull Android APP

本目录是通用Android手机扫码APP，不包含 `.idea`、`.kotlin`、`local.properties`、旧APK及构建缓存。扫码使用手机后置摄像头和屏幕预览框，不调用M71硬件扫码头或其广播接口。

## 网络模式

- 公司内网：默认访问 `http://10.243.129.131/api`，直接调用 `/scan/*`；
- 手机外网：默认访问 `https://tthih.top`，通过阿里云指令队列、WinServer和公司Linux Worker间接调用内网 `/api/scan/*`。

旧版在外网测试时会把地址强制变成 `https://tthih.top/api`，然后请求 `/api/health/ready`，因此返回404。新版外网测试直接请求 `https://tthih.top/health`。

## 首次外网登记

1. 打开APP右上角“设置”；
2. 选择“手机外网”；
3. 外网中继地址保持 `https://tthih.top`；
4. 填写管理员从阿里云取得的 `RELAY_APP_ENROLLMENT_KEY`；
5. 填写设备编号和员工工号，点击“保存”；
6. 显示“外网模式已就绪”后，登记码输入框会被清空，APP只保存云端签发的设备Token；
7. 点击“测试”，应显示“外网连接正常”。

阿里云查看登记码的命令（不要把输出发到聊天中）：

```bash
sudo awk -F= '$1=="RELAY_APP_ENROLLMENT_KEY" {print $2}' /etc/aliyun-api-relay/api.env
```

该文件位于阿里云服务器，不在公司Linux。每台手机首次登记成功后会保存独立设备Token，之后不需要重复填写登记码。

## 手机摄像头扫码

- 点击首页“物料申请”打开扫码弹窗；
- 首次使用时允许摄像头权限；
- APP固定优先使用手机后置摄像头，并在弹窗中显示实时预览框；
- 将一维条码或二维码完整放入预览框后自动识别并关闭摄像头；
- 识别结果继续进入原有物料预览、数量确认和内网/外网提交流程；
- `M71_USE` 保持硬件扫码头实现不变，两套工程不共享扫码入口。

## 编译

Android Studio打开本目录，或执行：

```bash
cd /opt/apps/material-pull/androidAPP/Android_USE
./gradlew assembleDebug
```

调试APK输出到 `app/build/outputs/apk/debug/app-debug.apk`。

2026-08-21 已在另一台安装有Android SDK的电脑上完成编译。公司Linux只保留规范化源码，不保留IDE缓存、Gradle缓存和重复源码压缩包。

`M71_USE` 是M71实体扫码枪专用工程，继续独立保留和维护；`Android_USE` 用于通用Android手机及外网扫码。两套工程用途不同，不得互相删除或覆盖。
