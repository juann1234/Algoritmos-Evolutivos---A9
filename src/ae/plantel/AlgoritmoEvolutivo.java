package ae.plantel;

import ae.plantel.Modelo.Accion;
import ae.plantel.Modelo.Instancia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Algoritmo evolutivo sobre un fitness ruidoso, con las variantes del temario
 *
 * [Informe §III-B manejo del ruido; §III-C reemplazo, parada y diversidad; §IV-B(4) búsqueda aleatoria]
 * como opciones de configuración.
 *
 * Tres cuidados que el ruido del simulador obliga a tener, en todas las
 * variantes:
 *
 *  1. ESCENARIOS COMUNES. Toda la población de una generación se evalúa sobre
 *     los mismos N escenarios, que rotan de una generación a la siguiente.
 *
 *  2. REEVALUACIÓN DE LOS SOBREVIVIENTES. Como los escenarios rotan, todo
 *     individuo que pasa a la generación siguiente se vuelve a evaluar. Si no,
 *     uno que tuvo una evaluación afortunada quedaría instalado para siempre.
 *     Esto hace que la reevaluación tenga costo, y ese costo es lo que decide
 *     qué modelos de evolución son viables (ver gap generacional).
 *
 *  3. SELECCIÓN FINAL EN DOS ETAPAS. Al terminar se toman los K mejores
 *     candidatos y se los vuelve a medir sobre los mismos escenarios y con más
 *     réplicas; gana el mejor de esa segunda medición.
 *
 * Variantes (Temas 4, 8 y 9):
 *
 *  - GAP GENERACIONAL. Cada generación reemplaza una fracción G de la
 *    población; el resto (los mejores) sobrevive. G = 1 es el modelo
 *    generacional con elitismo; G = 2/P es el estado estacionario. Con
 *    reevaluación de sobrevivientes, cada generación cuesta P evaluaciones
 *    cualquiera sea G, así que un G chico produce muy pocos hijos por
 *    evaluación gastada.
 *  - SHARING. La selección usa un fitness compartido que castiga a los
 *    individuos con muchos vecinos cercanos, para sostener la diversidad.
 *  - CHC. Selección elitista padres + hijos, cruzamiento HUX solo entre padres
 *    suficientemente distintos (prevención de incesto), sin mutación, y
 *    reinicio cataclísmico cuando la población converge.
 *  - MEMÉTICO. Cada cierta cantidad de generaciones, búsqueda local
 *    (ascenso de colina) sobre los campos contractuales del mejor individuo.
 *
 * El presupuesto se mide en evaluaciones de fitness, incluyendo las
 * reevaluaciones y la búsqueda local: es lo que hace justa la comparación
 * entre variantes.
 */
public final class AlgoritmoEvolutivo {

    /** Semillas de escenarios para validar: nunca se usan durante la búsqueda. */
    public static final long SEMILLA_VALIDACION = 777_000L;
    public static final int N_VALIDACION = 200;

    public enum Modelo { GENERACIONAL, CHC }

    /** Configuración del AE; es lo que barre el diseño factorial del informe. */
    public static final class Config {
        public int poblacion = 100;
        public double pC = 0.75;
        public double pM = 0.05;
        public int elite = 2;
        public int torneo = 3;
        public int replicas = 30;         // N del simulador durante la búsqueda
        public int maxEvaluaciones = 40000;
        /**
         * Límite de tiempo de reloj en segundos; 0 = sin límite. Es el segundo
         * criterio de parada que promete el informe (§III-C: "40.000
         * evaluaciones, más un límite de tiempo de reloj"). Se chequea entre
         * generaciones. OJO: con límite de tiempo una corrida deja de ser
         * reproducible (depende de la máquina), por eso por defecto está apagado
         * y solo actúa como red de seguridad en ClusterUY.
         */
        public double segundosMax = 0;
        public long semilla = 1;
        /** Selección final: cuántos candidatos se remiden y con cuántas réplicas. */
        public int finalistas = 10;
        public int replicasSeleccion = 100;
        /** Restringe la búsqueda a la grilla enumerable (ver OptimoExacto). */
        public boolean grilla = false;

