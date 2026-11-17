package ae.plantel;

import ae.plantel.Modelo.Accion;
import ae.plantel.Modelo.EstadoJugador;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.JugadorBase;
import ae.plantel.Modelo.Pertenencia;
import ae.plantel.Modelo.Puesto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Decodificador, operador de reparación y función de fitness.
 *
 * CALENDARIO. El horizonte son 3 temporadas de dos mitades. La ventana w
 * (1..6) se aplica ANTES del período w: las impares son de verano y las pares
 * de invierno. Las seis caen dentro del horizonte, y toda operación tiene por
 * delante al menos media temporada en la que cuesta salario y rinde (o no)
 * puntos. Antes, la última ventana se aplicaba después de la última temporada
 * y era una ventana contable gratis: renovar a todo el plantel ahí sumaba 9.4
 * de fitness sin cambiar un solo punto de liga.
 *
 * ECONOMÍA. En cada período el club recibe su presupuesto salarial y paga la
 * masa salarial vigente; la diferencia va a la caja. Así un contrato caro
 * cuesta dinero de verdad, y no solo ocupa lugar bajo el tope.
 *
 * DESCARTE DE OPERACIONES. Una operación pedida puede no ejecutarse porque el
 * estado no la admite (el jugador ya no está), porque no alcanza la caja, o
 * porque la negociación fracasa. Las dos primeras son parte de la
 * decodificación; la tercera es azar del escenario.
 *
 * RESTRICCIONES. Cupo de fichas y tope salarial se tratan con REPARACIÓN, una
 * sola vez por ventana y al terminar de procesarla (la opción de menor
 * overhead del Tema 5). Los mínimos por puesto se tratan con PENALIZACIÓN.
 */
public final class Evaluador {

    // Pesos de las penalizaciones, en unidades de utilidad.
    public static final double LAMBDA_FICHAS = 60.0;
    public static final double LAMBDA_MASA = 60.0;
    /**
     * La falta de jugadores en un puesto ya cuesta puntos por sí sola, porque
     * esos minutos los juega la filial. Esta penalización es solo una guarda
     * contra planteles degenerados: se aplica sobre el déficit PROMEDIO de los
     * seis períodos y con un peso chico, para no contar el mismo costo dos
     * veces.
     */
    public static final double LAMBDA_PUESTO = 10.0;

    private Evaluador() {
    }

    // ----------------------------------------------------------------
    // Resultados
    // ----------------------------------------------------------------

    /** Resultado de simular un plan sobre UN escenario. */
    public static final class Resultado {
        public double utilidad;
        public double[] puntos;          // por temporada
        public double valorFinal;        // caja + pases
        public double caja;
        public double valorPlantel;
        public int plantelFinal;
        public double penalizacion;
        public int concretadas;
        public int descartadas;
        public boolean factible;
        public int salidasLibres;        // perdidos por vencimiento de contrato
        public int perdidosClausula;     // se los llevó un rival pagando la cláusula
        public double gastoPases;        // egresos menos ingresos por operaciones
        public double salariosPagados;
    }

    /** Fitness de un plan sobre N escenarios. */
    public static final class Fitness {
        public double valor;             // f(x)
        public double utilidadMedia;
        public double desvio;
        public double penalizacionMedia;
        public double factibles;
        public double concretadasMedia;
        public double puntosMedios;      // suma de las tres temporadas
        public double costoNetoMedio;    // para la variante multiobjetivo

        @Override
        public String toString() {
            return String.format("f=%.2f  U=%.2f  sd=%.2f  pen=%.2f  factibles=%.0f%%  ops=%.1f",
                    valor, utilidadMedia, desvio, penalizacionMedia,
                    factibles * 100, concretadasMedia);
        }
    }

    // ----------------------------------------------------------------
    // Estado del club dentro de un escenario
    // ----------------------------------------------------------------

