package ae.plantel.jmetal;

import org.uma.jmetal.qualityindicator.impl.GenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.Spread;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.util.front.Front;
import org.uma.jmetal.util.front.impl.ArrayFront;
import org.uma.jmetal.util.point.PointSolution;
import org.uma.jmetal.util.point.impl.ArrayPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Métricas para evaluar frentes de Pareto, calculadas con los indicadores de
 * jMetal 5.10.
 *
 * [Informe §IV-C: "hipervolumen, spread y distancia generacional, con la
 * aproximación del frente de Pareto real construida como la unión de las
 * soluciones no dominadas de todas las ejecuciones"; Temas 10 y 12]
 *
 * Todos los puntos están en el espacio de minimización de jMetal:
 * (−f₁, f₂). Antes de medir, los frentes se NORMALIZAN con los extremos del
 * frente de referencia (lo que hace jMetal en sus estudios), porque f₁ son
 * puntos y f₂ millones: sin normalizar, una métrica de distancia la decide
 * solo el objetivo de mayor escala.
 *
 * <ul>
 *   <li><b>Hipervolumen relativo</b> (RHV, Tema 10): HV(A) / HV(referencia),
 *       con punto de referencia 10% más allá del nadir del frente de
 *       referencia. 1 = igual que la referencia; mayor es mejor.</li>
 *   <li><b>Spread</b> (Δ de Deb, Tema 10): uniformidad y cobertura de los
 *       extremos; 0 es ideal; menor es mejor.</li>
 *   <li><b>Distancia generacional</b> (GD, Tema 10, con p = 2): cercanía al
 *       frente de referencia; menor es mejor.</li>
 * </ul>
 */
public final class IndicadoresMO {

    /** Margen del punto de referencia del HV más allá del nadir (10%). */
    public static final double MARGEN_HV = 0.10;

    private final double[] minimo;
    private final double[] maximo;
    private final List<double[]> referencia;
    private final Front referenciaNormalizada;
    private final double hvReferencia;

    /** @param frenteReferencia frente no dominado de referencia (unión de todas las corridas) */
    public IndicadoresMO(List<double[]> frenteReferencia) {
        this.referencia = noDominados(frenteReferencia);
        int m = referencia.get(0).length;
        minimo = new double[m];
        maximo = new double[m];
        Arrays.fill(minimo, Double.POSITIVE_INFINITY);
        Arrays.fill(maximo, Double.NEGATIVE_INFINITY);
        for (double[] p : referencia) {
            for (int k = 0; k < m; k++) {
                minimo[k] = Math.min(minimo[k], p[k]);
                maximo[k] = Math.max(maximo[k], p[k]);
            }
        }
        referenciaNormalizada = aFront(normalizar(referencia));
        hvReferencia = hipervolumenAbsoluto(referencia);
    }

    /** Hipervolumen relativo: HV(frente) / HV(referencia). */
    public double hipervolumenRelativo(List<double[]> frente) {
        return hvReferencia > 0 ? hipervolumenAbsoluto(frente) / hvReferencia : 0.0;
    }

    /**
     * Hipervolumen con el PISAHypervolume de jMetal. La implementación de jMetal
     * espera puntos en [0, 1] y usa (1, 1) como punto de referencia; por eso se
     * escala de modo que el nadir quede en 1/(1+margen) y se recorta a 1 lo que
     * cae más allá (no aporta volumen).
     */
    public double hipervolumenAbsoluto(List<double[]> frente) {
        List<PointSolution> sol = new ArrayList<>();
        for (double[] p : frente) {
            double[] q = new double[p.length];
            for (int k = 0; k < p.length; k++) {
                double rango = (maximo[k] - minimo[k]) * (1.0 + MARGEN_HV);
                q[k] = rango > 0 ? Math.min(1.0, Math.max(0.0, (p[k] - minimo[k]) / rango)) : 0.0;
            }
            sol.add(punto(q));
        }
        if (sol.isEmpty()) {
            return 0.0;
        }
        return new PISAHypervolume<PointSolution>(new double[] { 1.0, 1.0 }).evaluate(sol);
    }

    /** Spread de jMetal sobre el frente normalizado. */
    public double spread(List<double[]> frente) {
        if (frente.size() < 2) {
            return 1.0;   // un solo punto no cubre nada del frente
        }
        return new Spread<PointSolution>(referenciaNormalizada).evaluate(soluciones(normalizar(frente)));
    }

    /** Distancia generacional de jMetal (p = 2) sobre el frente normalizado. */
    public double distanciaGeneracional(List<double[]> frente) {
        return new GenerationalDistance<PointSolution>(referenciaNormalizada)
                .evaluate(soluciones(normalizar(frente)));
    }

    public List<double[]> referencia() {
        return referencia;
    }

    // ----------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------

    private List<double[]> normalizar(List<double[]> frente) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : frente) {
            double[] q = new double[p.length];
            for (int k = 0; k < p.length; k++) {
                double rango = maximo[k] - minimo[k];
                q[k] = rango > 0 ? (p[k] - minimo[k]) / rango : 0.0;
            }
            out.add(q);
        }
        return out;
    }

    private static PointSolution punto(double[] q) {
        PointSolution s = new PointSolution(q.length);
        for (int k = 0; k < q.length; k++) {
            s.setObjective(k, q[k]);
        }
        return s;
    }

    private static List<PointSolution> soluciones(List<double[]> frente) {
        List<PointSolution> out = new ArrayList<>();
        for (double[] p : frente) {
            out.add(punto(p));
        }
        return out;
    }

    private static Front aFront(List<double[]> puntos) {
        Front f = new ArrayFront(puntos.size(), puntos.get(0).length);
        for (int i = 0; i < puntos.size(); i++) {
            f.setPoint(i, new ArrayPoint(puntos.get(i)));
        }
        return f;
    }

    /** Filtra los no dominados (minimización en todos los objetivos), sin repetidos. */
    public static List<double[]> noDominados(List<double[]> puntos) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : puntos) {
            boolean dominado = false;
            for (double[] q : puntos) {
                if (q != p && domina(q, p)) {
                    dominado = true;
                    break;
                }
            }
            if (!dominado && out.stream().noneMatch(o -> Arrays.equals(o, p))) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(p -> p[0]));
        return out;
    }

    /** true si a domina a b (minimización). */
    public static boolean domina(double[] a, double[] b) {
        boolean mejorEnAlguno = false;
        for (int k = 0; k < a.length; k++) {
            if (a[k] > b[k]) {
                return false;
            }
            if (a[k] < b[k]) {
                mejorEnAlguno = true;
            }
        }
        return mejorEnAlguno;
    }

    /**
     * Hipervolumen 2D calculado a mano (barrido), SOLO para validar el de jMetal
     * en {@code ValidarMO}. Puntos ya normalizados a [0, 1], referencia (1, 1).
     */
    static double hipervolumen2dManual(List<double[]> puntosNormalizados) {
        List<double[]> nd = noDominados(puntosNormalizados);   // ordenados por x creciente
        double hv = 0;
        double yAnterior = 1.0;
        for (double[] p : nd) {
            if (p[0] >= 1.0 || p[1] >= 1.0) {
                continue;
            }
            hv += (1.0 - p[0]) * (yAnterior - p[1]);
            yAnterior = p[1];
        }
        return hv;
    }

    /** Puntos de un frente de soluciones, en el espacio de minimización. */
    public static List<double[]> puntos(List<SolucionPlantel> frente) {
        List<double[]> out = new ArrayList<>();
        for (SolucionPlantel s : frente) {
            out.add(s.getObjectives().clone());
        }
        return out;
    }
}
