package ae.plantel.jmetal;

import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Cromosoma;
import ae.plantel.Escenario;
import ae.plantel.Estadistica;
import ae.plantel.Evaluador;
import ae.plantel.GreedyMiope;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Parametros;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Evaluación experimental del AE contra las técnicas de referencia.
 *
 * [Informe §IV-B comparación con otras técnicas; §IV-C métricas y eficiencia;
 * Tema 12 "En la práctica: qué reportar"]
 *
 * Por instancia de evaluación:
 * <ul>
 *   <li>AE sobre jMetal ({@link AGPlantel}) y búsqueda aleatoria, R corridas
 *       independientes cada uno con el mismo presupuesto (§IV-B(4)).</li>
 *   <li>Statu quo (§IV-B(5)) y greedy miope (§IV-B(3)): son deterministas,
 *       se evalúan una vez.</li>
 *   <li>Todo plan se revalúa con N = 200 sobre escenarios de validación.</li>
 * </ul>
 * Se reporta: mejor, media, mediana y desvío; % de mejora del AE sobre cada
 * técnica, [f(AE) − f(OT)] / f(OT) con la media del AE; tests según el
 * protocolo del curso (Shapiro-Wilk → t de Welch o Mann-Whitney contra la
 * aleatoria; Wilcoxon de una muestra y criterio del Tema 12 contra las
 * deterministas; Friedman con post-hoc de Holm sobre las cuatro técnicas,
 * bloques = semillas); robustez del plan final con 10.000 simulaciones
 * (§IV-C); tiempo medio y desvío, y evaluaciones hasta alcanzar el 50, 90 y 99%
 * de la mejora final (§IV-C).
 *
 * Las referencias "plan real del club" (§IV-B(1)) y "óptimo exacto"
 * (§IV-B(2), ver OptimoExacto) no entran acá: la primera necesita datos reales
 * (PENDIENTES P18) y la segunda solo existe en micro-instancias.
 *
 * Protocolo (D-A13): N = 10 réplicas, 120.000 evaluaciones por defecto.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.CompararReferencias [evaluaciones] [corridas] [instancias...]
 *       instancias: eval-chica eval-media eval-grande demo  (por defecto las tres de evaluación)
 *
 * Deja resultados/comparacion_&lt;instancia&gt;.csv con los datos crudos.
 */
public final class CompararReferencias {

    /** Escenarios de la validación extendida de robustez (informe §IV-C). */
    public static final int N_ROBUSTEZ = 10_000;
    /** Semillas de robustez: distintas de búsqueda, selección y validación. */
    public static final long SEMILLA_ROBUSTEZ = 50_000_000L;