    /**
     * Estado del club dentro de un escenario. Todo está indexado por la
     * posición del jugador en la instancia; el id del jugador solo se usa como
     * clave de los sorteos del escenario.
     */
    private static final class Club {
        final int n;
        final EstadoJugador[] e;
        final int[] ids;
        final boolean[] enPlantel;     // propios presentes
        final boolean[] cedido;        // propios a préstamo afuera
        final boolean[] prestado;      // ajenos incorporados a préstamo
        final boolean[] noDisponible;  // ya no se pueden fichar
        final double[] salarioPrestamo;
        int nPlantel;
        int nCedidos;
        int nPrestados;

        // buffers de trabajo, para no asignar memoria en cada período
        final int[] conv;
        final int[] prioridad;
        final double[] disponibles;
        final double[] minutos;
        final int[] auxIdx;
        final double[] auxTheta;
        final double[] auxScore;
        final double[] auxNiveles;

        double caja;
        double penalizacionDura;
        double deficitPuestos;
        int concretadas;
        int descartadas;
        boolean violoDuras;
        int salidasLibres;
        int perdidosClausula;
        double gastoPases;
        double salariosPagados;

        Club(Instancia inst, Cromosoma x) {
            n = inst.jugadores.size();
            e = new EstadoJugador[n];
            ids = new int[n];
            enPlantel = new boolean[n];
            cedido = new boolean[n];
            prestado = new boolean[n];
            noDisponible = new boolean[n];
            salarioPrestamo = new double[n];
            conv = new int[n];
            prioridad = new int[n];
            disponibles = new double[n];
            minutos = new double[n];
            auxIdx = new int[n];
            auxTheta = new double[n];
            auxScore = new double[n];
            auxNiveles = new double[n];
            for (int i = 0; i < n; i++) {
                JugadorBase j = inst.jugadores.get(i);
                e[i] = EstadoJugador.desdeBase(j);
                ids[i] = j.id();
                prioridad[i] = x.prioridadDe(i).ordinal();
                if (j.pertenencia() == Pertenencia.PROPIO) {
                    enPlantel[i] = true;
                    nPlantel++;
                }
            }
            caja = inst.presupuestoInicial;
        }

        void entrar(int i) {
            if (!enPlantel[i]) {
                enPlantel[i] = true;
                nPlantel++;
            }
        }

        void salir(int i) {
            if (enPlantel[i]) {
                enPlantel[i] = false;
                nPlantel--;
            }
        }

        double masaSalarial(Parametros p) {
            double masa = 0;
            for (int i = 0; i < n; i++) {
                if (enPlantel[i]) {
                    masa += e[i].salario;
                } else if (prestado[i]) {
                    masa += salarioPrestamo[i];
                } else if (cedido[i]) {
                    masa += e[i].salario * (1.0 - p.mercado.ahorroCesion);
                }
            }
            return masa;
        }

        int fichas() {
            return nPlantel + nPrestados;
        }

        void pagar(double monto) {
            caja -= monto;
            gastoPases += monto;
        }

        void cobrar(double monto) {
            caja += monto;
            gastoPases -= monto;
        }
    }

    // ----------------------------------------------------------------
    // Negociación
    // ----------------------------------------------------------------

