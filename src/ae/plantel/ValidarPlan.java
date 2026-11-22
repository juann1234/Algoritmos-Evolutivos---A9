package ae.plantel;

import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Validación del decodificador y de la función de fitness.
 *
 * [Informe §III-A decodificador y §III-B fitness: validación]
 *
 * Lo que hay que ver acá, antes de escribir una línea del AE:
 *
 *   1. El statu quo es evaluable y da lo mismo que antes.
 *   2. Un plan aleatorio es, en promedio, PEOR que el statu quo. Si los planes
 *      al azar ya fueran mejores, el problema sería trivial.
 *   3. Pero el mejor de muchos planes aleatorios SÍ supera al statu quo: hay
 *      señal que explotar, que es la condición para que el AE tenga sentido.
 *   4. La reparación funciona: casi ningún escenario queda infactible.
 *   5. El costo por evaluación permite el presupuesto de 40.000 del informe.
 *
 * Uso:  java -cp out ae.plantel.ValidarPlan
 */
public final class ValidarPlan {

    private static final Parametros P = new Parametros();

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        final String linea = "=".repeat(78);
        final int N = 30;

        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        System.out.println(inst.resumen());

        System.out.println();
        System.out.println(linea);
        System.out.println("1. Plan statu quo");
        System.out.println(linea);
        Cromosoma sq = Cromosoma.statuQuo(inst);
        Evaluador.Fitness fsq = Evaluador.evaluar(inst, sq, 200, 1000, P);
        System.out.println("  " + fsq);

        System.out.println();
        System.out.println(linea);
        System.out.println("2-3. Planes aleatorios (200 individuos, N=" + N + ")");
        System.out.println(linea);
        Aleatorio rng = new Aleatorio(42);
        int muestras = 200;
        double[] valores = new double[muestras];
        double factibles = 0;
        double ops = 0;
        Cromosoma mejor = null;
        double mejorValor = Double.NEGATIVE_INFINITY;
        long t0 = System.nanoTime();
        for (int i = 0; i < muestras; i++) {
            Cromosoma c = Cromosoma.aleatorio(inst, rng);
            Evaluador.Fitness f = Evaluador.evaluar(inst, c, N, 7000, P);
            valores[i] = f.valor;
            factibles += f.factibles;
            ops += f.concretadasMedia;
            if (f.valor > mejorValor) {
                mejorValor = f.valor;
                mejor = c;
            }
        }
        double dt = (System.nanoTime() - t0) / 1e9;
        Arrays.sort(valores);
        double media = Arrays.stream(valores).average().orElse(0);

        System.out.printf("  fitness aleatorio: media %.2f | mediana %.2f | peor %.2f | mejor %.2f%n",
                media, valores[muestras / 2], valores[0], valores[muestras - 1]);
        System.out.printf("  statu quo:         %.2f%n", fsq.valor);
        System.out.printf("  aleatorios que superan al statu quo: %d de %d%n",
                (int) Arrays.stream(valores).filter(v -> v > fsq.valor).count(), muestras);
        System.out.printf("  escenarios factibles tras reparación: %.1f%%%n",
                100.0 * factibles / muestras);
        System.out.printf("  operaciones concretadas por plan: %.1f%n", ops / muestras);
        System.out.printf("  costo: %.3f s por evaluación (N=%d) -> %.1f min "
                        + "para 40.000 evaluaciones%n",
                dt / muestras, N, dt / muestras * 40000 / 60);

        System.out.println();
        System.out.println(linea);
        System.out.println("4. Mejor plan aleatorio encontrado (primeras filas)");
        System.out.println(linea);
        Evaluador.Fitness fm = Evaluador.evaluar(inst, mejor, 200, 1000, P);
        System.out.println("  " + fm);
        System.out.printf("  mejora sobre statu quo: %+.2f (%.1f%%)%n",
                fm.valor - fsq.valor, 100.0 * (fm.valor - fsq.valor) / Math.abs(fsq.valor));
        System.out.printf("  operaciones pedidas por el cromosoma: %d de %d genes%n%n",
                mejor.operacionesPedidas(inst), mejor.n);
        System.out.print(mejor.describir(inst, 12));

        System.out.println();
        System.out.println(linea);
        System.out.println("5. Sensibilidad al presupuesto (mismo plan, otra caja)");
        System.out.println(linea);
        System.out.printf("%12s%12s%12s%n", "presupuesto", "f(statu quo)", "f(aleatorio)");
        for (double pres : new double[] { 0, 60, 120, 300 }) {
            Instancia var = new Instancia(inst.nombre, inst.jugadores, pres,
                    inst.topeMasaSalarial, inst.cupoFichas, inst.maxCedidos);
            double a = Evaluador.evaluar(var, sq, 50, 1000, P).valor;
            double b = Evaluador.evaluar(var, mejor, 50, 1000, P).valor;
            System.out.printf("%12.0f%12.2f%12.2f%n", pres, a, b);
        }
    }
}
