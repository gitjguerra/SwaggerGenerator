package com.mercantil.swaggergenerator.component.special;

import java.util.Map;

public interface SpecialRequestHandler {

    boolean supports(String endpointPath, boolean hasBody, RequestPhase phase);

    void apply(String endpointPath,
               Map<String, Object> props,
               Map<String, Object> example,
               RequestPhase phase);
}