    private static double sigmoide(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Probabilidad de que el jugador acepte: se compara lo ofrecido contra lo
     * que exige HOY, que depende de su nivel actual y no del salario que firmó
     * hace años.
     */
    private static double probAcepta(double ofrecido, double exigido, Parametros p) {
        return sigmoide(p.mercado.pendienteNegociacion * (ofrecido / exigido - 1.0));
    }

    /**
     * Cuánto más (o menos) salario exige el jugador según la cláusula que se le
     * propone. Una cláusula baja le deja la puerta abierta para irse, así que
     * acepta cobrar menos; un blindaje alto se paga con salario. Es el
     * compromiso entre cláusula y salario que describe el informe.
     */
    private static double exigenciaPorClausula(double clausulaMult, Parametros p) {
        double t = (clausulaMult - Cromosoma.CLAUSULA_MIN)
                / (Cromosoma.CLAUSULA_MAX - Cromosoma.CLAUSULA_MIN);
        return p.mercado.exigenciaClausulaMin
                + (p.mercado.exigenciaClausulaMax - p.mercado.exigenciaClausulaMin) * t;
    }

    private static double valor(EstadoJugador e, Parametros p) {
        return Evolucion.valorMercado(e.theta, e.edad, e.aniosContrato, p.valor);
    }

    /**
     * Prima de firma: la parte del valor creado por el contrato nuevo que se
     * lleva el jugador. Si el contrato nuevo no alarga al vigente, es cero.
     */
    private static double primaDeFirma(EstadoJugador e, int duracionNueva, Parametros p) {
        double conNuevo = Evolucion.valorMercado(e.theta, e.edad, duracionNueva, p.valor);
        return p.mercado.repartoExtension * Math.max(0.0, conNuevo - valor(e, p));
    }

    // ----------------------------------------------------------------
    // Ventana de pases
    // ----------------------------------------------------------------

    private static final int COMPRA = 0;
    private static final int PRESTAMO = 1;
    private static final int RENOVACION = 2;

    /** Operación de la ventana que la reparación puede deshacer. */
    private record Revertible(int i, int tipo, int aniosPrevios, double salarioPrevio,
                              double clausulaPrevia, double reembolso) {
    }

    private static void aplicarVentana(Instancia inst, Cromosoma x, Club club, int ventana,
                                       Escenario esc, Parametros p) {
        int periodo = ventana - 1;
        // Una operación de verano tiene la temporada entera por delante; una
        // de invierno, la mitad.
        int mitadesRestantes = (ventana % 2 == 1) ? Parametros.MITADES : 1;

        // Si la caja no alcanza para todo, se concretan primero las de mayor
        // nivel. Es parte de la decodificación, no una decisión del cromosoma.
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < x.n; i++) {
            if (x.ventana[i] == ventana && x.accion[i] != 0) {
                indices.add(i);
            }
        }
        if (indices.isEmpty()) {
            reparar(inst, club, List.of(), p);
            return;
        }
        indices.sort(Comparator.comparingDouble((Integer i) -> -club.e[i].theta));

        List<Revertible> revertibles = new ArrayList<>();
        Parametros.Mercado m = p.mercado;

        for (int i : indices) {
            JugadorBase j = inst.jugadores.get(i);
            int id = club.ids[i];
            EstadoJugador e = club.e[i];
            Accion a = x.accionDe(inst, i);
            boolean enPlantel = club.enPlantel[i];

            switch (a) {
                case MANTENER, IGNORAR -> {
                    // nada que hacer
                }
                case RENOVAR -> {
                    if (!enPlantel) {
                        club.descartadas++;
                        break;
                    }
                    int duracion = x.duracion[i];
                    // Nadie extiende contrato por menos de lo que ya cobra.
                    double ofrecido = Math.max(j.contrato().salario() * x.salario[i],
                            e.salario * m.incrementoMinRenovacion);
                    double exigido = Evolucion.salarioReferencia(e.theta, p.valor)
                            * exigenciaPorClausula(x.clausula[i], p);
                    if (esc.en(id, periodo, Escenario.NEGOCIACION).uniforme()
                            >= probAcepta(ofrecido, exigido, p)) {
                        club.descartadas++;
                        break;
                    }
                    double prima = primaDeFirma(e, duracion, p);
                    if (prima > club.caja) {
                        club.descartadas++;
                        break;
                    }
                    revertibles.add(new Revertible(i, RENOVACION, e.aniosContrato,
                            e.salario, e.clausula, prima));
                    club.pagar(prima);
                    e.aniosContrato = duracion;
                    e.salario = ofrecido;
                    e.clausula = x.clausula[i] * valor(e, p);
                    club.concretadas++;
                }
                case VENDER -> {
                    if (!enPlantel) {
                        club.descartadas++;
                        break;
                    }
                    if (esc.en(id, periodo, Escenario.VENTA).uniforme() >= m.probVenta) {
                        club.descartadas++;
                        break;
                    }
                    club.cobrar(valor(e, p) * m.descuentoVenta);
                    club.salir(i);
                    club.noDisponible[i] = true;
                    club.concretadas++;
                }
                case CEDER -> {
                    // La cesión dura hasta el fin de la temporada en curso y el
                    // jugador vuelve. Afuera juega más de lo que jugaría acá de
                    // suplente, que es para lo que sirve ceder a un juvenil.
                    if (!enPlantel || club.nCedidos >= inst.maxCedidos) {
                        club.descartadas++;
                        break;
                    }
                    club.salir(i);
                    club.cedido[i] = true;
                    club.nCedidos++;
                    club.concretadas++;
                }
                case LIBERAR -> {
                    double costo = e.salario * e.aniosContrato * m.costoRescision;
                    if (!enPlantel || costo > club.caja) {
                        club.descartadas++;
                        break;
                    }
                    club.pagar(costo);
                    club.salir(i);
                    club.noDisponible[i] = true;
                    e.aniosContrato = 0;
                    club.concretadas++;
                }
                case COMPRAR -> {
                    if (enPlantel || club.prestado[i] || club.noDisponible[i]) {
                        club.descartadas++;   // ya no está en el mercado
                        break;
                    }
                    int duracion = x.duracion[i];
                    double precio = valor(e, p) * m.primaCompra + primaDeFirma(e, duracion, p);
                    if (precio > club.caja) {
                        club.descartadas++;   // restricción dura de caja
                        break;
                    }
                    double ofrecido = j.contrato().salario() * x.salario[i];
                    double exigido = Evolucion.salarioReferencia(e.theta, p.valor)
                            * exigenciaPorClausula(x.clausula[i], p);
                    if (esc.en(id, periodo, Escenario.NEGOCIACION).uniforme()
                            >= probAcepta(ofrecido, exigido, p)) {
                        club.descartadas++;
                        break;
                    }
                    revertibles.add(new Revertible(i, COMPRA, e.aniosContrato,
                            e.salario, e.clausula, precio));
                    club.pagar(precio);
                    e.aniosContrato = duracion;
                    e.salario = ofrecido;
                    e.clausula = x.clausula[i] * valor(e, p);
                    club.entrar(i);
                    club.concretadas++;
                }
                case INCORPORAR_PRESTAMO -> {
                    if (enPlantel || club.prestado[i] || club.noDisponible[i]
                            || club.nPrestados >= inst.maxPrestamos) {
                        club.descartadas++;
                        break;
                    }
                    double cargo = m.cargoPrestamo * valor(e, p)
                            * mitadesRestantes / Parametros.MITADES;
                    if (cargo > club.caja) {
                        club.descartadas++;
                        break;
                    }
                    double ofrecido = j.contrato().salario() * x.salario[i];
                    double exigido = Evolucion.salarioReferencia(e.theta, p.valor);
                    if (esc.en(id, periodo, Escenario.NEGOCIACION).uniforme()
                            >= probAcepta(ofrecido, exigido, p)) {
                        club.descartadas++;
                        break;
                    }
                    revertibles.add(new Revertible(i, PRESTAMO, 0, 0, 0, cargo));
                    club.pagar(cargo);
                    club.prestado[i] = true;
                    club.salarioPrestamo[i] = ofrecido;
                    club.nPrestados++;
                    club.concretadas++;
                }
                default -> throw new IllegalStateException("acción desconocida: " + a);
            }
        }

        reparar(inst, club, revertibles, p);
    }