    private CompararReferencias() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        int evaluaciones = args.length > 0 ? Integer.parseInt(args[0]) : AlgoritmoEvolutivo.EVALUACIONES_FINALES;
        int corridas = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        List<String> nombres = args.length > 2 ? Arrays.asList(args).subList(2, args.length)
                : List.of("eval-chica", "eval-media", "eval-grande");
        Parametros p = new Parametros();
        for (String nombre : nombres) {
            comparar(ExperimentoMO.instancia(nombre), evaluaciones, corridas, p);
        }
    }

    static void comparar(Instancia inst, int evaluaciones, int corridas, Parametros p)
            throws Exception {
        String linea = "=".repeat(92);
        System.out.println();
        System.out.println(linea);
        System.out.println(inst.resumen());
        System.out.printf("Presupuesto: %d evaluaciones por corrida con N = %d, %d corridas por técnica estocástica%n",
                evaluaciones, AlgoritmoEvolutivo.N_FINAL, corridas);
        System.out.println(linea);

        // ---------------- corridas ----------------
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<AlgoritmoEvolutivo.Resultado>> fAe = new ArrayList<>();
        List<Future<AlgoritmoEvolutivo.Resultado>> fAz = new ArrayList<>();
        for (int s = 1; s <= corridas; s++) {
            AlgoritmoEvolutivo.Config cfg = AlgoritmoEvolutivo.Config.protocoloFinal();   // N = 10 (D-A13)
            cfg.semilla = s;
            cfg.maxEvaluaciones = evaluaciones;
            fAe.add(pool.submit((Callable<AlgoritmoEvolutivo.Resultado>) () -> AGPlantel.ejecutar(inst, cfg, p)));
            fAz.add(pool.submit((Callable<AlgoritmoEvolutivo.Resultado>) () ->
                    AlgoritmoEvolutivo.busquedaAleatoria(inst, cfg, p)));
        }
        long tg = System.nanoTime();
        int[] evalsGreedy = new int[1];
        Cromosoma greedy = GreedyMiope.construir(inst, evalsGreedy);
        double segGreedy = (System.nanoTime() - tg) / 1e9;

        double[] ae = new double[corridas];
        double[] az = new double[corridas];
        double[] tAe = new double[corridas];
        double[] tAz = new double[corridas];
        double[][] hasta = new double[3][corridas];
        double[] fracciones = { 0.50, 0.90, 0.99 };
        Cromosoma mejorAe = null;
        double mejorValorAe = Double.NEGATIVE_INFINITY;
        StringBuilder csv = new StringBuilder("tecnica,semilla,fitness,segundos,evaluaciones\n");
        for (int i = 0; i < corridas; i++) {
            AlgoritmoEvolutivo.Resultado ra = fAe.get(i).get();
            AlgoritmoEvolutivo.Resultado rz = fAz.get(i).get();
            ae[i] = ra.fitnessMejor.valor;
            az[i] = rz.fitnessMejor.valor;
            tAe[i] = ra.segundos;
            tAz[i] = rz.segundos;
            for (int k = 0; k < fracciones.length; k++) {
                hasta[k][i] = evaluacionesHasta(ra, fracciones[k], evaluaciones);
            }
            if (ae[i] > mejorValorAe) {
                mejorValorAe = ae[i];
                mejorAe = ra.mejor;
            }
            csv.append(String.format(Locale.ROOT, "AE,%d,%.4f,%.2f,%d%n", i + 1, ae[i], tAe[i], ra.evaluaciones));
            csv.append(String.format(Locale.ROOT, "aleatoria,%d,%.4f,%.2f,%d%n", i + 1, az[i], tAz[i], rz.evaluaciones));
        }
        pool.shutdown();
        Evaluador.Fitness fSq = Evaluador.evaluar(inst, Cromosoma.statuQuo(inst),
                AlgoritmoEvolutivo.N_VALIDACION, AlgoritmoEvolutivo.SEMILLA_VALIDACION, p);
        Evaluador.Fitness fGr = Evaluador.evaluar(inst, greedy,
                AlgoritmoEvolutivo.N_VALIDACION, AlgoritmoEvolutivo.SEMILLA_VALIDACION, p);
        csv.append(String.format(Locale.ROOT, "statu_quo,0,%.4f,0,1%n", fSq.valor));
        csv.append(String.format(Locale.ROOT, "greedy,0,%.4f,%.2f,%d%n", fGr.valor, segGreedy, evalsGreedy[0]));

        // ---------------- calidad ----------------
        System.out.printf("%-20s%10s%10s%10s%9s%12s%12s%n", "técnica", "mejor", "media", "mediana",
                "desvío", "seg (media)", "seg (sd)");
        fila("AE (jMetal)", ae, tAe);
        fila("búsqueda aleatoria", az, tAz);
        System.out.printf("%-20s%10.2f%10s%10s%9s%12.1f%12s   (%d evaluaciones)%n", "greedy miope",
                fGr.valor, "—", "—", "—", segGreedy, "—", evalsGreedy[0]);
        System.out.printf("%-20s%10.2f%10s%10s%9s%12s%12s%n", "statu quo", fSq.valor, "—", "—", "—", "—", "—");

        double mAe = Estadistica.media(ae);
        System.out.println();
        System.out.println("  % de mejora del AE (media) = [f(AE) - f(OT)] / f(OT):");
        System.out.printf("    sobre la aleatoria (media): %+6.2f%%%n", 100 * (mAe - Estadistica.media(az)) / Math.abs(Estadistica.media(az)));
        System.out.printf("    sobre el greedy:            %+6.2f%%%n", 100 * (mAe - fGr.valor) / Math.abs(fGr.valor));
        System.out.printf("    sobre el statu quo:         %+6.2f%%%n", 100 * (mAe - fSq.valor) / Math.abs(fSq.valor));

        // ---------------- tests ----------------
        System.out.println();
        System.out.println("  Tests (alfa = 0,05):");
        System.out.printf("    AE vs aleatoria: %s%n", ExperimentoMO.comparar(ae, az));
        double[] cteGr = new double[corridas];
        double[] cteSq = new double[corridas];
        Arrays.fill(cteGr, fGr.valor);
        Arrays.fill(cteSq, fSq.valor);
        System.out.printf("    AE vs greedy:    Wilcoxon de una muestra p = %.4g; criterio Tema 12: %s%n",
                Estadistica.wilcoxon(ae, cteGr)[1], Estadistica.difiereCriterioTema12(ae, cteGr) ? "difieren" : "no difieren");
        System.out.printf("    AE vs statu quo: Wilcoxon de una muestra p = %.4g; criterio Tema 12: %s%n",
                Estadistica.wilcoxon(ae, cteSq)[1], Estadistica.difiereCriterioTema12(ae, cteSq) ? "difieren" : "no difieren");
        // Friedman sobre las 4 técnicas, bloques = semillas, y Holm sobre los pares del AE.
        double[][] bloques = new double[corridas][4];
        for (int i = 0; i < corridas; i++) {
            bloques[i] = new double[] { ae[i], az[i], fGr.valor, fSq.valor };
        }
        double[] rangos = Estadistica.rangosFriedman(bloques);
        double[] fr = Estadistica.friedman(bloques);
        System.out.printf("    Friedman (AE, aleatoria, greedy, statu quo): chi2 = %.2f, p = %.3g; "
                + "rangos medios %.2f / %.2f / %.2f / %.2f%n", fr[0], fr[1], rangos[0], rangos[1], rangos[2], rangos[3]);
        double[] crudos = { Estadistica.wilcoxon(ae, az)[1], Estadistica.wilcoxon(ae, cteGr)[1],
                Estadistica.wilcoxon(ae, cteSq)[1] };
        double[] holm = Estadistica.holm(crudos);
        System.out.printf("    post-hoc de Holm (Wilcoxon pareado por semilla): AE-aleatoria %.4g, "
                + "AE-greedy %.4g, AE-statu quo %.4g%n", holm[0], holm[1], holm[2]);

        // ---------------- robustez ----------------
        System.out.println();
        System.out.printf("  Robustez con %,d simulaciones (escenarios nuevos):%n", N_ROBUSTEZ);
        System.out.printf("    %-22s%10s%10s%10s%10s%n", "plan", "U media", "U desvío", "U P5", "U P95");
        robustez("mejor plan del AE", inst, mejorAe, p);
        robustez("greedy miope", inst, greedy, p);
        robustez("statu quo", inst, Cromosoma.statuQuo(inst), p);

        // ---------------- eficiencia ----------------
        System.out.println();
        System.out.println("  Evaluaciones hasta alcanzar una fracción de la mejora final del AE "
                + "(mejor de la generación, escenarios de búsqueda):");
        for (int k = 0; k < fracciones.length; k++) {
            System.out.printf("    %3.0f%%: media %8.0f  desvío %7.0f%n", 100 * fracciones[k],
                    Estadistica.media(hasta[k]), Estadistica.desvio(hasta[k]));
        }

        String ruta = "resultados/comparacion_" + inst.nombre + ".csv";
        Files.createDirectories(Path.of("resultados"));
        Files.writeString(Path.of(ruta), csv.toString(), StandardCharsets.UTF_8);
        System.out.println("  datos crudos en " + ruta);
    }

    private static void fila(String nombre, double[] v, double[] t) {
        System.out.printf("%-20s%10.2f%10.2f%10.2f%9.2f%12.1f%12.1f%n", nombre, Estadistica.maximo(v),
                Estadistica.media(v), Estadistica.mediana(v), Estadistica.desvio(v),
                Estadistica.media(t), Estadistica.desvio(t));
    }

    /**
     * Evaluaciones gastadas hasta que el mejor de la generación alcanza
     * inicial + fracción · (final − inicial). Aproxima las evaluaciones por
     * generación como presupuesto / generaciones.
     *
     * [Informe §IV-C: "evaluaciones hasta alcanzar percentiles de la mejor solución"]
     */
    static double evaluacionesHasta(AlgoritmoEvolutivo.Resultado r, double fraccion, int presupuesto) {
        List<Double> h = r.historialMejor;
        double ini = h.get(0);
        double fin = h.stream().mapToDouble(Double::doubleValue).max().orElse(ini);
        if (fin <= ini) {
            return 0;
        }
        double objetivo = ini + fraccion * (fin - ini);
        double porGeneracion = (double) r.evaluaciones / Math.max(1, h.size());
        for (int g = 0; g < h.size(); g++) {
            if (h.get(g) >= objetivo) {
                return g * porGeneracion;
            }
        }
        return presupuesto;
    }

    /** Media, desvío y percentiles 5 y 95 de la utilidad sobre 10.000 escenarios. */
    private static void robustez(String nombre, Instancia inst, Cromosoma x, Parametros p) {
        double[] u = new double[N_ROBUSTEZ];
        for (int k = 0; k < N_ROBUSTEZ; k++) {
            u[k] = Evaluador.simular(inst, x, new Escenario(SEMILLA_ROBUSTEZ + k), p).utilidad;
        }
        double[] o = u.clone();
        Arrays.sort(o);
        System.out.printf("    %-22s%10.2f%10.2f%10.2f%10.2f%n", nombre, Estadistica.media(u),
                Estadistica.desvio(u), o[(int) (0.05 * N_ROBUSTEZ)], o[(int) (0.95 * N_ROBUSTEZ)]);
    }
}
