package ae.plantel;

import java.util.Arrays;

/**
 * Tests estadísticos necesarios para la evaluación experimental, implementados
 *
 * [Informe §IV-A y §IV-B análisis estadístico]
 * sin dependencias externas.
 *
 * El guion es el del Tema 12: primero test de normalidad; si las muestras son
 * normales se compara con ANOVA, si no con Kruskal-Wallis; y para ordenar
 * varias configuraciones sobre varios bloques, ranking de Friedman.
 *
 * Nota sobre el test de normalidad: se usa Kolmogorov-Smirnov con los
 * parámetros ESTIMADOS de la muestra, que es la variante de Lilliefors. Usar
 * los valores críticos del KS clásico con parámetros estimados es un error
 * frecuente: el test queda demasiado permisivo y casi nunca rechaza.
 */
public final class Estadistica {

    private Estadistica() {
    }

    // ----------------------------------------------------------------
    // Descriptivos
    // ----------------------------------------------------------------

    public static double media(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s / v.length;
    }

    public static double desvio(double[] v) {
        if (v.length < 2) {
            return 0;
        }
        double m = media(v);
        double s = 0;
        for (double x : v) {
            s += (x - m) * (x - m);
        }
        return Math.sqrt(s / (v.length - 1));
    }

    public static double mediana(double[] v) {
        double[] c = v.clone();
        Arrays.sort(c);
        int n = c.length;
        return n % 2 == 1 ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2.0;
    }

