package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.Cromosoma;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;

import java.util.Comparator;
import java.util.List;

/**
 * Los operadores evolutivos del proyecto con las interfaces de jMetal.
 *
 * [Informe §III-C operadores; DECISIONES D-A1 y D-A2]
 *
 * La lógica de cada operador es la de {@link Operadores} (código de Juan,
 * validado): acá solo se adapta la interfaz. Todos reciben el
 * {@link Aleatorio} de la corrida en lugar de usar {@code JMetalRandom}, que es
 * un singleton global: con corridas en paralelo, el singleton haría que una
 * semilla no reprodujera su corrida (D-A2).
 */
public final class OperadoresJMetal {

    private OperadoresJMetal() {
    }

    /**
     * Cruzamiento uniforme a nivel de gen + aritmético en los reales.
     *
     * [Informe §III-C cruzamiento]
     */
    public static final class Cruzamiento implements CrossoverOperator<SolucionPlantel> {
        private static final long serialVersionUID = 1L;
        private final double pC;
        private final Aleatorio rng;
        private final boolean grilla;

        /** @param grilla si true, proyecta los hijos a la grilla de OptimoExacto */
        public Cruzamiento(double pC, Aleatorio rng, boolean grilla) {
            this.pC = pC;
            this.rng = rng;
            this.grilla = grilla;
        }

        /** Instancia para el cruzamiento por bloques; null = uniforme a nivel de gen. */
        private Instancia bloques;

        /** Pasa al cruzamiento por bloques posicionales (P11). */
        public Cruzamiento porBloques(Instancia inst) {
            this.bloques = inst;
            return this;
        }

        @Override
        public List<SolucionPlantel> execute(List<SolucionPlantel> padres) {
            SolucionPlantel a = padres.get(0);
            SolucionPlantel b = padres.get(1);
            Cromosoma[] hijos = bloques == null
                    ? Operadores.cruzar(a.aCromosoma(), b.aCromosoma(), pC, rng)
                    : Operadores.cruzarPorBloques(bloques, a.aCromosoma(), b.aCromosoma(), pC, rng);
            if (grilla) {
                Operadores.proyectarAGrilla(hijos[0]);
                Operadores.proyectarAGrilla(hijos[1]);
            }
            int obj = a.getNumberOfObjectives();
            return List.of(SolucionPlantel.desde(hijos[0], obj), SolucionPlantel.desde(hijos[1], obj));
        }

        @Override
        public double getCrossoverProbability() {
            return pC;
        }

        @Override
        public int getNumberOfRequiredParents() {
            return 2;
        }

        @Override
        public int getNumberOfGeneratedChildren() {
            return 2;
        }
    }

    /**
     * Mutación por gen: con probabilidad pM, uno de los cinco operadores.
     * Modifica la solución en el lugar, como espera jMetal.
     *
     * [Informe §III-C mutación]
     */
    public static final class Mutacion implements MutationOperator<SolucionPlantel> {
        private static final long serialVersionUID = 1L;
        private final Instancia instancia;
        private final double pM;
        private final Aleatorio rng;
        private final boolean grilla;

        public Mutacion(Instancia instancia, double pM, Aleatorio rng, boolean grilla) {
            this.instancia = instancia;
            this.pM = pM;
            this.rng = rng;
            this.grilla = grilla;
        }

        @Override
        public SolucionPlantel execute(SolucionPlantel s) {
            Cromosoma c = s.aCromosoma();
            Operadores.mutar(instancia, c, pM, rng);
            if (grilla) {
                Operadores.proyectarAGrilla(c);
            }
            s.copiarDesde(c);
            return s;
        }

        @Override
        public double getMutationProbability() {
            return pM;
        }
    }

    /**
     * Torneo de tamaño k CON reposición, como {@link Operadores#torneo}: el
     * retador reemplaza al actual solo si es estrictamente mejor según el
     * comparador. En el mono-objetivo el comparador es el objetivo; en NSGA-II,
     * rango de dominancia y distancia de crowding.
     *
     * No se usa la NaryTournamentSelection de jMetal porque usa JMetalRandom y
     * elige sin reposición (D-A2).
     *
     * [Informe §III-C selección por torneo de tamaño 3]
     */
    public static final class Torneo implements SelectionOperator<List<SolucionPlantel>, SolucionPlantel> {
        private static final long serialVersionUID = 1L;
        private final int k;
        private final Comparator<SolucionPlantel> comparador;
        private final Aleatorio rng;

        public Torneo(int k, Comparator<SolucionPlantel> comparador, Aleatorio rng) {
            this.k = k;
            this.comparador = comparador;
            this.rng = rng;
        }

        @Override
        public SolucionPlantel execute(List<SolucionPlantel> poblacion) {
            SolucionPlantel mejor = poblacion.get(rng.entero(0, poblacion.size()));
            for (int i = 1; i < k; i++) {
                SolucionPlantel reto = poblacion.get(rng.entero(0, poblacion.size()));
                if (comparador.compare(reto, mejor) < 0) {
                    mejor = reto;
                }
            }
            return mejor;
        }
    }

    /**
     * Selección proporcional al fitness escalado ({@link Operadores#escalar}),
     * con la "ruleta con punteros equiespaciados" del Tema 3 (stochastic
     * universal sampling): un solo giro y n punteros a distancia total/n, que
     * reduce el error de muestreo de n giros independientes.
     *
     * [Informe §III-C: "selección proporcional con escalado"] (Alen, P10)
     */
    public static final class ProporcionalEscalada
            implements SelectionOperator<List<SolucionPlantel>, SolucionPlantel> {
        private static final long serialVersionUID = 1L;
        private final Aleatorio rng;

        public ProporcionalEscalada(Aleatorio rng) {
            this.rng = rng;
        }

        /** Selecciona {@code cantidad} individuos de una sola vez (SUS). */
        public List<SolucionPlantel> seleccionar(List<SolucionPlantel> pob, int cantidad) {
            double[] w = pesos(pob);
            double total = 0;
            for (double x : w) {
                total += x;
            }
            double paso = total / cantidad;
            double puntero = rng.uniforme() * paso;
            List<SolucionPlantel> out = new java.util.ArrayList<>(cantidad);
            double acumulado = 0;
            int i = 0;
            for (int k = 0; k < cantidad; k++) {
                double objetivo = puntero + k * paso;
                while (i < w.length - 1 && acumulado + w[i] < objetivo) {
                    acumulado += w[i];
                    i++;
                }
                out.add(pob.get(i));
            }
            // SUS devuelve los elegidos en orden de la población; se mezclan
            // para que los pares del cruzamiento no sean vecinos fijos.
            rng.mezclar(out);
            return out;
        }

        /** Un solo giro de ruleta (para cumplir la interfaz de jMetal). */
        @Override
        public SolucionPlantel execute(List<SolucionPlantel> pob) {
            return pob.get(rng.elegir(pesos(pob)));
        }

        private static double[] pesos(List<SolucionPlantel> pob) {
            double[] f = new double[pob.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = ProblemaPlantel.fitness(pob.get(i));
            }
            return Operadores.escalar(f);
        }
    }
}
