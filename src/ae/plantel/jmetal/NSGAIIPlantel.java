package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Cromosoma;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;
import ae.plantel.Parametros;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.operator.selection.impl.RankingAndCrowdingSelection;
import org.uma.jmetal.util.SolutionListUtils;
import org.uma.jmetal.util.comparator.DominanceComparator;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * NSGA-II de jMetal 5.10 aplicado al problema del plantel, con el manejo de
 * ruido del proyecto.
 *
 * [Informe §III-C variante multiobjetivo con NSGA-II [4]; Tema 10; DECISIONES
 * D-A1 y D-A6]
 *
 * Es la clase {@code NSGAII} de jMetal (selección por torneo binario con rango
 * y crowding, reemplazo (μ+λ) por ordenamiento no dominado y distancia de
 * crowding, Tema 10). Se cambian solo tres cosas:
 *
 *  1. Población inicial y operadores del proyecto ({@link OperadoresJMetal}),
 *     con el generador de la corrida (D-A2).
 *  2. ESCENARIOS COMUNES con REEVALUACIÓN (M1, M2): los escenarios rotan en
 *     cada generación y, antes del reemplazo (μ+λ), los padres se reevalúan
 *     sobre los mismos escenarios que los hijos. Comparar padres medidos con
 *     un azar contra hijos medidos con otro favorecería a quien tuvo suerte.
 *     Cuesta P evaluaciones extra por generación, y se cuentan.
 *  3. El resultado se REVALÚA con N = 200 sobre los escenarios de validación y
 *     se vuelve a filtrar por no dominancia: es el frente que se reporta.
 *
 * Presupuesto (M5): cada generación cuesta 2P evaluaciones (P hijos + P padres
 * reevaluados); no se empieza una generación que no entra entera.
 */
public final class NSGAIIPlantel extends NSGAII<SolucionPlantel> {

    private static final long serialVersionUID = 1L;

    private final ProblemaPlantelMO problema;
    private final Aleatorio rng;
    private final int poblacion;
    private final int presupuesto;
    private final double segundosMax;
    private final long t0 = System.nanoTime();
    private int generaciones;
    private List<SolucionPlantel> frenteValidado;

    public NSGAIIPlantel(Instancia inst, AlgoritmoEvolutivo.Config cfg, Parametros p) {
        this(new ProblemaPlantelMO(inst, p, cfg.replicas, cfg.semilla * 1_000_003L,
                new Aleatorio(cfg.semilla)), cfg);
    }

    private NSGAIIPlantel(ProblemaPlantelMO problema, AlgoritmoEvolutivo.Config cfg) {
        super(problema, cfg.maxEvaluaciones, par(cfg.poblacion), par(cfg.poblacion), par(cfg.poblacion),
                new OperadoresJMetal.Cruzamiento(cfg.pC, problema.rng(), cfg.grilla),
                new OperadoresJMetal.Mutacion(problema.instancia(), cfg.pM, problema.rng(), cfg.grilla),
                new OperadoresJMetal.Torneo(2, new RankingAndCrowdingDistanceComparator<>(), problema.rng()),
                new DominanceComparator<>(),
                new SequentialSolutionListEvaluator<>());
        this.problema = problema;
        this.rng = problema.rng();
        this.poblacion = par(cfg.poblacion);
        this.presupuesto = cfg.maxEvaluaciones;
        this.segundosMax = cfg.segundosMax;
    }

    private static int par(int n) {
        return n % 2 == 0 ? n : n + 1;
    }

    /** [Informe §III-C inicialización] statu quo + perturbaciones + aleatorios sesgados. */
    @Override
    protected List<SolucionPlantel> createInitialPopulation() {
        Cromosoma[] pob = Operadores.poblacionInicial(problema.instancia(), poblacion, rng);
        List<SolucionPlantel> out = new ArrayList<>(pob.length);
        for (Cromosoma c : pob) {
            out.add(SolucionPlantel.desde(c, 2));
        }
        return out;
    }

    @Override
    protected List<SolucionPlantel> evaluatePopulation(List<SolucionPlantel> pob) {
        for (SolucionPlantel s : pob) {
            problema.evaluate(s);
        }
        return pob;
    }

