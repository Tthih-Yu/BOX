package com.example.materialpull.dto;

public class TaskActionRequest {
    public String operator;
    public String remark;
    public String exceptionReason;
    public String receiveScanCode;
    public String emptyContainerNo;
    public String expectedStatus;
    public String requestId;
    public Boolean force = false;
}
