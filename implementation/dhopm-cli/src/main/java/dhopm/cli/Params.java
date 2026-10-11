package dhopm.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed, defensive view over a request map (values may be String from CLI or Long/Double from JSON). */
public final class Params {

    private final Map<String, Object> raw;

    public Params(Map<String, Object> raw) {
        this.raw = raw;
    }

    public Map<String, Object> raw() {
        return raw;
    }

    public boolean has(String key) {
        return raw.get(key) != null;
    }

    public String str(String key) {
        Object v = raw.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public String requiredStr(String key) {
        String v = str(key);
        if (v == null || v.isBlank()) {
            throw ManagerException.missing(key);
        }
        return v;
    }

    public Double num(String key) {
        Object v = raw.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw ManagerException.invalid(key + " must be a real number, got: " + v);
        }
    }

    public double requiredNum(String key) {
        Double d = num(key);
        if (d == null) {
            throw ManagerException.missing(key);
        }
        return d;
    }

    public long longVal(String key, long fallback) {
        Object v = raw.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw ManagerException.invalid(key + " must be an integer, got: " + v);
        }
    }

    public long requiredLong(String key) {
        if (raw.get(key) == null) {
            throw ManagerException.missing(key);
        }
        return longVal(key, 0);
    }

    public int intVal(String key, int fallback) {
        Object v = raw.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw ManagerException.invalid(key + " must be an integer, got: " + v);
        }
    }

    public boolean boolVal(String key, boolean fallback) {
        Object v = raw.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    /** A list field: JSON array, or a CLI comma-separated string. */
    public List<String> strList(String key) {
        Object v = raw.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        List<String> out = new ArrayList<>();
        for (String part : String.valueOf(v).split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** Numeric list field: JSON array or CLI comma-separated string. */
    public List<Double> numList(String key) {
        List<String> raw = strList(key);
        if (raw == null) {
            return null;
        }
        List<Double> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                out.add(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                throw ManagerException.invalid(key + " must be a list of real numbers, got: " + s);
            }
        }
        return out;
    }
}
