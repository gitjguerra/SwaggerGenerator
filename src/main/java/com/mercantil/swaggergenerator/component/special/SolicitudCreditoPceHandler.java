package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class SolicitudCreditoPceHandler implements SpecialRequestHandler {

	private static final Logger log = LogManager.getLogger(SolicitudCreditoPceHandler.class);

	private static final String BODY_ENTRADA = "bodyEntradaGenerarSolicitudCreditosPce"; 
	private static final String TIPO_ID_CLIENTE = "tipoIdClte";
	private static final String ID = "Id";
	private static final String MTO_SOLIC = "mtoSolic";
	private static final String PLAZO_CDTO = "plazoCdto";
	private static final String CANT_CUOTAS = "cantCuotas";
	private static final String MTO_CUOTA = "mtoCuota";
	private static final String TASA = "tasa";
	private static final String TASA_COMIS_FLAT = "tasaComisFlat";
	private static final String MTO_COMIS_FLAT = "mtoComisFlat";
	private static final String TASA_PREAD = "tasaPread";

	private final RequestExampleProvider requestExampleProvider;

	public SolicitudCreditoPceHandler(RequestExampleProvider requestExampleProvider) {
		this.requestExampleProvider = requestExampleProvider;
	}

	@Override
	public boolean supports(String endpointPath, boolean hasBody) {

		return "/creditos/generar/solicitud-credito-pce".equalsIgnoreCase(endpointPath);
	}

	@Override
	public void apply(String endpointPath, Map<String, Object> props, Map<String, Object> example) {

		log.info("***** SPECIAL HANDLER: solicitud-credito-pce");

		// =======================================================
		// ✅ REQUEST: asegurar schema
		// =======================================================
		props.put(BODY_ENTRADA,
				Map.of("$ref", "#/components/schemas/BodyEntradaGenerarSolicitudCreditosPce"));

		// =======================================================
		// ✅ REQUEST: construir example
		// =======================================================
		Map<String, Object> body = new LinkedHashMap<>();

		body.put(TIPO_ID_CLIENTE, requestExampleProvider.parseValuePublic(TIPO_ID_CLIENTE,
				requestExampleProvider.getRuleValue(endpointPath, TIPO_ID_CLIENTE)));

		body.put(ID,
				requestExampleProvider.parseValuePublic(ID, requestExampleProvider.getRuleValue(endpointPath, ID)));

		body.put(MTO_SOLIC, requestExampleProvider.parseValuePublic(MTO_SOLIC,
				requestExampleProvider.getRuleValue(endpointPath, MTO_SOLIC)));

		body.put(PLAZO_CDTO, requestExampleProvider.parseValuePublic(PLAZO_CDTO,
				requestExampleProvider.getRuleValue(endpointPath, PLAZO_CDTO)));

		body.put(CANT_CUOTAS, requestExampleProvider.parseValuePublic(CANT_CUOTAS,
				requestExampleProvider.getRuleValue(endpointPath, CANT_CUOTAS)));

		body.put(MTO_CUOTA, requestExampleProvider.parseValuePublic(MTO_CUOTA,
				requestExampleProvider.getRuleValue(endpointPath, MTO_CUOTA)));

		body.put(TASA, requestExampleProvider.parseValuePublic(TASA,
				requestExampleProvider.getRuleValue(endpointPath, TASA)));

		body.put(TASA_COMIS_FLAT, requestExampleProvider.parseValuePublic(TASA_COMIS_FLAT,
				requestExampleProvider.getRuleValue(endpointPath, TASA_COMIS_FLAT)));

		body.put(MTO_COMIS_FLAT, requestExampleProvider.parseValuePublic(MTO_COMIS_FLAT,
				requestExampleProvider.getRuleValue(endpointPath, MTO_COMIS_FLAT)));

		body.put(TASA_PREAD, requestExampleProvider.parseValuePublic(TASA_PREAD,
				requestExampleProvider.getRuleValue(endpointPath, TASA_PREAD)));

		example.put(BODY_ENTRADA, body);

		// =======================================================
		// ✅ RESPONSE FIX (solo si estamos en response)
		// =======================================================
		// ⚠️ IMPORTANTE: solo limpiar si existe bodySalida (indicador de response)
		if (props.containsKey(BODY_ENTRADA)) {

			example.remove(BODY_ENTRADA);
			props.remove(BODY_ENTRADA);
		}
	}
}