package com.mercantil.swaggergenerator.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mercantil.swaggergenerator.config.ServiceConfig;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.service.OpenApiGeneratorService;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiController {

    @Autowired
    private OpenApiGeneratorService service;

    @Autowired
    private ServiceConfig config;

    @GetMapping("/{name}")
    public OpenApiDoc get(@PathVariable String name) {

        return config.getList().stream()
            .filter(s -> s.getName().equals(name))
            .findFirst()
            .map(service::generate)
			.orElseThrow(() ->
			    new ResponseStatusException(
			        HttpStatus.NOT_FOUND,
			        "Servicio no encontrado: " + name
			    )
			);

    }
}