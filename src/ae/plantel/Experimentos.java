package ae.plantel;

import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Configuración paramétrica: diseño factorial completo con análisis estadístico.
 *
 * [Informe §IV-A configuración paramétrica (diseño factorial)]
 *
 * Barre todas las combinaciones de probabilidad de cruzamiento, probabilidad de
 * mutación y tamaño de población (ortogonalidad: se analizan TODAS, no una
 * variable por vez), con R ejecuciones independientes por combinación e
 * instancia.
 *
 * Tres decisiones metodológicas que importan:
 *
 *  1. Se corre sobre las instancias de CONFIGURACIÓN, distintas de las de
 *     evaluación, para no sesgar la parametrización.
 *  2. El fitness que se compara es el REVALUADO con N=200 sobre escenarios
 *     distintos de los de la búsqueda. Comparar el fitness que cada ejecución
 *     reporta al terminar favorece a quien tuvo suerte con sus escenarios.
 *  3. Los bloques del test de Friedman son los pares (instancia, semilla): la
 *     semilla s de la configuración A y la de la configuración B comparten
 *     población inicial y escenarios, así que son comparables de a pares.
 *
 * El paralelismo es maestro-esclavo sobre ejecuciones independientes: no altera
 * el comportamiento del algoritmo, solo el tiempo de reloj.
 *
 * Uso:  java -cp out ae.plantel.Experimentos [evaluaciones] [ejecuciones] [instancias]
 */
public final class Experimentos {

    private static final Parametros P = new Parametros();
    private static final int N_VALIDACION = 200;
    private static final long SEMILLA_VALIDACION = 777_000L;

    private static final double[] PC = { 0.60, 0.75, 0.90 };
    private static final double[] PM = { 0.01, 0.05, 0.10 };
    private static final int[] POBLACION = { 50, 100, 200 };

    private record Celda(int config, int bloque, double fitness, double segundos) {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        final String linea = "=".repeat(78);

        int evaluaciones = args.length > 0 ? Integer.parseInt(args[0]) : 40_000;
        int ejecuciones = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int nInstancias = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        List<Instancia> instancias =
                GeneradorInstancias.configuracion().subList(0, nInstancias);

        // ---------------- grilla de configuraciones ----------------
        List<AlgoritmoEvolutivo.Config> grilla = new ArrayList<>();
        for (double pc : PC) {
            for (double pm : PM) {
                for (int pob : POBLACION) {
                    AlgoritmoEvolutivo.Config c = new AlgoritmoEvolutivo.Config();
                    c.pC = pc;
                    c.pM = pm;
                    c.poblacion = pob;
                    c.maxEvaluaciones = evaluaciones;
                    grilla.add(c);
                }
            }
        }

        int bloques = instancias.size() * ejecuciones;
        int totalCorridas = grilla.size() * bloques;
        int hilos = Runtime.getRuntime().availableProcessors();
        System.out.printf("Diseño factorial: %d configuraciones x %d instancias x %d "
                        + "ejecuciones = %d corridas de %d evaluaciones%n",
                grilla.size(), instancias.size(), ejecuciones, totalCorridas, evaluaciones);
        System.out.printf("Paralelismo: %d hilos%n%n", hilos);

        // ---------------- ejecución ----------------
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<Celda>> futuros = new ArrayList<>();
        long t0 = System.nanoTime();

        for (int ci = 0; ci < grilla.size(); ci++) {
            final int configIdx = ci;
            for (int ii = 0; ii < instancias.size(); ii++) {
                final Instancia inst = instancias.get(ii);
                for (int r = 0; r < ejecuciones; r++) {
                    final int bloque = ii * ejecuciones + r;
                    final long semilla = r + 1L;
                    futuros.add(pool.submit((Callable<Celda>) () -> {
                        AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
                        AlgoritmoEvolutivo.Config base = grilla.get(configIdx);
                        cfg.pC = base.pC;
                        cfg.pM = base.pM;
                        cfg.poblacion = base.poblacion;
                        cfg.maxEvaluaciones = base.maxEvaluaciones;
                        cfg.semilla = semilla;
                        long s0 = System.nanoTime();
                        // Motor: AE sobre jMetal (DECISIONES D-A1; equivalencia
                        // validada en jmetal/ValidarEquivalenciaJMetal).
                        AlgoritmoEvolutivo.Resultado res =
                                ae.plantel.jmetal.AGPlantel.ejecutar(inst, cfg, P);
                        double f = Evaluador.evaluar(
                                inst, res.mejor, N_VALIDACION, SEMILLA_VALIDACION, P).valor;
                        return new Celda(configIdx, bloque, f,
                                (System.nanoTime() - s0) / 1e9);
                    }));
                }
            }
        }

