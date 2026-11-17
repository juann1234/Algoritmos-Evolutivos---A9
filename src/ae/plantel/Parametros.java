package ae.plantel;

/**
 * Parámetros del simulador.
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
}
