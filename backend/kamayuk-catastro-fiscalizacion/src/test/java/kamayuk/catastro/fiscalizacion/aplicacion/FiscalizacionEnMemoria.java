package kamayuk.catastro.fiscalizacion.aplicacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kamayuk.catastro.compartido.Pagina;
import kamayuk.catastro.compartido.Paginacion;
import kamayuk.catastro.fiscalizacion.dominio.Acta;
import kamayuk.catastro.fiscalizacion.dominio.Campania;
import kamayuk.catastro.fiscalizacion.dominio.Candidato;
import kamayuk.catastro.fiscalizacion.dominio.CriterioDeCandidatos;
import kamayuk.catastro.fiscalizacion.dominio.EstadoDelCandidato;
import kamayuk.catastro.fiscalizacion.dominio.EtapaDeVerificacion;
import kamayuk.catastro.fiscalizacion.dominio.Evidencia;
import kamayuk.catastro.fiscalizacion.dominio.FiscalizacionRepository;
import kamayuk.catastro.fiscalizacion.dominio.Hallazgo;
import kamayuk.catastro.fiscalizacion.dominio.TasaDeDescarte;

/**
 * Un repositorio en memoria para las pruebas que no necesitan base de datos.
 *
 * <p>Lo que estas pruebas verifican es el <b>recorrido</b> —las dos compuertas, el umbral, el
 * hallazgo que nace de la segunda—, no como persiste PostgreSQL: eso lo prueban las que corren
 * contra el motor de verdad, y son las unicas que pueden decir algo sobre RLS.
 *
 * <p>Lo que no se usa lanza {@link UnsupportedOperationException} en vez de devolver vacio: una
 * prueba que pase porque un doble respondio «nada» a algo que nadie penso no verifica lo que dice
 * verificar.
 */
final class FiscalizacionEnMemoria implements FiscalizacionRepository {

    private final Map<Long, Campania> campanias = new LinkedHashMap<>();
    private final Map<Long, Candidato> candidatos = new LinkedHashMap<>();
    private final Map<Long, Hallazgo> hallazgos = new LinkedHashMap<>();
    private final Map<Long, Evidencia> evidencias = new LinkedHashMap<>();
    private final Map<Long, Acta> actas = new LinkedHashMap<>();

    private long siguienteId = 1;

    @Override
    public Campania guardar(Campania campania) {
        long id = campania.esNueva() ? siguienteId++ : campania.id();
        Campania guardada =
                new Campania(
                        id,
                        campania.codigo(),
                        campania.nombre(),
                        campania.estado(),
                        campania.inicio(),
                        campania.fin(),
                        campania.umbral());
        campanias.put(id, guardada);
        return guardada;
    }

    @Override
    public Optional<Campania> campaniaPorId(long id) {
        return Optional.ofNullable(campanias.get(id));
    }

    @Override
    public Optional<Campania> campaniaPorCodigo(String codigo) {
        return campanias.values().stream().filter(c -> c.codigo().equals(codigo)).findFirst();
    }

    @Override
    public Candidato guardar(Candidato candidato) {
        long id = candidato.esNuevo() ? siguienteId++ : candidato.id();
        Candidato guardado =
                new Candidato(
                        id,
                        candidato.campaniaId(),
                        candidato.predioId(),
                        candidato.clase(),
                        candidato.origen(),
                        candidato.score(),
                        candidato.insumos(),
                        candidato.geometria(),
                        candidato.estado(),
                        candidato.descarte());
        candidatos.put(id, guardado);
        return guardado;
    }

    @Override
    public Optional<Candidato> candidatoPorId(long id) {
        return Optional.ofNullable(candidatos.get(id));
    }

    @Override
    public Pagina<Candidato> candidatos(CriterioDeCandidatos criterio, Paginacion paginacion) {
        List<Candidato> encontrados =
                candidatos.values().stream()
                        .filter(c -> c.campaniaId() == criterio.campaniaId())
                        .filter(c -> criterio.estado() == null || c.estado() == criterio.estado())
                        .filter(c -> criterio.clase() == null || c.clase() == criterio.clase())
                        .toList();
        return Pagina.de(encontrados, paginacion, encontrados.size());
    }