        double[][] datos = new double[grilla.size()][bloques];
        double[][] tiempos = new double[grilla.size()][bloques];
        int hechas = 0;
        for (Future<Celda> f : futuros) {
            Celda c = f.get();
            datos[c.config()][c.bloque()] = c.fitness();
            tiempos[c.config()][c.bloque()] = c.segundos();
            if (++hechas % Math.max(1, totalCorridas / 10) == 0) {
                System.out.printf("  %d/%d corridas (%.0f%%)%n",
                        hechas, totalCorridas, 100.0 * hechas / totalCorridas);
            }
        }
        pool.shutdown();
        double dt = (System.nanoTime() - t0) / 1e9;
        System.out.printf("%nTiempo total: %.1f min%n", dt / 60);

        // ---------------- tabla de resultados ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("Resultados por configuración (fitness revaluado con N=200)");
        System.out.println(linea);
        System.out.printf("%4s%7s%7s%7s%10s%9s%9s%9s%8s%n",
                "#", "pC", "pM", "pob", "media", "desvío", "mediana", "mejor", "seg");
        for (int i = 0; i < grilla.size(); i++) {
            AlgoritmoEvolutivo.Config c = grilla.get(i);
            System.out.printf("%4d%7.2f%7.3f%7d%10.2f%9.2f%9.2f%9.2f%8.1f%n",
                    i + 1, c.pC, c.pM, c.poblacion,
                    Estadistica.media(datos[i]), Estadistica.desvio(datos[i]),
                    Estadistica.mediana(datos[i]), Estadistica.maximo(datos[i]),
                    Estadistica.media(tiempos[i]));
        }

        // ---------------- normalidad ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("Test de normalidad (Shapiro-Wilk, alfa=5%; Lilliefors como control)");
        System.out.println(linea);
        // Shapiro-Wilk es el test principal (informe §IV-A, DECISIONES D-A5);
        // Lilliefors se reporta como control porque el Tema 12 lo acepta.
        int normales = 0;
        int normalesLilliefors = 0;
        for (int i = 0; i < grilla.size(); i++) {
            if (bloques >= 3 && Estadistica.esNormalShapiro(datos[i])) {
                normales++;
            }
            if (Estadistica.esNormal(datos[i])) {
                normalesLilliefors++;
            }
        }
        System.out.printf("  configuraciones normales según Shapiro-Wilk: %d de %d%n",
                normales, grilla.size());
        System.out.printf("  ídem según Lilliefors (crítico %.4f para n=%d): %d de %d%n",
                Estadistica.criticoLilliefors(bloques), bloques, normalesLilliefors, grilla.size());

        // ---------------- comparación ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("Comparación entre configuraciones");
        System.out.println(linea);
        // Con pocas ejecuciones el test de normalidad no tiene potencia (y el
        // valor crítico de Lilliefors no está definido para n muy chico): no
        // rechazar la normalidad NO es evidencia de normalidad. Por debajo de
        // 10 muestras se usa directamente el test no paramétrico.
        boolean muestrasSuficientes = bloques >= 10;
        if (!muestrasSuficientes) {
            System.out.printf("  Solo %d muestras por configuración: el test de normalidad no es "
                    + "concluyente, se usa Kruskal-Wallis.%n", bloques);
        }
        if (muestrasSuficientes && normales == grilla.size()) {
            double[] a = Estadistica.anova(datos);
            System.out.printf("  Todas normales -> ANOVA: F = %.3f, p = %.3e%n", a[0], a[1]);
            System.out.println(a[1] < 0.05
                    ? "  p < 0.05: las medias difieren, la configuración importa."
                    : "  p >= 0.05: no hay evidencia de que la configuración importe.");
        } else {
            double[] kw = Estadistica.kruskalWallis(datos);
            System.out.printf("  Kruskal-Wallis: H = %.3f, p = %.3e%n", kw[0], kw[1]);
            System.out.println(kw[1] < 0.05
                    ? "  p < 0.05: las distribuciones difieren, la configuración importa."
                    : "  p >= 0.05: no hay evidencia de que la configuración importe.");
        }

        // ---------------- Friedman ----------------
        double[][] porBloque = new double[bloques][grilla.size()];
        for (int i = 0; i < grilla.size(); i++) {
            for (int b = 0; b < bloques; b++) {
                porBloque[b][i] = datos[i][b];
            }
        }
        double[] rangos = Estadistica.rangosFriedman(porBloque);
        double[] fr = Estadistica.friedman(porBloque);

