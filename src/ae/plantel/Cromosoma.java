package ae.plantel;

import ae.plantel.Modelo.Accion;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.JugadorBase;
import ae.plantel.Modelo.Pertenencia;
import ae.plantel.Modelo.PrioridadMinutos;

/**
 * Cromosoma: largo fijo, un gen por jugador considerado.
 *
 * [Informe §III-A representación y Cuadro I]
 *
 * Cada gen es la tupla (a_i, w_i, d_i, s_i, c_i, mu_i) de la Sección III-A del
 * informe, con codificación mixta: entera para acción, ventana, duración y
 * prioridad de minutos; real para salario y cláusula.
 *
 * Se guarda como arrays paralelos y no como lista de objetos: los operadores
 * evolutivos trabajan campo por campo (uniforme sobre acciones, BLX-alpha sobre
 * los reales) y el acceso contiguo importa cuando se evalúan decenas de miles
 * de individuos.
 *
 * El índice i corresponde a instancia.jugadores.get(i), y el valor de accion[i]
 * es un índice DENTRO del dominio válido de ese jugador (0..4 para los propios,
 * 0..2 para los del mercado). Por eso es imposible por construcción representar
 * una operación sin sentido, como vender a alguien que no pertenece al club.
 */
public final class Cromosoma {

    public static final int VENTANAS = 6;
    public static final int DURACION_MIN = 1;
    public static final int DURACION_MAX = 5;
    public static final double SALARIO_MIN = 0.7;
    public static final double SALARIO_MAX = 1.6;
    public static final double CLAUSULA_MIN = 1.0;
    public static final double CLAUSULA_MAX = 10.0;

    public final int n;
    public final int[] accion;      // índice dentro del dominio válido del jugador
    public final int[] ventana;     // 1..6
    public final int[] duracion;    // 1..5 temporadas
    public final double[] salario;  // múltiplo del salario de referencia
    public final double[] clausula; // múltiplo del valor de mercado
    public final int[] prioridad;   // 0..2 (DESARROLLO, ROTACION, TITULAR_PREFERENTE)

    public Cromosoma(int n) {
        this.n = n;
        this.accion = new int[n];
        this.ventana = new int[n];
        this.duracion = new int[n];
        this.salario = new double[n];
        this.clausula = new double[n];
        this.prioridad = new int[n];
    }

    /** Cantidad de acciones válidas para el jugador i de la instancia. */
    public static int dominioAccion(Instancia inst, int i) {
        return inst.jugadores.get(i).accionesValidas().length;
    }

    /** Traduce el gen a la acción concreta del dominio del jugador. */
    public Accion accionDe(Instancia inst, int i) {
        Accion[] validas = inst.jugadores.get(i).accionesValidas();
        return validas[Math.floorMod(accion[i], validas.length)];
    }

    public PrioridadMinutos prioridadDe(int i) {
        return PrioridadMinutos.values()[Math.floorMod(prioridad[i], 3)];
    }

    /** Plan statu quo: MANTENER a todos los propios, IGNORAR todo el mercado. */
    public static Cromosoma statuQuo(Instancia inst) {
        Cromosoma c = new Cromosoma(inst.jugadores.size());
        for (int i = 0; i < c.n; i++) {
            c.accion[i] = 0;
            c.ventana[i] = 1;
            c.duracion[i] = 3;
            c.salario[i] = 1.0;
            c.clausula[i] = 3.0;
            c.prioridad[i] = PrioridadMinutos.ROTACION.ordinal();
        }
        return c;
    }

    /** Individuo aleatorio dentro de los rangos válidos. */
    public static Cromosoma aleatorio(Instancia inst, Aleatorio rng) {
        Cromosoma c = new Cromosoma(inst.jugadores.size());
        for (int i = 0; i < c.n; i++) {
            c.accion[i] = rng.entero(0, dominioAccion(inst, i));
            c.ventana[i] = rng.entero(1, VENTANAS + 1);
            c.duracion[i] = rng.entero(DURACION_MIN, DURACION_MAX + 1);
            c.salario[i] = rng.uniforme(SALARIO_MIN, SALARIO_MAX);
            c.clausula[i] = rng.uniforme(CLAUSULA_MIN, CLAUSULA_MAX);
            c.prioridad[i] = rng.entero(0, 3);
        }
        return c;
    }

    public Cromosoma copia() {
        Cromosoma c = new Cromosoma(n);
        System.arraycopy(accion, 0, c.accion, 0, n);
        System.arraycopy(ventana, 0, c.ventana, 0, n);
        System.arraycopy(duracion, 0, c.duracion, 0, n);
        System.arraycopy(salario, 0, c.salario, 0, n);
        System.arraycopy(clausula, 0, c.clausula, 0, n);
        System.arraycopy(prioridad, 0, c.prioridad, 0, n);
        return c;
    }

    /** true si los dos cromosomas codifican exactamente el mismo plan. */
    public boolean igualA(Cromosoma o) {
        return java.util.Arrays.equals(accion, o.accion)
                && java.util.Arrays.equals(ventana, o.ventana)
                && java.util.Arrays.equals(duracion, o.duracion)
                && java.util.Arrays.equals(prioridad, o.prioridad)
                && java.util.Arrays.equals(salario, o.salario)
                && java.util.Arrays.equals(clausula, o.clausula);
    }

    /** Cantidad de genes que piden una operación (no MANTENER / no IGNORAR). */
    public int operacionesPedidas(Instancia inst) {
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (accionDe(inst, i) != Accion.MANTENER && accionDe(inst, i) != Accion.IGNORAR) {
                k++;
            }
        }
        return k;
    }

    /** Vuelca el plan en texto, al estilo del Cuadro I del informe. */
    public String describir(Instancia inst, int maxFilas) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%8s %-9s %-20s %6s %6s %6s %7s%n",
                "Jug.", "Tipo", "Acción", "Vent.", "Años", "Sal.", "Cláus."));
        int filas = 0;
        for (int i = 0; i < n && filas < maxFilas; i++) {
            Accion a = accionDe(inst, i);
            if (a == Accion.MANTENER || a == Accion.IGNORAR) {
                continue;
            }
            JugadorBase j = inst.jugadores.get(i);
            boolean contrato = a == Accion.RENOVAR || a == Accion.COMPRAR
                    || a == Accion.INCORPORAR_PRESTAMO;
            sb.append(String.format("%8s %-9s %-20s %6s %6s %6s %7s%n",
                    j.nombre(),
                    j.pertenencia() == Pertenencia.PROPIO ? "propio" : "mercado",
                    a,
                    "V" + ventana[i],
                    contrato ? String.valueOf(duracion[i]) : "—",
                    contrato ? String.format("%.2f", salario[i]) : "—",
                    contrato ? String.format("%.1f", clausula[i]) : "—"));
            filas++;
        }
        return sb.toString();
    }
}
