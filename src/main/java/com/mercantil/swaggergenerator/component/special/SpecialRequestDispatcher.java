package com.mercantil.swaggergenerator.component.special;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SpecialRequestDispatcher {

	private final List<SpecialRequestHandler> handlers;

	public SpecialRequestDispatcher(List<SpecialRequestHandler> handlers) {
		this.handlers = handlers;
	}

    public boolean applyIfMatch(String endpointPath,
                                boolean hasBody,
                                RequestPhase phase,
                                Map<String, Object> props,
                                Map<String, Object> example) {

        for (SpecialRequestHandler h : handlers) {

            if (h.supports(endpointPath, hasBody, phase)) {

                h.apply(endpointPath, props, example, phase);
                return true;
            }
        }

        return false;
    }
}