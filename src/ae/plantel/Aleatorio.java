package ae.plantel;

import java.util.List;

/**
 * Generador pseudoaleatorio del proyecto (SplitMix64).
 *
 * Reemplaza a java.util.Random por un motivo concreto y medido: con semillas
 * consecutivas, java.util.Random devuelve un primer sorteo casi idéntico
 * (0.3745, 0.3741, 0.3740, ... para las semillas 1000003, 1000004, 1000005).
 * Como los escenarios de una evaluación usan semillas consecutivas, el primer
 * evento de cada escenario salía igual en todas las réplicas: los N escenarios
 * no eran independientes.
 *
 * SplitMix64 pasa la semilla por una función de mezcla antes de usarla, así
 * que semillas vecinas producen secuencias sin relación. Al ser código propio,
 * la reproducibilidad no depende de la versión de la JVM.
 */
public final class Aleatorio {

    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long estado;

    public Aleatorio(long semilla) {
        resembrar(semilla);
    }

    /** Reinicia la secuencia. No asigna memoria: se puede llamar millones de veces. */
    public void resembrar(long semilla) {
        this.estado = mezclar(semilla);
    }

    /** Función de mezcla de 64 bits (finalizador de SplitMix64). */
    public static long mezclar(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private long siguiente() {
        estado += GAMMA;
        return mezclar(estado);
    }

    public double uniforme() {
        return (siguiente() >>> 11) * 0x1.0p-53;
    }

    public double uniforme(double a, double b) {
        return a + (b - a) * uniforme();
    }

    public int entero(int desdeInclusive, int hastaExclusive) {
        int ancho = hastaExclusive - desdeInclusive;
        return desdeInclusive + (int) Math.min(ancho - 1L, (long) (uniforme() * ancho));
    }

    /** Normal por Box-Muller. Sin estado guardado, para que resembrar sea limpio. */
    public double normal(double media, double desvio) {
        double u1 = Math.max(uniforme(), 1e-300);
        double u2 = uniforme();
        return media + desvio * Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    /** Gamma(forma, escala) por Marsaglia-Tsang. */
    public double gamma(double forma, double escala) {
        if (forma < 1.0) {
            double u = Math.max(uniforme(), 1e-300);
            return gamma(forma + 1.0, escala) * Math.pow(u, 1.0 / forma);
        }
        double d = forma - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x = normal(0.0, 1.0);
            double v = 1.0 + c * x;
            if (v <= 0) {
                continue;
            }
            v = v * v * v;
            double u = Math.max(uniforme(), 1e-300);
            if (u < 1.0 - 0.0331 * x * x * x * x
                    || Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v * escala;
            }
        }
    }

    /** Elección con pesos; los pesos no necesitan sumar 1. */
    public int elegir(double[] pesos) {
        double total = 0;
        for (double p : pesos) {
            total += p;
        }
        double u = uniforme() * total;
        double acum = 0;
        for (int i = 0; i < pesos.length; i++) {
            acum += pesos[i];
            if (u <= acum) {
                return i;
            }
        }
        return pesos.length - 1;
    }

    /** Mezcla in-place (Fisher-Yates). */
    public <T> void mezclar(List<T> lista) {
        for (int i = lista.size() - 1; i > 0; i--) {
            int j = entero(0, i + 1);
            T tmp = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, tmp);
        }
    }
}
