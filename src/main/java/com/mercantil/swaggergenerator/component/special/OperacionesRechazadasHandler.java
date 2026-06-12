package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class OperacionesRechazadasHandler implements SpecialRequestHandler {

    @Autowired
    private RequestExampleProvider requestExampleProvider;

    @Override
    public boolean supports(String endpointPath, boolean hasBody) {
        return "/inversiones/consultar/operaciones-rechazadas"
                .equalsIgnoreCase(endpointPath)
                && !hasBody;
    }

    @Override
    public void apply(String endpointPath,
                      Map<String, Object> requestProps,
                      Map<String, Object> requestExample) {

        System.out.println("✅ SPECIAL HANDLER: operaciones-rechazadas");

        requestProps.clear();
        requestExample.clear();

        // schema
        requestProps.put("timestamp", Map.of("type", "string"));
        requestProps.put("transactionType", Map.of("type", "string"));
        requestProps.put("errorCode", Map.of("type", "number"));
        requestProps.put("transmissionId", Map.of("type", "string"));
        requestProps.put("errorMessage", Map.of("type", "string"));

        requestProps.put("transactionData",
                Map.of("$ref", "#/components/schemas/BeanTransactionData"));

        // example
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> transactionData = new LinkedHashMap<>();

        root.put("timestamp",
                requestExampleProvider.getRuleValue(endpointPath, "timestamp"));

        root.put("transactionType",
                requestExampleProvider.getRuleValue(endpointPath, "transactionType"));

        root.put("errorCode",
                requestExampleProvider.parseValuePublic(
                        "errorCode",
                        requestExampleProvider.getRuleValue(endpointPath, "errorCode")));

        root.put("transmissionId",
                requestExampleProvider.getRuleValue(endpointPath, "transmissionId"));

        root.put("errorMessage",
                requestExampleProvider.getRuleValue(endpointPath, "errorMessage"));

        List<String> fields = List.of(
                "fechaRecepcionFondos","fechaSubasta","fechaSolicitudCliente",
                "idMoneda","rifCiCliente","nombreCliente",
                "nroCtaBancariaCliente","idTipoCtaBancariaCliente",
                "nroCtaBancariaExtCliente","idTipoCtaBancariaExtCliente",
                "idActEconomicaCliente","idDestinoFondos","idMedioPago",
                "tasaCambioBs","montoDivisa","contravalorBs",
                "idEstatus","idTipoOperacion","codigoSubasta","index"
        );

        for (String f : fields) {

            String value =
                    requestExampleProvider.getRuleValue(endpointPath, "transactionData." + f);

            if (value != null) {
                transactionData.put(
                        f,
                        requestExampleProvider.parseValuePublic(f, value)
                );
            }
        }

        root.put("transactionData", transactionData);
        requestExample.putAll(root);
    }
}