        // --- variantes ---
        public Modelo modelo = Modelo.GENERACIONAL;
        /** Fracción de la población reemplazada por generación (1 = generacional). */
        public double gap = 1.0;
        /** Radio de sharing en genes distintos; 0 lo desactiva. */
        public double sigmaSharing = 0.0;
        /** Cada cuántas generaciones se aplica la búsqueda local; 0 la desactiva. */
        public int memeticoCada = 0;
        /** Mejora mínima para aceptar un paso de la búsqueda local. */
        public double memeticoUmbral = 0.3;

        /** Evaluaciones equivalentes que consume la selección final. */
        public int costoSeleccion() {
            return (int) Math.ceil(finalistas * (double) replicasSeleccion / replicas);
        }

        long semillaBusqueda() {
            return semilla * 1_000_003L;
        }

        long semillaSeleccion() {
            return 900_000_000L + semilla * 10_000L;
        }

        @Override
        public String toString() {
            return String.format("%s pob=%d pC=%.2f pM=%.3f gap=%.2f sharing=%.0f memetico=%d "
                            + "N=%d evals=%d semilla=%d",
                    modelo, poblacion, pC, pM, gap, sigmaSharing, memeticoCada,
                    replicas, maxEvaluaciones, semilla);
        }
    }

    /** Resultado de una ejecución. */
    public static final class Resultado {
        public Cromosoma mejor;
        /** Fitness del mejor, medido con N=200 sobre los escenarios de validación. */
        public Evaluador.Fitness fitnessMejor;
        public final List<Double> historialMejor = new ArrayList<>();
        public final List<Double> historialMedia = new ArrayList<>();
        /** Distancia media entre pares de la población, por generación. */
        public final List<Double> historialDiversidad = new ArrayList<>();
        public int evaluaciones;
        public int generaciones;
        public int cataclismos;
        public int mejorasLocales;
        public double segundos;
    }

    /**
     * Mejor plan de una generación y el fitness con que se lo midió. Es público
     * porque el AE sobre jMetal (ae.plantel.jmetal.AGPlantel) reutiliza la misma
     * selección final (DECISIONES D-A1).
     */
    public record Candidato(Cromosoma plan, double fitness) {
    }

    /** Estado compartido de una ejecución. */
    private static final class Corrida {
        final Instancia inst;
        final Config cfg;
        final Parametros p;
        final Aleatorio rng;
        final Resultado out = new Resultado();
        final List<Candidato> candidatos = new ArrayList<>();
        final int presupuesto;
        long semillaEscenarios;

        Corrida(Instancia inst, Config cfg, Parametros p) {
            this.inst = inst;
            this.cfg = cfg;
            this.p = p;
            this.rng = new Aleatorio(cfg.semilla);
            this.presupuesto = Math.max(cfg.poblacion,
                    cfg.maxEvaluaciones - cfg.costoSeleccion());
            this.semillaEscenarios = cfg.semillaBusqueda();
        }

        double evaluar(Cromosoma c) {
            out.evaluaciones++;
            return Evaluador.evaluar(inst, c, cfg.replicas, semillaEscenarios, p).valor;
        }

        int restantes() {
            return presupuesto - out.evaluaciones;
        }

        final long t0 = System.nanoTime();

        /** [Informe §III-C parada por límite de tiempo de reloj] */
        boolean vencido() {
            return cfg.segundosMax > 0 && (System.nanoTime() - t0) / 1e9 >= cfg.segundosMax;
        }

        void rotarEscenarios() {
            semillaEscenarios += cfg.replicas;
        }
    }

    private AlgoritmoEvolutivo() {
    }

    // ----------------------------------------------------------------
    // Punto de entrada
    // ----------------------------------------------------------------