    public static double maximo(double[] v) {
        double m = Double.NEGATIVE_INFINITY;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    // ----------------------------------------------------------------
    // Normalidad (Kolmogorov-Smirnov / Lilliefors)
    // ----------------------------------------------------------------

    /** Función de distribución normal estándar. */
    public static double phi(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        // Abramowitz-Stegun 7.1.26
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }

    /** Estadístico D de Kolmogorov-Smirnov contra la normal ajustada. */
    public static double estadisticoD(double[] muestra) {
        double[] v = muestra.clone();
        Arrays.sort(v);
        double m = media(v);
        double s = desvio(v);
        if (s <= 0) {
            return 1.0;
        }
        int n = v.length;
        double d = 0;
        for (int i = 0; i < n; i++) {
            double fTeorica = phi((v[i] - m) / s);
            d = Math.max(d, Math.max((i + 1.0) / n - fTeorica, fTeorica - (double) i / n));
        }
        return d;
    }

    /** Valor crítico de Lilliefors al 5% (aproximación de Stephens). */
    public static double criticoLilliefors(int n) {
        double raiz = Math.sqrt(n);
        return 0.895 / (raiz - 0.01 + 0.85 / raiz);
    }

    /** true si NO se rechaza la normalidad al 5%. */
    public static boolean esNormal(double[] muestra) {
        return estadisticoD(muestra) < criticoLilliefors(muestra.length);
    }

    // ----------------------------------------------------------------
    // ANOVA de una vía
    // ----------------------------------------------------------------

    /** Devuelve {F, p-valor} para k grupos. */
    public static double[] anova(double[][] grupos) {
        int k = grupos.length;
        int n = 0;
        double granSuma = 0;
        for (double[] g : grupos) {
            n += g.length;
            for (double x : g) {
                granSuma += x;
            }
        }
        double granMedia = granSuma / n;

        double scEntre = 0;
        double scDentro = 0;
        for (double[] g : grupos) {
            double mg = media(g);
            scEntre += g.length * (mg - granMedia) * (mg - granMedia);
            for (double x : g) {
                scDentro += (x - mg) * (x - mg);
            }
        }
        int gl1 = k - 1;
        int gl2 = n - k;
        double f = (scEntre / gl1) / (scDentro / gl2);
        return new double[] { f, pValorF(f, gl1, gl2) };
    }

    /** P(F > f) con gl1 y gl2 grados de libertad. */
    public static double pValorF(double f, int gl1, int gl2) {
        if (f <= 0) {
            return 1.0;
        }
        return betai(gl2 / 2.0, gl1 / 2.0, gl2 / (gl2 + gl1 * f));
    }

    // ----------------------------------------------------------------
    // Kruskal-Wallis
    // ----------------------------------------------------------------

    /** Devuelve {H, p-valor} para k grupos. */
    public static double[] kruskalWallis(double[][] grupos) {
        int n = 0;
        for (double[] g : grupos) {
            n += g.length;
        }
        double[] todos = new double[n];
        int idx = 0;
        for (double[] g : grupos) {
            for (double x : g) {
                todos[idx++] = x;
            }
        }
        double[] rangos = rangos(todos);

        double h = 0;
        int desde = 0;
        for (double[] g : grupos) {
            double suma = 0;
            for (int i = 0; i < g.length; i++) {
                suma += rangos[desde + i];
            }
            h += suma * suma / g.length;
            desde += g.length;
        }
        h = 12.0 / (n * (n + 1.0)) * h - 3.0 * (n + 1);
        // Corrección por empates: sin ella, con muchos valores repetidos el
        // estadístico queda subestimado y el test pierde potencia.
        double c = 1.0 - sumaEmpates(todos) / ((double) n * n * n - n);
        if (c > 0) {
            h /= c;
        }
        return new double[] { h, pValorChi2(h, grupos.length - 1) };
    }

    /** Suma de (t^3 - t) sobre los grupos de valores empatados. */
    private static double sumaEmpates(double[] v) {
        double[] c = v.clone();
        Arrays.sort(c);
        double suma = 0;
        int i = 0;
        while (i < c.length) {
            int j = i;
            while (j + 1 < c.length && c[j + 1] == c[i]) {
                j++;
            }
            double t = j - i + 1;
            suma += t * t * t - t;
            i = j + 1;
        }
        return suma;
    }

    /** Rangos con promedio en los empates. */
    public static double[] rangos(double[] v) {
        int n = v.length;
        Integer[] orden = new Integer[n];
        for (int i = 0; i < n; i++) {
            orden[i] = i;
        }
        Arrays.sort(orden, (a, b) -> Double.compare(v[a], v[b]));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && v[orden[j + 1]] == v[orden[i]]) {
                j++;
            }
            double promedio = (i + j + 2) / 2.0;   // rangos 1-based
            for (int k = i; k <= j; k++) {
                r[orden[k]] = promedio;
            }
            i = j + 1;
        }
        return r;
    }

    // ----------------------------------------------------------------
    // Friedman
    // ----------------------------------------------------------------

    /**
     * Ranking de Friedman.
     *
     * @param datos [bloque][tratamiento], mayor es mejor
     * @return rango promedio de cada tratamiento (1 = el mejor)
     */
    public static double[] rangosFriedman(double[][] datos) {
        int bloques = datos.length;
        int tratamientos = datos[0].length;
        double[] acumulado = new double[tratamientos];
        for (double[] fila : datos) {
            double[] negada = new double[tratamientos];
            for (int j = 0; j < tratamientos; j++) {
                negada[j] = -fila[j];   // para que el rango 1 sea el mejor
            }
            double[] r = rangos(negada);
            for (int j = 0; j < tratamientos; j++) {
                acumulado[j] += r[j];
            }
        }
        for (int j = 0; j < tratamientos; j++) {
            acumulado[j] /= bloques;
        }
        return acumulado;
    }

    /** Estadístico de Friedman y su p-valor: {chi2, p}. */
    public static double[] friedman(double[][] datos) {
        int b = datos.length;
        int k = datos[0].length;
        double[] rango = rangosFriedman(datos);
        double suma = 0;
        for (double r : rango) {
            suma += r * r;
        }
        double chi2 = 12.0 * b / (k * (k + 1.0)) * (suma - k * (k + 1.0) * (k + 1.0) / 4.0);
        // Corrección por empates dentro de cada bloque.
        double empates = 0;
        for (double[] fila : datos) {
            empates += sumaEmpates(fila);
        }
        double c = 1.0 - empates / (b * ((double) k * k * k - k));
        if (c > 0) {
            chi2 /= c;
        }
        return new double[] { chi2, pValorChi2(chi2, k - 1) };
    }

    // ----------------------------------------------------------------
    // Comparación por pares: Wilcoxon de rangos con signo
    // ----------------------------------------------------------------

    /**
     * Test de Wilcoxon de rangos con signo para dos muestras PAREADAS (el
     * diagrama de decisión del Tema 12 lo indica para dos conjuntos
     * dependientes). Las muestras de dos configuraciones con la misma semilla
     * son pareadas: comparten población inicial y escenarios.
     *
     * Usa la aproximación normal con corrección por empates, adecuada desde
     * unas 20 parejas. Los pares con diferencia nula se descartan.
     *
     * @return {estadístico z, p-valor bilateral}
     */
    public static double[] wilcoxon(double[] a, double[] b) {
        int n = a.length;
        double[] dif = new double[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            double d = a[i] - b[i];
            if (d != 0) {
                dif[m++] = d;
            }
        }
        if (m == 0) {
            return new double[] { 0.0, 1.0 };
        }
        double[] abs = new double[m];
        for (int i = 0; i < m; i++) {
            abs[i] = Math.abs(dif[i]);
        }
        double[] r = rangos(abs);
        double wMas = 0;
        for (int i = 0; i < m; i++) {
            if (dif[i] > 0) {
                wMas += r[i];
            }
        }
        double media = m * (m + 1.0) / 4.0;
        double var = m * (m + 1.0) * (2.0 * m + 1.0) / 24.0 - sumaEmpates(abs) / 48.0;
        if (var <= 0) {
            return new double[] { 0.0, 1.0 };
        }
        double z = (wMas - media) / Math.sqrt(var);
        double p = 2.0 * (1.0 - phi(Math.abs(z)));
        return new double[] { z, Math.min(1.0, p) };
    }

    /**
     * Corrección de Bonferroni: con k comparaciones, cada una se juzga al nivel
     * alfa/k. Sin corregir, comparar la mejor configuración contra 26 restantes
     * al 5% casi garantiza "diferencias significativas" por puro azar.
     */
    public static double bonferroni(double p, int comparaciones) {
        return Math.min(1.0, p * comparaciones);
    }

    // ----------------------------------------------------------------
    // Funciones especiales
    // ----------------------------------------------------------------

    /** P(Chi2 > x) con gl grados de libertad. */
    public static double pValorChi2(double x, int gl) {
        if (x <= 0) {
            return 1.0;
        }
        return gammq(gl / 2.0, x / 2.0);
    }

    private static double gammln(double x) {
        double[] cof = { 76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5 };
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (int j = 0; j < 6; j++) {
            ser += cof[j] / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    /** Función gamma incompleta complementaria Q(a,x). */
    private static double gammq(double a, double x) {
        if (x < a + 1.0) {
            return 1.0 - gser(a, x);
        }
        return gcf(a, x);
    }

    private static double gser(double a, double x) {
        double ap = a;
        double sum = 1.0 / a;
        double del = sum;
        for (int n = 0; n < 500; n++) {
            ap++;
            del *= x / ap;
            sum += del;
            if (Math.abs(del) < Math.abs(sum) * 1e-12) {
                break;
            }
        }
        return sum * Math.exp(-x + a * Math.log(x) - gammln(a));
    }

    private static double gcf(double a, double x) {
        double b = x + 1.0 - a;
        double c = 1e30;
        double d = 1.0 / b;
        double h = d;
        for (int i = 1; i <= 500; i++) {
            double an = -i * (i - a);
            b += 2.0;
            d = an * d + b;
            if (Math.abs(d) < 1e-30) {
                d = 1e-30;
            }
            c = b + an / c;
            if (Math.abs(c) < 1e-30) {
                c = 1e-30;
            }
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < 1e-12) {
                break;
            }
        }
        return Math.exp(-x + a * Math.log(x) - gammln(a)) * h;
    }

    /** Función beta incompleta regularizada I_x(a,b). */
    private static double betai(double a, double b, double x) {
        if (x <= 0) {
            return 0.0;
        }
        if (x >= 1) {
            return 1.0;
        }
        double bt = Math.exp(gammln(a + b) - gammln(a) - gammln(b)
                + a * Math.log(x) + b * Math.log(1.0 - x));
        if (x < (a + 1.0) / (a + b + 2.0)) {
            return bt * betacf(a, b, x) / a;
        }
        return 1.0 - bt * betacf(b, a, 1.0 - x) / b;
    }

    private static double betacf(double a, double b, double x) {
        double qab = a + b;
        double qap = a + 1.0;
        double qam = a - 1.0;
        double c = 1.0;
        double d = 1.0 - qab * x / qap;
        if (Math.abs(d) < 1e-30) {
            d = 1e-30;
        }
        d = 1.0 / d;
        double h = d;
        for (int m = 1; m <= 300; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < 1e-30) {
                d = 1e-30;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < 1e-30) {
                c = 1e-30;
            }
            d = 1.0 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < 1e-30) {
                d = 1e-30;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < 1e-30) {
                c = 1e-30;
            }
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < 1e-12) {
                break;
            }
        }
        return h;
    }
}
