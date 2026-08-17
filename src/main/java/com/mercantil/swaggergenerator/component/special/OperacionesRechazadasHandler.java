package com.mercantil.swaggergenerator.component.special;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.component.RequestExampleProvider;

@Component
public class OperacionesRechazadasHandler implements SpecialRequestHandler {

	private static final Logger log = LogManager.getLogger(OperacionesRechazadasHandler.class);
	
	private static final String STRING = "string";
	
	private final RequestExampleProvider requestExampleProvider;
	public OperacionesRechazadasHandler(RequestExampleProvider requestExampleProvider) {
		this.requestExampleProvider = requestExampleProvider;
	}
	
    @Override
    public boolean supports(String endpointPath, boolean hasBody, RequestPhase phase) {
        return phase == RequestPhase.REQUEST
                && "/inversiones/consultar/operaciones-rechazadas"
                .equalsIgnoreCase(endpointPath)
                && !hasBody;
    }

    @Override
    public void apply(String endpointPath,
                      Map<String, Object> requestProps,
                      Map<String, Object> requestExample,
                      RequestPhase phase) {

        log.info(" ***** SPECIAL HANDLER: operaciones-rechazadas");

        requestProps.clear();
        requestExample.clear();

        // schema
        requestProps.put("timestamp", Map.of("type", STRING));
        requestProps.put("transactionType", Map.of("type", STRING));
        requestProps.put("errorCode", Map.of("type", "number"));
        requestProps.put("transmissionId", Map.of("type", STRING));
        requestProps.put("errorMessage", Map.of("type", STRING));

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