    public static Resultado ejecutar(Instancia inst, Config cfg, Parametros p) {
        long t0 = System.nanoTime();
        Corrida c = new Corrida(inst, cfg, p);

        Cromosoma[] pob = Operadores.poblacionInicial(inst, cfg.poblacion, c.rng);
        if (cfg.grilla) {
            for (Cromosoma x : pob) {
                Operadores.proyectarAGrilla(x);
            }
        }
        double[] fit = new double[pob.length];
        for (int i = 0; i < pob.length; i++) {
            fit[i] = c.evaluar(pob[i]);
        }
        registrar(c, pob, fit);

        if (cfg.modelo == Modelo.CHC) {
            bucleChc(c, pob, fit);
        } else {
            bucleGeneracional(c, pob, fit);
        }

        Resultado out = c.out;
        out.mejor = seleccionFinal(inst, c.candidatos, cfg, p);
        out.evaluaciones += cfg.costoSeleccion();
        out.fitnessMejor = Evaluador.evaluar(inst, out.mejor, N_VALIDACION, SEMILLA_VALIDACION, p);
        out.segundos = (System.nanoTime() - t0) / 1e9;
        return out;
    }

    // ----------------------------------------------------------------
    // Modelo generacional / gap generacional (con sharing y memético)
    // ----------------------------------------------------------------

    private static void bucleGeneracional(Corrida c, Cromosoma[] pob, double[] fit) {
        Config cfg = c.cfg;
        int tam = pob.length;
        // Con G = 1 sobreviven solo los elitistas; con G < 1, los mejores (1-G)P.
        int hijosPorGeneracion = cfg.gap >= 1.0
                ? tam - cfg.elite
                : Math.max(2, (int) Math.round(cfg.gap * tam));
        int sobrevivientes = tam - hijosPorGeneracion;

        while (c.restantes() >= tam && !c.vencido()) {
            double[] fitSeleccion = cfg.sigmaSharing > 0 ? compartido(pob, fit, cfg.sigmaSharing) : fit;
            int[] mejores = mejoresIndices(fit, sobrevivientes);

            Cromosoma[] hijos = new Cromosoma[tam];
            for (int e = 0; e < sobrevivientes; e++) {
                hijos[e] = pob[mejores[e]].copia();
            }
            for (int i = sobrevivientes; i < tam; i += 2) {
                Cromosoma p1 = pob[Operadores.torneo(fitSeleccion, cfg.torneo, c.rng)];
                Cromosoma p2 = pob[Operadores.torneo(fitSeleccion, cfg.torneo, c.rng)];
                Cromosoma[] par = Operadores.cruzar(p1, p2, cfg.pC, c.rng);
                Operadores.mutar(c.inst, par[0], cfg.pM, c.rng);
                Operadores.mutar(c.inst, par[1], cfg.pM, c.rng);
                if (cfg.grilla) {
                    Operadores.proyectarAGrilla(par[0]);
                    Operadores.proyectarAGrilla(par[1]);
                }
                hijos[i] = par[0];
                if (i + 1 < tam) {
                    hijos[i + 1] = par[1];
                }
            }

            // Escenarios nuevos: hijos y sobrevivientes se evalúan sobre ellos.
            c.rotarEscenarios();
            for (int i = 0; i < tam; i++) {
                pob[i] = hijos[i];
                fit[i] = c.evaluar(pob[i]);
            }
            c.out.generaciones++;

            if (cfg.memeticoCada > 0 && c.out.generaciones % cfg.memeticoCada == 0) {
                int idx = mejoresIndices(fit, 1)[0];
                fit[idx] = busquedaLocal(c, pob[idx], fit[idx]);
            }
            registrar(c, pob, fit);
        }
    }

    /**
     * Fitness compartido (sharing, Tema 8).
     *
     * f'(i) = (f(i) - fmin + 1) / m(i),  m(i) = suma_j sh(d(i,j)),
     * sh(d) = 1 - d / sigma si d &lt; sigma, 0 si no.
     *
     * Se desplaza por fmin porque el sharing divide: aplicado sobre valores
     * del orden de 200 con diferencias de pocas unidades, la división por el
     * tamaño del nicho borraría toda la presión selectiva.
     *
     * [Informe §III-C preservación de diversidad (sharing)]
     */
    private static double[] compartido(Cromosoma[] pob, double[] fit, double sigma) {
        int tam = pob.length;
        double fmin = Double.POSITIVE_INFINITY;
        for (double f : fit) {
            fmin = Math.min(fmin, f);
        }
        double[] nicho = new double[tam];
        for (int i = 0; i < tam; i++) {
            nicho[i] += 1.0;   // sh(d(i,i)) = 1
            for (int j = i + 1; j < tam; j++) {
                int d = Operadores.distancia(pob[i], pob[j]);
                if (d < sigma) {
                    double sh = 1.0 - d / sigma;
                    nicho[i] += sh;
                    nicho[j] += sh;
                }
            }
        }
        double[] out = new double[tam];
        for (int i = 0; i < tam; i++) {
            out[i] = (fit[i] - fmin + 1.0) / nicho[i];
        }
        return out;
    }

