package ae.plantel;

import ae.plantel.Modelo.Accion;
import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * P31: ¿de dónde sale la dispersión del patrimonio en las instancias reales?
 *
 * En f1 (puntos) el AE es muy estable entre semillas, pero el costo neto
 * (f2) varía cientos de millones. Hay dos sospechosos, y cada variante apaga
 * uno:
 *
 *  - LA SENSIBILIDAD A LOS MINUTOS (D-J3, no identificable con los datos).
 *    Con el supuesto actual (M = 1200), darle todos los minutos a un juvenil
 *    de 19 lo hace crecer 3,5 puntos por temporada más que no dárselos; con
 *    M = 300, 1,9; con M = 50, 0,5. Las tres versiones están recalibradas
 *    (crecimientoMax, sigma) para ajustar igual de bien el crecimiento medio
 *    por edad (herramientas/calibrar.py &lt;carpeta&gt; 300,50).
 *  - LA GANANCIA PATRIMONIAL DE RENOVAR (D4/D5). En la variante sin ella, el
 *    jugador se queda con todo el valor que crea alargar su contrato.
 *
 * Resultado (D-J7): los puntos son robustos a las dos cosas; la renovación no
 * es la causa de la dispersión; el NIVEL del patrimonio depende del supuesto
 * de los minutos, y la DISPERSIÓN es estructural (con beta = 0,01 hay una
 * franja de planes casi equivalentes en fitness con patrimonios distintos).
 *
 * Uso:  java -cp out ae.plantel.SensibilidadPatrimonio &lt;variante&gt; [evaluaciones] [semillas]
 *       variantes: base, minutos-medio, minutos-debil, sin-ganancia-renovacion
 *
 * [Informe §I-C dinámica de los atributos; §IV-B comparación con el plan real]
 */
public final class SensibilidadPatrimonio {

    private static final int N_VAL = AlgoritmoEvolutivo.N_VALIDACION;
    private static final long SEMILLA_VAL = AlgoritmoEvolutivo.SEMILLA_VALIDACION;

    private SensibilidadPatrimonio() {
    }

    static Parametros variante(String nombre) {
        Parametros p = Parametros.calibrados();
        switch (nombre) {
            case "base" -> {
            }
            case "minutos-medio" -> {
                p.evolucion.minutosMediaSaturacion = 300;
                p.evolucion.crecimientoMax = 5.983;
                p.evolucion.sigmaTheta = 2.267;
            }
            case "minutos-debil" -> {
                p.evolucion.minutosMediaSaturacion = 50;
                p.evolucion.crecimientoMax = 4.607;
                p.evolucion.sigmaTheta = 2.292;
            }
            case "sin-ganancia-renovacion" -> p.mercado.repartoExtension = 1.0;
            default -> throw new IllegalArgumentException("variante desconocida: " + nombre);
        }
        return p;
    }

    /** {puntos de las 3 temporadas, costo neto (f2), patrimonio final}. */
    static double[] medir(Instancia inst, Cromosoma x, Parametros p) {
        double[] d = new double[3];
        for (int k = 0; k < N_VAL; k++) {
            Evaluador.Resultado r = Evaluador.simular(inst, x, new Escenario(SEMILLA_VAL + k), p);
            d[0] += (r.puntos[0] + r.puntos[1] + r.puntos[2]) / N_VAL;
            d[1] += (r.gastoPases + r.salariosPagados - r.valorPlantel) / N_VAL;
            d[2] += r.valorFinal / N_VAL;
        }
        return d;
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        String nombre = args.length > 0 ? args[0] : "base";
        int evaluaciones = args.length > 1 ? Integer.parseInt(args[1]) : 12_000;
        int semillas = args.length > 2 ? Integer.parseInt(args[2]) : 6;

        Parametros p = variante(nombre);
        Instancia inst = InstanciasReales.cargar("barcelona-2020-chica");
        double[] real = medir(inst, InstanciasReales.planReal(inst), p);
        System.out.printf("%-26s %-9s %8s %10s %11s %6s %8s%n",
                "variante", "plan", "puntos", "costo neto", "patrimonio", "renov.", "jóvenes");
        System.out.printf("%-26s %-9s %8.2f %10.1f %11.1f%n", nombre, "real", real[0], real[1], real[2]);
        for (int s = 1; s <= semillas; s++) {
            AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
            cfg.replicas = AlgoritmoEvolutivo.N_FINAL;
            cfg.maxEvaluaciones = evaluaciones;
            cfg.semilla = s;
            AlgoritmoEvolutivo.Resultado r = AlgoritmoEvolutivo.ejecutar(inst, cfg, p);
            double[] x = medir(inst, r.mejor, p);
            int renovaciones = 0;
            int jovenes = 0;
            for (int i = 0; i < r.mejor.n; i++) {
                Accion a = r.mejor.accionDe(inst, i);
                if (a == Accion.RENOVAR) {
                    renovaciones++;
                }
                if (a == Accion.COMPRAR && inst.jugadores.get(i).edad() <= 23) {
                    jovenes++;
                }
            }
            System.out.printf("%-26s %-9s %8.2f %10.1f %11.1f %6d %8d%n", nombre, "AE " + s,
                    x[0], x[1], x[2], renovaciones, jovenes);
        }
    }
}
