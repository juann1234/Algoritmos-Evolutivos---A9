package ae.plantel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo de datos del problema.
 *
 * [Informe §I-A dominio de acciones; §I-C restricciones; §III-A gen μ]
 *
 * Se separa lo estructural de lo que evoluciona:
 *   JugadorBase   -> datos de entrada de la instancia, nunca se modifican
 *   EstadoJugador -> lo que cambia dentro de una simulación
 *
 * Esa separación permite correr N simulaciones independientes sobre la misma
 * instancia sin copiar ni ensuciar los datos originales.
 */
public final class Modelo {

    private Modelo() {
    }

    public enum Puesto { POR, DEF, MED, DEL }

    /** Mínimos por puesto; restricción blanda (penalización) en el fitness. */
    public static final Map<Puesto, Integer> MINIMOS_POR_PUESTO = new EnumMap<>(Puesto.class);

    /** Composición del once titular (4-3-3). */
    public static final Map<Puesto, Integer> ONCE_TITULAR = new EnumMap<>(Puesto.class);

    static {
        MINIMOS_POR_PUESTO.put(Puesto.POR, 2);
        MINIMOS_POR_PUESTO.put(Puesto.DEF, 7);
        MINIMOS_POR_PUESTO.put(Puesto.MED, 7);
        MINIMOS_POR_PUESTO.put(Puesto.DEL, 4);

        ONCE_TITULAR.put(Puesto.POR, 1);
        ONCE_TITULAR.put(Puesto.DEF, 4);
        ONCE_TITULAR.put(Puesto.MED, 3);
        ONCE_TITULAR.put(Puesto.DEL, 3);
    }

    public enum Pertenencia { PROPIO, MERCADO }

    /**
     * Dominio de acciones, dependiente de la pertenencia del jugador.
     *
     * El dominio restringido es lo que hace imposible por construcción
     * representar operaciones sin sentido, como vender a alguien que no
     * pertenece al club.
     */
    public enum Accion {
        // --- jugadores propios: índices 0..4 del gen ---
        MANTENER, RENOVAR, VENDER, CEDER, LIBERAR,
        // --- candidatos del mercado: índices 0..2 del gen ---
        IGNORAR, COMPRAR, INCORPORAR_PRESTAMO;

        public static final Accion[] PROPIO = { MANTENER, RENOVAR, VENDER, CEDER, LIBERAR };
        public static final Accion[] MERCADO = { IGNORAR, COMPRAR, INCORPORAR_PRESTAMO };
    }

    /** Gen mu_i: cómo se reparten los minutos dentro del puesto. */
    public enum PrioridadMinutos {
        DESARROLLO,          // se le dan minutos aunque no sea el mejor
        ROTACION,            // juega según su nivel relativo
        TITULAR_PREFERENTE   // tiene prioridad sobre el resto del puesto
    }

    /** Contrato vigente de un jugador. */
    public record Contrato(int aniosRestantes, double salario, double clausula) {
    }

    /** Datos de entrada, constantes durante toda la simulación. */
    public record JugadorBase(
            int id,
            String nombre,
            Puesto puesto,
            Pertenencia pertenencia,
            int edad,
            double thetaInicial,
            double potencial,
            Contrato contrato,
            double historialLesiones) {

        public Accion[] accionesValidas() {
            return pertenencia == Pertenencia.PROPIO ? Accion.PROPIO : Accion.MERCADO;
        }
    }

    /** Lo que cambia dentro de una simulación concreta. */
    public static final class EstadoJugador {
        public final JugadorBase base;
        public double theta;
        public int edad;
        public int aniosContrato;
        public double salario;
        public double historialLesiones;
        public double minutosPrevios;      // minutos de la temporada anterior completa
        public double clausula;            // monto de la cláusula vigente, en millones
        public double minutosTemporada;    // acumulado de la temporada en curso
        public double diasBajaTemporada;   // ídem, días de baja
        public boolean enPlantel;
        public boolean cedido;

