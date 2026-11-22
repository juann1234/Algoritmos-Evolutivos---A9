package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Cromosoma;
import ae.plantel.Estadistica;
import ae.plantel.Evaluador;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;
import ae.plantel.Parametros;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Estudio del número de réplicas N del simulador.
 *
 * [Informe §IV-A: "Se estudiará aparte el número de réplicas N, buscando el
 * menor valor que mantenga estable el ordenamiento"] (Alen, P14)
 *
 * N es el número de escenarios con que se estima f(x). Un N chico abarata cada
 * evaluación pero hace que la selección ordene a los individuos por ruido.
 * Lo que importa para el AE no es el valor de f sino el ORDEN (el torneo solo
 * compara), así que se mide cuánto se parece el orden con N escenarios al orden
 * "verdadero", estimado con N_REF = 1.000 escenarios independientes.
 *
 * Dos conjuntos de planes, porque el ruido pesa distinto según la etapa:
 * <ul>
 *   <li><b>diversos</b>: 100 planes como los de una población inicial;</li>
 *   <li><b>convergidos</b>: la población final de una corrida del AE, planes
 *       muy parecidos entre sí, que es donde ordenar es más difícil.</li>
 * </ul>
 * Parte 1. Para cada N: τ de Kendall y ρ de Spearman contra el orden de
 * referencia (media de 5 repeticiones con escenarios distintos).
 *
 * Parte 2. Lo que decide (DECISIONES D-A13): el AE con distintos N a IGUAL
 * COSTO, es decir, con el mismo número total de simulaciones (evaluaciones × N
 * = 240.000, lo de 8.000 evaluaciones con N = 30). Un N chico ordena peor pero
 * permite más generaciones; la parte 1 sola no puede decir qué pesa más. Se
 * comparan los N contra N = 30 (el valor actual) con Wilcoxon pareado por
 * semilla y Holm; se cambia N solo si alguno es significativamente mejor.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.EstudioReplicas [instancia] [semillas] [simulaciones] [soloParte2]
 */
public final class EstudioReplicas {

    static final int N_REF = 1000;
    static final int[] NS = { 5, 10, 20, 30, 50, 100, 200 };
    static final int REPETICIONES = 5;
    static final long SEMILLA_REF = 60_000_000L;
    static final long SEMILLA_N = 61_000_000L;

    private EstudioReplicas() {
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        String nombre = args.length > 0 ? args[0] : "demo";
        Instancia inst = ExperimentoMO.instancia(nombre);
        Parametros p = new Parametros();
        System.out.println(inst.resumen());
        boolean soloParte2 = args.length > 3 && Boolean.parseBoolean(args[3]);
        if (!soloParte2) {
            parte1(inst, p);
        }
        parte2(inst, p, args.length > 1 ? Integer.parseInt(args[1]) : 10,
                args.length > 2 ? Long.parseLong(args[2]) : 8000L * 30);
    }

    private static void parte1(Instancia inst, Parametros p) {

        // Planes diversos: como una población inicial.
        Cromosoma[] diversos = Operadores.poblacionInicial(inst, 100, new Aleatorio(4242));
        // Planes convergidos: población final de una corrida corta del AE.
        AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
        cfg.maxEvaluaciones = 6000;
        cfg.semilla = 77;
        AGPlantel ag = new AGPlantel(inst, cfg, p);
        ag.run();
        List<Cromosoma> conv = new ArrayList<>();
        for (SolucionPlantel s : ag.getPopulation()) {
            conv.add(s.aCromosoma());
        }

        System.out.println();
        System.out.printf("Referencia: N = %d escenarios independientes. %d repeticiones por N.%n",
                N_REF, REPETICIONES);
        System.out.printf("%6s%16s%16s%18s%18s%14s%n", "N", "ρ diversos", "τ diversos",
                "ρ convergidos", "τ convergidos", "ms/evaluación");
        double[] refD = evaluar(inst, diversos, N_REF, SEMILLA_REF, p);
        double[] refC = evaluar(inst, conv.toArray(new Cromosoma[0]), N_REF, SEMILLA_REF, p);
        int elegido = -1;
        for (int n : NS) {
            double rhoD = 0, tauD = 0, rhoC = 0, tauC = 0;
            long t0 = System.nanoTime();
            for (int r = 0; r < REPETICIONES; r++) {
                long sem = SEMILLA_N + (long) r * 10_000L;
                double[] d = evaluar(inst, diversos, n, sem, p);
                double[] c = evaluar(inst, conv.toArray(new Cromosoma[0]), n, sem, p);
                rhoD += spearman(d, refD) / REPETICIONES;
                tauD += kendall(d, refD) / REPETICIONES;
                rhoC += spearman(c, refC) / REPETICIONES;
                tauC += kendall(c, refC) / REPETICIONES;
            }
            double ms = (System.nanoTime() - t0) / 1e6 / (REPETICIONES * (diversos.length + conv.size()));
            System.out.printf("%6d%16.3f%16.3f%18.3f%18.3f%14.2f%n", n, rhoD, tauD, rhoC, tauC, ms);
            if (elegido < 0 && rhoD >= 0.9 && tauC >= 0.5) {
                elegido = n;
            }
        }
        System.out.println();
        System.out.println(elegido > 0
                ? "Menor N que cumple ρ ≥ 0,9 (diversos) y τ ≥ 0,5 (convergidos): " + elegido
                        + "  (criterio fijado de antemano; resultó demasiado laxo, ver parte 2)"
                : "Ningún N del barrido cumple el criterio.");

    }

    /** Parte 2: AE con distintos N a igual número total de simulaciones. */
    private static void parte2(Instancia inst, Parametros p, int semillas, long simulaciones) {
        int[] ns = { 5, 10, 20, 30, 50 };
        System.out.println();
        System.out.printf("Parte 2: AE con distintos N y el mismo total de simulaciones (%,d), %d semillas%n",
                simulaciones, semillas);
        System.out.printf("%6s%10s%10s%9s%9s%14s%n", "N", "evals", "media", "desvío", "mejor", "p Holm vs 30");
        double[][] fit = new double[ns.length][semillas];
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());
        try {
            java.util.List<java.util.List<java.util.concurrent.Future<Double>>> fs = new ArrayList<>();
            for (int n : ns) {
                java.util.List<java.util.concurrent.Future<Double>> l = new ArrayList<>();
                for (int sem = 1; sem <= semillas; sem++) {
                    AlgoritmoEvolutivo.Config c = new AlgoritmoEvolutivo.Config();
                    c.replicas = n;
                    c.maxEvaluaciones = (int) (simulaciones / n);
                    c.semilla = sem;
                    l.add(pool.submit(() -> AGPlantel.ejecutar(inst, c, p).fitnessMejor.valor));
                }
                fs.add(l);
            }
            for (int i = 0; i < ns.length; i++) {
                for (int sem = 0; sem < semillas; sem++) {
                    fit[i][sem] = fs.get(i).get(sem).get();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdown();
        }
        int ref = 3;   // N = 30
        double[] crudos = new double[ns.length - 1];
        for (int i = 0, k = 0; i < ns.length; i++) {
            if (i != ref) {
                crudos[k++] = Estadistica.wilcoxon(fit[i], fit[ref])[1];
            }
        }
        double[] holm = Estadistica.holm(crudos);
        for (int i = 0, k = 0; i < ns.length; i++) {
            String ph = i == ref ? "(referencia)" : String.format("%.4f", holm[k++]);
            System.out.printf("%6d%10d%10.2f%9.2f%9.2f%14s%n", ns[i], simulaciones / ns[i],
                    Estadistica.media(fit[i]), Estadistica.desvio(fit[i]), Estadistica.maximo(fit[i]), ph);
        }
    }

    private static double[] evaluar(Instancia inst, Cromosoma[] planes, int n, long semilla, Parametros p) {
        double[] f = new double[planes.length];
        for (int i = 0; i < planes.length; i++) {
            f[i] = Evaluador.evaluar(inst, planes[i], n, semilla, p).valor;
        }
        return f;
    }

    /** ρ de Spearman: correlación de Pearson entre los rangos (con empates promediados). */
    public static double spearman(double[] a, double[] b) {
        double[] ra = Estadistica.rangos(a);
        double[] rb = Estadistica.rangos(b);
        double ma = Estadistica.media(ra);
        double mb = Estadistica.media(rb);
        double sab = 0, saa = 0, sbb = 0;
        for (int i = 0; i < a.length; i++) {
            sab += (ra[i] - ma) * (rb[i] - mb);
            saa += (ra[i] - ma) * (ra[i] - ma);
            sbb += (rb[i] - mb) * (rb[i] - mb);
        }
        return saa > 0 && sbb > 0 ? sab / Math.sqrt(saa * sbb) : 0;
    }

    /**
     * τ-b de Kendall: (concordantes − discordantes) / raíz((n0 − empates a)(n0 − empates b)).
     * Los planes repetidos de una población convergida dan empates exactos en
     * las dos series, que no cuentan ni a favor ni en contra.
     */
    public static double kendall(double[] a, double[] b) {
        long conc = 0, disc = 0, empA = 0, empB = 0, n0 = 0;
        for (int i = 0; i < a.length; i++) {
            for (int j = i + 1; j < a.length; j++) {
                double da = Math.signum(a[i] - a[j]);
                double db = Math.signum(b[i] - b[j]);
                n0++;
                if (da == 0) {
                    empA++;
                }
                if (db == 0) {
                    empB++;
                }
                if (da * db > 0) {
                    conc++;
                } else if (da * db < 0) {
                    disc++;
                }
            }
        }
        double den = Math.sqrt((double) (n0 - empA) * (n0 - empB));
        return den > 0 ? (conc - disc) / den : 0;
    }
}
