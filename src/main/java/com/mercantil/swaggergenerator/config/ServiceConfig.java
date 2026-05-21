package com.mercantil.swaggergenerator.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.model.ServiceItem;

@Component
@ConfigurationProperties(prefix = "services")
public class ServiceConfig {

    private List<ServiceItem> list;

    public List<ServiceItem> getList() {
        return list;
    }

    public void setList(List<ServiceItem> list) {
        this.list = list;
    }
}