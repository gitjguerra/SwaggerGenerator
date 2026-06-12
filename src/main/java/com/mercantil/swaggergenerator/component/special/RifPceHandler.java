package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class RifPceHandler implements SpecialRequestHandler {

	@Autowired
	private RequestExampleProvider requestExampleProvider;

	@Override
	public boolean supports(String endpointPath, boolean hasBody) {

		return "/creditos/consultar/rif-pce".equalsIgnoreCase(endpointPath);
	}

	@Override
	public void apply(String endpointPath, Map<String, Object> props, Map<String, Object> example) {

		System.out.println("***** SPECIAL HANDLER: rif-pce");

		// =======================================================
		// ✅ REQUEST FIX
		// =======================================================
		if (example.containsKey("bodyEntradaConsultarRifPce")) {

			example.remove("bodyEntradaConsultarRifPce");

			Map<String, Object> rif = new LinkedHashMap<>();

			String nacRif = requestExampleProvider.getRuleValue(endpointPath, "rifPce.nacRif");
			String ciRif = requestExampleProvider.getRuleValue(endpointPath, "rifPce.ciRif");

			rif.put("nacRif", requestExampleProvider.parseValuePublic("nacRif", nacRif));
			rif.put("ciRif", requestExampleProvider.parseValuePublic("ciRif", ciRif));

			example.put("bodyEntradaConsultarRifPce", Map.of("rifPce", rif));

			return;
		}

		// =======================================================
		// ✅ RESPONSE FIX (CLAVE)
		// =======================================================
		if (example.containsKey("bodySalidaConsultarRifPce")) {

			// 🔥 eliminar completamente el body
			example.remove("bodySalidaConsultarRifPce");

			return;
		}
	}
}