    /**
     * Operador de reparación: deshace operaciones de esta ventana hasta
     * recuperar el cupo de fichas y el tope salarial.
     *
     * Se deshace primero la que menos nivel aporta por millón de salario. Una
     * incorporación se cancela y se devuelve lo pagado; una renovación vuelve
     * al contrato anterior. Si la violación persiste porque viene del plantel
     * heredado, queda como penalización.
     */
    private static void reparar(Instancia inst, Club club, List<Revertible> operaciones,
                                Parametros p) {
        List<Revertible> candidatos = new ArrayList<>(operaciones);
        candidatos.sort(Comparator.comparingDouble((Revertible r) -> {
            double salario = r.tipo() == PRESTAMO
                    ? club.salarioPrestamo[r.i()] : club.e[r.i()].salario;
            return club.e[r.i()].theta / Math.max(0.05, salario);
        }));

        double masa = club.masaSalarial(p);
        while (!candidatos.isEmpty()
                && (club.fichas() > inst.cupoFichas || masa > inst.topeMasaSalarial)) {
            Revertible r = candidatos.remove(0);
            int i = r.i();
            EstadoJugador e = club.e[i];
            if (r.tipo() == PRESTAMO) {
                club.prestado[i] = false;
                club.salarioPrestamo[i] = 0;
                club.nPrestados--;
            } else {
                if (r.tipo() == COMPRA) {
                    club.salir(i);
                }
                e.aniosContrato = r.aniosPrevios();
                e.salario = r.salarioPrevio();
                e.clausula = r.clausulaPrevia();
            }
            club.cobrar(r.reembolso());
            club.concretadas--;
            club.descartadas++;
            masa = club.masaSalarial(p);
        }

        // Lo que quede sin reparar se penaliza, a razón de media temporada.
        if (club.fichas() > inst.cupoFichas) {
            club.penalizacionDura += LAMBDA_FICHAS / Parametros.MITADES
                    * (club.fichas() - inst.cupoFichas) / (double) inst.cupoFichas;
            club.violoDuras = true;
        }
        if (masa > inst.topeMasaSalarial) {
            club.penalizacionDura += LAMBDA_MASA / Parametros.MITADES
                    * (masa - inst.topeMasaSalarial) / inst.topeMasaSalarial;
            club.violoDuras = true;
        }
    }

