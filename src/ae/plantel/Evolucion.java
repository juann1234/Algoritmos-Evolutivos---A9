package ae.plantel;

/**
 * Evolución de atributos, lesiones, salario de referencia y valor de mercado.
 *
 * [Informe §I-B ec. (1), modelo de lesiones y valor de mercado]
 *
 * Implementa la ecuación (1) del informe:
 *
 *   theta(t+1) = theta(t) + gMas(edad) * h(minutos) + gMenos(edad) + eps
 *
 * La asimetría entre gMas y gMenos es el corazón del modelo:
 *   - gMas depende de los minutos  -> un juvenil que no juega no progresa.
 *   - gMenos NO depende de ellos   -> el tiempo pasa igual para todos.
 */
public final class Evolucion {

    private Evolucion() {
    }

    // ----------------------------------------------------------------
    // Evolución del nivel (una vez por temporada)
    // ----------------------------------------------------------------

    /** gMas(edad): potencial de crecimiento, decreciente y nulo desde los 26. */
    public static double crecimientoPorEdad(double edad, Parametros.Evolucion p) {
        if (edad >= p.edadFinCrecimiento) {
            return 0.0;
        }
        double tramo = (p.edadFinCrecimiento - edad) / (p.edadFinCrecimiento - p.edadInicio);
        return p.crecimientoMax * Math.max(0.0, Math.min(1.0, tramo));
    }

    /** h(m): saturante en los minutos de la temporada. h(0) = 0, h(inf) -> 1. */
    public static double factorMinutos(double minutos, Parametros.Evolucion p) {
        double m = Math.max(0.0, minutos);
        return m / (m + p.minutosMediaSaturacion);
    }

    /** gMenos(edad) &lt;= 0: comienza hacia los 28 y se acentúa con la edad. */
    public static double declivePorEdad(double edad, Parametros.Evolucion p) {
        if (edad <= p.edadInicioDeclive) {
            return 0.0;
        }
        return -p.decliveCoef * Math.pow(edad - p.edadInicioDeclive, p.decliveExp);
    }

    /**
     * Aplica la ecuación (1) y devuelve el nivel de la temporada siguiente.
     *
     * [Informe §I-B ec. (1)]
     */
    public static double evolucionarNivel(double theta, double edad, double minutos,
                                          double potencial, Aleatorio rng,
                                          Parametros.Evolucion p) {
        double delta = crecimientoPorEdad(edad, p) * factorMinutos(minutos, p)
                + declivePorEdad(edad, p)
                + rng.normal(0.0, p.sigmaTheta);
        double nuevo = theta + delta;
        // El potencial es un techo, pero solo frena el crecimiento: un jugador
        // en declive puede quedar por debajo sin que el techo lo sostenga.
        nuevo = Math.min(nuevo, Math.max(potencial, theta));
        return Math.min(p.thetaMax, Math.max(p.thetaMin, nuevo));
    }

    // ----------------------------------------------------------------
    // Lesiones (se sortean por mitad de temporada)
    // ----------------------------------------------------------------

    private static double sigmoide(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Probabilidad de sufrir al menos una lesión en una temporada completa.
     *
     * [Informe §I-B probabilidad logística de lesión]
     */
    public static double probabilidadLesion(double edad, double historial,
                                            double minutosPrevios, Parametros.Lesion p) {
        double z = p.a0
                + p.a1Edad * (edad - 24.0)
                + p.a2Historial * (historial / 100.0)
                + p.a3Carga * (minutosPrevios / Parametros.MINUTOS_TEMPORADA);
        return sigmoide(z);
    }

    /**
     * Días de baja en UNA MITAD de temporada (0 si no se lesiona).
     *
     * La probabilidad por mitad se elige para que la probabilidad de lesionarse
     * al menos una vez en la temporada completa siga siendo la del modelo
     * anual: pMitad = 1 - raíz(1 - pTemporada).
     */
    public static double sortearLesionMitad(double edad, double historial,
                                            double minutosPrevios, Aleatorio rng,
                                            Parametros.Lesion p) {
        double pTemporada = probabilidadLesion(edad, historial, minutosPrevios, p);
        double pMitad = 1.0 - Math.sqrt(1.0 - pTemporada);
        if (rng.uniforme() >= pMitad) {
            return 0.0;
        }
        return Math.min(rng.gamma(p.diasForma, p.diasEscala), p.diasTemporada / Parametros.MITADES);
    }

    /** Minutos máximos disponibles en una mitad, dados los días de baja. */
    public static double minutosDisponiblesMitad(double diasBaja, Parametros.Lesion p) {
        double diasMitad = p.diasTemporada / Parametros.MITADES;
        double fraccion = Math.max(0.0, 1.0 - diasBaja / diasMitad);
        return Parametros.MINUTOS_TEMPORADA / Parametros.MITADES * fraccion;
    }

    /** El historial arrastra, pero decae: la propensión no es eterna. */
    public static double actualizarHistorial(double historial, double diasBaja,
                                             Parametros.Lesion p) {
        return p.decaimientoHistorial * historial + diasBaja;
    }

    // ----------------------------------------------------------------
    // Salario y valor
    // ----------------------------------------------------------------

    /**
     * Salario anual que un jugador de nivel theta pide hoy. Es la misma curva
     * con la que se generan los salarios de las instancias: un juvenil que sube
     * de 72 a 80 pasa a exigir exp(0.075*8) = 1.82 veces su salario viejo, así
     * que retenerlo cuesta.
     */
    public static double salarioReferencia(double theta, Parametros.Valor p) {
        return p.salarioBase * Math.exp(p.salarioEscala * (theta - 60.0));
    }

    /**
     * Valor de mercado en millones: nivel, edad y contrato restante.
     *
     * [Informe §I-B valor de mercado V_j(t)]
     */
    public static double valorMercado(double theta, double edad, double aniosContrato,
                                      Parametros.Valor p) {
        double base = p.valorBase * Math.exp(p.thetaEscala * (theta - p.thetaRef));
        double fEdad = p.edadTope / (1.0 + Math.exp(p.edadPendiente * (edad - p.edadCentro)));
        double plenos = p.contratoAniosPlenos;
        double fContrato = p.contratoPiso
                + (1.0 - p.contratoPiso) * Math.max(0.0, Math.min(aniosContrato, plenos)) / plenos;
        return Math.max(p.valorMin, base * fEdad * fContrato);
    }

    /**
     * Valor patrimonial NETO de un jugador propio al cierre del horizonte.
     *
     * Al valor de mercado se le resta el sobrecosto del contrato: lo que el
     * club se comprometió a pagarle por encima de lo que vale un jugador de su
     * nivel, por los años que le quedan. Un contrato largo y caro firmado en la
     * última ventana ya no es patrimonio gratis: es una deuda. El piso es el
     * costo de rescindirlo, porque el club siempre puede liberar al jugador.
     *
     * [Informe §I-C término V₃ de la ec. (2)]
     */
    public static double valorNeto(double theta, double edad, int aniosContrato,
                                   double salario, Parametros p) {
        double valor = valorMercado(theta, edad, aniosContrato, p.valor);
        double sobrecosto = Math.max(0.0, salario - salarioReferencia(theta, p.valor))
                * aniosContrato;
        double piso = -salario * aniosContrato * p.mercado.costoRescision;
        return Math.max(piso, valor - sobrecosto);
    }
}
