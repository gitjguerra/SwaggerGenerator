package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class SolicitudCreditoPceHandler implements SpecialRequestHandler {

	@Autowired
	private RequestExampleProvider requestExampleProvider;

	@Override
	public boolean supports(String endpointPath, boolean hasBody) {

		return "/creditos/generar/solicitud-credito-pce".equalsIgnoreCase(endpointPath);
	}

	@Override
	public void apply(String endpointPath, Map<String, Object> props, Map<String, Object> example) {

		System.out.println("***** SPECIAL HANDLER: solicitud-credito-pce");

		// =======================================================
		// ✅ REQUEST: asegurar schema
		// =======================================================
		props.put("bodyEntradaGenerarSolicitudCreditosPce",
				Map.of("$ref", "#/components/schemas/BodyEntradaGenerarSolicitudCreditosPce"));

		// =======================================================
		// ✅ REQUEST: construir example
		// =======================================================
		Map<String, Object> body = new LinkedHashMap<>();

		body.put("tipoIdClte", requestExampleProvider.parseValuePublic("tipoIdClte",
				requestExampleProvider.getRuleValue(endpointPath, "tipoIdClte")));

		body.put("id",
				requestExampleProvider.parseValuePublic("id", requestExampleProvider.getRuleValue(endpointPath, "id")));

		body.put("mtoSolic", requestExampleProvider.parseValuePublic("mtoSolic",
				requestExampleProvider.getRuleValue(endpointPath, "mtoSolic")));

		body.put("plazoCdto", requestExampleProvider.parseValuePublic("plazoCdto",
				requestExampleProvider.getRuleValue(endpointPath, "plazoCdto")));

		body.put("cantCuotas", requestExampleProvider.parseValuePublic("cantCuotas",
				requestExampleProvider.getRuleValue(endpointPath, "cantCuotas")));

		body.put("mtoCuota", requestExampleProvider.parseValuePublic("mtoCuota",
				requestExampleProvider.getRuleValue(endpointPath, "mtoCuota")));

		body.put("tasa", requestExampleProvider.parseValuePublic("tasa",
				requestExampleProvider.getRuleValue(endpointPath, "tasa")));

		body.put("tasaComisFlat", requestExampleProvider.parseValuePublic("tasaComisFlat",
				requestExampleProvider.getRuleValue(endpointPath, "tasaComisFlat")));

		body.put("mtoComisFlat", requestExampleProvider.parseValuePublic("mtoComisFlat",
				requestExampleProvider.getRuleValue(endpointPath, "mtoComisFlat")));

		body.put("tasaPread", requestExampleProvider.parseValuePublic("tasaPread",
				requestExampleProvider.getRuleValue(endpointPath, "tasaPread")));

		example.put("bodyEntradaGenerarSolicitudCreditosPce", body);

		// =======================================================
		// ✅ RESPONSE FIX (solo si estamos en response)
		// =======================================================
		// ⚠️ IMPORTANTE: solo limpiar si existe bodySalida (indicador de response)
		if (props.containsKey("bodySalidaGenerarSolicitudCreditosPce")) {

			example.remove("bodyEntradaGenerarSolicitudCreditosPce");
			props.remove("bodyEntradaGenerarSolicitudCreditosPce");
		}
	}
}