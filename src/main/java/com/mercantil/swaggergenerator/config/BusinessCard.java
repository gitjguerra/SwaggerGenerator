package com.mercantil.swaggergenerator.config;

import lombok.Getter;

@Getter
public enum BusinessCard {

    // =========================================================
    // 🔹 BASE
    // =========================================================

    BM_HOME("BM_HOME"),
    SEPARATOR(System.getProperty("file.separator")),
    APP("swagger-generator"),

    // =========================================================
    // 🔹 ARCHIVO PRINCIPAL
    // =========================================================

    CONFIG("generator.xml"),
    TEST_ENVIRONMENT("testEnvironment"),

    // =========================================================
    // 🔹 CONFIGURACIÓN
    // =========================================================

    LOG4J("pathLog4jFile"),
    RULES("pathRules"),
    ABBREVIATIONS("pathAbbreviations"),

    // =========================================================
    // 🔹 OPCIONAL (FUTURO)
    // =========================================================

    OUTPUT_DIR("pathOutputDir"),
    BASE_PACKAGE("basePackage");

    private String value;

    BusinessCard(String value) {
        this.value = value;
    }
}