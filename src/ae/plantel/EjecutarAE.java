package ae.plantel;

import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Primera pregunta real del proyecto: ¿el AE le gana al statu quo, y le gana a
 *
 * [Informe §IV-B(4) búsqueda aleatoria y §IV-B(5) statu quo]
 * la búsqueda aleatoria con el MISMO presupuesto de evaluaciones?
 *
 * La comparación contra búsqueda aleatoria con presupuesto igualado es la que
 * demuestra que la mejora viene del mecanismo evolutivo y no de haber mirado
 * más soluciones.
 *
 * Todas las soluciones finales se revalúan con N=200 y escenarios distintos de
 * los usados durante la búsqueda: comparar fitness obtenidos con los escenarios
 * de entrenamiento sobreestima a quien tuvo suerte.
 *
 * Uso:  java -cp out ae.plantel.EjecutarAE [evaluaciones] [semilla]
 */
public final class EjecutarAE {

    private static final Parametros P = new Parametros();
    private static final long SEMILLA_VALIDACION = 777_000L;
    private static final int N_VALIDACION = 200;

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        final String linea = "=".repeat(78);

        int evaluaciones = args.length > 0 ? Integer.parseInt(args[0]) : 12000;
        long semilla = args.length > 1 ? Long.parseLong(args[1]) : 1L;

        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        System.out.println(inst.resumen());

        AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
        cfg.maxEvaluaciones = evaluaciones;
        cfg.semilla = semilla;
        System.out.println("Config: " + cfg);

        // ---------------- referencia: statu quo ----------------
        Cromosoma sq = Cromosoma.statuQuo(inst);
        Evaluador.Fitness fsq = Evaluador.evaluar(inst, sq, N_VALIDACION, SEMILLA_VALIDACION, P);

        // ---------------- AE ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("1. Algoritmo evolutivo");
        System.out.println(linea);
        AlgoritmoEvolutivo.Resultado res = AlgoritmoEvolutivo.ejecutar(inst, cfg, P);
        System.out.printf("  %d generaciones, %d evaluaciones, %.1f s%n",
                res.generaciones, res.evaluaciones, res.segundos);
        System.out.println("  mejor (revaluado con N=200): " + res.fitnessMejor);

        System.out.println();
        System.out.println("  convergencia (mejor / media de la generación):");
        List<Double> hm = res.historialMejor;
        List<Double> hp = res.historialMedia;
        int paso = Math.max(1, hm.size() / 12);
        for (int g = 0; g < hm.size(); g += paso) {
            System.out.printf("    gen %4d  mejor %8.2f   media %8.2f%n", g, hm.get(g), hp.get(g));
        }
        System.out.printf("    gen %4d  mejor %8.2f   media %8.2f%n",
                hm.size() - 1, hm.get(hm.size() - 1), hp.get(hp.size() - 1));

        // ---------------- búsqueda aleatoria, mismo presupuesto ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("2. Búsqueda aleatoria con el mismo presupuesto de evaluaciones");
        System.out.println(linea);
        // Mismo presupuesto, misma selección final, mismos escenarios de búsqueda.
        AlgoritmoEvolutivo.Resultado azar = AlgoritmoEvolutivo.busquedaAleatoria(inst, cfg, P);
        Evaluador.Fitness fAzar = azar.fitnessMejor;
        System.out.printf("  %d evaluaciones, %.1f s%n", azar.evaluaciones, azar.segundos);
        System.out.println("  mejor (revaluado con N=200): " + fAzar);

        // ---------------- comparación ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("3. Comparación (todos revaluados con N=200, escenarios nuevos)");
        System.out.println(linea);
        System.out.printf("%-26s%12s%12s%14s%n", "método", "f(x)", "U media", "mejora vs SQ");
        fila("statu quo", fsq, fsq);
        fila("búsqueda aleatoria", fAzar, fsq);
        fila("algoritmo evolutivo", res.fitnessMejor, fsq);

        System.out.println();
        System.out.println(linea);
        System.out.println("4. Plan encontrado por el AE (primeras filas)");
        System.out.println(linea);
        System.out.printf("  operaciones pedidas: %d de %d genes%n%n",
                res.mejor.operacionesPedidas(inst), res.mejor.n);
        System.out.print(res.mejor.describir(inst, 14));
    }

    private static void fila(String nombre, Evaluador.Fitness f, Evaluador.Fitness base) {
        System.out.printf("%-26s%12.2f%12.2f%13.1f%%%n",
                nombre, f.valor, f.utilidadMedia,
                100.0 * (f.valor - base.valor) / Math.abs(base.valor));
    }
}