    /**
     * La población inicial todavía no tiene rango ni crowding (jMetal los
     * calcula recién en el reemplazo), y el torneo los necesita: se calculan
     * acá.
     */
    @Override
    protected void initProgress() {
        setPopulation(new RankingAndCrowdingSelection<SolucionPlantel>(poblacion, dominanceComparator)
                .execute(getPopulation()));
    }

    @Override
    protected void updateProgress() {
        generaciones++;
    }

    /** [Informe §III-C parada: evaluaciones + tiempo de reloj] */
    @Override
    protected boolean isStoppingConditionReached() {
        boolean vencido = segundosMax > 0 && (System.nanoTime() - t0) / 1e9 >= segundosMax;
        return vencido || presupuesto - problema.evaluaciones() < 2L * poblacion;
    }

    /** Cruzamiento y mutación de jMetal; después, escenarios nuevos para la generación. */
    @Override
    protected List<SolucionPlantel> reproduction(List<SolucionPlantel> pool) {
        List<SolucionPlantel> hijos = super.reproduction(pool);
        problema.rotarEscenarios();
        return hijos;
    }

    /**
     * Reemplazo (μ+λ) de NSGA-II, con los padres reevaluados sobre los mismos
     * escenarios que los hijos.
     *
     * [Informe §III-B reevaluación; Tema 10 NSGA-II]
     */
    @Override
    protected List<SolucionPlantel> replacement(List<SolucionPlantel> padres,
                                                List<SolucionPlantel> hijos) {
        List<SolucionPlantel> reevaluados = new ArrayList<>(padres.size());
        for (SolucionPlantel s : padres) {
            SolucionPlantel c = s.copy();
            problema.evaluate(c);
            reevaluados.add(c);
        }
        return super.replacement(reevaluados, hijos);
    }

    @Override
    public void run() {
        super.run();
        // Revaluación del conjunto no dominado final con escenarios de validación.
        List<SolucionPlantel> finales = SolutionListUtils.getNonDominatedSolutions(getPopulation());
        ProblemaPlantelMO validador = new ProblemaPlantelMO(problema.instancia(), problema.parametros(),
                AlgoritmoEvolutivo.N_VALIDACION, AlgoritmoEvolutivo.SEMILLA_VALIDACION, rng);
        List<SolucionPlantel> revaluados = new ArrayList<>();
        for (SolucionPlantel s : finales) {
            SolucionPlantel c = SolucionPlantel.desde(s.aCromosoma(), 2);
            validador.evaluate(c);
            revaluados.add(c);
        }
        frenteValidado = sinRepetidos(SolutionListUtils.getNonDominatedSolutions(revaluados));
    }

    /** Frente final, revaluado con N = 200 sobre escenarios de validación. */
    @Override
    public List<SolucionPlantel> getResult() {
        return frenteValidado;
    }

    public int generaciones() {
        return generaciones;
    }

    public long evaluaciones() {
        return problema.evaluaciones();
    }

    @Override
    public String getName() {
        return "NSGAIIPlantel";
    }

    @Override
    public String getDescription() {
        return "NSGA-II de jMetal 5.10 con escenarios comunes y reevaluación de padres";
    }

    /**
     * Quita soluciones con el mismo vector de objetivos. Dos planes distintos
     * pueden dar exactamente los mismos objetivos cuando difieren solo en genes
     * que no se expresan (por ejemplo, la prioridad de minutos de un candidato
     * que no se compra): son el mismo punto del frente y contarlos dos veces
     * sesgaría spread y GD.
     */
    private static List<SolucionPlantel> sinRepetidos(List<SolucionPlantel> lista) {
        List<SolucionPlantel> out = new ArrayList<>();
        for (SolucionPlantel s : lista) {
            boolean repetido = false;
            for (SolucionPlantel o : out) {
                if (java.util.Arrays.equals(o.getObjectives(), s.getObjectives())) {
                    repetido = true;
                    break;
                }
            }
            if (!repetido) {
                out.add(s);
            }
        }
        return out;
    }
}
