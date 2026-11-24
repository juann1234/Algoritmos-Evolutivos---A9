package ae.plantel;

/**
 * Parámetros del simulador.
 *
 * [Informe §I-B ec. (1), lesiones y valor; §I-C γ y β; §III-B ρ]
 *
 * Todos los coeficientes del modelo viven acá y en ningún otro lado. Eso permite
 * calibrarlos sin tocar la lógica, correr análisis de sensibilidad y dejar
 * documentado en el informe de dónde sale cada número.
 *
 * Unidades:
 *   nivel (theta)   : puntos en escala 0-100
 *   edad            : años cumplidos al inicio de la temporada
 *   minutos         : minutos disputados (una temporada son 38*90 = 3420)
 *   valor / salario : millones de euros (salario por temporada)
 */
public final class Parametros {

    /** Una temporada de liga: 38 fechas de 90 minutos. */
    public static final double MINUTOS_TEMPORADA = 38 * 90;

    /**
     * Cada temporada se juega en dos mitades, con una ventana de pases antes de
     * cada una. Así las seis ventanas caen DENTRO del horizonte: la de invierno
     * incide sobre la segunda mitad de su temporada, y la última ventana
     * todavía tiene media temporada por delante.
     */
    public static final int MITADES = 2;

    /**
     * Ecuación de evolución del nivel (ec. 1 del informe):
     *   theta(t+1) = theta(t) + gMas(edad) * h(minutos) + gMenos(edad) + eps
     */
    public static final class Evolucion {
        public double crecimientoMax = 4.0;
        public double edadInicio = 16.0;
        public double edadFinCrecimiento = 26.0;

        /** Saturación por minutos: h(m) = m / (m + minutosMediaSaturacion). */
        public double minutosMediaSaturacion = 1200.0;

        /*
         * El declive depende SOLO de la edad, nunca de los minutos. Si
         * dependiera de los minutos, el AE aprendería a guardar veteranos en el
         * banco para congelarles el nivel y revenderlos caros.
         */
        public double edadInicioDeclive = 28.0;
        public double decliveCoef = 0.30;
        public double decliveExp = 1.30;

        public double sigmaTheta = 1.00;
        public double thetaMin = 30.0;
        public double thetaMax = 99.0;
    }

    /** Lesiones: probabilidad logística por temporada más días de baja. */
    public static final class Lesion {
        public double a0 = -1.20;
        public double a1Edad = 0.08;
        public double a2Historial = 0.60;
        public double a3Carga = 0.30;

        public double diasForma = 2.0;
        public double diasEscala = 25.0;
        public double diasTemporada = 300.0;

        /** La propensión se arrastra pero decae. */
        public double decaimientoHistorial = 0.60;
    }

    /** Valor de mercado derivado de nivel, edad y contrato restante. */
    public static final class Valor {
        public double valorBase = 8.0;
        public double thetaRef = 70.0;
        public double thetaEscala = 0.14;

        public double edadCentro = 28.5;
        public double edadPendiente = 0.35;
        public double edadTope = 1.25;

        public double contratoPiso = 0.45;
        public double contratoAniosPlenos = 3.0;

        public double valorMin = 0.2;

        /** Salario de referencia: salarioBase * exp(salarioEscala * (theta - 60)). */
        public double salarioBase = 0.35;
        public double salarioEscala = 0.075;
    }

    /** Fricciones del mercado de pases y comportamiento de los otros clubes. */
    public static final class Mercado {
        // --- operaciones propias ---
        public double primaCompra = 1.15;      // sobreprecio sobre el valor de mercado
        public double descuentoVenta = 0.95;
        public double probVenta = 0.90;        // que aparezca comprador en la ventana
        public double costoRescision = 0.50;   // fracción del salario restante al liberar
        public double pendienteNegociacion = 6.0;

        /** Una renovación exige mejorar el salario vigente al menos este factor. */
        public double incrementoMinRenovacion = 1.10;

        /**
         * Reparto del valor creado al firmar un contrato largo. Alargar un
         * contrato sube el valor de mercado del jugador; él lo sabe y se queda
         * con esta fracción como prima de firma. Sin esto, renovar (o comprar a
         * alguien con el contrato por vencer y firmarle cinco años) era
         * fabricar patrimonio casi gratis.
         */
        public double repartoExtension = 0.50;

        // --- cesiones y préstamos: duran hasta el fin de la temporada en curso ---
        public double ahorroCesion = 0.50;     // parte del salario que paga el otro club
        public double cargaCesion = 0.85;      // fracción de los minutos que juega el cedido
        public double cargoPrestamo = 0.08;    // del valor, por temporada completa
        public double cargaExterna = 0.70;     // minutos de un jugador ajeno en su club

        // --- cláusula de rescisión: baja = acepta menos salario, pero queda expuesto ---
        public double exigenciaClausulaMin = 0.85;   // con la cláusula más baja
        public double exigenciaClausulaMax = 1.15;   // con la cláusula más alta (blindaje)

        // --- clubes rivales (agentes reactivos) ---
        /** Probabilidad máxima, por temporada, de que un rival oferte por un propio. */
        public double probOfertaRivalMax = 0.30;
        public double thetaInteresRival = 80.0;
        public double escalaInteresRival = 4.0;
        /** El rival valora al jugador entre estos múltiplos de su valor de mercado. */
        public double valoracionRivalMin = 1.0;
        public double valoracionRivalMax = 2.5;

        /** Probabilidad máxima, por temporada, de que otro club fiche a un candidato. */
        public double probFichadoMax = 0.25;
        public double thetaInteresMercado = 76.0;