    /**
     * Búsqueda local de un memético ligero: ascenso de colina sobre salario,
     * duración y cláusula de los jugadores con los que el plan firma contrato.
     * La estructura del plan (a quién se ficha, cuándo) no se toca: eso lo
     * explora el AE.
     *
     * Contra el ruido: los vecinos se evalúan sobre los mismos escenarios que el
     * individuo actual, y un paso solo se acepta si mejora más que un umbral.
     * Aun así, una búsqueda local sobre un fitness ruidoso puede terminar
     * persiguiendo ruido; la reevaluación de la generación siguiente lo
     * corrige, pero el costo en evaluaciones ya se pagó.
     */
    private static double busquedaLocal(Corrida c, Cromosoma x, double fActual) {
        List<Integer> genes = new ArrayList<>();
        for (int i = 0; i < x.n; i++) {
            Accion a = x.accionDe(c.inst, i);
            if (a == Accion.RENOVAR || a == Accion.COMPRAR || a == Accion.INCORPORAR_PRESTAMO) {
                genes.add(i);
            }
        }
        c.rng.mezclar(genes);

        double[][] pasos = { { 0.10, 0, 0 }, { -0.10, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 },
                             { 0, 0, 1.0 }, { 0, 0, -1.0 } };
        for (int i : genes) {
            for (double[] paso : pasos) {
                if (c.restantes() <= 0) {
                    return fActual;
                }
                Cromosoma y = x.copia();
                y.salario[i] = recortar(y.salario[i] + paso[0],
                        Cromosoma.SALARIO_MIN, Cromosoma.SALARIO_MAX);
                y.duracion[i] = (int) recortar(y.duracion[i] + paso[1],
                        Cromosoma.DURACION_MIN, Cromosoma.DURACION_MAX);
                y.clausula[i] = recortar(y.clausula[i] + paso[2],
                        Cromosoma.CLAUSULA_MIN, Cromosoma.CLAUSULA_MAX);
                if (y.igualA(x)) {
                    continue;
                }
                double f = c.evaluar(y);
                if (f > fActual + c.cfg.memeticoUmbral) {
                    copiarEn(y, x);   // lamarckiano: el individuo hereda la mejora
                    fActual = f;
                    c.out.mejorasLocales++;
                }
            }
        }
        return fActual;
    }

    // ----------------------------------------------------------------
    // CHC (Tema 9)
    // ----------------------------------------------------------------

