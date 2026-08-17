package com.mercantil.swaggergenerator.component.special;

import java.util.Map;

/**
 * Punto de extensión para endpoints cuyo request necesita una estructura de
 * ejemplo que un {@link com.mercantil.swaggergenerator.component.RequestBuilder}
 * genérico no puede producir a partir del schema del DTO por sí solo.
 *
 * <p>Antes de crear un nuevo handler, probar primero si alcanza con reglas en
 * rules.xml: {@code RequestExampleProvider} aplica cada
 * {@code <field name="..." value="..."/>} sobre el ejemplo generado aunque la
 * ruta describa una estructura (wrapper, renombrado, array anidado) que no
 * exista literalmente en el DTO — ver
 * {@code RequestExampleProvider#applyRawRequestRules}. Un handler solo debería
 * ser necesario cuando el problema NO es de valores/estructura del ejemplo,
 * sino algo que rules.xml no puede expresar (p.ej. inyectar un body completo
 * en un endpoint donde JavaParser no detectó ninguno, como hace
 * {@link OperacionesRechazadasHandler}).</p>
 */
public interface SpecialRequestHandler {

    boolean supports(String endpointPath, boolean hasBody, RequestPhase phase);

    void apply(String endpointPath,
               Map<String, Object> props,
               Map<String, Object> example,
               RequestPhase phase);
}