        /** Un jugador ajeno al que se le vence el contrato renueva con esta probabilidad. */
        public double probRenuevaExterno = 0.75;
        public int aniosRenovacionExterna = 3;

        // --- calibración con datos reales (D-J5); los valores por defecto
        //     reproducen el modelo original, calibrados() los cambia ---

        /**
         * true: el multiplicador salarial del gen se aplica sobre el salario de
         * referencia del nivel actual del jugador, como define el informe
         * (§I-A). false (modelo original): sobre su salario actual, lo que
         * hacía imposible fichar a quien viene de una liga que paga poco.
         */
        public boolean ofertaSobreReferencia = false;

        /**
         * true: fichar a un jugador con el contrato vencido no paga traspaso,
         * solo la prima de firma. false (modelo original): se cobraba el 45% de
         * su valor aunque fuera libre.
         */
        public boolean agenteLibreSinTraspaso = false;

        /** Cociente ofrecido / exigido con 50% de aceptación (original: 1). */
        public double umbralAceptacion = 1.0;

        /**
         * true: cuando otro club ficha a un candidato, el jugador sigue en el
         * mercado con un contrato nuevo (más caro de comprar), y la probabilidad
         * anual de cambiar de club baja con el nivel, como en los datos.
         * false (original): el jugador desaparece del mercado y las estrellas
         * son las más buscadas.
         */
        public boolean fichajeExternoReubica = false;
        public double probCambioClubBase = 0.354;       // a nivel thetaCambioClubRef
        public double probCambioClubPendiente = -0.0115; // por punto de nivel
        public double thetaCambioClubRef = 72.0;
        public int aniosContratoNuevoClub = 4;
    }

    public final Evolucion evolucion = new Evolucion();
    public final Lesion lesion = new Lesion();
    public final Valor valor = new Valor();
    public final Mercado mercado = new Mercado();

    /** Horizonte: 3 temporadas de dos mitades = 6 períodos = 6 ventanas. */
    public int temporadas = 3;

    /** Factor de descuento: la T1 pesa más que la T3, sin volver miope al plan. */
    public double gamma = 0.92;

    /**
     * Peso del patrimonio final frente a los puntos de liga. Es un tipo de
     * cambio entre millones y puntos que ningún dato justifica del todo: por
     * eso existe la variante multiobjetivo. Se recalibra por barrido
     * (SensibilidadBeta) cada vez que cambia el modelo económico.
     */
    public double beta = 0.05;

    /** Peso del desvío entre escenarios: penaliza planes poco robustos. */
    public double rho = 0.5;

    public int edadRetiro = 38;

    /**
     * Parámetros calibrados contra datos reales (PENDIENTES P17, DECISIONES D-J2).
     *
     * Se usan con las instancias reales. Los valores por defecto se mantienen
     * para las instancias sintéticas, así los números de referencia de
     * validar.sh no cambian. Reproducir con:
     *   python3 herramientas/calibrar.py &lt;carpeta_con_los_csv&gt;
     *
     *  - Salario: FIFA 21, cinco grandes ligas, nivel &gt;= 65 (n = 2.968, R2 = 0,60).
     *  - Valor: FIFA 21, contratos de 3+ años (n = 1.569, R2 = 0,95). El factor
     *    contractual se mantiene y se VALIDÓ con montos reales de Transfermarkt:
     *    precio/valor con 1 año de contrato 0,67 (modelo 0,63), con 2 años 0,79
     *    (modelo 0,82).
     *  - Evolución: cambio de rating entre ediciones consecutivas con minutos de
     *    liga de Transfermarkt (n = 35.146). La sensibilidad a los minutos no es
     *    identificable con estos datos (simultaneidad) y queda en el supuesto
     *    de 1200 (D-J3).
     *  - Lesiones y negociación: sin datos para calibrar; quedan los supuestos.
     *
     * [No está en el informe inicial: ver DECISIONES D-J2]
     */
    public static Parametros calibrados() {
        Parametros p = new Parametros();
        p.valor.salarioBase = 0.0888;
        p.valor.salarioEscala = 0.1727;

        p.valor.valorBase = 2.8283;
        p.valor.thetaEscala = 0.2291;
        p.valor.edadTope = 1.5170;
        p.valor.edadPendiente = 0.2140;
        p.valor.edadCentro = 26.2514;

        p.evolucion.crecimientoMax = 9.3041;
        p.evolucion.minutosMediaSaturacion = 1200.0;
        p.evolucion.decliveCoef = 0.1897;
        p.evolucion.decliveExp = 0.7121;
        p.evolucion.edadInicioDeclive = 28.9461;
        p.evolucion.sigmaTheta = 2.2512;

        // Mercado (D-J5): oferta sobre el salario de referencia, aceptación
        // centrada en el percentil 10 del cociente salario real / referencia de
        // los fichajes reales (n = 4.952), y cambio de club decreciente con el
        // nivel (35% anual a nivel 72, 12% a nivel 92; 5 grandes ligas).
        p.mercado.ofertaSobreReferencia = true;
        p.mercado.agenteLibreSinTraspaso = true;
        p.mercado.umbralAceptacion = 0.61;
        p.mercado.fichajeExternoReubica = true;

        // beta recalibrado para la escala monetaria real (D-J6): los valores
        // reales son unas cinco veces los sintéticos, y en el barrido sobre
        // barcelona-2020-chica beta = 0.01 es el codo: el AE suma puntos de
        // liga sin resignar patrimonio. Con 0.05 el AE cambia partidos por
        // dinero; con 0, gasta todo.
        p.beta = 0.01;
        return p;
    }
}