    /**
     * CHC: selección elitista padres + hijos, HUX con prevención de incesto,
     * sin mutación, y reinicio cataclísmico al converger.
     *
     * Con escenarios rotativos, los padres se reevalúan junto con los hijos
     * antes de competir: compararlos con fitness medidos sobre escenarios
     * distintos favorecería a quien tuvo suerte.
     */
    private static void bucleChc(Corrida c, Cromosoma[] pob, double[] fit) {
        int tam = pob.length;
        int umbralInicial = Math.max(1, pob[0].n / 4);
        int umbral = umbralInicial;

        while (c.restantes() >= 2 * tam && !c.vencido()) {
            // Emparejamiento al azar; solo se cruzan los suficientemente distintos.
            List<Integer> orden = new ArrayList<>();
            for (int i = 0; i < tam; i++) {
                orden.add(i);
            }
            c.rng.mezclar(orden);
            List<Cromosoma> hijos = new ArrayList<>();
            for (int k = 0; k + 1 < tam; k += 2) {
                Cromosoma a = pob[orden.get(k)];
                Cromosoma b = pob[orden.get(k + 1)];
                if (Operadores.distancia(a, b) / 2 > umbral) {
                    Cromosoma[] par = Operadores.hux(a, b, c.rng);
                    if (c.cfg.grilla) {
                        Operadores.proyectarAGrilla(par[0]);
                        Operadores.proyectarAGrilla(par[1]);
                    }
                    hijos.add(par[0]);
                    hijos.add(par[1]);
                }
            }

            c.rotarEscenarios();
            int total = tam + hijos.size();
            Cromosoma[] todos = new Cromosoma[total];
            double[] fitTodos = new double[total];
            for (int i = 0; i < tam; i++) {
                todos[i] = pob[i];
                fitTodos[i] = c.evaluar(pob[i]);
            }
            for (int h = 0; h < hijos.size(); h++) {
                todos[tam + h] = hijos.get(h);
                fitTodos[tam + h] = c.evaluar(hijos.get(h));
            }

            // Supervivencia elitista: los mejores tam de padres + hijos.
            int[] mejores = mejoresIndices(fitTodos, tam);
            boolean entroAlguno = false;
            for (int i = 0; i < tam; i++) {
                if (mejores[i] >= tam) {
                    entroAlguno = true;
                }
            }
            Cromosoma[] nueva = new Cromosoma[tam];
            for (int i = 0; i < tam; i++) {
                nueva[i] = todos[mejores[i]];
                fit[i] = fitTodos[mejores[i]];
            }
            System.arraycopy(nueva, 0, pob, 0, tam);
            c.out.generaciones++;

            if (!entroAlguno) {
                umbral--;
            }
            if (umbral < 0) {
                // Reinicio cataclísmico: se conserva el mejor y el resto se
                // genera perturbándolo fuertemente.
                int idx = mejoresIndices(fit, 1)[0];
                Cromosoma mejor = pob[idx].copia();
                for (int i = 0; i < tam; i++) {
                    pob[i] = i == 0 ? mejor : Operadores.perturbar(c.inst, mejor, 0.35, c.rng);
                    if (c.cfg.grilla) {
                        Operadores.proyectarAGrilla(pob[i]);
                    }
                }
                c.rotarEscenarios();
                for (int i = 0; i < tam && c.restantes() > 0; i++) {
                    fit[i] = c.evaluar(pob[i]);
                }
                umbral = umbralInicial;
                c.out.cataclismos++;
            }
            registrar(c, pob, fit);
        }
    }

    // ----------------------------------------------------------------
    // Búsqueda aleatoria (referencia)
    // ----------------------------------------------------------------

    /**
     * Búsqueda aleatoria con EXACTAMENTE el mismo presupuesto y la misma
     * selección final que el AE.
     *
     * [Informe §IV-B(4)]
     */
    public static Resultado busquedaAleatoria(Instancia inst, Config cfg, Parametros p) {
        long t0 = System.nanoTime();
        Aleatorio rng = new Aleatorio(cfg.semilla * 31 + 7);
        Resultado out = new Resultado();
        int presupuesto = Math.max(1, cfg.maxEvaluaciones - cfg.costoSeleccion());
        List<Candidato> candidatos = new ArrayList<>();

        for (int i = 0; i < presupuesto; i++) {
            if (cfg.segundosMax > 0 && (System.nanoTime() - t0) / 1e9 >= cfg.segundosMax) {
                break;
            }
            Cromosoma c = i == 0 ? Cromosoma.statuQuo(inst) : Operadores.aleatorioSesgado(inst, rng);
            if (cfg.grilla) {
                Operadores.proyectarAGrilla(c);
            }
            double f = Evaluador.evaluar(inst, c, cfg.replicas, cfg.semillaBusqueda(), p).valor;
            insertar(candidatos, new Candidato(c, f), cfg.finalistas);
        }
        out.evaluaciones = presupuesto + cfg.costoSeleccion();
        out.mejor = seleccionFinal(inst, candidatos, cfg, p);
        out.fitnessMejor = Evaluador.evaluar(inst, out.mejor, N_VALIDACION, SEMILLA_VALIDACION, p);
        out.segundos = (System.nanoTime() - t0) / 1e9;
        return out;
    }

