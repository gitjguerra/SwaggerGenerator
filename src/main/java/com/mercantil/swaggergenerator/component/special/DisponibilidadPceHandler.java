package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class DisponibilidadPceHandler implements SpecialRequestHandler {

	private static final Logger log = LogManager.getLogger(DisponibilidadPceHandler.class);

	private static final String BODY_ENTRADA = "bodyEntradaConsultarDisponibilidadPce";
	private static final String TIPO_ID_CLIENTE = "tipoIdClte";
	private static final String NUM_ID_CLIENTE = "numIdCtle";
	
	private final RequestExampleProvider requestExampleProvider;
	public DisponibilidadPceHandler(RequestExampleProvider requestExampleProvider) {
		this.requestExampleProvider = requestExampleProvider;
	}

	@Override
	public boolean supports(String endpointPath, boolean hasBody, RequestPhase phase) {

		return phase == RequestPhase.REQUEST
				&& "/creditos/consultar/disponibilidad-pce".equalsIgnoreCase(endpointPath);
	}

	@Override
	public void apply(String endpointPath, Map<String, Object> props, Map<String, Object> example,
			RequestPhase phase) {

		log.info("***** SPECIAL HANDLER: disponibilidad-pce");

		// =======================================================
		// ✅ REQUEST FIX
		// =======================================================

		example.computeIfPresent(BODY_ENTRADA, (key, value) -> {

			Map<String, Object> body = new LinkedHashMap<>();

			String tipoId = requestExampleProvider.getRuleValue(endpointPath, TIPO_ID_CLIENTE);

			String numId = requestExampleProvider.getRuleValue(endpointPath, NUM_ID_CLIENTE);

			body.put(TIPO_ID_CLIENTE, requestExampleProvider.parseValuePublic(TIPO_ID_CLIENTE, tipoId));

			body.put(NUM_ID_CLIENTE, requestExampleProvider.parseValuePublic(NUM_ID_CLIENTE, numId));

			return body;
		});
	}
}