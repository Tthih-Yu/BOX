package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.PrintJobEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.PrintJobService;
import com.example.materialpull.common.OperatorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/print-jobs")
@RequiredArgsConstructor
@RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE, UserRole.PLANNER, UserRole.VIEWER})
public class PrintJobController {
    private final PrintJobService service;
    private final com.example.materialpull.service.AutoPrintService autoPrintService;
    private final com.example.materialpull.service.TaskService taskService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String channel,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        if (page == null && size == null && (channel == null || channel.isBlank())) {
            return ApiResponse.ok(service.list(status));
        }
        return ApiResponse.ok(service.page(status, channel, page == null ? 0 : page, size == null ? 20 : size));
    }

    @PostMapping("/auto-print/run")
    @RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE})
    public ApiResponse<Integer> runAutoPrint() {
        return ApiResponse.ok(autoPrintService.runNow());
    }

    @GetMapping("/auto-print/area-schedules")
    @RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE})
    public ApiResponse<java.util.Map<String, Object>> areaSchedules() {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("json", autoPrintService.areaSchedulesJson());
        body.put("areas", autoPrintService.knownDeliveryAreas());
        return ApiResponse.ok(body);
    }

    @PostMapping("/auto-print/area-schedules")
    @RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE})
    public ApiResponse<Void> saveAreaSchedules(@RequestBody java.util.Map<String, Object> req) {
        Object json = req == null ? null : req.get("json");
        autoPrintService.saveAreaSchedules(json == null ? "" : String.valueOf(json));
        return ApiResponse.ok(null);
    }

    @PostMapping
    @RequireRoles({UserRole.WAREHOUSE})
    public ApiResponse<PrintJobEntity> create(@RequestBody FactoryDtos.PrintRequest req) {
        PrintJobEntity job = service.createForTask(req);
        // 打印出来即视为完成：浏览器打印会立即置为 PRINTED，此时自动闭环任务；
        // 提交本地代理队列的作业(RENDERED/SENT)尚未真正出纸，待代理回调 PRINTED 再闭环。
        if (job != null && job.getTaskNo() != null
                && job.getStatus() == com.example.materialpull.enums.PrintJobStatus.PRINTED) {
            taskService.completeByPrint(job.getTaskNo(), OperatorResolver.currentOperator());
        }
        return ApiResponse.ok(job);
    }

    @PostMapping("/callback")
    @RequireRoles({UserRole.SYSTEM})
    public ApiResponse<PrintJobEntity> callback(@RequestBody FactoryDtos.PrintCallbackRequest req) {
        PrintJobEntity job = service.callback(req);
        // 本地代理回传打印成功时，同样把任务自动闭环为已完成。
        if (job != null && job.getTaskNo() != null
                && job.getStatus() == com.example.materialpull.enums.PrintJobStatus.PRINTED) {
            taskService.completeByPrint(job.getTaskNo(), OperatorResolver.systemOperator());
        }
        return ApiResponse.ok(job);
    }

    /**
     * 本地打印代理轮询领取待打印作业（拉模式）。返回作业含 ZPL 内容，代理据此 RAW 打印后调 /callback 回传结果。
     * 用 X-Api-Key 鉴权（SYSTEM 角色），供无登录态的 Windows 代理调用。
     */
    @GetMapping("/next")
    @RequireRoles({UserRole.SYSTEM, UserRole.WAREHOUSE})
    public ApiResponse<List<PrintJobEntity>> next(@RequestParam(required = false) String printerName,
                                                  @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ApiResponse.ok(service.claimForAgent(printerName, limit == null ? 10 : limit));
    }
}