    // ----------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------

    /** Anota el mejor de la generación como candidato y actualiza los historiales. */
    private static void registrar(Corrida c, Cromosoma[] pob, double[] fit) {
        int idx = mejoresIndices(fit, 1)[0];
        c.candidatos.add(new Candidato(pob[idx].copia(), fit[idx]));
        c.out.historialMejor.add(fit[idx]);
        c.out.historialMedia.add(Estadistica.media(fit));
        c.out.historialDiversidad.add(diversidad(pob, c.rng));
    }

    /** Distancia media entre pares, estimada sobre una muestra de pares. */
    private static double diversidad(Cromosoma[] pob, Aleatorio rng) {
        // Se usa un generador aparte para no alterar la secuencia del AE.
        Aleatorio r = new Aleatorio(pob.length * 7919L + pob[0].n);
        int pares = Math.min(200, pob.length * (pob.length - 1) / 2);
        double suma = 0;
        for (int k = 0; k < pares; k++) {
            int i = r.entero(0, pob.length);
            int j = r.entero(0, pob.length);
            suma += Operadores.distancia(pob[i], pob[j]);
        }
        return suma / pares;
    }

    private static void insertar(List<Candidato> lista, Candidato c, int maximo) {
        lista.add(c);
        if (lista.size() > 4 * maximo) {
            lista.sort(Comparator.comparingDouble((Candidato x) -> -x.fitness()));
            lista.subList(maximo, lista.size()).clear();
        }
    }

    /**
     * Segunda etapa: remide a los K mejores candidatos distintos sobre un mismo
     * conjunto de escenarios, que no se usó ni en la búsqueda ni se usa en la
     * validación, y devuelve al ganador.
     *
     * [Informe §III-B manejo del ruido: reemplaza al "N creciente" (DECISIONES M3)]
     */
    public static Cromosoma seleccionFinal(Instancia inst, List<Candidato> candidatos,
                                           Config cfg, Parametros p) {
        candidatos.sort(Comparator.comparingDouble((Candidato x) -> -x.fitness()));
        List<Cromosoma> finalistas = new ArrayList<>();
        for (Candidato c : candidatos) {
            boolean repetido = false;
            for (Cromosoma f : finalistas) {
                if (f.igualA(c.plan())) {
                    repetido = true;
                    break;
                }
            }
            if (!repetido) {
                finalistas.add(c.plan());
            }
            if (finalistas.size() >= cfg.finalistas) {
                break;
            }
        }
        Cromosoma mejor = finalistas.get(0);
        double mejorValor = Double.NEGATIVE_INFINITY;
        for (Cromosoma c : finalistas) {
            double v = Evaluador.evaluar(
                    inst, c, cfg.replicasSeleccion, cfg.semillaSeleccion(), p).valor;
            if (v > mejorValor) {
                mejorValor = v;
                mejor = c;
            }
        }
        return mejor;
    }

    private static void copiarEn(Cromosoma origen, Cromosoma destino) {
        System.arraycopy(origen.accion, 0, destino.accion, 0, origen.n);
        System.arraycopy(origen.ventana, 0, destino.ventana, 0, origen.n);
        System.arraycopy(origen.duracion, 0, destino.duracion, 0, origen.n);
        System.arraycopy(origen.salario, 0, destino.salario, 0, origen.n);
        System.arraycopy(origen.clausula, 0, destino.clausula, 0, origen.n);
        System.arraycopy(origen.prioridad, 0, destino.prioridad, 0, origen.n);
    }

    private static double recortar(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int[] mejoresIndices(double[] fitness, int k) {
        Integer[] orden = new Integer[fitness.length];
        for (int i = 0; i < fitness.length; i++) {
            orden[i] = i;
        }
        java.util.Arrays.sort(orden, (a, b) -> Double.compare(fitness[b], fitness[a]));
        int[] out = new int[k];
        for (int i = 0; i < k; i++) {
            out[i] = orden[i];
        }
        return out;
    }
}
