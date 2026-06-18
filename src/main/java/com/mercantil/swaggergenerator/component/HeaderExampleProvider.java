package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class HeaderExampleProvider {

	public Map<String, Object> buildHeaderEntrada() {

		Map<String, Object> header = new LinkedHashMap<>();

		header.put("identificadorUnicoGlobal", "f215a700-4fdb-45ec-8540-7b030663afb3");
		header.put("identificacionCanal", "0006");
		header.put("identificacionSubCanal", "01");
		header.put("siglaAplicacion", "OLB");
		header.put("identificacionUsuario", "");
		header.put("direccionIpConsumidor", "10.0.12.48");
		header.put("direccionIpCliente", "10.0.12.48");
		header.put("fechaEnvioMensaje", "20251226");
		header.put("horaEnvioMensaje", "135046");
		header.put("atributoPagineo", "");
		header.put("claveBusqueda", "");
		header.put("cantidadRegistros", 0);

		return header;
	}

	public Map<String, Object> buildHeaderSalida() {

		Map<String, Object> header = new LinkedHashMap<>();

		header.put("tipoMensaje", "I");
		header.put("mensajeProgramadorSistema", "Procesado correctamente");
		header.put("codigoMensajeProgramador", "0000");
		header.put("mensajeUsuario", "Operación exitosa");
		header.put("codigoMensajeUsuario", "0000");
		header.put("fechaSalidaMensaje", "20251226");
		header.put("horaSalidaMensaje", "135046");

		return header;
	}
}