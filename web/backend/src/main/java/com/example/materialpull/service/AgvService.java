package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AgvService {
    private final AgvJobRepository agvJobRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final AuditService auditService;
    private final RealtimePushService pushService;
    private final AppProperties properties;
    private final ExternalHttpClient externalHttpClient;

    public List<AgvJobEntity> list(String status) {
        if (status == null || status.isBlank()) return agvJobRepository.findTop1000ByOrderByCreatedAtDesc();
        AgvJobStatus s;
        try {
            s = AgvJobStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知AGV状态：" + status);
        }
        return agvJobRepository.findTop1000ByStatusOrderByCreatedAtDesc(s);
    }

    @Transactional
    public AgvJobEntity dispatch(FactoryDtos.AgvDispatchRequest req) {
        FactoryDtos.AgvDispatchRequest request = req == null ? new FactoryDtos.AgvDispatchRequest() : req;
        if (request.taskNo == null || request.taskNo.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "任务号不能为空");
        ReplenishmentTaskEntity task = taskRepository.findByTaskNo(request.taskNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在：" + request.taskNo));
        return dispatchForTask(task, request.containerNo, request.fromLocation, request.toLocation, request.jobType, OperatorResolver.currentOperator());
    }

    @Transactional
    public AgvJobEntity dispatchForTask(ReplenishmentTaskEntity task, String containerNo, String from, String to, String jobType, String operator) {
        if (task == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "任务不能为空");
        AgvJobEntity job = new AgvJobEntity();
        job.setAgvJobNo(IdGenerator.id("AGV"));
        job.setTaskNo(task.getTaskNo());
        job.setJobType(firstNonBlank(jobType, "DELIVER_FULL_BOX"));
        job.setContainerNo(containerNo);
        job.setFromLocation(firstNonBlank(from, task.getWarehouseAddress(), task.getWarehouseLocation(), task.getWarehouseCode(), "仓库"));
        job.setToLocation(firstNonBlank(to, task.getSendStationAddress(), task.getDeliveryAddress(), task.getStationCode()));
        job.setPriority(task.getPriority() == null ? PriorityLevel.NORMAL : task.getPriority());
        job.setRequestPayload(payload(job));
        String responsePayload = externalHttpClient.postJson(
                properties.getAgvDispatchUrl(),
                job.getRequestPayload(),
                properties.getAgvAuthHeader(),
                properties.getAgvApiKey(),
                properties.getExternalCallTimeoutMs(),
                "AGV调度"
        );
        job.setStatus(AgvJobStatus.SENT);
        job.setSentAt(LocalDateTime.now());
        job.setResponsePayload(responsePayload);
        job = agvJobRepository.save(job);
        bindAgvJobNo(task, job);
        task.setAgvDispatched(true);
        taskRepository.save(task);
        auditService.iface("AGV_DISPATCH", "OUT", job.getRequestPayload(), job.getResponsePayload(), true, "AGV任务已下发到真实调度系统：" + job.getAgvJobNo());
        auditService.task(task.getTaskNo(), "AGV_DISPATCH", task.getStatus().name(), task.getStatus().name(), operator, "AGV任务=" + job.getAgvJobNo());
        pushService.publish("agvJobs", job);
        return job;
    }


    private void bindAgvJobNo(ReplenishmentTaskEntity task, AgvJobEntity job) {
        if (job.getJobType() != null && job.getJobType().toUpperCase(Locale.ROOT).contains("RETURN")) {
            task.setReturnAgvJobNo(job.getAgvJobNo());
            return;
        }
        task.setDeliveryAgvJobNo(job.getAgvJobNo());
        task.setAgvJobNo(job.getAgvJobNo());
    }

    @Transactional
    public void cancelByTaskNo(String taskNo, String operator) {
        if (taskNo == null || taskNo.isBlank()) return;
        for (AgvJobEntity job : agvJobRepository.findByTaskNo(taskNo)) {
            if (job.getStatus() == AgvJobStatus.ARRIVED || job.getStatus() == AgvJobStatus.CONFIRMED || job.getStatus() == AgvJobStatus.CANCELLED) continue;
            String cancelPayload = "{\"agvJobNo\":\"" + esc(job.getAgvJobNo()) + "\",\"externalJobNo\":\"" + esc(job.getExternalJobNo()) + "\",\"taskNo\":\"" + esc(job.getTaskNo()) + "\",\"operator\":\"" + esc(firstNonBlank(operator, OperatorResolver.systemOperator())) + "\"}";
            String responsePayload = externalHttpClient.postJson(
                    properties.getAgvCancelUrl(),
                    cancelPayload,
                    properties.getAgvAuthHeader(),
                    properties.getAgvApiKey(),
                    properties.getExternalCallTimeoutMs(),
                    "AGV取消"
            );
            job.setStatus(AgvJobStatus.CANCELLED);
            job.setLastError("任务取消，AGV作业取消，操作人=" + firstNonBlank(operator, "系统用户"));
            job.setResponsePayload(responsePayload);
            agvJobRepository.save(job);
            auditService.iface("AGV_CANCEL", "OUT", cancelPayload, job.getResponsePayload(), true, "任务取消已同步真实AGV系统");
            pushService.publish("agvJobs", job);
        }
    }

    @Transactional
    public AgvJobEntity callback(FactoryDtos.AgvCallbackRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "AGV回调内容不能为空");
        return callback(req.agvJobNo, req.status, req.externalJobNo, req.message);
    }

    @Transactional
    public AgvJobEntity callback(String agvJobNo, String status, String externalJobNo, String message) {
        if (agvJobNo == null || agvJobNo.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "AGV任务号不能为空");
        if (status == null || status.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "AGV状态不能为空");
        AgvJobEntity job = agvJobRepository.findByAgvJobNo(agvJobNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "AGV任务不存在：" + agvJobNo));
        AgvJobStatus s = AgvJobStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        job.setStatus(s);
        if (externalJobNo != null && !externalJobNo.isBlank()) job.setExternalJobNo(externalJobNo);
        if (s == AgvJobStatus.ARRIVED) job.setArrivedAt(LocalDateTime.now());
        if (s == AgvJobStatus.CONFIRMED) job.setConfirmedAt(LocalDateTime.now());
        if (s == AgvJobStatus.FAILED) job.setLastError(message);
        job.setResponsePayload("{\"status\":\"" + s.name() + "\",\"message\":\"" + esc(message) + "\"}");
        agvJobRepository.save(job);
        auditService.iface("AGV_CALLBACK", "IN", agvJobNo, job.getResponsePayload(), s != AgvJobStatus.FAILED, firstNonBlank(message, "AGV状态回调"));
        pushService.publish("agvJobs", job);
        return job;
    }

    @Transactional
    public void markArrivedByTask(String taskNo) {
        for (AgvJobEntity job : agvJobRepository.findByTaskNo(taskNo)) {
            if (job.getStatus() == AgvJobStatus.SENT || job.getStatus() == AgvJobStatus.ACCEPTED || job.getStatus() == AgvJobStatus.IN_TRANSIT) {
                job.setStatus(AgvJobStatus.ARRIVED);
                job.setArrivedAt(LocalDateTime.now());
                agvJobRepository.save(job);
                pushService.publish("agvJobs", job);
            }
        }
    }

    private String payload(AgvJobEntity job) {
        return "{\"agvJobNo\":\"" + esc(job.getAgvJobNo()) + "\",\"taskNo\":\"" + esc(job.getTaskNo()) + "\",\"from\":\"" + esc(job.getFromLocation()) + "\",\"to\":\"" + esc(job.getToLocation()) + "\",\"containerNo\":\"" + esc(job.getContainerNo()) + "\",\"priority\":\"" + job.getPriority() + "\"}";
    }
    private String esc(String v) { return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
