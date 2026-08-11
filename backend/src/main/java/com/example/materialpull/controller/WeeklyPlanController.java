package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.entity.WeeklyPlanBatchEntity;
import com.example.materialpull.entity.WeeklyPlanRowEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.WeeklyPlanRowRepository;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.WeeklyPlanCalcService;
import com.example.materialpull.service.WeeklyPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/weekly-plan")
@RequiredArgsConstructor
public class WeeklyPlanController {
    private final WeeklyPlanService service;
    private final WeeklyPlanCalcService calcService;
    private final WeeklyPlanRowRepository rowRepository;

    /**
     * 上传周计划 Excel。planYear 必填(模板日期只有月/日)。
     * 首次上传若与已有计划冲突且未确认，返回 needConfirm=true 与冲突明细，不落库；
     * 前端确认后带 confirm=true 再次上传即覆盖。
     */
    @PostMapping("/import")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<WeeklyPlanService.ImportResult> importPlan(@RequestParam MultipartFile file,
                                                                  @RequestParam Integer planYear,
                                                                  @RequestParam(value = "confirm", required = false, defaultValue = "false") boolean confirm) {
        return ApiResponse.ok(service.importPlan(file, planYear, confirm, OperatorResolver.currentOperator()));
    }

    @GetMapping("/batches")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<WeeklyPlanBatchEntity>> batches() { return ApiResponse.ok(service.listBatches()); }

    /**
     * 下载周计划模板 xlsx。给了 startDate 则按起始日自动填七天日期(月/日)并据此校正标题 WKxx；
     * customer/factory/project/customerNo 会预填到各数据行的前四列。
     */
    @GetMapping("/template")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ResponseEntity<byte[]> template(@RequestParam(defaultValue = "1") int weekNo,
                                           @RequestParam(required = false) String factoryTag,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) String customer,
                                           @RequestParam(required = false) String factory,
                                           @RequestParam(required = false) String project,
                                           @RequestParam(required = false) String customerNo,
                                           @RequestParam(required = false) Integer blankRows) {
        byte[] body = service.buildTemplate(new WeeklyPlanService.TemplateOptions(
                weekNo, factoryTag, startDate, customer, factory, project, customerNo, blankRows));
        int titleWeek = startDate == null ? weekNo : startDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        String filename = "weekly-plan-template-WK" + titleWeek + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, 200);
    }

    @DeleteMapping("/batches/{batchNo}")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<Void> delete(@PathVariable String batchNo) { service.deleteBatch(batchNo); return ApiResponse.ok(null); }

    /** 分页查询某批次的计划行。 */
    @GetMapping("/rows")
    @RequireRoles({UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<Map<String, Object>> rows(@RequestParam String batchNo,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500),
                Sort.by(Sort.Direction.ASC, "productCode"));
        Page<WeeklyPlanRowEntity> result = rowRepository.findByBatchNo(batchNo, pageable);
        return ApiResponse.ok(Map.of("total", result.getTotalElements(), "rows", result.getContent()));
    }

    /**
     * 触发联动计算。apply=false 只预览(返回将写入的映射数量)，apply=true 覆盖料号映射 quantity。
     * BOM 缺组件、组件缺映射、单根用量未维护时，对应条目跳过并保持原值，其余可计算项照常刷新。
     */
    @PostMapping("/calculate")
    @RequireRoles({UserRole.PLANNER})
    public ApiResponse<WeeklyPlanCalcService.CalcResult> calculate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate,
            @RequestParam(value = "apply", required = false, defaultValue = "false") boolean apply) {
        return ApiResponse.ok(calcService.calculate(planDate, apply));
    }
}
