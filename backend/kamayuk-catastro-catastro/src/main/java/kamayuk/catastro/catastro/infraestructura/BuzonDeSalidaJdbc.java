package kamayuk.catastro.catastro.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kamayuk.catastro.catastro.dominio.BuzonDeSalida;
import kamayuk.catastro.catastro.dominio.EventoDeCatastro;
import kamayuk.catastro.catastro.dominio.HechoDeCatastro;
import kamayuk.catastro.catastro.dominio.TipoDeEventoDeCatastro;
import kamayuk.catastro.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El buzon de salida contra PostgreSQL (C-8, `V5`).
 *
 * <p><b>Ni una llamada de red.</b> Escribe una fila y nada mas, en la transaccion de quien publica.
 * Quien entrega es otro proceso.
 */
@Repository
public class BuzonDeSalidaJdbc extends RepositorioJdbc implements BuzonDeSalida {

    public BuzonDeSalidaJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>La deduplicacion es del MOTOR y no de un {@code if}.</b> Se escribe con {@code ON
     * CONFLICT DO NOTHING} sobre {@code catastro_evento_uq} y se mira si volvio fila: dos corridas
     * simultaneas del mismo ejercicio leerian las dos «no esta» con un {@code SELECT} previo y las
     * dos publicarian. Aqui la segunda no escribe, y lo sabe porque el {@code RETURNING} viene
     * vacio.
     *
     * <p>El {@code SELECT} de la huella solo corre <b>cuando ya estaba</b>, que es el unico caso en
     * que hay algo que comparar.
     */
    @Override
    public Publicacion publicar(HechoDeCatastro hecho) {
        Optional<Long> escrito =
                jdbc().sql(
                                """
                                INSERT INTO catastro_evento (municipalidad_id, evento_id, tipo,
                                                             predio_id, ejercicio, cuerpo, huella,
                                                             estado, creado_en)
                                VALUES (%s, :evento, :tipo, :predio, :ejercicio,
                                        CAST(:cuerpo AS jsonb), :huella, 'PENDIENTE', now())
                                ON CONFLICT (municipalidad_id, evento_id) DO NOTHING
                                RETURNING id
                                """
                                        .formatted(MUNICIPALIDAD_ACTUAL))
                        .param("evento", hecho.eventoId())
                        .param("tipo", hecho.tipo().name())
                        .param("predio", hecho.predioId())
                        .param("ejercicio", hecho.ejercicio())
                        .param("cuerpo", hecho.cuerpo())
                        .param("huella", hecho.huella())
                        .query(Long.class)
                        .optional();
        if (escrito.isPresent()) {
            return Publicacion.NUEVO;
        }
        String huellaQueYaEstaba =
                jdbc().sql("SELECT huella FROM catastro_evento WHERE evento_id = :evento")
                        .param("evento", hecho.eventoId())
                        .query(String.class)
                        .single();
        if (!huellaQueYaEstaba.equals(hecho.huella())) {
            throw new HechoSelladoReescrito(hecho, huellaQueYaEstaba);
        }
        return Publicacion.YA_ESTABA;
    }

    @Override
    public List<EventoDeCatastro> pendientes(int limite) {
        return jdbc().sql(
                        """
                        SELECT id, evento_id, tipo, predio_id, ejercicio, cuerpo::text AS cuerpo,
                               huella, intentos, creado_en
                          FROM catastro_evento
                         WHERE estado = 'PENDIENTE'
                         ORDER BY id
                         LIMIT :limite
                        """)
                .param("limite", limite)
                .query(
                        (fila, numero) ->
                                new EventoDeCatastro(
                                        fila.getLong("id"),
                                        (UUID) fila.getObject("evento_id"),
                                        TipoDeEventoDeCatastro.valueOf(fila.getString("tipo")),
                                        (Long) fila.getObject("predio_id"),
                                        (Integer) fila.getObject("ejercicio"),
                                        fila.getString("cuerpo"),
                                        fila.getString("huella").strip(),
                                        fila.getInt("intentos"),
                                        fila.getTimestamp("creado_en").toInstant()))
                .list();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Solo pasa de {@code PENDIENTE} a {@code ENTREGADO}: acusar dos veces el mismo evento —que
     * pasa cada vez que el acuse se pierde despues de que el receptor confirmara— no vuelve a mover
     * la hora de entrega. Sin el {@code AND estado = 'PENDIENTE'}, la hora de entrega diria la del
     * ultimo acuse en vez de la de la entrega.
     */
    @Override
    public int marcarEntregados(List<UUID> eventoIds, Instant cuando) {
        if (eventoIds.isEmpty()) {
            return 0;
        }
        return jdbc().sql(
                        """
                        UPDATE catastro_evento
                           SET estado = 'ENTREGADO', entregado_en = :cuando, ultimo_error = NULL
                         WHERE evento_id = ANY (:eventos)
                           AND estado = 'PENDIENTE'
                        """)
                .param("cuando", java.sql.Timestamp.from(cuando))
                .param("eventos", eventoIds.toArray(new UUID[0]))
                .update();
    }

    @Override
    public void anotarIntentoFallido(List<UUID> eventoIds, String motivo) {
        if (eventoIds.isEmpty()) {
            return;
        }
        jdbc().sql(
                        """
                        UPDATE catastro_evento
                           SET intentos = intentos + 1, ultimo_error = :motivo
                         WHERE evento_id = ANY (:eventos)
                           AND estado = 'PENDIENTE'
                        """)
                .param("motivo", motivo.length() <= 400 ? motivo : motivo.substring(0, 400))
                .param("eventos", eventoIds.toArray(new UUID[0]))
                .update();
    }

    @Override
    public long pendientesQueQuedan() {
        return jdbc().sql("SELECT count(*) FROM catastro_evento WHERE estado = 'PENDIENTE'")
                .query(Long.class)
                .single();
    }
}
