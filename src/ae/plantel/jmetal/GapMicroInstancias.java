package ae.plantel.jmetal;

import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Cromosoma;
import ae.plantel.Estadistica;
import ae.plantel.Evaluador;
import ae.plantel.GeneradorInstancias;
import ae.plantel.Modelo.Instancia;
import ae.plantel.OptimoExacto;
import ae.plantel.Parametros;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
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
 * GAP del AE respecto del óptimo exacto en micro-instancias difíciles.
 *
 * [Informe §IV-B(2): "sobre instancias reducidas (cinco jugadores, dominio de
 * acciones restringido y condiciones contractuales fijas) y con escenarios
 * fijados de antemano, se enumerará el espacio completo"; Tema 12: % GAP]
 * (Alen, P16)
 *
 * En la micro-instancia original (4 propios + 2 candidatos) el statu quo ya
 * quedaba a 1% del óptimo, así que un GAP de 0% decía poco (VALIDACION §7.1).
 * Acá se usan tres micro-instancias de CINCO jugadores (3 propios + 2
 * candidatos, como dice el informe) elegidas porque el statu quo queda lejos
 * del óptimo: semillas 55, 56 y 57 (5,4%, 12,1% y 5,5%).
 *
 * Para cada una: se enumeran los 107.811 planes (N = 30 escenarios fijos) y se
 * corre el AE sobre jMetal, restringido al mismo espacio (grilla), con 30
 * semillas por presupuesto. El AE optimiza el MISMO objetivo que se enumera:
 * busca y elige su plan final sobre esos 30 escenarios fijos, sin rotarlos
 * (D-A15). Si rotara, estaría optimizando el valor esperado, que no es lo que
 * la enumeración maximiza, y el GAP mezclaría dos objetivos distintos.
 * GAP = [f(óptimo) − f(AE)] / f(AE) (Tema 12).
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.GapMicroInstancias [corridas]
 * Deja resultados/gap_micro.csv.
 */
public final class GapMicroInstancias {

    static final long[] SEMILLAS = { 55, 56, 57 };
    static final int[] PRESUPUESTOS = { 500, 1000, 2000, 4000, 8000 };

    private GapMicroInstancias() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        int corridas = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        Parametros p = new Parametros();
        StringBuilder csv = new StringBuilder("instancia,presupuesto,semilla,fitness,gap_pct,optimo\n");
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (long semInst : SEMILLAS) {
            Instancia inst = GeneradorInstancias.generar("micro-" + semInst, 3, 2, semInst,
                    78.0, 26.5, 120.0, 1.25);
            OptimoExacto.Optimo opt = OptimoExacto.enumerar(inst, OptimoExacto.N_ENUM, OptimoExacto.SEMILLA_ENUM);
            double fsq = Evaluador.evaluar(inst, Cromosoma.statuQuo(inst), OptimoExacto.N_ENUM,
                    OptimoExacto.SEMILLA_ENUM, p).valor;
            System.out.println();
            System.out.println(inst.resumen());
            System.out.printf("óptimo %.4f (%,d planes, %.1f s); statu quo %.4f -> GAP del statu quo %.2f%%%n",
                    opt.valor(), opt.planes(), opt.segundos(), fsq, gap(opt.valor(), fsq));
            System.out.printf("%12s%12s%10s%10s%10s%14s%n", "presupuesto", "f media", "GAP med", "GAP sd",
                    "GAP peor", "óptimo en");
            for (int pres : PRESUPUESTOS) {
                List<Future<Double>> fs = new ArrayList<>();
                for (int r = 1; r <= corridas; r++) {
                    AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
                    cfg.poblacion = 50;
                    cfg.maxEvaluaciones = pres;
                    cfg.replicas = OptimoExacto.N_ENUM;
                    cfg.grilla = true;
                    cfg.semilla = r;
                    // El AE optimiza el MISMO objetivo que se enumera: escenarios
                    // fijos de la enumeración, sin rotar (DECISIONES D-A15).
                    cfg.semillaEscenariosFija = OptimoExacto.SEMILLA_ENUM;
                    cfg.replicasSeleccion = OptimoExacto.N_ENUM;
                    fs.add(pool.submit((Callable<Double>) () -> Evaluador.evaluar(inst,
                            AGPlantel.ejecutar(inst, cfg, p).mejor, OptimoExacto.N_ENUM,
                            OptimoExacto.SEMILLA_ENUM, p).valor));
                }
                double[] g = new double[corridas];
                double[] f = new double[corridas];
                int hits = 0;
                for (int r = 0; r < corridas; r++) {
                    f[r] = fs.get(r).get();
                    g[r] = gap(opt.valor(), f[r]);
                    boolean esOptimo = f[r] >= opt.valor() - 1e-9;
                    hits += esOptimo ? 1 : 0;
                    csv.append(String.format(Locale.ROOT, "%s,%d,%d,%.6f,%.4f,%b%n", inst.nombre, pres, r + 1,
                            f[r], g[r], esOptimo));
                }
                System.out.printf("%12d%12.4f%9.2f%%%9.2f%%%9.2f%%%10d de %d%n", pres, Estadistica.media(f),
                        Estadistica.media(g), Estadistica.desvio(g), Estadistica.maximo(g), hits, corridas);
            }
        }
        pool.shutdown();
        Files.createDirectories(Path.of("resultados"));
        Files.writeString(Path.of("resultados/gap_micro.csv"), csv.toString(), StandardCharsets.UTF_8);
        System.out.println();
        System.out.println("  datos crudos en resultados/gap_micro.csv");
    }

    /** % GAP del Tema 12: [f(VT) − f(AE)] / f(AE). */
    static double gap(double optimo, double f) {
        return 100.0 * (optimo - f) / Math.abs(f);
    }
}
