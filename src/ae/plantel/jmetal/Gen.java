package ae.plantel.jmetal;

/**
 * Un gen del cromosoma: la decisión sobre UN jugador, como una variable de
 * jMetal.
 *
 * [Informe §III-A representación: tupla (a_i, w_i, d_i, s_i, c_i, mu_i)]
 *
 * En jMetal cada solución es una lista de variables de un mismo tipo. Acá el
 * tipo es esta tupla completa, con codificación mixta (enteros para acción,
 * ventana, duración y prioridad; reales para salario y cláusula). Que la
 * variable sea el gen entero, y no cada campo por separado, es lo que permite
 * que el cruzamiento herede a cada jugador completo de un padre (Tema 7:
 * preservar bloques constructivos).
 *
 * El valor de {@code accion} es un índice dentro del dominio válido del
 * jugador (0..4 propios, 0..2 mercado), igual que en {@link ae.plantel.Cromosoma}.
 * Es inmutable: los operadores crean genes nuevos.
 */
public record Gen(int accion, int ventana, int duracion, double salario, double clausula,
                  int prioridad) {

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "(%d,V%d,%d,%.2f,%.2f,%d)",
                accion, ventana, duracion, salario, clausula, prioridad);
    }
}
