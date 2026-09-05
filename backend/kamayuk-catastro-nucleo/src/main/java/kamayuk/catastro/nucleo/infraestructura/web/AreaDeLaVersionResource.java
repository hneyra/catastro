package kamayuk.catastro.nucleo.infraestructura.web;

import kamayuk.catastro.dominio.AreaM2;
import org.jspecify.annotations.Nullable;

/**
 * El area de terreno de <b>una version</b> de ficha, por su identificador (C-5, #49, RF-055).
 *
 * <p>No «el area del predio»: la de la version concreta que otro contexto guardo cuando el
 * contribuyente declaro. Es lo que permite contrastar lo declarado contra lo inscrito hoy, que es
 * exactamente la subvaluacion por ampliacion no declarada.
 *
 * <p>{@code existe} distingue «esa version no esta» de «esa version no tiene area»: la segunda no
 * puede pasar —{@code ficha_catastral.area_terreno} es {@code NOT NULL}— y por eso el area es nula
 * <b>solo</b> cuando la version no existe. Se publica el discriminador igual, porque un cliente que
 * tuviera que deducirlo de un {@code null} estaria adivinando.
 */
public record AreaDeLaVersionResource(long fichaId, boolean existe, @Nullable AreaM2 areaTerreno) {}
