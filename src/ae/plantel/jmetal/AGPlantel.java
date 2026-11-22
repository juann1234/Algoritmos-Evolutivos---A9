package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.AlgoritmoEvolutivo.Candidato;
import ae.plantel.AlgoritmoEvolutivo.Config;
import ae.plantel.Cromosoma;
import ae.plantel.Estadistica;
import ae.plantel.Evaluador;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;
import ae.plantel.Parametros;
import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.util.comparator.ObjectiveComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AE generacional con elitismo sobre el esqueleto
 * {@code AbstractGeneticAlgorithm} de jMetal 5.10, con el manejo de ruido del
 * proyecto.
 *
 * [Informe §III-B manejo del ruido; §III-C reemplazo generacional con
 * elitismo, parada por evaluaciones; DECISIONES D-A1]
 *
 * jMetal pone el ciclo (Tema 6): inicializar, evaluar, y mientras no se
 * cumpla la parada: seleccionar, reproducir, evaluar hijos, reemplazar. Lo
 * que esta clase agrega es lo que exige un fitness ruidoso, igual que el AE de
 * referencia ({@link AlgoritmoEvolutivo}):
 *
 *  1. ESCENARIOS COMUNES (M1): cada generación se evalúa sobre un conjunto de N
 *     escenarios propio; se rota en {@link #reproduction} justo antes de evaluar
 *     a los hijos.
 *  2. REEVALUACIÓN (M2): los elitistas se eligen con el fitness de la
 *     generación anterior y se REEVALÚAN sobre los escenarios nuevos antes de
 *     convivir con los hijos; si no, un individuo con suerte quedaría fijo.
 *  3. SELECCIÓN FINAL EN DOS ETAPAS (M3): al terminar, los 10 mejores
 *     candidatos se remiden con más réplicas sobre un mismo conjunto de
 *     escenarios ({@link AlgoritmoEvolutivo#seleccionFinal}).
 *  4. PRESUPUESTO EN EVALUACIONES (M5), incluida la selección final.
 *
 * Diferencia con el AE de referencia: jMetal hace TODAS las selecciones de una
 * generación antes de cruzar, mientras que el AE propio intercala torneo y
 * cruzamiento. Con la misma semilla los dos consumen el azar en otro orden, así
 * que no dan el mismo plan; la equivalencia se valida en distribución
 * ({@code ValidarEquivalenciaJMetal}).
 */
public final class AGPlantel extends AbstractGeneticAlgorithm<SolucionPlantel, SolucionPlantel> {

    private static final long serialVersionUID = 1L;

    private final Config cfg;
    private final ProblemaPlantel problema;
    private final Aleatorio rng;
    private final int presupuesto;
    private final long t0;
    private final List<Candidato> candidatos = new ArrayList<>();
    private final AlgoritmoEvolutivo.Resultado resultado = new AlgoritmoEvolutivo.Resultado();
    private final Comparator<SolucionPlantel> porObjetivo = new ObjectiveComparator<>(0);

    /** Arma el AE con los operadores del proyecto (ver {@link OperadoresJMetal}). */
    public AGPlantel(Instancia inst, Config cfg, Parametros p) {
        this(new ProblemaPlantel(inst, p, cfg.replicas,
                cfg.semillaEscenariosFija != null ? cfg.semillaEscenariosFija : cfg.semilla * 1_000_003L,
                new Aleatorio(cfg.semilla)), cfg);
    }

    private AGPlantel(ProblemaPlantel problema, Config cfg) {
        super(problema);
        this.problema = problema;
        this.cfg = cfg;
        this.rng = problema.rng();
        setMaxPopulationSize(cfg.poblacion);
        OperadoresJMetal.Cruzamiento cruce = new OperadoresJMetal.Cruzamiento(cfg.pC, rng, cfg.grilla);
        this.crossoverOperator = cfg.cruce == AlgoritmoEvolutivo.Cruce.BLOQUES_POSICIONALES
                ? cruce.porBloques(problema.instancia()) : cruce;
        this.mutationOperator = new OperadoresJMetal.Mutacion(problema.instancia(), cfg.pM, rng, cfg.grilla);
        this.selectionOperator = cfg.seleccion == AlgoritmoEvolutivo.Seleccion.PROPORCIONAL_ESCALADA
                ? new OperadoresJMetal.ProporcionalEscalada(rng)
                : new OperadoresJMetal.Torneo(cfg.torneo, porObjetivo, rng);
        // Mismo descuento que el AE de referencia: la selección final consume
        // parte del presupuesto.
        this.presupuesto = Math.max(cfg.poblacion, cfg.maxEvaluaciones - cfg.costoSeleccion());
        this.t0 = System.nanoTime();
    }

    // ----------------------------------------------------------------
    // Pasos del esqueleto de jMetal
    // ----------------------------------------------------------------

    /**
     * Statu quo + perturbaciones suyas + aleatorios sesgados (D-A3).
     *
     * [Informe §III-C inicialización]
     */
    @Override
    protected List<SolucionPlantel> createInitialPopulation() {
        Cromosoma[] pob = Operadores.poblacionInicial(problema.instancia(), cfg.poblacion, rng);
        List<SolucionPlantel> out = new ArrayList<>(pob.length);
        for (Cromosoma c : pob) {
            if (cfg.grilla) {
                Operadores.proyectarAGrilla(c);
            }
            out.add(SolucionPlantel.desde(c, 1));
        }
        return out;
    }

    @Override
    protected List<SolucionPlantel> evaluatePopulation(List<SolucionPlantel> poblacion) {
        for (SolucionPlantel s : poblacion) {
            problema.evaluate(s);
        }
        return poblacion;
    }

    /** Pool de apareamiento: tantos padres como hijos hacen falta (P - élite, par). */
    @Override
    protected List<SolucionPlantel> selection(List<SolucionPlantel> poblacion) {
        int hijos = hijosPorGeneracion();
        if (selectionOperator instanceof OperadoresJMetal.ProporcionalEscalada sus) {
            return sus.seleccionar(poblacion, hijos);   // ruleta con punteros equiespaciados
        }
        List<SolucionPlantel> pool = new ArrayList<>(hijos);
        for (int i = 0; i < hijos; i++) {
            pool.add(selectionOperator.execute(poblacion));
        }
        return pool;
    }

    /**
     * Cruzamiento y mutación de a pares; después se rotan los escenarios, porque
     * lo que sigue es evaluar la generación nueva.
     */
    @Override
    protected List<SolucionPlantel> reproduction(List<SolucionPlantel> pool) {
        List<SolucionPlantel> hijos = new ArrayList<>(pool.size());
        for (int i = 0; i + 1 < pool.size(); i += 2) {
            for (SolucionPlantel h : crossoverOperator.execute(List.of(pool.get(i), pool.get(i + 1)))) {
                hijos.add(mutationOperator.execute(h));
            }
        }
        if (cfg.semillaEscenariosFija == null) {
            problema.rotarEscenarios();   // con escenarios fijos (solo para GAP) no se rota
        }
        return hijos;
    }

    /**
     * Reemplazo generacional con elitismo: los {@code cfg.elite} mejores de la
     * generación anterior (según los escenarios anteriores) se reevalúan sobre
     * los escenarios nuevos y completan la población junto con los hijos.
     *
     * [Informe §III-C reemplazo; §III-B reevaluación de la élite]
     */
    @Override
    protected List<SolucionPlantel> replacement(List<SolucionPlantel> poblacion,
                                                List<SolucionPlantel> hijos) {
        List<SolucionPlantel> ordenada = new ArrayList<>(poblacion);
        ordenada.sort(porObjetivo);
        int cupoHijos = cfg.poblacion - cfg.elite;
        List<SolucionPlantel> nueva = new ArrayList<>(cfg.poblacion);
        for (int e = 0; e < cfg.elite; e++) {
            SolucionPlantel elite = ordenada.get(e).copy();
            problema.evaluate(elite);
            nueva.add(elite);
        }
        nueva.addAll(hijos.subList(0, Math.min(cupoHijos, hijos.size())));
        return nueva;
    }

    @Override
    protected void initProgress() {
        registrar(getPopulation());
    }

    @Override
    protected void updateProgress() {
        resultado.generaciones++;
        registrar(getPopulation());
    }

    /**
     * Parada por esfuerzo prefijado en evaluaciones (Tema 5): no se empieza una
     * generación que no entra entera en el presupuesto. Además, límite de tiempo
     * de reloj si {@code cfg.segundosMax > 0}.
     *
     * [Informe §III-C criterio de parada: evaluaciones + tiempo de reloj]
     */
    @Override
    protected boolean isStoppingConditionReached() {
        boolean vencido = cfg.segundosMax > 0 && (System.nanoTime() - t0) / 1e9 >= cfg.segundosMax;
        return vencido || presupuesto - problema.evaluaciones() < cfg.poblacion;
    }

    @Override
    public void run() {
        super.run();
        resultado.evaluaciones = (int) problema.evaluaciones() + cfg.costoSeleccion();
        resultado.mejor = AlgoritmoEvolutivo.seleccionFinal(problema.instancia(), candidatos, cfg,
                problema.parametros());
        resultado.fitnessMejor = Evaluador.evaluar(problema.instancia(), resultado.mejor,
                AlgoritmoEvolutivo.N_VALIDACION, AlgoritmoEvolutivo.SEMILLA_VALIDACION,
                problema.parametros());
        resultado.segundos = (System.nanoTime() - t0) / 1e9;
    }

    /** El plan ganador de la selección final (llamar después de {@link #run()}). */
    @Override
    public SolucionPlantel getResult() {
        SolucionPlantel s = SolucionPlantel.desde(resultado.mejor, 1);
        s.setObjective(0, -resultado.fitnessMejor.valor);
        s.setAttribute(ProblemaPlantel.ATRIBUTO_FITNESS, resultado.fitnessMejor);
        return s;
    }

    /** Resultado en el mismo formato que el AE de referencia (historiales incluidos). */
    public AlgoritmoEvolutivo.Resultado resultado() {
        return resultado;
    }

    @Override
    public String getName() {
        return "AGPlantel";
    }

    @Override
    public String getDescription() {
        return "AE generacional con elitismo y escenarios comunes, sobre jMetal 5.10";
    }

    // ----------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------

    private int hijosPorGeneracion() {
        int h = cfg.poblacion - cfg.elite;
        return h % 2 == 0 ? h : h + 1;   // el cruzamiento produce de a dos
    }

    /** Anota el mejor de la generación como candidato y actualiza los historiales. */
    private void registrar(List<SolucionPlantel> pob) {
        SolucionPlantel mejor = pob.get(0);
        double[] fit = new double[pob.size()];
        Cromosoma[] planes = new Cromosoma[pob.size()];
        for (int i = 0; i < pob.size(); i++) {
            SolucionPlantel s = pob.get(i);
            fit[i] = ProblemaPlantel.fitness(s);
            planes[i] = s.aCromosoma();
            if (porObjetivo.compare(s, mejor) < 0) {
                mejor = s;
            }
        }
        candidatos.add(new Candidato(mejor.aCromosoma(), ProblemaPlantel.fitness(mejor)));
        resultado.historialMejor.add(ProblemaPlantel.fitness(mejor));
        resultado.historialMedia.add(Estadistica.media(fit));
        resultado.historialDiversidad.add(diversidad(planes));
    }

    /** Distancia media entre pares (muestra de 200 pares), como el AE de referencia. */
    private static double diversidad(Cromosoma[] pob) {
        Aleatorio r = new Aleatorio(pob.length * 7919L + pob[0].n);
        int pares = Math.min(200, pob.length * (pob.length - 1) / 2);
        double suma = 0;
        for (int k = 0; k < pares; k++) {
            suma += Operadores.distancia(pob[r.entero(0, pob.length)], pob[r.entero(0, pob.length)]);
        }
        return suma / pares;
    }

    /** Atajo: corre el AE y devuelve el resultado. */
    public static AlgoritmoEvolutivo.Resultado ejecutar(Instancia inst, Config cfg, Parametros p) {
        AGPlantel ag = new AGPlantel(inst, cfg, p);
        ag.run();
        return ag.resultado();
    }
}
