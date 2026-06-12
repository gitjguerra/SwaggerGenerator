package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class DisponibilidadPceHandler implements SpecialRequestHandler {

	@Autowired
	private RequestExampleProvider requestExampleProvider;

	@Override
	public boolean supports(String endpointPath, boolean hasBody) {

		return "/creditos/consultar/disponibilidad-pce".equalsIgnoreCase(endpointPath);
	}

	@Override
	public void apply(String endpointPath, Map<String, Object> props, Map<String, Object> example) {

		System.out.println("***** SPECIAL HANDLER: disponibilidad-pce");

		// =======================================================
		// ✅ REQUEST FIX
		// =======================================================
		if (example.containsKey("bodyEntradaConsultarDisponibilidadPce")) {

			example.remove("bodyEntradaConsultarDisponibilidadPce");

			Map<String, Object> body = new LinkedHashMap<>();

			String tipoId = requestExampleProvider.getRuleValue(endpointPath, "tipoIdClte");
			String numId = requestExampleProvider.getRuleValue(endpointPath, "numIdCtle");

			body.put("tipoIdClte", requestExampleProvider.parseValuePublic("tipoIdClte", tipoId));

			body.put("numIdCtle", requestExampleProvider.parseValuePublic("numIdCtle", numId));

			example.put("bodyEntradaConsultarDisponibilidadPce", body);

			return;
		}
	}
}