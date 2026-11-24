package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Estadistica;
import ae.plantel.GeneradorInstancias;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Experimento multiobjetivo: NSGA-II contra agregación lineal con barrido de β.
 *
 * [Informe §III-C variante multiobjetivo y §IV-C métricas; Tema 10 (métodos
 * basados en Pareto vs agregación) y Tema 12 (qué reportar en MOEAs)]
 *
 * Los dos competidores gastan el MISMO presupuesto total por corrida:
 * <ul>
 *   <li><b>NSGA-II</b> ({@link NSGAIIPlantel}): una corrida con B evaluaciones,
 *       devuelve un frente.</li>
 *   <li><b>Agregación</b> (Tema 10, "agregación o combinación lineal de
 *       objetivos"): el AE mono-objetivo ({@link AGPlantel}) corrido con k
 *       valores de β, cada uno con B/k evaluaciones; el frente es el conjunto no
 *       dominado de los k planes. Es lo que haría alguien que no usa un MOEA.</li>
 * </ul>
 *
 * Todo plan se revalúa con N = 200 sobre escenarios de validación. El frente de
 * referencia es la unión no dominada de TODOS los frentes de las dos técnicas
 * en todas las corridas (Tema 12: "mínimo 30, recomendado 50"). Se reportan,
 * por métrica, media, desvío, mejor, y el test que corresponde según
 * Shapiro-Wilk (t de Welch o Mann-Whitney).
 *
 * Además se ubica el plan mono-objetivo con el β del modelo respecto del frente
 * de NSGA-II (por D-A6 debería quedar sobre el frente o muy cerca), y el statu
 * quo y, en las instancias reales, el plan real del club respecto de todos los
 * frentes.
 *
 * Protocolo (D-A13): N = 10 réplicas, 120.000 evaluaciones por defecto.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.ExperimentoMO [evaluaciones] [corridas] [instancia]
 *       instancia: demo | eval-chica | eval-media | eval-grande
 *                  | barcelona-2020-chica | barcelona-2020-media | barcelona-2020-grande
 *       (por defecto demo). Con las reales usa Parametros.calibrados() y ubica
 *       además el plan real del club respecto de los frentes (P29).
 *
 * Deja resultados/mo_&lt;instancia&gt;_metricas.csv y resultados/mo_&lt;instancia&gt;_frentes.csv.
 */
public final class ExperimentoMO {

    /**
     * Barrido de β de la agregación, como múltiplos del β del modelo (incluye
     * el propio β). Con el β sintético (0,05) da {0; 0,02; 0,05; 0,10; 0,20;
     * 0,50}, el barrido original; con el calibrado (0,01, D-J6) da {0; 0,004;
     * 0,01; 0,02; 0,04; 0,10}, que cubre el mismo rango relativo en la escala
     * monetaria real (DECISIONES D-A17).
     */
    static final double[] BETAS_RELATIVOS = { 0.0, 0.4, 1.0, 2.0, 4.0, 10.0 };

    private ExperimentoMO() {
    }

    private record Corrida(String algoritmo, int semilla, List<double[]> frente, double segundos,
                           double[] puntoBetaModelo) {
    }

    /** true si la instancia es una de las reales (datos del Barcelona, D-J4). */
    public static boolean esReal(String nombre) {
        return nombre.startsWith("barcelona-");
    }

    /**
     * Parámetros que corresponden a la instancia: calibrados con datos reales
     * para las reales (D-J2), los de siempre para las sintéticas. Devuelve un
     * objeto nuevo en cada llamada, así se puede modificar (por ejemplo β).
     */
    public static Parametros parametros(String nombre) {
        return esReal(nombre) ? Parametros.calibrados() : new Parametros();
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        int evaluaciones = args.length > 0 ? Integer.parseInt(args[0]) : AlgoritmoEvolutivo.EVALUACIONES_FINALES;
        int corridas = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        String nombre = args.length > 2 ? args[2] : "demo";
        Instancia inst = instancia(nombre);
        Parametros base = parametros(nombre);
        double[] betas = betas(base);
        String linea = "=".repeat(86);
        System.out.println(inst.resumen());
        System.out.printf("Parámetros %s | presupuesto por corrida: %d evaluaciones | %d corridas por técnica | betas %s%n",
                esReal(nombre) ? "calibrados (D-J2)" : "por defecto", evaluaciones, corridas,
                java.util.Arrays.toString(betas));

        int hilos = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<Corrida>> futuros = new ArrayList<>();
        for (int s = 1; s <= corridas; s++) {
            final int sem = s;
            futuros.add(pool.submit((Callable<Corrida>) () -> correrNsga(inst, base, sem, evaluaciones)));
            futuros.add(pool.submit((Callable<Corrida>) () -> correrAgregacion(inst, nombre, sem, evaluaciones)));
        }
        List<Corrida> nsga = new ArrayList<>();
        List<Corrida> agreg = new ArrayList<>();
        for (Future<Corrida> f : futuros) {
            Corrida c = f.get();
            (c.algoritmo().equals("NSGA-II") ? nsga : agreg).add(c);
        }
        pool.shutdown();

        // ---------------- frente de referencia ----------------
        List<double[]> union = new ArrayList<>();
        for (Corrida c : nsga) {
            union.addAll(c.frente());
        }
        for (Corrida c : agreg) {
            union.addAll(c.frente());
        }
        IndicadoresMO ind = new IndicadoresMO(union);
        System.out.println();
        System.out.println(linea);
        System.out.printf("Frente de referencia: %d puntos no dominados (unión de %d frentes)%n",
                ind.referencia().size(), nsga.size() + agreg.size());
        System.out.println(linea);

        // ---------------- métricas por corrida ----------------
        String[] metricas = { "RHV", "spread", "GD", "puntos", "segundos" };
        double[][] mN = new double[metricas.length][nsga.size()];
        double[][] mA = new double[metricas.length][agreg.size()];
        StringBuilder csv = new StringBuilder("algoritmo,semilla,rhv,spread,gd,puntos,segundos\n");
        llenar(ind, nsga, mN, csv);
        llenar(ind, agreg, mA, csv);

        System.out.printf("%-10s%18s%18s%14s%14s  %s%n", "métrica", "NSGA-II media±sd",
                "agregación media±sd", "mejor NSGA", "mejor agreg.", "test (protocolo del curso)");
        boolean[] menorEsMejor = { false, true, true, false, true };
        for (int k = 0; k < metricas.length; k++) {
            double[] a = mN[k];
            double[] b = mA[k];
            String test = comparar(a, b);
            System.out.printf("%-10s%11.4f±%-6.4f%11.4f±%-6.4f%14.4f%14.4f  %s%n", metricas[k],
                    Estadistica.media(a), Estadistica.desvio(a), Estadistica.media(b), Estadistica.desvio(b),
                    menorEsMejor[k] ? min(a) : Estadistica.maximo(a),
                    menorEsMejor[k] ? min(b) : Estadistica.maximo(b), test);
        }

        // ---------------- el plan mono-objetivo respecto del frente ----------------
        System.out.println();
        System.out.println(linea);
        System.out.printf("Plan mono-objetivo (β = %.3f, el del modelo) respecto del frente de NSGA-II de la misma semilla%n",
                base.beta);
        System.out.println(linea);
        int dominados = 0;
        double gdMono = 0;
        for (Corrida a : agreg) {
            Corrida n = nsga.stream().filter(x -> x.semilla() == a.semilla()).findFirst().orElseThrow();
            double[] q = a.puntoBetaModelo();
            boolean dom = n.frente().stream().anyMatch(p -> IndicadoresMO.domina(p, q));
            if (dom) {
                dominados++;
            }
            gdMono += ind.distanciaGeneracional(List.of(q)) / agreg.size();
        }
        System.out.printf("  dominado por el frente de NSGA-II en %d de %d corridas; "
                + "distancia media al frente de referencia (normalizada) %.4f%n", dominados, agreg.size(), gdMono);

        // ---------------- plan real y statu quo respecto de los frentes ----------------
        ProblemaPlantelMO val = new ProblemaPlantelMO(inst, base, AlgoritmoEvolutivo.N_VALIDACION,
                AlgoritmoEvolutivo.SEMILLA_VALIDACION, new Aleatorio(0));
        List<String[]> extras = new ArrayList<>();
        extras.add(new String[] { "statu_quo", null });
        if (esReal(nombre)) {
            extras.add(new String[] { "plan_real", null });
        }
        System.out.println();
        System.out.println(linea);
        System.out.println("Planes de referencia respecto de los frentes (f1 utilidad deportiva, f2 costo neto)");
        System.out.println(linea);
        StringBuilder extrasCsv = new StringBuilder();
        for (String[] e : extras) {
            ae.plantel.Cromosoma x = e[0].equals("plan_real") ? ae.plantel.InstanciasReales.planReal(inst)
                    : ae.plantel.Cromosoma.statuQuo(inst);
            SolucionPlantel s = SolucionPlantel.desde(x, 2);
            val.evaluate(s);
            double[] q = s.getObjectives().clone();
            long domNsga = nsga.stream().filter(c -> c.frente().stream().anyMatch(pt -> IndicadoresMO.domina(pt, q))).count();
            long domAgreg = agreg.stream().filter(c -> c.frente().stream().anyMatch(pt -> IndicadoresMO.domina(pt, q))).count();
            boolean domRef = ind.referencia().stream().anyMatch(pt -> IndicadoresMO.domina(pt, q));
            System.out.printf("  %-10s f1 = %8.2f  f2 = %9.2f | dominado por el frente de NSGA-II en %d de %d corridas, "
                    + "por el de la agregación en %d de %d; por el de referencia: %s%n", e[0], -q[0], q[1],
                    domNsga, nsga.size(), domAgreg, agreg.size(), domRef ? "sí" : "no");
            extrasCsv.append(String.format(Locale.ROOT, "%s,0,%.4f,%.4f%n", e[0], -q[0], q[1]));
        }

        // ---------------- archivos ----------------
        StringBuilder frentes = new StringBuilder("algoritmo,semilla,f1_utilidad,f2_costo_neto\n");
        for (Corrida c : nsga) {
            volcar(frentes, c);
        }
        for (Corrida c : agreg) {
            volcar(frentes, c);
        }
        for (double[] p : ind.referencia()) {
            frentes.append(String.format(Locale.ROOT, "referencia,0,%.4f,%.4f%n", -p[0], p[1]));
        }
        frentes.append(extrasCsv);
        escribir("resultados/mo_" + nombre + "_metricas.csv", csv.toString());
        escribir("resultados/mo_" + nombre + "_frentes.csv", frentes.toString());
    }

    // ----------------------------------------------------------------

    private static Corrida correrNsga(Instancia inst, Parametros p, int semilla, int evaluaciones) {
        long t0 = System.nanoTime();
        AlgoritmoEvolutivo.Config cfg = AlgoritmoEvolutivo.Config.protocoloFinal();   // N = 10 (D-A13)
        cfg.semilla = semilla;
        cfg.maxEvaluaciones = evaluaciones;
        NSGAIIPlantel alg = new NSGAIIPlantel(inst, cfg, p);
        alg.run();
        return new Corrida("NSGA-II", semilla, IndicadoresMO.puntos(alg.getResult()),
                (System.nanoTime() - t0) / 1e9, null);
    }

    /** β absolutos del barrido para los parámetros dados (D-A17). */
    static double[] betas(Parametros p) {
        double[] b = new double[BETAS_RELATIVOS.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = BETAS_RELATIVOS[i] * p.beta;
        }
        return b;
    }

    private static Corrida correrAgregacion(Instancia inst, String nombre, int semilla, int evaluaciones) {
        long t0 = System.nanoTime();
        List<double[]> puntos = new ArrayList<>();
        double[] puntoModelo = null;
        Parametros modelo = parametros(nombre);
        ProblemaPlantelMO val = new ProblemaPlantelMO(inst, modelo, AlgoritmoEvolutivo.N_VALIDACION,
                AlgoritmoEvolutivo.SEMILLA_VALIDACION, new Aleatorio(0));
        double[] betas = betas(modelo);
        for (double beta : betas) {
            Parametros p = parametros(nombre);
            p.beta = beta;
            AlgoritmoEvolutivo.Config cfg = AlgoritmoEvolutivo.Config.protocoloFinal();   // N = 10 (D-A13)
            cfg.semilla = semilla;
            cfg.maxEvaluaciones = evaluaciones / betas.length;
            AlgoritmoEvolutivo.Resultado r = AGPlantel.ejecutar(inst, cfg, p);
            // f1 y f2 no dependen de β: se miden siempre con los parámetros del modelo.
            SolucionPlantel s = SolucionPlantel.desde(r.mejor, 2);
            val.evaluate(s);
            double[] q = s.getObjectives().clone();
            puntos.add(q);
            if (beta == modelo.beta) {
                puntoModelo = q;
            }
        }
        return new Corrida("agregacion", semilla, IndicadoresMO.noDominados(puntos),
                (System.nanoTime() - t0) / 1e9, puntoModelo);
    }

    private static void llenar(IndicadoresMO ind, List<Corrida> cs, double[][] m, StringBuilder csv) {
        for (int i = 0; i < cs.size(); i++) {
            Corrida c = cs.get(i);
            m[0][i] = ind.hipervolumenRelativo(c.frente());
            m[1][i] = ind.spread(c.frente());
            m[2][i] = ind.distanciaGeneracional(c.frente());
            m[3][i] = c.frente().size();
            m[4][i] = c.segundos();
            csv.append(String.format(Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f,%d,%.1f%n", c.algoritmo(), c.semilla(),
                    m[0][i], m[1][i], m[2][i], c.frente().size(), c.segundos()));
        }
    }

    /** Shapiro-Wilk en las dos muestras; t de Welch si son normales, si no Mann-Whitney. */
    static String comparar(double[] a, double[] b) {
        if (a.length < 3 || b.length < 3) {
            return "n < 3: sin test";
        }
        boolean constante = Estadistica.desvio(a) == 0 && Estadistica.desvio(b) == 0;
        if (constante) {
            return "sin variación";
        }
        boolean normales = Estadistica.desvio(a) > 0 && Estadistica.desvio(b) > 0
                && Estadistica.esNormalShapiro(a) && Estadistica.esNormalShapiro(b);
        double p = normales ? Estadistica.tStudent(a, b)[2] : Estadistica.mannWhitney(a, b)[2];
        return String.format(Locale.ROOT, "%s p = %.4f%s", normales ? "t Welch" : "Mann-Whitney", p,
                p < 0.05 ? " (significativa)" : "");
    }

    private static double min(double[] v) {
        double m = Double.POSITIVE_INFINITY;
        for (double x : v) {
            m = Math.min(m, x);
        }
        return m;
    }

    private static void volcar(StringBuilder sb, Corrida c) {
        for (double[] p : c.frente()) {
            sb.append(String.format(Locale.ROOT, "%s,%d,%.4f,%.4f%n", c.algoritmo(), c.semilla(), -p[0], p[1]));
        }
    }

    private static void escribir(String ruta, String contenido) throws IOException {
        Path destino = Path.of(ruta);
        if (destino.getParent() != null) {
            Files.createDirectories(destino.getParent());
        }
        Files.writeString(destino, contenido, StandardCharsets.UTF_8);
        System.out.println("  escrito " + destino);
    }

    /**
     * Instancia por nombre: la demo de desarrollo, una sintética de evaluación o
     * de configuración, o una real ({@code barcelona-2020-chica|media|grande},
     * D-J4, cargada de instancias/).
     */
    public static Instancia instancia(String nombre) {
        if (esReal(nombre)) {
            return ae.plantel.InstanciasReales.cargar(nombre);
        }
        if (nombre.equals("demo")) {
            return GeneradorInstancias.generar("demo", 25, 50, 101);
        }
        for (Instancia i : GeneradorInstancias.evaluacion()) {
            if (i.nombre.equals(nombre)) {
                return i;
            }
        }
        for (Instancia i : GeneradorInstancias.configuracion()) {
            if (i.nombre.equals(nombre)) {
                return i;
            }
        }
        throw new IllegalArgumentException("instancia desconocida: " + nombre);
    }
}
