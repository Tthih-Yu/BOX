package com.example.materialpull.repository;

import com.example.materialpull.entity.SimpleBomEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 简单 BOM 的高速批量写入。绕过 JPA 逐行 flush，用 JDBC batch 一次提交多行，
 * 面向 500 万行量级导入。仅负责物理插入，不做业务校验(校验在 Service 层完成)。
 */
@Repository
@RequiredArgsConstructor
public class SimpleBomJdbcDao {
    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO t_simple_bom (batch_no, material_code, component_code, created_at) VALUES (?, ?, ?, ?)";

    public void batchInsert(List<SimpleBomEntity> rows) {
        if (rows == null || rows.isEmpty()) return;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, rows.size(), (ps, row) -> {
            ps.setString(1, row.getBatchNo());
            ps.setString(2, row.getMaterialCode());
            ps.setString(3, row.getComponentCode());
            ps.setTimestamp(4, now);
        });
    }
}
