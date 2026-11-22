package ae.plantel.jmetal;

import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Cromosoma;
import ae.plantel.Estadistica;
import ae.plantel.GeneradorInstancias;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Parametros;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Valida que el AE sobre jMetal ({@link AGPlantel}) sea equivalente al AE de
 * referencia ({@link AlgoritmoEvolutivo}) de Juan.
 *
 * [Informe §III; DECISIONES D-A1: la versión jMetal reemplaza a la propia en
 * los experimentos solo si rinde lo mismo]
 *
 * Qué se chequea:
 *  1. Reproducibilidad: la misma semilla da el mismo plan, aunque las corridas
 *     vayan en paralelo (es lo que motiva D-A2).
 *  2. Presupuesto: las dos implementaciones gastan las mismas evaluaciones y
 *     hacen las mismas generaciones.
 *  3. Calidad: con R semillas por implementación, las distribuciones del
 *     fitness revaluado (N = 200, escenarios de validación) no difieren:
 *     Mann-Whitney (y t de Welch si las dos son normales según Shapiro-Wilk),
 *     más el criterio |media A - media B| &lt;= máx(desvíos) del Tema 12.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.ValidarEquivalenciaJMetal [semillas] [evaluaciones]
 */
public final class ValidarEquivalenciaJMetal {

    private ValidarEquivalenciaJMetal() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        int semillas = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int evaluaciones = args.length > 1 ? Integer.parseInt(args[1]) : 8000;
        Parametros p = new Parametros();
        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        System.out.println(inst.resumen());
        String linea = "=".repeat(78);

        // ---------------- 1. reproducibilidad en paralelo ----------------
        System.out.println(linea);
        System.out.println("1. Reproducibilidad: semilla 1, dos veces y en paralelo");
        System.out.println(linea);
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()));
        Future<AlgoritmoEvolutivo.Resultado> r1 = pool.submit(() -> AGPlantel.ejecutar(inst, config(1, 2000), p));
        Future<AlgoritmoEvolutivo.Resultado> r2 = pool.submit(() -> AGPlantel.ejecutar(inst, config(1, 2000), p));
        Cromosoma a = r1.get().mejor;
        Cromosoma b = r2.get().mejor;
        boolean igual = a.igualA(b) && r1.get().fitnessMejor.valor == r2.get().fitnessMejor.valor;
        System.out.printf("  f = %.4f y %.4f -> %s%n", r1.get().fitnessMejor.valor,
                r2.get().fitnessMejor.valor, igual ? "IDÉNTICOS (OK)" : "DISTINTOS (FALLA)");

        // ---------------- 2 y 3. presupuesto y calidad ----------------
        System.out.println();
        System.out.println(linea);
        System.out.printf("2-3. %d semillas por implementación, %d evaluaciones%n", semillas, evaluaciones);
        System.out.println(linea);
        List<Future<AlgoritmoEvolutivo.Resultado>> propios = new ArrayList<>();
        List<Future<AlgoritmoEvolutivo.Resultado>> jmetal = new ArrayList<>();
        for (int s = 1; s <= semillas; s++) {
            final int sem = s;
            propios.add(pool.submit(() -> AlgoritmoEvolutivo.ejecutar(inst, config(sem, evaluaciones), p)));
            jmetal.add(pool.submit(() -> AGPlantel.ejecutar(inst, config(sem, evaluaciones), p)));
        }
        double[] fp = new double[semillas];
        double[] fj = new double[semillas];
        boolean mismoPresupuesto = true;
        System.out.printf("%8s%14s%14s%10s%10s%8s%8s%n", "semilla", "f propio", "f jMetal",
                "evals P", "evals J", "gen P", "gen J");
        for (int i = 0; i < semillas; i++) {
            AlgoritmoEvolutivo.Resultado rp = propios.get(i).get();
            AlgoritmoEvolutivo.Resultado rj = jmetal.get(i).get();
            fp[i] = rp.fitnessMejor.valor;
            fj[i] = rj.fitnessMejor.valor;
            mismoPresupuesto &= rp.evaluaciones == rj.evaluaciones && rp.generaciones == rj.generaciones;
            System.out.printf("%8d%14.2f%14.2f%10d%10d%8d%8d%n", i + 1, fp[i], fj[i],
                    rp.evaluaciones, rj.evaluaciones, rp.generaciones, rj.generaciones);
        }
        pool.shutdown();

        System.out.println();
        System.out.printf("  propio : media %.2f  desvío %.2f  mediana %.2f  mejor %.2f%n",
                Estadistica.media(fp), Estadistica.desvio(fp), Estadistica.mediana(fp), Estadistica.maximo(fp));
        System.out.printf("  jMetal : media %.2f  desvío %.2f  mediana %.2f  mejor %.2f%n",
                Estadistica.media(fj), Estadistica.desvio(fj), Estadistica.mediana(fj), Estadistica.maximo(fj));
        System.out.printf("  presupuesto y generaciones iguales: %s%n", mismoPresupuesto ? "sí (OK)" : "NO (FALLA)");

        // Protocolo del curso (StatTests lámina 9, informe §IV-B): normalidad con
        // Shapiro-Wilk; si las dos son normales, t de Student (Welch); si no,
        // Mann-Whitney. Se muestran los dos para que se vea si discrepan.
        double[] swp = Estadistica.shapiroWilk(fp);
        double[] swj = Estadistica.shapiroWilk(fj);
        boolean normales = swp[1] >= 0.05 && swj[1] >= 0.05;
        System.out.printf("  Shapiro-Wilk p: propio %.3f, jMetal %.3f -> %s%n", swp[1], swj[1],
                normales ? "normales, corresponde t de Student" : "no normales, corresponde Mann-Whitney");
        double[] mw = Estadistica.mannWhitney(fj, fp);
        double[] t = Estadistica.tStudent(fj, fp);
        System.out.printf("  t de Welch:   t = %.3f, p = %.4f%s%n", t[0], t[2], normales ? "   <- test del protocolo" : "");
        System.out.printf("  Mann-Whitney: U = %.1f, p = %.4f%s%n", mw[0], mw[2], normales ? "" : "   <- test del protocolo");
        double pProtocolo = normales ? t[2] : mw[2];
        double dif = Estadistica.media(fj) - Estadistica.media(fp);
        double ee = Math.sqrt(Estadistica.varianza(fj) / fj.length + Estadistica.varianza(fp) / fp.length);
        System.out.printf("  diferencia de medias (jMetal - propio): %+.2f, IC 95%% aprox. [%+.2f, %+.2f]%n",
                dif, dif - 1.96 * ee, dif + 1.96 * ee);
        boolean criterio = !Estadistica.difiereCriterioTema12(fj, fp);
        System.out.printf("  criterio del Tema 12 (|dif. medias| <= máx desvíos): %s%n",
                criterio ? "no difieren" : "DIFIEREN");
        boolean ok = igual && mismoPresupuesto && pProtocolo >= 0.05 && criterio;
        System.out.println();
        System.out.println(ok ? "EQUIVALENCIA OK: el AE sobre jMetal rinde igual que el de referencia."
                : "EQUIVALENCIA NO CONFIRMADA: revisar.");
        if (!ok) {
            System.exit(1);
        }
    }

    private static AlgoritmoEvolutivo.Config config(int semilla, int evaluaciones) {
        AlgoritmoEvolutivo.Config c = new AlgoritmoEvolutivo.Config();
        c.semilla = semilla;
        c.maxEvaluaciones = evaluaciones;
        return c;
    }
}
