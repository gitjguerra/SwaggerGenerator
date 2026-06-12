package com.mercantil.swaggergenerator.component.special;

import java.util.Map;

public interface SpecialRequestHandler {

    boolean supports(String endpointPath, boolean hasBody);

    void apply(String endpointPath,
               Map<String, Object> requestProps,
               Map<String, Object> requestExample);
}