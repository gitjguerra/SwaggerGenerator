package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class LineaCreditoComprometidaHandler implements SpecialRequestHandler {

	private static final Logger log = LogManager.getLogger(LineaCreditoComprometidaHandler.class);
	
	private static final String BODY_ENTRADA = "bodyEntradaConsultarLineaCreditoComprometida";
	private static final String TIPO_CONSULTA = "tipoConsulta";
	
	private final RequestExampleProvider requestExampleProvider;
	public LineaCreditoComprometidaHandler(RequestExampleProvider requestExampleProvider) {
		this.requestExampleProvider = requestExampleProvider;
	}

    @Override
    public boolean supports(String endpointPath, boolean hasBody) {
        return "/creditos/consultar/linea-credito-comprometida"
                .equalsIgnoreCase(endpointPath);
    }

    @Override
    public void apply(String endpointPath,
                      Map<String, Object> props,
                      Map<String, Object> example) {

        log.info("***** SPECIAL HANDLER: linea-credito-comprometida");

        if (!example.containsKey(BODY_ENTRADA)) {
            return;
        }

        // 🔥 eliminar body generado incorrecto
        example.remove(BODY_ENTRADA);

        // =====================================================
        // ✅ construir estructura correcta manualmente
        // =====================================================
        Map<String, Object> body = new LinkedHashMap<>();

        // ✅ valores desde rules.xml
        String tipoConsulta =
                requestExampleProvider.getRuleValue(endpointPath, TIPO_CONSULTA);

        String numPer =
                requestExampleProvider.getRuleValue(endpointPath, "gruposEmpresasLineasCreditosComprometida[0].numPer");

        String nacRifGrupo =
                requestExampleProvider.getRuleValue(endpointPath,
                        "gruposEmpresasLineasCreditosComprometida[0].id.nacRifGrupo");

        String numCiRif =
                requestExampleProvider.getRuleValue(endpointPath,
                        "gruposEmpresasLineasCreditosComprometida[0].id.numCiRif");

        // ✅ defaults si no vienen del rule
        body.put(TIPO_CONSULTA,
                requestExampleProvider.parseValuePublic(TIPO_CONSULTA,
                        tipoConsulta != null ? tipoConsulta : "1"));

        Map<String, Object> id = new LinkedHashMap<>();
        id.put("nacRifGrupo",
                requestExampleProvider.parseValuePublic("nacRifGrupo",
                        nacRifGrupo != null ? nacRifGrupo : "V"));

        id.put("numCiRif",
                requestExampleProvider.parseValuePublic("numCiRif",
                        numCiRif != null ? numCiRif : "4321456"));

        Map<String, Object> grupo = new LinkedHashMap<>();
        grupo.put("numPer",
                requestExampleProvider.parseValuePublic("numPer",
                        numPer != null ? numPer : "3148421"));

        grupo.put("id", id);

        body.put("gruposEmpresasLineasCreditosComprometida",
                new Object[]{grupo});

        example.put(BODY_ENTRADA, body);
    }
}