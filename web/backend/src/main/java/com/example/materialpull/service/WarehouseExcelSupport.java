package com.example.materialpull.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WarehouseExcelSupport {
    public String status() { return "READY"; }
}