    /** Déficit relativo respecto de los mínimos por puesto, en este período. */
    private static double deficitPuestos(Instancia inst, Club club) {
        int[] cuenta = new int[Puesto.values().length];
        for (int i = 0; i < club.n; i++) {
            if (club.enPlantel[i] || club.prestado[i]) {
                cuenta[club.e[i].puesto().ordinal()]++;
            }
        }
        double deficit = 0;
        for (Map.Entry<Puesto, Integer> m : inst.minimosPorPuesto.entrySet()) {
            int faltan = m.getValue() - cuenta[m.getKey().ordinal()];
            if (faltan > 0) {
                deficit += faltan / (double) m.getValue();
            }
        }
        return deficit;
    }

    // ----------------------------------------------------------------
    // Un período (media temporada)
    // ----------------------------------------------------------------

    private static double jugarPeriodo(Instancia inst, Club club, int periodo,
                                       Escenario esc, Parametros p) {
        double minutosPeriodo = Parametros.MINUTOS_TEMPORADA / Parametros.MITADES;

        // 1. Lesiones de TODOS los jugadores, propios y ajenos. Como el sorteo
        //    va por (jugador, período), la suerte de cada uno es la misma en
        //    todos los planes, juegue en el club que juegue.
        int nConv = 0;
        for (int i = 0; i < club.n; i++) {
            EstadoJugador e = club.e[i];
            double dias = Evolucion.sortearLesionMitad(e.edad, e.historialLesiones,
                    e.minutosPrevios, esc.en(club.ids[i], periodo, Escenario.LESION), p.lesion);
            double libres = Evolucion.minutosDisponiblesMitad(dias, p.lesion);
            e.diasBajaTemporada += dias;

            if (club.enPlantel[i] || club.prestado[i]) {
                club.conv[nConv++] = i;
                club.disponibles[i] = libres;
            } else if (club.cedido[i]) {
                e.minutosTemporada += Math.min(p.mercado.cargaCesion * minutosPeriodo, libres);
            } else {
                e.minutosTemporada += Math.min(p.mercado.cargaExterna * minutosPeriodo, libres);
            }
        }

        // 2. Reparto de minutos y 3. rendimiento.
        double filial = Temporada.repartirMinutos(club.e, club.conv, nConv, club.prioridad,
                club.disponibles, minutosPeriodo, club.minutos,
                club.auxIdx, club.auxTheta, club.auxScore);
        for (int c = 0; c < nConv; c++) {
            int i = club.conv[c];
            club.e[i].minutosTemporada += club.minutos[i];
        }
        double fuerza = Temporada.fuerza(club.e, club.conv, nConv, club.minutos, filial,
                club.auxNiveles);
        double puntos = Temporada.puntosTemporada(fuerza) / Parametros.MITADES;

        // 4. Economía del período: entra el presupuesto salarial, sale la masa.
        double masa = club.masaSalarial(p);
        club.caja += (inst.topeMasaSalarial - masa) / Parametros.MITADES;
        club.salariosPagados += masa / Parametros.MITADES;
        return puntos;
    }

