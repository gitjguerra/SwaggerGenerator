package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class LineaCreditoComprometidaHandler implements SpecialRequestHandler {

    @Autowired
    private RequestExampleProvider requestExampleProvider;

    @Override
    public boolean supports(String endpointPath, boolean hasBody) {
        return "/creditos/consultar/linea-credito-comprometida"
                .equalsIgnoreCase(endpointPath);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(String endpointPath,
                      Map<String, Object> props,
                      Map<String, Object> example) {

        System.out.println("***** SPECIAL HANDLER: linea-credito-comprometida");

        if (!example.containsKey("bodyEntradaConsultarLineaCreditoComprometida")) {
            return;
        }

        // 🔥 eliminar body generado incorrecto
        example.remove("bodyEntradaConsultarLineaCreditoComprometida");

        // =====================================================
        // ✅ construir estructura correcta manualmente
        // =====================================================
        Map<String, Object> body = new LinkedHashMap<>();

        // ✅ valores desde rules.xml
        String tipoConsulta =
                requestExampleProvider.getRuleValue(endpointPath, "tipoConsulta");

        String numPer =
                requestExampleProvider.getRuleValue(endpointPath, "gruposEmpresasLineasCreditosComprometida[0].numPer");

        String nacRifGrupo =
                requestExampleProvider.getRuleValue(endpointPath,
                        "gruposEmpresasLineasCreditosComprometida[0].id.nacRifGrupo");

        String numCiRif =
                requestExampleProvider.getRuleValue(endpointPath,
                        "gruposEmpresasLineasCreditosComprometida[0].id.numCiRif");

        // ✅ defaults si no vienen del rule
        body.put("tipoConsulta",
                requestExampleProvider.parseValuePublic("tipoConsulta",
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

        example.put("bodyEntradaConsultarLineaCreditoComprometida", body);
    }
}