package ae.plantel;

/**
 * Un escenario omega: una realización completa del azar del horizonte.
 *
 * [Informe §III-B common random numbers, ref. [3]]
 *
 * Cada sorteo se identifica por (jugador, período, evento) y su valor depende
 * solo de esa clave y de la semilla del escenario, NO del orden en que se
 * pidan los sorteos. Es lo que hace que common random numbers funcione de
 * verdad.
 *
 * Con un único flujo secuencial (lo que había antes), una sola operación de
 * más en la primera ventana desplazaba todos los sorteos posteriores: la
 * lesión que en el plan A le tocaba al jugador 12 en el plan B le tocaba a
 * otro. Medido: la correlación de la utilidad entre el statu quo y el mismo
 * plan con una venta caía de 0.97 a 0.54, o sea que la reducción de varianza
 * se perdía justo cuando dos planes diferían en algo.
 *
 * Con sorteos por clave, la lesión del jugador 12 en la segunda mitad de la
 * temporada 2 es la misma en todos los planes evaluados sobre este escenario,
 * juegue donde juegue.
 */
public final class Escenario {

    // Tipos de evento. Cada uno tiene su propia secuencia por (jugador, período).
    public static final int LESION = 1;       // ocurrencia y días de baja
    public static final int NIVEL = 2;        // ruido de la evolución anual
    public static final int NEGOCIACION = 3;  // el jugador acepta o no la oferta
    public static final int VENTA = 4;        // aparece comprador o no
    public static final int RIVAL = 5;        // oferta de otro club por un jugador propio
    public static final int FICHADO = 6;      // otro club se lleva a un candidato
    public static final int RENUEVA = 7;      // un jugador ajeno renueva con su club

    public final long semilla;
    private final Aleatorio flujo = new Aleatorio(0);

    public Escenario(long semilla) {
        this.semilla = semilla;
    }

    /**
     * Devuelve el generador posicionado en la secuencia de esta clave.
     *
     * El objeto devuelto es compartido: hay que consumir los sorteos del
     * evento antes de volver a llamar a este método.
     */
    public Aleatorio en(int jugador, int periodo, int evento) {
        long h = Aleatorio.mezclar(semilla + 0x9E3779B97F4A7C15L * (jugador + 1L));
        h = Aleatorio.mezclar(h + 0xC2B2AE3D27D4EB4FL * (periodo + 1L));
        h = Aleatorio.mezclar(h + 0x165667B19E3779F9L * evento);
        flujo.resembrar(h);
        return flujo;
    }
}
