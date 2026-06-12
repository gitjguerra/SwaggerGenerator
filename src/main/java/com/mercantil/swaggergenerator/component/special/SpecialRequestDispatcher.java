package com.mercantil.swaggergenerator.component.special;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpecialRequestDispatcher {

    @Autowired
    private List<SpecialRequestHandler> handlers;

    public boolean applyIfMatch(String endpointPath,
                                boolean hasBody,
                                Map<String, Object> requestProps,
                                Map<String, Object> requestExample) {

        for (SpecialRequestHandler h : handlers) {

            if (h.supports(endpointPath, hasBody)) {

                h.apply(endpointPath, requestProps, requestExample);
                return true;
            }
        }

        return false;
    }
}