        private EstadoJugador(JugadorBase base) {
            this.base = base;
            this.theta = base.thetaInicial();
            this.edad = base.edad();
            this.aniosContrato = base.contrato().aniosRestantes();
            this.salario = base.contrato().salario();
            this.historialLesiones = base.historialLesiones();
            this.clausula = base.contrato().clausula();
            this.minutosPrevios = 0.0;
            this.enPlantel = base.pertenencia() == Pertenencia.PROPIO;
            this.cedido = false;
        }

        public static EstadoJugador desdeBase(JugadorBase base) {
            return new EstadoJugador(base);
        }

        public EstadoJugador copia() {
            EstadoJugador c = new EstadoJugador(base);
            c.theta = theta;
            c.edad = edad;
            c.aniosContrato = aniosContrato;
            c.salario = salario;
            c.historialLesiones = historialLesiones;
            c.minutosPrevios = minutosPrevios;
            c.clausula = clausula;
            c.minutosTemporada = minutosTemporada;
            c.diasBajaTemporada = diasBajaTemporada;
            c.enPlantel = enPlantel;
            c.cedido = cedido;
            return c;
        }

        public int id() {
            return base.id();
        }

        public Puesto puesto() {
            return base.puesto();
        }
    }

    /** Una instancia del problema: plantel propio, mercado y reglas económicas. */
    public static final class Instancia {
        public final String nombre;
        public final List<JugadorBase> jugadores;
        public final double presupuestoInicial;
        public final double topeMasaSalarial;
        public final int cupoFichas;
        public final int maxCedidos;
        /** Máximo de jugadores incorporados a préstamo al mismo tiempo. */
        public final int maxPrestamos = 3;
        /** Mínimos por puesto de ESTA instancia: una micro-instancia de 4
         *  jugadores no puede exigir los 20 de un plantel profesional. */
        public final Map<Puesto, Integer> minimosPorPuesto;

        public Instancia(String nombre, List<JugadorBase> jugadores,
                         double presupuestoInicial, double topeMasaSalarial) {
            this(nombre, jugadores, presupuestoInicial, topeMasaSalarial, 25, 7);
        }

        public Instancia(String nombre, List<JugadorBase> jugadores,
                         double presupuestoInicial, double topeMasaSalarial,
                         int cupoFichas, int maxCedidos) {
            this(nombre, jugadores, presupuestoInicial, topeMasaSalarial,
                    cupoFichas, maxCedidos, MINIMOS_POR_PUESTO);
        }

        public Instancia(String nombre, List<JugadorBase> jugadores,
                         double presupuestoInicial, double topeMasaSalarial,
                         int cupoFichas, int maxCedidos,
                         Map<Puesto, Integer> minimosPorPuesto) {
            this.minimosPorPuesto = Map.copyOf(minimosPorPuesto);
            this.nombre = nombre;
            this.jugadores = List.copyOf(jugadores);
            this.presupuestoInicial = presupuestoInicial;
            this.topeMasaSalarial = topeMasaSalarial;
            this.cupoFichas = cupoFichas;
            this.maxCedidos = maxCedidos;
        }

        public List<JugadorBase> propios() {
            return filtrar(Pertenencia.PROPIO);
        }

        public List<JugadorBase> mercado() {
            return filtrar(Pertenencia.MERCADO);
        }

        private List<JugadorBase> filtrar(Pertenencia p) {
            List<JugadorBase> out = new ArrayList<>();
            for (JugadorBase j : jugadores) {
                if (j.pertenencia() == p) {
                    out.add(j);
                }
            }
            return out;
        }

        public String resumen() {
            List<JugadorBase> pr = propios();
            double edad = 0, theta = 0, masa = 0;
            for (JugadorBase j : pr) {
                edad += j.edad();
                theta += j.thetaInicial();
                masa += j.contrato().salario();
            }
            int n = pr.size();
            return String.format(
                    "Instancia '%s': %d propios / %d candidatos | edad media %.1f | "
                            + "nivel medio %.1f | masa salarial %.1fM de un tope de %.1fM | "
                            + "presupuesto %.1fM",
                    nombre, n, mercado().size(), edad / n, theta / n,
                    masa, topeMasaSalarial, presupuestoInicial);
        }
    }
}