    // ----------------------------------------------------------------
    // Fin de temporada
    // ----------------------------------------------------------------

    private static void cerrarTemporada(Instancia inst, Club club, int temporada,
                                        Escenario esc, Parametros p) {
        // 1. Evolución anual de todos, con los minutos que jugó cada uno.
        for (int i = 0; i < club.n; i++) {
            EstadoJugador e = club.e[i];
            e.theta = Evolucion.evolucionarNivel(e.theta, e.edad, e.minutosTemporada,
                    e.base.potencial(), esc.en(club.ids[i], temporada, Escenario.NIVEL),
                    p.evolucion);
            e.historialLesiones = Evolucion.actualizarHistorial(
                    e.historialLesiones, e.diasBajaTemporada, p.lesion);
            e.minutosPrevios = e.minutosTemporada;
            e.minutosTemporada = 0;
            e.diasBajaTemporada = 0;
            e.edad++;
            e.aniosContrato = Math.max(0, e.aniosContrato - 1);
        }

        // 2. Los préstamos terminan: los ajenos se van y los cedidos vuelven.
        for (int i = 0; i < club.n; i++) {
            if (club.prestado[i]) {
                club.prestado[i] = false;
                club.salarioPrestamo[i] = 0;
            }
            if (club.cedido[i]) {
                club.cedido[i] = false;
                club.entrar(i);
            }
        }
        club.nPrestados = 0;
        club.nCedidos = 0;

        // 3. Vencimientos y retiros: se van sin compensación.
        for (int i = 0; i < club.n; i++) {
            EstadoJugador e = club.e[i];
            if (club.enPlantel[i] && (e.aniosContrato <= 0 || e.edad > p.edadRetiro)) {
                club.salir(i);
                club.noDisponible[i] = true;
                club.salidasLibres++;
            }
        }

        if (temporada >= p.temporadas - 1) {
            return;   // tras la última temporada no hay más mercado
        }
        moverOtrosClubes(club, temporada, esc, p);
    }

    /**
     * Los otros clubes, como agentes reactivos de reglas simples.
     *
     * (a) Un candidato del mercado puede ser fichado por otro club y dejar de
     *     estar disponible: esperar a comprarlo más adelante tiene riesgo.
     * (b) Un jugador ajeno al que se le vence el contrato suele renovar; si no,
     *     el mercado se abarataría solo con el paso del tiempo.
     * (c) Un jugador propio puede recibir una oferta. Si la valoración del
     *     rival alcanza la cláusula, la ejecuta: el club cobra la cláusula y
     *     pierde al jugador, sin poder negarse. Es lo que hace que la cláusula
     *     sea una decisión y no un número decorativo.
     */
    private static void moverOtrosClubes(Club club, int temporada, Escenario esc,
                                         Parametros p) {
        Parametros.Mercado m = p.mercado;

        for (int i = 0; i < club.n; i++) {
            if (club.enPlantel[i] || club.noDisponible[i]) {
                continue;
            }
            EstadoJugador e = club.e[i];
            if (e.aniosContrato <= 0 && esc.en(club.ids[i], temporada, Escenario.RENUEVA)
                    .uniforme() < m.probRenuevaExterno) {
                e.aniosContrato = m.aniosRenovacionExterna;
            }
            double pFichado = m.probFichadoMax
                    * sigmoide((e.theta - m.thetaInteresMercado) / m.escalaInteresRival);
            if (esc.en(club.ids[i], temporada, Escenario.FICHADO).uniforme() < pFichado) {
                club.noDisponible[i] = true;
            }
        }

        for (int i = 0; i < club.n; i++) {
            if (!club.enPlantel[i]) {
                continue;
            }
            EstadoJugador e = club.e[i];
            Aleatorio rng = esc.en(club.ids[i], temporada, Escenario.RIVAL);
            double pOferta = m.probOfertaRivalMax
                    * sigmoide((e.theta - m.thetaInteresRival) / m.escalaInteresRival);
            if (rng.uniforme() >= pOferta) {
                continue;
            }
            double valoracion = valor(e, p)
                    * rng.uniforme(m.valoracionRivalMin, m.valoracionRivalMax);
            if (valoracion >= e.clausula) {
                club.cobrar(e.clausula);
                club.salir(i);
                club.noDisponible[i] = true;
                club.perdidosClausula++;
            }
        }
    }

