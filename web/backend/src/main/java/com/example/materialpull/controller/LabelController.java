package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.dto.LabelDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.LabelScanRuleRepository;
import com.example.materialpull.repository.LabelTemplateRepository;
import com.example.materialpull.security.RequireRoles;
import com.example.materialpull.service.LabelService;
import com.example.materialpull.service.SiteQrLabelGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping("/labels")
@RequiredArgsConstructor
public class LabelController {
    private final LabelService labelService;
    private final SiteQrLabelGenerateService siteQrLabelGenerateService;
    private final LabelTemplateRepository templateRepository;
    private final LabelScanRuleRepository ruleRepository;
    private final com.example.materialpull.service.CodeRenderService codeRenderService;

    @GetMapping
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
    public ApiResponse<List<LabelEntity>> list() { return ApiResponse.ok(labelService.list()); }

    @PostMapping("/generate")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
    public ApiResponse<List<LabelEntity>> generate(@RequestBody LabelDtos.GenerateRequest req) { return ApiResponse.ok(siteQrLabelGenerateService.generate(req)); }

    @PostMapping("/site")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
    public ApiResponse<LabelEntity> createSite(@RequestBody LabelDtos.SiteLabelRequest req) { return ApiResponse.ok(labelService.createSiteLabel(req)); }

    @PostMapping("/factory")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
    public ApiResponse<LabelEntity> createFactory(@RequestBody LabelDtos.FactoryPullLabelRequest req) { return ApiResponse.ok(labelService.createFactoryPullLabel(req)); }

    @PostMapping("/universal")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE})
    public ApiResponse<LabelEntity> createUniversal(@RequestBody LabelDtos.UniversalLabelRequest req) { return ApiResponse.ok(labelService.createUniversalLabel(req)); }

    @GetMapping("/preview/{scanCode}")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
    public ApiResponse<LabelDtos.PreviewResponse> preview(@PathVariable String scanCode) { return ApiResponse.ok(labelService.preview(scanCode)); }

    @PostMapping("/{scanCode}/print")
    @RequireRoles({UserRole.ADMIN, UserRole.WAREHOUSE})
    public ApiResponse<LabelEntity> print(@PathVariable String scanCode, @RequestBody LabelDtos.PrintRequest req) { return ApiResponse.ok(labelService.print(scanCode, req)); }

    @PostMapping("/{scanCode}/void")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<LabelEntity> voidLabel(@PathVariable String scanCode) { return ApiResponse.ok(labelService.voidLabel(scanCode, OperatorResolver.currentOperator())); }

    @PostMapping("/code/render")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
    public ApiResponse<LabelDtos.CodeRenderResponse> renderCode(@RequestBody LabelDtos.CodeRenderRequest req) {
        return ApiResponse.ok(codeRenderService.render(req));
    }

    @GetMapping("/templates")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<LabelTemplateEntity>> templates() { return ApiResponse.ok(templateRepository.findAll(topPage()).getContent()); }

    @PostMapping("/templates")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<LabelTemplateEntity> saveTemplate(@RequestBody LabelTemplateEntity e) { return ApiResponse.ok(templateRepository.save(e)); }

    @DeleteMapping("/templates/{id}")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<Void> delTemplate(@PathVariable Long id) {
        templateRepository.findById(id).ifPresent(e -> { e.setEnabled(false); templateRepository.save(e); });
        return ApiResponse.ok(null);
    }

    @GetMapping("/scan-rules")
    @RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.VIEWER})
    public ApiResponse<List<LabelScanRuleEntity>> scanRules() { return ApiResponse.ok(ruleRepository.findAll(topPage()).getContent()); }

    @PostMapping("/scan-rules")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<LabelScanRuleEntity> saveScanRule(@RequestBody LabelScanRuleEntity e) { return ApiResponse.ok(ruleRepository.save(e)); }

    @DeleteMapping("/scan-rules/{id}")
    @RequireRoles({UserRole.ADMIN})
    public ApiResponse<Void> delScanRule(@PathVariable Long id) {
        ruleRepository.findById(id).ifPresent(e -> { e.setEnabled(false); ruleRepository.save(e); });
        return ApiResponse.ok(null);
    }

    private PageRequest topPage() { return PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id")); }
}
