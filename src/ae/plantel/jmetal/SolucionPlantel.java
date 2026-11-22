package ae.plantel.jmetal;

import ae.plantel.Cromosoma;
import org.uma.jmetal.solution.AbstractSolution;

/**
 * Solución de jMetal para el problema del plantel: una lista de {@link Gen},
 * uno por jugador de la instancia (propios y candidatos), en el mismo orden
 * que {@code Instancia.jugadores}.
 *
 * [Informe §III-A representación; DECISIONES D-A1]
 *
 * jMetal 5.10 (Tema 6) pide que el problema defina su propia codificación
 * cuando no es una de las clásicas (binaria, entera, real, permutación). Esta
 * clase es esa codificación. El simulador y los operadores de Juan trabajan
 * sobre {@link Cromosoma} (arrays paralelos, más rápidos), así que la solución
 * se convierte de ida y vuelta con {@link #aCromosoma()} y
 * {@link #desde(Cromosoma, int)}. La conversión es O(n) y cuesta nada frente
 * a una evaluación (unos 3 ms con N = 30).
 */
public final class SolucionPlantel extends AbstractSolution<Gen> {

    private static final long serialVersionUID = 1L;

    public SolucionPlantel(int numeroDeGenes, int numeroDeObjetivos) {
        super(numeroDeGenes, numeroDeObjetivos);
    }

    /** Construye la solución a partir de un cromosoma (sin evaluar). */
    public static SolucionPlantel desde(Cromosoma c, int numeroDeObjetivos) {
        SolucionPlantel s = new SolucionPlantel(c.n, numeroDeObjetivos);
        s.copiarDesde(c);
        return s;
    }

    /** Sobrescribe los genes con los del cromosoma (mutación en el lugar). */
    public void copiarDesde(Cromosoma c) {
        for (int i = 0; i < c.n; i++) {
            setVariable(i, new Gen(c.accion[i], c.ventana[i], c.duracion[i],
                    c.salario[i], c.clausula[i], c.prioridad[i]));
        }
    }

    /** Vuelca los genes a un cromosoma, que es lo que entiende el simulador. */
    public Cromosoma aCromosoma() {
        int n = getNumberOfVariables();
        Cromosoma c = new Cromosoma(n);
        for (int i = 0; i < n; i++) {
            Gen g = getVariable(i);
            c.accion[i] = g.accion();
            c.ventana[i] = g.ventana();
            c.duracion[i] = g.duracion();
            c.salario[i] = g.salario();
            c.clausula[i] = g.clausula();
            c.prioridad[i] = g.prioridad();
        }
        return c;
    }

    @Override
    public SolucionPlantel copy() {
        SolucionPlantel s = new SolucionPlantel(getNumberOfVariables(), getNumberOfObjectives());
        for (int i = 0; i < getNumberOfVariables(); i++) {
            s.setVariable(i, getVariable(i));   // Gen es inmutable: alcanza con la referencia
        }
        for (int k = 0; k < getNumberOfObjectives(); k++) {
            s.setObjective(k, getObjective(k));
        }
        s.attributes.putAll(attributes);
        return s;
    }
}
