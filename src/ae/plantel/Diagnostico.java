package ae.plantel;

import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Diagnóstico del plan que encuentra el AE: de dónde sale la utilidad.
 *
 * [Informe §I-B y §I-C: verificación de que la utilidad sale de los puntos de liga]
 *
 * Sirve para detectar que el AE esté explotando un agujero del simulador en
 * lugar de resolver el problema. Las señales de alarma conocidas:
 *   - patrimonio que sube sin que suban los puntos (renovaciones o fichajes
 *     contables al final del horizonte);
 *   - plantel que se achica y aun así rinde más;
 *   - jugadores perdidos en masa por cláusula o por vencimiento.
 */
public final class Diagnostico {

    private static final Parametros P = new Parametros();

    private static void desglose(String nombre, Instancia inst, Cromosoma x) {
        int n = 200;
        double u = 0, caja = 0, pases = 0, jug = 0, libres = 0, clausula = 0;
        double sal = 0, gasto = 0, pen = 0;
        double[] pts = new double[P.temporadas];
        for (int k = 0; k < n; k++) {
            Evaluador.Resultado r = Evaluador.simular(inst, x, new Escenario(777_000L + k), P);
            u += r.utilidad / n;
            caja += r.caja / n;
            pases += r.valorPlantel / n;
            jug += r.plantelFinal / (double) n;
            libres += r.salidasLibres / (double) n;
            clausula += r.perdidosClausula / (double) n;
            sal += r.salariosPagados / n;
            gasto += r.gastoPases / n;
            pen += r.penalizacion / n;
            for (int t = 0; t < pts.length; t++) {
                pts[t] += r.puntos[t] / n;
            }
        }
        System.out.printf("%-14s U=%7.2f  puntos %5.1f %5.1f %5.1f  pen %5.2f%n",
                nombre, u, pts[0], pts[1], pts[2], pen);
        System.out.printf("%-14s caja %7.1fM  pases %7.1fM  jugadores %4.1f  "
                        + "libres %4.1f  por cláusula %4.2f%n", "", caja, pases, jug, libres, clausula);
        System.out.printf("%-14s salarios pagados %6.1fM  gasto neto en pases %7.1fM%n%n",
                "", sal, gasto);
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);

        AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
        cfg.maxEvaluaciones = args.length > 0 ? Integer.parseInt(args[0]) : 12000;
        AlgoritmoEvolutivo.Resultado res = AlgoritmoEvolutivo.ejecutar(inst, cfg, P);

        System.out.println("=".repeat(78));
        System.out.println("¿De dónde sale la utilidad?");
        System.out.println("=".repeat(78));
        desglose("statu quo", inst, Cromosoma.statuQuo(inst));
        desglose("mejor del AE", inst, res.mejor);
    }
}
