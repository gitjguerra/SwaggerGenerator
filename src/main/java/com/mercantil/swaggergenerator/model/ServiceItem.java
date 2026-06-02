package com.mercantil.swaggergenerator.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceItem {

    private String name;
    private String basePackage;
    private String host;
    private String basePath;

	private String controllersPath;
	private List<String> beansPath;
	private String configPath;

}
