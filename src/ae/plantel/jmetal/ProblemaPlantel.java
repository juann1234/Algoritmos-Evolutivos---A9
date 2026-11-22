package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.Evaluador;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;
import ae.plantel.Parametros;
import org.uma.jmetal.problem.Problem;

/**
 * El problema del plantel como {@code Problem} de jMetal: versión
 * mono-objetivo.
 *
 * [Informe §III-B ec. (3); DECISIONES D-A1]
 *
 * El objetivo es el fitness f(x) de la ec. (3) calculado por
 * {@link Evaluador#evaluar}, guardado NEGADO porque jMetal minimiza (Tema 6:
 * "en general los algoritmos de jMetal resuelven problemas de
 * minimización"). El {@link Evaluador.Fitness} completo queda como atributo
 * de la solución para los reportes.
 *
 * ESCENARIOS COMUNES (informe §III-B, DECISIONES M1). El problema guarda la
 * semilla de los N escenarios con que se evalúa. El algoritmo la rota una vez
 * por generación con {@link #rotarEscenarios()}, así toda la población de una
 * generación se mide sobre el mismo azar. Esto vuelve al problema un objeto
 * con estado: una instancia de ProblemaPlantel sirve para UNA corrida.
 */
public class ProblemaPlantel implements Problem<SolucionPlantel> {

    private static final long serialVersionUID = 1L;

    /** Clave del atributo donde queda el {@link Evaluador.Fitness} completo. */
    public static final String ATRIBUTO_FITNESS = "fitnessPlantel";

    protected final Instancia instancia;
    protected final Parametros parametros;
    protected final int replicas;
    protected final Aleatorio rng;
    protected long semillaEscenarios;
    protected long evaluaciones;

    /**
     * @param replicas          N, escenarios por evaluación
     * @param semillaEscenarios semilla del primer conjunto de escenarios
     * @param rng               generador de la corrida (para createSolution); ver D-A2
     */
    public ProblemaPlantel(Instancia instancia, Parametros parametros, int replicas,
                           long semillaEscenarios, Aleatorio rng) {
        this.instancia = instancia;
        this.parametros = parametros;
        this.replicas = replicas;
        this.semillaEscenarios = semillaEscenarios;
        this.rng = rng;
    }

    @Override
    public int getNumberOfVariables() {
        return instancia.jugadores.size();
    }

    @Override
    public int getNumberOfObjectives() {
        return 1;
    }

    @Override
    public int getNumberOfConstraints() {
        // Las restricciones duras se reparan en el decodificador y las blandas
        // se penalizan dentro del fitness (informe §III-A), así que jMetal no
        // ve ninguna.
        return 0;
    }

    @Override
    public String getName() {
        return "PlantelMonoObjetivo(" + instancia.nombre + ")";
    }

    @Override
    public void evaluate(SolucionPlantel solucion) {
        Evaluador.Fitness f = Evaluador.evaluar(instancia, solucion.aCromosoma(), replicas,
                semillaEscenarios, parametros);
        evaluaciones++;
        asignarObjetivos(solucion, f);
        solucion.setAttribute(ATRIBUTO_FITNESS, f);
    }

    /** Traduce el fitness a los objetivos de jMetal (que minimiza). */
    protected void asignarObjetivos(SolucionPlantel solucion, Evaluador.Fitness f) {
        solucion.setObjective(0, -f.valor);
    }

    /**
     * Individuo aleatorio sesgado a la inacción, como en el AE propio
     * ({@link Operadores#aleatorioSesgado}). La población inicial completa la
     * arma el algoritmo con {@link Operadores#poblacionInicial}.
     *
     * [Informe §III-C inicialización]
     */
    @Override
    public SolucionPlantel createSolution() {
        return SolucionPlantel.desde(Operadores.aleatorioSesgado(instancia, rng),
                getNumberOfObjectives());
    }

    /** Pasa al siguiente conjunto de N escenarios (una vez por generación). */
    public void rotarEscenarios() {
        semillaEscenarios += replicas;
    }

    /**
     * Fija la semilla de los escenarios. La usa el modelo de islas para que
     * todas las islas evalúen cada generación sobre el MISMO azar y los
     * migrantes sean comparables con los locales (escenarios comunes, M1).
     */
    public void fijarSemillaEscenarios(long semilla) {
        semillaEscenarios = semilla;
    }

    /** f(x) de una solución evaluada por este problema (deshace el signo). */
    public static double fitness(SolucionPlantel s) {
        return -s.getObjective(0);
    }

    public long evaluaciones() {
        return evaluaciones;
    }

    public Instancia instancia() {
        return instancia;
    }

    public Parametros parametros() {
        return parametros;
    }

    public Aleatorio rng() {
        return rng;
    }
}
