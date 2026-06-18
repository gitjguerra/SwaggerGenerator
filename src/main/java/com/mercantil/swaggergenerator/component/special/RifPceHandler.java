package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class RifPceHandler implements SpecialRequestHandler {

	private static final Logger log = LogManager.getLogger(RifPceHandler.class);

	private final RequestExampleProvider requestExampleProvider;

	public RifPceHandler(RequestExampleProvider requestExampleProvider) {
		this.requestExampleProvider = requestExampleProvider;
	}

	@Override
	public boolean supports(String endpointPath, boolean hasBody) {

		return "/creditos/consultar/rif-pce".equalsIgnoreCase(endpointPath);
	}

	@Override
	public void apply(String endpointPath, Map<String, Object> props, Map<String, Object> example) {

		log.info("***** SPECIAL HANDLER: rif-pce");

		// =======================================================
		// ✅ REQUEST FIX
		// =======================================================
		example.computeIfPresent("bodyEntradaConsultarRifPce", (key, value) -> {

			Map<String, Object> rif = new LinkedHashMap<>();

			String nacRif = requestExampleProvider.getRuleValue(endpointPath, "rifPce.nacRif");

			String ciRif = requestExampleProvider.getRuleValue(endpointPath, "rifPce.ciRif");

			rif.put("nacRif", requestExampleProvider.parseValuePublic("nacRif", nacRif));

			rif.put("ciRif", requestExampleProvider.parseValuePublic("ciRif", ciRif));

			return Map.of("rifPce", rif);
		});

		// =======================================================
		// ✅ RESPONSE FIX (CLAVE)
		// =======================================================
		example.computeIfPresent("bodySalidaConsultarRifPce", (key, value) -> null);
	}
}