    // ----------------------------------------------------------------
    // Simulación de un escenario
    // ----------------------------------------------------------------

    public static Resultado simular(Instancia inst, Cromosoma x, Escenario esc, Parametros p) {
        Club club = new Club(inst, x);
        Resultado out = new Resultado();
        out.puntos = new double[p.temporadas];
        int periodos = p.temporadas * Parametros.MITADES;

        for (int t = 0; t < p.temporadas; t++) {
            for (int mitad = 0; mitad < Parametros.MITADES; mitad++) {
                int periodo = t * Parametros.MITADES + mitad;
                aplicarVentana(inst, x, club, periodo + 1, esc, p);
                club.deficitPuestos += deficitPuestos(inst, club);
                out.puntos[t] += jugarPeriodo(inst, club, periodo, esc, p);
            }
            out.utilidad += Math.pow(p.gamma, t + 1) * out.puntos[t];
            cerrarTemporada(inst, club, t, esc, p);
        }

        double pases = 0;
        for (int i = 0; i < club.n; i++) {
            if (club.enPlantel[i]) {
                EstadoJugador e = club.e[i];
                pases += Evolucion.valorNeto(e.theta, e.edad, e.aniosContrato, e.salario, p);
            }
        }
        double penalizacion = club.penalizacionDura
                + LAMBDA_PUESTO * club.deficitPuestos / periodos;

        out.valorPlantel = pases;
        out.caja = club.caja;
        out.valorFinal = pases + club.caja;
        out.utilidad += p.beta * out.valorFinal - penalizacion;
        out.plantelFinal = club.nPlantel;
        out.penalizacion = penalizacion;
        out.concretadas = club.concretadas;
        out.descartadas = club.descartadas;
        out.factible = !club.violoDuras;
        out.salidasLibres = club.salidasLibres;
        out.perdidosClausula = club.perdidosClausula;
        out.gastoPases = club.gastoPases;
        out.salariosPagados = club.salariosPagados;
        return out;
    }

    // ----------------------------------------------------------------
    // Fitness sobre N escenarios
    // ----------------------------------------------------------------

    /**
     * Ecuación (3) del informe:
     *
     *   f(x) = media U(x, omega_k) - rho * sd(U) - penalizaciones
     *
     * (las penalizaciones ya vienen descontadas de cada U).
     *
     * El escenario k usa la semilla semillaBase + k. Todos los individuos de
     * una generación comparten semillaBase (common random numbers).
     */
    public static Fitness evaluar(Instancia inst, Cromosoma x, int nReplicas,
                                  long semillaBase, Parametros p) {
        double[] u = new double[nReplicas];
        Fitness f = new Fitness();
        for (int k = 0; k < nReplicas; k++) {
            Resultado r = simular(inst, x, new Escenario(semillaBase + k), p);
            u[k] = r.utilidad;
            f.penalizacionMedia += r.penalizacion / nReplicas;
            f.factibles += (r.factible ? 1.0 : 0.0) / nReplicas;
            f.concretadasMedia += r.concretadas / (double) nReplicas;
            for (double pts : r.puntos) {
                f.puntosMedios += pts / nReplicas;
            }
            // Costo neto del plan: lo que se gastó en pases y salarios menos
            // el patrimonio en jugadores con el que se termina.
            f.costoNetoMedio += (r.gastoPases + r.salariosPagados - r.valorPlantel) / nReplicas;
        }
        f.utilidadMedia = Estadistica.media(u);
        f.desvio = Estadistica.desvio(u);
        f.valor = f.utilidadMedia - p.rho * f.desvio;
        return f;
    }
}
