package ae.plantel.jmetal;

import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Estadistica;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Parametros;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Validación y evaluación del modelo de islas.
 *
 * [Informe §III-C islas con migración en anillo; §IV-C "speedup del esquema
 * paralelo al variar los recursos"; Tema 11; Tema 12 "en el caso de AE
 * paralelos debe estudiarse la calidad de resultados y eficiencia
 * computacional al utilizar diferente número de recursos"] (Alen, P13)
 *
 *  1. Determinismo: la misma semilla da el mismo plan con 1 hilo y con k hilos
 *     (la migración es sincrónica).
 *  2. Speedup y eficiencia: tiempo con 1, 2, ..., k hilos para la misma corrida
 *     (mediana de 3 repeticiones). speedup = T₁ / T_h, eficiencia = speedup / h.
 *  3. Calidad: islas contra el AE panmíctico ({@link AGPlantel}) con el mismo
 *     presupuesto, R semillas; Shapiro-Wilk → t de Welch o Mann-Whitney.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.ExperimentoIslas [semillas] [evaluaciones] [islas] [instancia]
 */
public final class ExperimentoIslas {

    static final int INTERVALO = 5;
    static final int MIGRANTES = 2;

    private ExperimentoIslas() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        int semillas = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int evaluaciones = args.length > 1 ? Integer.parseInt(args[1]) : 24_000;
        int islas = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        String nombre = args.length > 3 ? args[3] : "demo";
        Instancia inst = ExperimentoMO.instancia(nombre);
        Parametros p = new Parametros();
        int nucleos = Runtime.getRuntime().availableProcessors();
        String linea = "=".repeat(78);
        System.out.println(inst.resumen());
        System.out.printf("%d islas, migración en anillo cada %d generaciones, %d migrantes; %d núcleos%n",
                islas, INTERVALO, MIGRANTES, nucleos);

        // ---------------- 1. determinismo ----------------
        System.out.println(linea);
        System.out.println("1. Determinismo: semilla 1 con 1 hilo y con " + islas + " hilos");
        System.out.println(linea);
        AlgoritmoEvolutivo.Resultado r1 = correr(inst, p, 1, 2000, islas, 1);
        AlgoritmoEvolutivo.Resultado rk = correr(inst, p, 1, 2000, islas, islas);
        boolean det = r1.mejor.igualA(rk.mejor) && r1.fitnessMejor.valor == rk.fitnessMejor.valor;
        System.out.printf("  f = %.4f y %.4f -> %s%n", r1.fitnessMejor.valor, rk.fitnessMejor.valor,
                det ? "IDÉNTICOS (OK)" : "DISTINTOS (FALLA)");

        // ---------------- 2. speedup ----------------
        System.out.println();
        System.out.println(linea);
        System.out.printf("2. Speedup (una corrida de %d evaluaciones, mediana de 3)%n", evaluaciones);
        System.out.println(linea);
        System.out.printf("%8s%12s%10s%12s%n", "hilos", "segundos", "speedup", "eficiencia");
        double t1 = 0;
        StringBuilder csvS = new StringBuilder("hilos,segundos,speedup,eficiencia\n");
        for (int h = 1; h <= Math.min(islas, Math.max(nucleos, 1)); h++) {
            double[] t = new double[3];
            for (int k = 0; k < 3; k++) {
                t[k] = correr(inst, p, 1, evaluaciones, islas, h).segundos;
            }
            double med = Estadistica.mediana(t);
            if (h == 1) {
                t1 = med;
            }
            double sp = t1 / med;
            System.out.printf("%8d%12.2f%10.2f%12.2f%n", h, med, sp, sp / h);
            csvS.append(String.format(Locale.ROOT, "%d,%.3f,%.3f,%.3f%n", h, med, sp, sp / h));
        }
        if (nucleos < islas) {
            System.out.printf("  (esta máquina tiene %d núcleos: con más hilos no hay más paralelismo real;"
                    + " el barrido completo va en ClusterUY)%n", nucleos);
        }

        // ---------------- 3. calidad ----------------
        System.out.println();
        System.out.println(linea);
        System.out.printf("3. Calidad: islas vs AE panmíctico, %d semillas, %d evaluaciones%n", semillas, evaluaciones);
        System.out.println(linea);
        double[] fi = new double[semillas];
        double[] fp = new double[semillas];
        StringBuilder csvQ = new StringBuilder("tecnica,semilla,fitness,segundos\n");
        for (int s = 1; s <= semillas; s++) {
            AlgoritmoEvolutivo.Resultado ri = correr(inst, p, s, evaluaciones, islas, Math.min(islas, nucleos));
            AlgoritmoEvolutivo.Config cfg = config(s, evaluaciones);
            AlgoritmoEvolutivo.Resultado rp = AGPlantel.ejecutar(inst, cfg, p);
            fi[s - 1] = ri.fitnessMejor.valor;
            fp[s - 1] = rp.fitnessMejor.valor;
            csvQ.append(String.format(Locale.ROOT, "islas,%d,%.4f,%.2f%npanmictico,%d,%.4f,%.2f%n",
                    s, fi[s - 1], ri.segundos, s, fp[s - 1], rp.segundos));
        }
        System.out.printf("  islas      : media %.2f  desvío %.2f  mejor %.2f%n", Estadistica.media(fi),
                Estadistica.desvio(fi), Estadistica.maximo(fi));
        System.out.printf("  panmíctico : media %.2f  desvío %.2f  mejor %.2f%n", Estadistica.media(fp),
                Estadistica.desvio(fp), Estadistica.maximo(fp));
        System.out.printf("  test: %s%n", ExperimentoMO.comparar(fi, fp));

        Files.createDirectories(Path.of("resultados"));
        Files.writeString(Path.of("resultados/islas_speedup_" + nombre + ".csv"), csvS.toString(), StandardCharsets.UTF_8);
        Files.writeString(Path.of("resultados/islas_calidad_" + nombre + ".csv"), csvQ.toString(), StandardCharsets.UTF_8);
        System.out.println("  datos crudos en resultados/islas_*_" + nombre + ".csv");
        if (!det) {
            System.exit(1);
        }
    }

    private static AlgoritmoEvolutivo.Resultado correr(Instancia inst, Parametros p, int semilla,
                                                       int evaluaciones, int islas, int hilos) throws Exception {
        return new IslasPlantel(inst, config(semilla, evaluaciones), p, islas, INTERVALO, MIGRANTES, hilos).ejecutar();
    }

    private static AlgoritmoEvolutivo.Config config(int semilla, int evaluaciones) {
        AlgoritmoEvolutivo.Config c = AlgoritmoEvolutivo.Config.protocoloFinal();   // N = 10 (D-A13)
        c.semilla = semilla;
        c.maxEvaluaciones = evaluaciones;
        return c;
    }
}