        System.out.println();
        System.out.println(linea);
        System.out.println("Ranking de Friedman (1 = mejor), " + bloques + " bloques");
        System.out.println(linea);
        System.out.printf("  estadístico = %.2f, p = %.3e%n%n", fr[0], fr[1]);

        Integer[] orden = new Integer[grilla.size()];
        for (int i = 0; i < grilla.size(); i++) {
            orden[i] = i;
        }
        java.util.Arrays.sort(orden, Comparator.comparingDouble(i -> rangos[i]));
        System.out.printf("%6s%7s%7s%7s%12s%10s%n",
                "puesto", "pC", "pM", "pob", "rango med.", "media");
        for (int k = 0; k < Math.min(10, orden.length); k++) {
            int i = orden[k];
            AlgoritmoEvolutivo.Config c = grilla.get(i);
            System.out.printf("%6d%7.2f%7.3f%7d%12.2f%10.2f%n",
                    k + 1, c.pC, c.pM, c.poblacion, rangos[i], Estadistica.media(datos[i]));
        }
        int peor = orden[orden.length - 1];
        System.out.printf("%6s%7.2f%7.3f%7d%12.2f%10.2f%n", "último",
                grilla.get(peor).pC, grilla.get(peor).pM, grilla.get(peor).poblacion,
                rangos[peor], Estadistica.media(datos[peor]));

        // ---------------- comparación por pares ----------------
        // El ranking dice quién quedó primero; no dice si la diferencia con el
        // segundo es real. Se compara la mejor contra cada una de las demás
        // con Wilcoxon pareado por semilla y corrección de Bonferroni.
        System.out.println();
        System.out.println(linea);
        System.out.println("Mejor configuración contra las demás (Wilcoxon pareado, post-hoc de Holm)");
        System.out.println(linea);
        // Holm en lugar de Bonferroni: StatTests lámina 22, DECISIONES D-A5.
        int mejor = orden[0];
        int comparaciones = grilla.size() - 1;
        int indistinguibles = 0;
        double[] pCrudos = new double[orden.length];
        double[] zs = new double[orden.length];
        for (int k = 1; k < orden.length; k++) {
            double[] w = Estadistica.wilcoxon(datos[mejor], datos[orden[k]]);
            zs[k] = w[0];
            pCrudos[k] = w[1];
        }
        double[] pHolm = Estadistica.holm(java.util.Arrays.copyOfRange(pCrudos, 1, pCrudos.length));
        for (int k = 1; k < orden.length; k++) {
            int i = orden[k];
            double[] w = { zs[k], pCrudos[k] };
            double pAjustado = pHolm[k - 1];
            if (pAjustado >= 0.05) {
                indistinguibles++;
            }
            if (k <= 5 || k == orden.length - 1) {
                AlgoritmoEvolutivo.Config c = grilla.get(i);
                System.out.printf("  vs pC=%.2f pM=%.3f pob=%3d : z = %6.2f  p ajustado = %.3e%s%n",
                        c.pC, c.pM, c.poblacion, w[0], pAjustado,
                        pAjustado < 0.05 ? "" : "  (no significativa)");
            }
        }
        System.out.printf("  %d de %d configuraciones no se distinguen estadísticamente de la mejor%n",
                indistinguibles, comparaciones);

        AlgoritmoEvolutivo.Config elegida = grilla.get(orden[0]);
        System.out.println();
        System.out.printf("CONFIGURACIÓN ELEGIDA: pC = %.2f, pM = %.3f, población = %d%n",
                elegida.pC, elegida.pM, elegida.poblacion);

        exportarCsv(grilla, datos, bloques);
    }

    /** Vuelca los datos crudos para rehacer el análisis en R o en una planilla. */
    private static void exportarCsv(List<AlgoritmoEvolutivo.Config> grilla,
                                    double[][] datos, int bloques) {
        StringBuilder sb = new StringBuilder("config,pC,pM,poblacion,bloque,fitness\n");
        for (int i = 0; i < grilla.size(); i++) {
            AlgoritmoEvolutivo.Config c = grilla.get(i);
            for (int b = 0; b < bloques; b++) {
                sb.append(String.format("%d,%.2f,%.3f,%d,%d,%.4f%n",
                        i + 1, c.pC, c.pM, c.poblacion, b, datos[i][b]));
            }
        }
        try {
            Path destino = Path.of("resultados_configuracion.csv");
            Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
            System.out.println("Datos crudos en " + destino.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("No se pudo escribir el CSV: " + e.getMessage());
        }
    }
}
