package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.Evaluador;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Parametros;

/**
 * Variante multiobjetivo del problema del plantel.
 *
 * [Informe §III-C: "versión multiobjetivo con NSGA-II, con f1 el rendimiento
 * esperado y f2 el costo neto"; DECISIONES D-A6]
 *
 * Objetivos (jMetal minimiza):
 * <ul>
 *   <li>objetivo 0 = −f₁, con f₁ = E[Σₜ γᵗ Rₜ] − E[penalización]: utilidad
 *       deportiva (puntos de liga descontados);</li>
 *   <li>objetivo 1 = f₂ = E[gasto en pases + salarios − patrimonio en jugadores]:
 *       costo neto del plan, en millones.</li>
 * </ul>
 *
 * Por qué estos dos y no otros: la caja final es una constante menos el costo
 * neto, así que el fitness mono-objetivo es una agregación lineal de los dos,
 * <pre>  f(x) + ρ·σ̂(x) = f₁(x) − β·f₂(x) + β·(presupuesto + T·tope)</pre>
 * con T temporadas. Elegir β es elegir un punto del frente; NSGA-II devuelve el
 * frente entero y deja la elección al director deportivo. La identidad se
 * verifica en {@code ValidarMO}.
 *
 * Penalizaciones: van dentro de f₁ (no como restricciones de jMetal) para que
 * la identidad sea exacta. Son chicas: tras la reparación, el 99,6% de los
 * escenarios son factibles (VALIDACION §1).
 */
public final class ProblemaPlantelMO extends ProblemaPlantel {

    private static final long serialVersionUID = 1L;

    public ProblemaPlantelMO(Instancia instancia, Parametros parametros, int replicas,
                             long semillaEscenarios, Aleatorio rng) {
        super(instancia, parametros, replicas, semillaEscenarios, rng);
    }

    @Override
    public int getNumberOfObjectives() {
        return 2;
    }

    @Override
    public String getName() {
        return "PlantelMultiobjetivo(" + instancia.nombre + ")";
    }

    @Override
    protected void asignarObjetivos(SolucionPlantel solucion, Evaluador.Fitness f) {
        solucion.setObjective(0, -f.utilidadDeportivaMedia);
        solucion.setObjective(1, f.costoNetoMedio);
    }

    /** Constante C de la identidad f + ρσ = f₁ − β f₂ + C. */
    public static double constanteAgregacion(Instancia inst, Parametros p) {
        return p.beta * (inst.presupuestoInicial + p.temporadas * inst.topeMasaSalarial);
    }

    /** f₁ de una solución evaluada por este problema. */
    public static double utilidadDeportiva(SolucionPlantel s) {
        return -s.getObjective(0);
    }

    /** f₂ de una solución evaluada por este problema. */
    public static double costoNeto(SolucionPlantel s) {
        return s.getObjective(1);
    }
}