    @Override
    public TasaDeDescarte tasaDeDescarte(long campaniaId) {
        List<Candidato> deLaCampania =
                candidatos.values().stream().filter(c -> c.campaniaId() == campaniaId).toList();
        return new TasaDeDescarte(
                deLaCampania.size(),
                cuantosDescartadosEn(deLaCampania, EtapaDeVerificacion.GABINETE),
                cuantosDescartadosEn(deLaCampania, EtapaDeVerificacion.CAMPO),
                deLaCampania.stream()
                        .filter(c -> c.estado() == EstadoDelCandidato.VERIFICADO_EN_CAMPO)
                        .count());
    }

    private static long cuantosDescartadosEn(List<Candidato> unos, EtapaDeVerificacion etapa) {
        return unos.stream()
                .filter(c -> c.descarte() != null && c.descarte().etapa() == etapa)
                .count();
    }

    @Override
    public Hallazgo guardar(Hallazgo hallazgo) {
        long id = hallazgo.esNuevo() ? siguienteId++ : hallazgo.id();
        Hallazgo guardado =
                new Hallazgo(
                        id,
                        hallazgo.candidatoId(),
                        hallazgo.clase(),
                        hallazgo.predioId(),
                        hallazgo.fichaId(),
                        hallazgo.areaDeLaFicha(),
                        hallazgo.areaVerificada(),
                        hallazgo.inspector(),
                        hallazgo.verificadoEn(),
                        hallazgo.estado(),
                        hallazgo.geometria());
        hallazgos.put(id, guardado);
        return guardado;
    }

    @Override
    public Optional<Hallazgo> hallazgoPorId(long id) {
        return Optional.ofNullable(hallazgos.get(id));
    }

    @Override
    public Optional<Hallazgo> hallazgoDelCandidato(long candidatoId) {
        return hallazgos.values().stream().filter(h -> h.candidatoId() == candidatoId).findFirst();
    }

    @Override
    public Pagina<Hallazgo> hallazgos(long campaniaId, Paginacion paginacion) {
        List<Hallazgo> encontrados = new ArrayList<>();
        for (Hallazgo hallazgo : hallazgos.values()) {
            Candidato candidato = candidatos.get(hallazgo.candidatoId());
            if (candidato != null && candidato.campaniaId() == campaniaId) {
                encontrados.add(hallazgo);
            }
        }
        return Pagina.de(encontrados, paginacion, encontrados.size());
    }

    @Override
    public Evidencia guardar(Evidencia evidencia) {
        boolean repetida =
                evidencias.values().stream()
                        .anyMatch(otra -> otra.huella().equals(evidencia.huella()));
        if (repetida) {
            throw new org.springframework.dao.DuplicateKeyException(
                    "evidencia_sha256_uq: una foto no sustenta dos actas");
        }
        long id = siguienteId++;
        Evidencia guardada =
                new Evidencia(
                        id,
                        evidencia.hallazgoId(),
                        evidencia.tipo(),
                        evidencia.huella(),
                        evidencia.ruta(),
                        evidencia.capturadoEn(),
                        evidencia.recibidoEn(),
                        evidencia.dispositivo());
        evidencias.put(id, guardada);
        return guardada;
    }

    @Override
    public List<Evidencia> evidenciasDe(long hallazgoId) {
        return evidencias.values().stream().filter(e -> e.hallazgoId() == hallazgoId).toList();
    }

    @Override
    public Acta guardar(Acta acta) {
        boolean repetida =
                actas.values().stream()
                        .anyMatch(
                                otra ->
                                        otra.numero().equals(acta.numero())
                                                || otra.hallazgoId() == acta.hallazgoId());
        if (repetida) {
            throw new org.springframework.dao.DuplicateKeyException(
                    "acta_numero_uq / acta_hallazgo_uq");
        }
        long id = siguienteId++;
        Acta guardada =
                new Acta(
                        id,
                        acta.numero(),
                        acta.hallazgoId(),
                        acta.fecha(),
                        acta.inspector(),
                        acta.detalle());
        actas.put(id, guardada);
        return guardada;
    }

    @Override
    public Optional<Acta> actaDelHallazgo(long hallazgoId) {
        return actas.values().stream().filter(a -> a.hallazgoId() == hallazgoId).findFirst();
    }
}
