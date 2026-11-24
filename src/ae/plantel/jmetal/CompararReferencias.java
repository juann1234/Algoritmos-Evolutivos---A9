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
 * Con las instancias reales (barcelona-2020-*) se usan los parámetros
 * calibrados (D-J2) y se suma como referencia el PLAN REAL del club
 * (§IV-B(1), D-J4; Alen, P29), con desglose en puntos y patrimonio. El
 * óptimo exacto (§IV-B(2)) solo existe en micro-instancias: ver
 * GapMicroInstancias.
 *
 * Protocolo (D-A13): N = 10 réplicas, 120.000 evaluaciones por defecto.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.CompararReferencias [evaluaciones] [corridas] [instancias...]
 *       instancias: eval-chica eval-media eval-grande demo
 *                   barcelona-2020-chica barcelona-2020-media barcelona-2020-grande
 *       (por defecto las tres sintéticas de evaluación)
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
        for (String nombre : nombres) {
            // Reales: parámetros calibrados y el plan real como referencia (P29).
            Instancia inst = ExperimentoMO.instancia(nombre);
            comparar(inst, evaluaciones, corridas, ExperimentoMO.parametros(nombre),
                    ExperimentoMO.esReal(nombre) ? ae.plantel.InstanciasReales.planReal(inst) : null);
        }
    }

    /**
     * @param planReal plan que el club ejecutó (§IV-B(1)), o null si la
     *                 instancia es sintética y no tiene plan real
     */
    static void comparar(Instancia inst, int evaluaciones, int corridas, Parametros p, Cromosoma planReal)
            throws Exception {
        String linea = "=".repeat(92);
        System.out.println();
        System.out.println(linea);
        System.out.println(inst.resumen());
        System.out.printf("Presupuesto: %d evaluaciones por corrida con N = %d, %d corridas por técnica estocástica"
                + "%nParámetros: %s (β = %.3f)%n", evaluaciones, AlgoritmoEvolutivo.N_FINAL, corridas,
                planReal != null ? "calibrados con datos reales (D-J2)" : "por defecto", p.beta);
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
        Cromosoma greedy = GreedyMiope.construir(inst, evalsGreedy, p);
        double segGreedy = (System.nanoTime() - tg) / 1e9;

        double[] ae = new double[corridas];
        double[] az = new double[corridas];
        double[] tAe = new double[corridas];
        double[] tAz = new double[corridas];
        double[] f1Ae = new double[corridas];
        double[] f2Ae = new double[corridas];
        double[] ptsAe = new double[corridas];
        double[][] hasta = new double[3][corridas];
        double[] fracciones = { 0.50, 0.90, 0.99 };
        Cromosoma mejorAe = null;
        double mejorValorAe = Double.NEGATIVE_INFINITY;
        StringBuilder csv = new StringBuilder("tecnica,semilla,fitness,f1_utilidad,f2_costo_neto,puntos,segundos,evaluaciones\n");
        for (int i = 0; i < corridas; i++) {
            AlgoritmoEvolutivo.Resultado ra = fAe.get(i).get();
            AlgoritmoEvolutivo.Resultado rz = fAz.get(i).get();
            ae[i] = ra.fitnessMejor.valor;
            az[i] = rz.fitnessMejor.valor;
            tAe[i] = ra.segundos;
            tAz[i] = rz.segundos;
            f1Ae[i] = ra.fitnessMejor.utilidadDeportivaMedia;
            f2Ae[i] = ra.fitnessMejor.costoNetoMedio;
            ptsAe[i] = ra.fitnessMejor.puntosMedios;
            for (int k = 0; k < fracciones.length; k++) {
                hasta[k][i] = evaluacionesHasta(ra, fracciones[k], evaluaciones);
            }
            if (ae[i] > mejorValorAe) {
                mejorValorAe = ae[i];
                mejorAe = ra.mejor;
            }
            fila(csv, "AE", i + 1, ra.fitnessMejor, tAe[i], ra.evaluaciones);
            fila(csv, "aleatoria", i + 1, rz.fitnessMejor, tAz[i], rz.evaluaciones);
        }
        pool.shutdown();

        // Referencias deterministas: se evalúan una vez sobre los escenarios de validación.
        List<String> refNombres = new ArrayList<>(List.of("greedy", "statu quo"));
        List<Evaluador.Fitness> refFit = new ArrayList<>();
        List<Cromosoma> refPlan = new ArrayList<>(List.of(greedy, Cromosoma.statuQuo(inst)));
        if (planReal != null) {
            refNombres.add(0, "plan real");
            refPlan.add(0, planReal);
        }
        for (Cromosoma x : refPlan) {
            refFit.add(Evaluador.evaluar(inst, x, AlgoritmoEvolutivo.N_VALIDACION,
                    AlgoritmoEvolutivo.SEMILLA_VALIDACION, p));
        }
        for (int r = 0; r < refNombres.size(); r++) {
            String clave = refNombres.get(r).replace(' ', '_');
            fila(csv, clave, 0, refFit.get(r), refNombres.get(r).equals("greedy") ? segGreedy : 0,
                    refNombres.get(r).equals("greedy") ? evalsGreedy[0] : 1);
        }

        // ---------------- calidad ----------------
        System.out.printf("%-20s%10s%10s%10s%9s%12s%12s%n", "técnica", "mejor", "media", "mediana",
                "desvío", "seg (media)", "seg (sd)");
        fila("AE (jMetal)", ae, tAe);
        fila("búsqueda aleatoria", az, tAz);
        for (int r = 0; r < refNombres.size(); r++) {
            boolean esGreedy = refNombres.get(r).equals("greedy");
            System.out.printf("%-20s%10.2f%10s%10s%9s%12s%12s%s%n", refNombres.get(r), refFit.get(r).valor,
                    "—", "—", "—", esGreedy ? String.format("%.1f", segGreedy) : "—", "—",
                    esGreedy ? "   (" + evalsGreedy[0] + " evaluaciones)" : "");
        }

        double mAe = Estadistica.media(ae);
        System.out.println();
        System.out.println("  % de mejora del AE (media) = [f(AE) - f(OT)] / f(OT):");
        System.out.printf("    %-22s %+6.2f%%%n", "sobre la aleatoria:", 100 * (mAe - Estadistica.media(az)) / Math.abs(Estadistica.media(az)));
        for (int r = 0; r < refNombres.size(); r++) {
            double f = refFit.get(r).valor;
            System.out.printf("    %-22s %+6.2f%%%n", "sobre " + refNombres.get(r) + ":", 100 * (mAe - f) / Math.abs(f));
        }

        // ---------------- por qué gana: puntos y patrimonio ----------------
        System.out.println();
        System.out.println("  Desglose (f1 = utilidad deportiva, a maximizar; f2 = costo neto en M, a minimizar):");
        System.out.printf("    %-20s%12s%12s%14s%n", "plan", "f1", "f2", "puntos (3 t.)");
        System.out.printf("    %-20s%12.2f%12.2f%14.1f   (media de %d corridas)%n", "AE (jMetal)",
                Estadistica.media(f1Ae), Estadistica.media(f2Ae), Estadistica.media(ptsAe), corridas);
        for (int r = 0; r < refNombres.size(); r++) {
            Evaluador.Fitness f = refFit.get(r);
            System.out.printf("    %-20s%12.2f%12.2f%14.1f%n", refNombres.get(r), f.utilidadDeportivaMedia,
                    f.costoNetoMedio, f.puntosMedios);
        }
        if (planReal != null) {
            Evaluador.Fitness fr = refFit.get(0);
            int domina = 0;
            for (int i = 0; i < corridas; i++) {
                if (f1Ae[i] >= fr.utilidadDeportivaMedia && f2Ae[i] <= fr.costoNetoMedio
                        && (f1Ae[i] > fr.utilidadDeportivaMedia || f2Ae[i] < fr.costoNetoMedio)) {
                    domina++;
                }
            }
            System.out.printf("    el plan del AE domina al plan real (más f1 y menos f2) en %d de %d corridas%n",
                    domina, corridas);
        }

        // ---------------- tests ----------------
        System.out.println();
        System.out.println("  Tests (alfa = 0,05):");
        System.out.printf("    AE vs aleatoria: %s%n", ExperimentoMO.comparar(ae, az));
        double[][] constantes = new double[refNombres.size()][corridas];
        for (int r = 0; r < refNombres.size(); r++) {
            Arrays.fill(constantes[r], refFit.get(r).valor);
            System.out.printf("    AE vs %-10s Wilcoxon de una muestra p = %.4g; criterio Tema 12: %s%n",
                    refNombres.get(r) + ":", Estadistica.wilcoxon(ae, constantes[r])[1],
                    Estadistica.difiereCriterioTema12(ae, constantes[r]) ? "difieren" : "no difieren");
        }
        // Friedman sobre todas las técnicas, bloques = semillas, y Holm sobre los pares del AE.
        int k = 2 + refNombres.size();
        double[][] bloques = new double[corridas][k];
        for (int i = 0; i < corridas; i++) {
            bloques[i][0] = ae[i];
            bloques[i][1] = az[i];
            for (int r = 0; r < refNombres.size(); r++) {
                bloques[i][2 + r] = refFit.get(r).valor;
            }
        }
        double[] rangos = Estadistica.rangosFriedman(bloques);
        double[] fr = Estadistica.friedman(bloques);
        StringBuilder nombresF = new StringBuilder("AE, aleatoria");
        StringBuilder rangosF = new StringBuilder(String.format("%.2f / %.2f", rangos[0], rangos[1]));
        for (int r = 0; r < refNombres.size(); r++) {
            nombresF.append(", ").append(refNombres.get(r));
            rangosF.append(String.format(" / %.2f", rangos[2 + r]));
        }
        System.out.printf("    Friedman (%s): chi2 = %.2f, p = %.3g; rangos medios %s%n", nombresF, fr[0], fr[1], rangosF);
        double[] crudos = new double[1 + refNombres.size()];
        crudos[0] = Estadistica.wilcoxon(ae, az)[1];
        for (int r = 0; r < refNombres.size(); r++) {
            crudos[1 + r] = Estadistica.wilcoxon(ae, constantes[r])[1];
        }
        double[] holm = Estadistica.holm(crudos);
        StringBuilder holmTxt = new StringBuilder(String.format("AE-aleatoria %.4g", holm[0]));
        for (int r = 0; r < refNombres.size(); r++) {
            holmTxt.append(String.format(", AE-%s %.4g", refNombres.get(r), holm[1 + r]));
        }
        System.out.printf("    post-hoc de Holm (Wilcoxon pareado por semilla): %s%n", holmTxt);

        // ---------------- robustez ----------------
        System.out.println();
        System.out.printf("  Robustez con %d simulaciones (escenarios nuevos):%n", N_ROBUSTEZ);
        System.out.printf("    %-22s%10s%10s%10s%10s%n", "plan", "U media", "U desvío", "U P5", "U P95");
        robustez("mejor plan del AE", inst, mejorAe, p);
        for (int r = 0; r < refNombres.size(); r++) {
            robustez(refNombres.get(r), inst, refPlan.get(r), p);
        }

        // ---------------- eficiencia ----------------
        System.out.println();
        System.out.println("  Evaluaciones hasta alcanzar una fracción de la mejora final del AE "
                + "(mejor de la generación, escenarios de búsqueda):");
        for (int q = 0; q < fracciones.length; q++) {
            System.out.printf("    %3.0f%%: media %8.0f  desvío %7.0f%n", 100 * fracciones[q],
                    Estadistica.media(hasta[q]), Estadistica.desvio(hasta[q]));
        }

        String ruta = "resultados/comparacion_" + inst.nombre + ".csv";
        Files.createDirectories(Path.of("resultados"));
        Files.writeString(Path.of(ruta), csv.toString(), StandardCharsets.UTF_8);
        System.out.println("  datos crudos en " + ruta);
    }

    private static void fila(StringBuilder csv, String tecnica, int semilla, Evaluador.Fitness f,
                             double segundos, int evaluaciones) {
        csv.append(String.format(Locale.ROOT, "%s,%d,%.4f,%.4f,%.4f,%.2f,%.2f,%d%n", tecnica, semilla, f.valor,
                f.utilidadDeportivaMedia, f.costoNetoMedio, f.puntosMedios, segundos, evaluaciones));
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
