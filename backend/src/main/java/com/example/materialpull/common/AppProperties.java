package com.example.materialpull.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.factory")
public class AppProperties {
    /** 本地业务锁等待毫秒数，防止重复扫码/重复点按钮造成状态乱跳。 */
    private long lockWaitMs = 5000;
    /** 任务超过多久未更新被视为卡住。 */
    private long stuckTaskMinutes = 180;
    /** 自动恢复最多一次处理多少条，避免定时任务撑爆内存。 */
    private int recoverBatchSize = 100;
    /** 同一标签已存在未完成任务时是否直接返回原任务。 */
    private boolean returnExistingTaskOnDuplicateScan = true;
    /** 库存不足时是否禁止进入拣料。 */
    private boolean strictInventory = true;
    /** 推送失败时是否只落日志不影响主流程。 */
    private boolean ignorePushFailure = true;
    /** 默认打印类型，可由部署环境覆盖。 */
    private String defaultPrintType = "OUTBOUND_LABEL";
    /** 默认打印机名。生产环境必须在页面或环境变量中配置真实打印机名称。 */
    private String defaultPrinterName = "";
    /** 真实打印服务提交地址；为空时禁止生成打印作业，避免任务被误认为已打印。 */
    private String printSubmitUrl = "";
    /** 真实打印服务取消地址；为空时已下发的打印作业禁止静默取消。 */
    private String printCancelUrl = "";
    /** 打印服务认证请求头名称。 */
    private String printAuthHeader = "X-Api-Key";
    /** 打印服务认证密钥。 */
    private String printApiKey = "";
    /** 真实 AGV 调度服务提交地址；为空时禁止下发 AGV 作业。 */
    private String agvDispatchUrl = "";
    /** 真实 AGV 调度服务取消地址；为空时已下发的 AGV 作业禁止静默取消。 */
    private String agvCancelUrl = "";
    /** AGV 服务认证请求头名称。 */
    private String agvAuthHeader = "X-Api-Key";
    /** AGV 服务认证密钥。 */
    private String agvApiKey = "";
    /** 外部设备接口调用超时时间。 */
    private long externalCallTimeoutMs = 5000;
}
