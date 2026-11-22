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
        // Sin empates y con hasta 50 pares se usa la distribución EXACTA de W+
        // (lo mismo que hace scipy con method="auto"). Con n = 10, que es lo
        // que tiene la comparación de variantes, la aproximación normal daba
        // p = 0.005 donde el exacto es 0.002. Ver DECISIONES D-A5.
        if (m <= WILCOXON_EXACTO_MAX && sumaEmpates(abs) == 0 && m == n) {
            return new double[] { z, wilcoxonExacto(m, wMas) };
        }
        double p = 2.0 * (1.0 - phi(Math.abs(z)));
        return new double[] { z, Math.min(1.0, p) };
    }

    /** Hasta cuántos pares (sin empates ni ceros) se usa el p exacto de Wilcoxon. */
    public static final int WILCOXON_EXACTO_MAX = 50;

    /**
     * p bilateral exacto de Wilcoxon: cuenta, por programación dinámica, de
     * cuántas de las 2^m asignaciones de signo sale cada valor de W+.
     *
     * [Informe §IV-B análisis estadístico; StatTests lámina 7]
     */
    static double wilcoxonExacto(int m, double wMas) {
        int max = m * (m + 1) / 2;
        double[] cuenta = new double[max + 1];
        cuenta[0] = 1.0;
        for (int r = 1; r <= m; r++) {
            for (int s = max; s >= r; s--) {
                cuenta[s] += cuenta[s - r];
            }
        }
        double total = Math.pow(2.0, m);
        int w = (int) Math.round(wMas);
        double abajo = 0, arriba = 0;
        for (int s = 0; s <= max; s++) {
            if (s <= w) {
                abajo += cuenta[s];
            }
            if (s >= w) {
                arriba += cuenta[s];
            }
        }
        return Math.min(1.0, 2.0 * Math.min(abajo, arriba) / total);
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
    // Shapiro-Wilk (Royston, algoritmo AS R94)
    // ----------------------------------------------------------------

    /**
     * Test de normalidad de Shapiro-Wilk. Es el que promete el informe y el que
     * usan las diapositivas de StatTests; reemplaza a Lilliefors como test
     * principal (DECISIONES D-A5). Válido para 3 &lt;= n &lt;= 5000.
     *
     * Es una traducción del algoritmo AS R94 de Royston (1995), el mismo que
     * usan R (shapiro.test) y scipy (stats.shapiro).
     *
     * [Informe §IV-A y §IV-B: test de Shapiro-Wilk]
     *
     * @return {W, p-valor}
     */
    public static double[] shapiroWilk(double[] muestra) {
        int n = muestra.length;
        if (n < 3) {
            throw new IllegalArgumentException("Shapiro-Wilk necesita al menos 3 datos");
        }
        double[] x = muestra.clone();
        Arrays.sort(x);
        int nn2 = n / 2;
        double[] a = new double[nn2 + 1];   // índice 1..nn2, como en el original
        double an = n;

        if (n == 3) {
            a[1] = Math.sqrt(0.5);
        } else {
            double an25 = an + 0.25;
            double summ2 = 0.0;
            for (int i = 1; i <= nn2; i++) {
                a[i] = cuantilNormal((i - 0.375) / an25);
                summ2 += a[i] * a[i];
            }
            summ2 *= 2.0;
            double ssumm2 = Math.sqrt(summ2);
            double rsn = 1.0 / Math.sqrt(an);
            double a1 = poly(SW_C1, rsn) - a[1] / ssumm2;
            int i1;
            double fac;
            if (n > 5) {
                i1 = 3;
                double a2 = -a[2] / ssumm2 + poly(SW_C2, rsn);
                fac = Math.sqrt((summ2 - 2.0 * a[1] * a[1] - 2.0 * a[2] * a[2])
                        / (1.0 - 2.0 * a1 * a1 - 2.0 * a2 * a2));
                a[2] = a2;
            } else {
                i1 = 2;
                fac = Math.sqrt((summ2 - 2.0 * a[1] * a[1]) / (1.0 - 2.0 * a1 * a1));
            }
            a[1] = a1;
            for (int i = i1; i <= nn2; i++) {
                a[i] /= -fac;
            }
        }

        double rango = x[n - 1] - x[0];
        if (rango < 1e-19) {
            return new double[] { 1.0, 1.0 };   // muestra constante
        }
        double xx = x[0] / rango;
        double sx = xx;
        double sa = -a[1];
        for (int i = 1, j = n - 1; i < n; j--) {
            double xi = x[i] / rango;
            sx += xi;
            i++;
            if (i != j) {
                sa += Integer.signum(i - j) * a[Math.min(i, j)];
            }
        }
        sa /= n;
        sx /= n;
        double ssa = 0, ssx = 0, sax = 0;
        for (int i = 0, j = n - 1; i < n; i++, j--) {
            double asa = i != j ? Integer.signum(i - j) * a[1 + Math.min(i, j)] - sa : -sa;
            double xsx = x[i] / rango - sx;
            ssa += asa * asa;
            ssx += xsx * xsx;
            sax += asa * xsx;
        }
        double ssassx = Math.sqrt(ssa * ssx);
        double w1 = (ssassx - sax) * (ssassx + sax) / (ssa * ssx);
        double w = 1.0 - w1;

        if (n == 3) {
            double p = 6.0 / Math.PI * (Math.asin(Math.sqrt(w)) - Math.PI / 3.0);
            return new double[] { w, Math.max(0.0, p) };
        }
        double y = Math.log(w1);
        double lxx = Math.log(an);
        double m, s;
        if (n <= 11) {
            double gamma = poly(SW_G, an);
            if (y >= gamma) {
                return new double[] { w, 1e-99 };
            }
            y = -Math.log(gamma - y);
            m = poly(SW_C3, an);
            s = Math.exp(poly(SW_C4, an));
        } else {
            m = poly(SW_C5, lxx);
            s = Math.exp(poly(SW_C6, lxx));
        }
        return new double[] { w, 1.0 - phiPreciso((y - m) / s) };
    }

    /** true si Shapiro-Wilk NO rechaza la normalidad al 5%. */
    public static boolean esNormalShapiro(double[] muestra) {
        return shapiroWilk(muestra)[1] >= 0.05;
    }

    private static final double[] SW_G = { -2.273, 0.459 };
    private static final double[] SW_C1 = { 0.0, 0.221157, -0.147981, -2.07119, 4.434685, -2.706056 };
    private static final double[] SW_C2 = { 0.0, 0.042981, -0.293762, -1.752461, 5.682633, -3.582633 };
    private static final double[] SW_C3 = { 0.544, -0.39978, 0.025054, -6.714e-4 };
    private static final double[] SW_C4 = { 1.3822, -0.77857, 0.062767, -0.0020322 };
    private static final double[] SW_C5 = { -1.5861, -0.31082, -0.083751, 0.0038915 };
    private static final double[] SW_C6 = { -0.4803, -0.082676, 0.0030302 };

    /** Polinomio c[0] + c[1] x + ... (misma convención que el AS R94). */
    private static double poly(double[] c, double x) {
        double r = c[0];
        if (c.length > 1) {
            double p = x * c[c.length - 1];
            for (int j = c.length - 2; j > 0; j--) {
                p = (p + c[j]) * x;
            }
            r += p;
        }
        return r;
    }

    // ----------------------------------------------------------------
    // Dos muestras independientes: t de Student (Welch) y Mann-Whitney
    // ----------------------------------------------------------------

    /**
     * Test t de Student para dos muestras independientes, en la versión de
     * Welch (no asume varianzas iguales). Para muestras normales (Tema 12:
     * "para dos conjuntos de muestras: test de Student").
     *
     * [Informe §IV-B: "test t de Student o de Mann-Whitney"]
     *
     * @return {t, grados de libertad, p-valor bilateral}
     */
    public static double[] tStudent(double[] a, double[] b) {
        double va = varianza(a) / a.length;
        double vb = varianza(b) / b.length;
        double t = (media(a) - media(b)) / Math.sqrt(va + vb);
        double gl = (va + vb) * (va + vb)
                / (va * va / (a.length - 1) + vb * vb / (b.length - 1));
        double p = betai(gl / 2.0, 0.5, gl / (gl + t * t));
        return new double[] { t, gl, Math.min(1.0, p) };
    }

    /**
     * Test U de Mann-Whitney para dos muestras independientes (Tema 12: "para
     * dos conjuntos de muestras [no normales]: test de Mann-Whitney").
     * Aproximación normal con corrección por empates y por continuidad, igual
     * que scipy.stats.mannwhitneyu(method="asymptotic").
     *
     * [Informe §IV-B: "test t de Student o de Mann-Whitney"]
     *
     * @return {U de la primera muestra, z, p-valor bilateral}
     */
    public static double[] mannWhitney(double[] a, double[] b) {
        int n1 = a.length;
        int n2 = b.length;
        int n = n1 + n2;
        double[] todos = new double[n];
        System.arraycopy(a, 0, todos, 0, n1);
        System.arraycopy(b, 0, todos, n1, n2);
        double[] r = rangos(todos);
        double r1 = 0;
        for (int i = 0; i < n1; i++) {
            r1 += r[i];
        }
        double u1 = r1 - n1 * (n1 + 1.0) / 2.0;
        double u2 = (double) n1 * n2 - u1;
        double u = Math.max(u1, u2);
        double mu = n1 * n2 / 2.0;
        double s = Math.sqrt(n1 * (double) n2 / 12.0
                * ((n + 1.0) - sumaEmpates(todos) / ((double) n * (n - 1.0))));
        if (s == 0) {
            return new double[] { u1, 0.0, 1.0 };
        }
        double z = (u - mu - 0.5) / s;
        return new double[] { u1, z, Math.min(1.0, 2.0 * (1.0 - phiPreciso(z))) };
    }

    /** Varianza muestral (n - 1). */
    public static double varianza(double[] v) {
        double d = desvio(v);
        return d * d;
    }

    // ----------------------------------------------------------------
    // Comparaciones múltiples: Holm
    // ----------------------------------------------------------------

    /**
     * Corrección de Holm (step-down) para k comparaciones: ordena los p de menor
     * a mayor, multiplica el i-ésimo por (k - i) y fuerza la monotonía. Controla
     * el mismo error de familia que Bonferroni pero rechaza más. Es el post-hoc
     * de Friedman que usan las diapositivas de StatTests (lámina 22).
     *
     * [Informe §IV-B: "test de Friedman con post-hoc de Holm"]
     *
     * @return p ajustados, en el mismo orden que la entrada
     */
    public static double[] holm(double[] p) {
        int k = p.length;
        Integer[] orden = new Integer[k];
        for (int i = 0; i < k; i++) {
            orden[i] = i;
        }
        Arrays.sort(orden, (x, y) -> Double.compare(p[x], p[y]));
        double[] ajustado = new double[k];
        double acumulado = 0;
        for (int r = 0; r < k; r++) {
            int i = orden[r];
            acumulado = Math.max(acumulado, (k - r) * p[i]);
            ajustado[i] = Math.min(1.0, acumulado);
        }
        return ajustado;
    }

    /**
     * Criterio de significancia del Tema 12 cuando no se puede hacer un test
     * (por ejemplo, contra un método determinista): A difiere de B si
     * |media(A) - media(B)| &gt; máx(desvío(A), desvío(B)).
     *
     * [Informe §IV-B; Tema 12, "Análisis comparativo: tests estadísticos"]
     */
    public static boolean difiereCriterioTema12(double[] a, double[] b) {
        return Math.abs(media(a) - media(b)) > Math.max(desvio(a), desvio(b));
    }

    // ----------------------------------------------------------------
    // Normal: cuantil y distribución con precisión de doble
    // ----------------------------------------------------------------

    /**
     * Cuantil de la normal estándar (Wichura, algoritmo AS 241, PPND16),
     * con error relativo del orden de 1e-16.
     */
    public static double cuantilNormal(double p) {
        double q = p - 0.5;
        double r;
        if (Math.abs(q) <= 0.425) {
            r = 0.180625 - q * q;
            return q * (((((((r * 2509.0809287301226727 + 33430.575583588128105) * r
                    + 67265.770927008700853) * r + 45921.953931549871457) * r
                    + 13731.693765509461125) * r + 1971.5909503065514427) * r
                    + 133.14166789178437745) * r + 3.387132872796366608)
                    / (((((((r * 5226.495278852545925 + 28729.085735721942674) * r
                    + 39307.89580009271061) * r + 21213.794301586595867) * r
                    + 5394.1960214247511077) * r + 687.1870074920579083) * r
                    + 42.313330701600911252) * r + 1.0);
        }
        r = q < 0 ? p : 1.0 - p;
        r = Math.sqrt(-Math.log(r));
        double val;
        if (r <= 5.0) {
            r -= 1.6;
            val = (((((((r * 7.7454501427834140764e-4 + 0.0227238449892691845833) * r
                    + 0.24178072517745061177) * r + 1.27045825245236838258) * r
                    + 3.64784832476320460504) * r + 5.7694972214606914055) * r
                    + 4.6303378461565452959) * r + 1.42343711074968357734)
                    / (((((((r * 1.05075007164441684324e-9 + 5.475938084995344946e-4) * r
                    + 0.0151986665636164571966) * r + 0.14810397642748007459) * r
                    + 0.68976733498510000455) * r + 1.6763848301838038494) * r
                    + 2.05319162663775882187) * r + 1.0);
        } else {
            r -= 5.0;
            val = (((((((r * 2.01033439929228813265e-7 + 2.71155556874348757815e-5) * r
                    + 0.0012426609473880784386) * r + 0.026532189526576123093) * r
                    + 0.29656057182850489123) * r + 1.7848265399172913358) * r
                    + 5.4637849111641143699) * r + 6.6579046435011037772)
                    / (((((((r * 2.04426310338993978564e-15 + 1.4215117583164458887e-7) * r
                    + 1.8463183175100546818e-5) * r + 7.868691311456132591e-4) * r
                    + 0.0148753612908506148525) * r + 0.13692988092273580531) * r
                    + 0.59983220655588793769) * r + 1.0);
        }
        return q < 0 ? -val : val;
    }

    /**
     * Distribución normal estándar con precisión de doble, vía la función
     * gamma incompleta: Φ(z) = 1 - Q(1/2, z²/2)/2 para z &gt;= 0. La función
     * {@link #phi} de arriba (Abramowitz-Stegun) tiene error de 1e-7, que no
     * alcanza para p-valores chicos de Shapiro-Wilk.
     */
    public static double phiPreciso(double z) {
        if (z == 0) {
            return 0.5;
        }
        double cola = 0.5 * gammq(0.5, z * z / 2.0);
        return z > 0 ? 1.0 - cola : cola;
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
