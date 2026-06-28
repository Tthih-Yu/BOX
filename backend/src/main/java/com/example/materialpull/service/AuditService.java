package com.example.materialpull.service;

import com.example.materialpull.entity.*;
import com.example.materialpull.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final ScanLogRepository scanLogRepository;
    private final TaskLogRepository taskLogRepository;
    private final PrintLogRepository printLogRepository;
    private final InterfaceLogRepository interfaceLogRepository;

    public void scan(String labelCode, String boxCode, String action, boolean success, String message, String operator, String deviceNo, String stationCode, String materialCode) {
        ScanLogEntity log = new ScanLogEntity();
        log.setLabelCode(labelCode); log.setBoxCode(boxCode); log.setAction(action);
        log.setSuccess(success); log.setMessage(message); log.setOperator(operator); log.setDeviceNo(deviceNo);
        log.setStationCode(stationCode); log.setMaterialCode(materialCode);
        scanLogRepository.save(log);
    }

    public void task(String taskNo, String action, String fromStatus, String toStatus, String operator, String message) {
        TaskLogEntity log = new TaskLogEntity();
        log.setTaskNo(taskNo); log.setAction(action); log.setFromStatus(fromStatus); log.setToStatus(toStatus);
        log.setOperator(operator); log.setMessage(message);
        taskLogRepository.save(log);
    }

    public void print(String labelCode, String action, String operator, String printerName, boolean success, String message) {
        PrintLogEntity log = new PrintLogEntity();
        log.setLabelCode(labelCode); log.setAction(action); log.setOperator(operator); log.setPrinterName(printerName);
        log.setSuccess(success); log.setMessage(message);
        printLogRepository.save(log);
    }

    public void iface(String name, String direction, String req, String resp, boolean success, String message) {
        InterfaceLogEntity log = new InterfaceLogEntity();
        log.setInterfaceName(name); log.setDirection(direction); log.setRequestBody(req); log.setResponseBody(resp);
        log.setSuccess(success); log.setMessage(message);
        interfaceLogRepository.save(log);
    }
}
