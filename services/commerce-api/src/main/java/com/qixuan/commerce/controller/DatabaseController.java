package com.qixuan.commerce.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
public class DatabaseController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/db-check")
    public ResponseEntity<Map<String, String>> check() {
        String database = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "database", database));
    }
}