package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.enums.*;
import com.example.materialpull.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/meta")
@RequireRoles({UserRole.ADMIN, UserRole.PLANNER, UserRole.WAREHOUSE, UserRole.LINE, UserRole.VIEWER})
public class MetaController {
    @GetMapping("/business")
    public ApiResponse<FactoryDtos.BusinessMeta> business() {
        FactoryDtos.BusinessMeta meta = new FactoryDtos.BusinessMeta();
        Arrays.stream(UserRole.values()).forEach(x -> meta.userRoles.add(new FactoryDtos.MetaOption(x.label, x.name())));
        Arrays.stream(TaskStatus.values()).forEach(x -> meta.taskStatuses.add(new FactoryDtos.MetaOption(x.label, x.name())));
        Arrays.stream(BoxPoolStatus.values()).forEach(x -> meta.boxPoolStatuses.add(new FactoryDtos.MetaOption(x.label, x.name())));
        Arrays.stream(PlanStatus.values()).forEach(x -> meta.planStatuses.add(new FactoryDtos.MetaOption(x.label, x.name())));
        Arrays.stream(DemandStatus.values()).forEach(x -> meta.demandStatuses.add(new FactoryDtos.MetaOption(x.label, x.name())));
        Arrays.stream(PurchaseStatus.values()).forEach(x -> meta.purchaseStatuses.add(new FactoryDtos.MetaOption(x.label, x.name())));
        meta.labelUsageTypes.add(new FactoryDtos.MetaOption("使用标签", "USE"));
        meta.labelUsageTypes.add(new FactoryDtos.MetaOption("备用标签", "SPARE"));
        meta.labelUsageTypes.add(new FactoryDtos.MetaOption("PPC单盒标签", "PLAN_SINGLE_BOX"));
        meta.deliveryModes.add(new FactoryDtos.MetaOption("正常配送", "NORMAL"));
        meta.deliveryModes.add(new FactoryDtos.MetaOption("紧急配送", "URGENT"));
        meta.exceptionTypes.add(new FactoryDtos.MetaOption("缺料", "SHORTAGE"));
        meta.exceptionTypes.add(new FactoryDtos.MetaOption("错料", "WRONG_MATERIAL"));
        meta.exceptionTypes.add(new FactoryDtos.MetaOption("标签异常", "LABEL_ERROR"));
        meta.exceptionTypes.add(new FactoryDtos.MetaOption("现场异常", "SITE_EXCEPTION"));
        meta.boxSides.add(new FactoryDtos.MetaOption("A", "A"));
        meta.boxSides.add(new FactoryDtos.MetaOption("B", "B"));
        meta.agvCallbackStatuses.add(new FactoryDtos.MetaOption("接收", "ACCEPTED"));
        meta.agvCallbackStatuses.add(new FactoryDtos.MetaOption("运输", "IN_TRANSIT"));
        meta.agvCallbackStatuses.add(new FactoryDtos.MetaOption("到达", "ARRIVED"));
        meta.agvCallbackStatuses.add(new FactoryDtos.MetaOption("失败", "FAILED"));
        return ApiResponse.ok(meta);
    }
}
