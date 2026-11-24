package ae.plantel;

import ae.plantel.Modelo.Accion;
import ae.plantel.Modelo.Contrato;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.JugadorBase;
import ae.plantel.Modelo.Pertenencia;
import ae.plantel.Modelo.PrioridadMinutos;
import ae.plantel.Modelo.Puesto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Instancias armadas con datos reales y plan real del club.
 *
 * Los CSV los genera herramientas/preparar_instancias.py a partir de FIFA 21 y
 * Transfermarkt (DECISIONES D-J4). Este cargador no hace ninguna transformación
 * de los datos: todo lo que se decidió sobre ellos está en el script, para que
 * quede en un solo lugar.
 *
 * Formato de una instancia: líneas "# clave=valor" con los datos económicos y
 * después una tabla con encabezado (id, nombre, puesto, pertenencia, edad,
 * theta, potencial, anios_contrato, salario, clausula, historial_lesiones, ...).
 *
 * [Informe §IV-A generación de instancias: instancias con datos públicos]
 */
public final class InstanciasReales {

    /** Carpeta por defecto de las instancias reales, relativa a la raíz del repo. */
    public static final Path CARPETA = Path.of("instancias");

    private InstanciasReales() {
    }

    /** Carga una instancia real por nombre (por ejemplo "barcelona-2020-chica"). */
    public static Instancia cargar(String nombre) {
        return cargar(CARPETA.resolve(nombre + ".csv"));
    }

    public static Instancia cargar(Path ruta) {
        List<String> lineas = leer(ruta);
        Map<String, String> meta = new HashMap<>();
        int k = 0;
        while (k < lineas.size() && lineas.get(k).startsWith("#")) {
            String l = lineas.get(k).substring(1).trim();
            int igual = l.indexOf('=');
            if (igual > 0) {
                meta.put(l.substring(0, igual).trim(), l.substring(igual + 1).trim());
            }
            k++;
        }
        Map<String, Integer> col = columnas(lineas.get(k++));
        List<JugadorBase> jugadores = new ArrayList<>();
        for (; k < lineas.size(); k++) {
            if (lineas.get(k).isBlank()) {
                continue;
            }
            String[] c = partir(lineas.get(k));
            jugadores.add(new JugadorBase(
                    Integer.parseInt(c[col.get("id")]),
                    c[col.get("nombre")],
                    Puesto.valueOf(c[col.get("puesto")]),
                    Pertenencia.valueOf(c[col.get("pertenencia")]),
                    Integer.parseInt(c[col.get("edad")]),
                    Double.parseDouble(c[col.get("theta")]),
                    Double.parseDouble(c[col.get("potencial")]),
                    new Contrato(Integer.parseInt(c[col.get("anios_contrato")]),
                            Double.parseDouble(c[col.get("salario")]),
                            Double.parseDouble(c[col.get("clausula")])),
                    Double.parseDouble(c[col.get("historial_lesiones")])));
        }
        return new Instancia(
                meta.getOrDefault("nombre", ruta.getFileName().toString()),
                jugadores,
                Double.parseDouble(meta.get("presupuesto")),
                Double.parseDouble(meta.get("tope_masa_salarial")),
                Integer.parseInt(meta.getOrDefault("cupo_fichas", "25")),
                7,
                Modelo.MINIMOS_POR_PUESTO);
    }

    /**
     * Convierte el plan real del club en un cromosoma de la instancia.
     *
     * Los jugadores que el plan no menciona quedan en MANTENER (propios) o
     * IGNORAR (mercado), con rotación de minutos. Si el plan menciona un
     * jugador que no está en la instancia, es un error de armado y se avisa.
     *
     * [Informe §IV-B comparación con el plan real del club]
     */
    public static Cromosoma planReal(Instancia inst, Path ruta) {
        Map<Integer, Integer> indice = new HashMap<>();
        for (int i = 0; i < inst.jugadores.size(); i++) {
            indice.put(inst.jugadores.get(i).id(), i);
        }
        Cromosoma x = Cromosoma.statuQuo(inst);
        for (int i = 0; i < x.n; i++) {
            x.prioridad[i] = PrioridadMinutos.ROTACION.ordinal();
        }

        List<String> lineas = leer(ruta);
        Map<String, Integer> col = columnas(lineas.get(0));
        for (String l : lineas.subList(1, lineas.size())) {
            if (l.isBlank()) {
                continue;
            }
            String[] c = partir(l);
            int id = Integer.parseInt(c[col.get("id")]);
            Integer i = indice.get(id);
            if (i == null) {
                throw new IllegalArgumentException("el plan real menciona al jugador " + id
                        + " (" + c[col.get("nombre")] + "), que no está en " + inst.nombre);
            }
            Accion a = Accion.valueOf(c[col.get("accion")]);
            Accion[] dominio = inst.jugadores.get(i).accionesValidas();
            int pos = -1;
            for (int d = 0; d < dominio.length; d++) {
                if (dominio[d] == a) {
                    pos = d;
                }
            }
            if (pos < 0) {
                throw new IllegalArgumentException("acción " + a + " fuera del dominio de "
                        + c[col.get("nombre")]);
            }
            x.accion[i] = pos;
            x.ventana[i] = Integer.parseInt(c[col.get("ventana")]);
            x.duracion[i] = Integer.parseInt(c[col.get("duracion")]);
            x.salario[i] = Double.parseDouble(c[col.get("salario")]);
            x.clausula[i] = Double.parseDouble(c[col.get("clausula")]);
        }
        return x;
    }

    public static Cromosoma planReal(Instancia inst) {
        return planReal(inst, CARPETA.resolve("plan_real_barcelona.csv"));
    }

    // ----------------------------------------------------------------

    private static List<String> leer(Path ruta) {
        try {
            return Files.readAllLines(ruta, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("no puedo leer " + ruta.toAbsolutePath()
                    + " (¿se corrió herramientas/preparar_instancias.py?)", e);
        }
    }

    private static Map<String, Integer> columnas(String encabezado) {
        Map<String, Integer> col = new HashMap<>();
        String[] c = partir(encabezado);
        for (int i = 0; i < c.length; i++) {
            col.put(c[i].trim(), i);
        }
        return col;
    }

    /** CSV simple con campos opcionalmente entre comillas (los nombres pueden llevar comas). */
    private static String[] partir(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char ch = linea.charAt(i);
            if (ch == '"') {
                comillas = !comillas;
            } else if (ch == ',' && !comillas) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        campos.add(actual.toString());
        return campos.toArray(new String[0]);
    }
}
