package org.example.receipt;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of warranty fields from receipt text (PDF text or OCR output).
 * Pure and stateless; every field is {@code null} when it can't be found with reasonable confidence.
 */
public final class ReceiptParser {
    private static final int CI = Pattern.CASE_INSENSITIVE;

    // ---- brand / retailer -------------------------------------------------------------------------------------
    private record Brand(String name, Pattern pattern) {}

    /** Checked in this order. Names that are also everyday words only match in their usual or upper case. */
    private static final List<Brand> BRANDS = List.of(
            "Samsung", "Apple", "Dell", "HP", "Lenovo", "Sony", "LG", "Amazon", "Flipkart", "Best Buy", "Walmart",
            "Target", "OnePlus", "Xiaomi", "Oppo", "Vivo", "Realme", "Nokia", "Motorola"
    ).stream().map(ReceiptParser::toBrand).toList();

    // ---- dates ------------------------------------------------------------------------------------------------
    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;
    private static final int LABEL_WINDOW = 40;

    private static final String MONTH =
            "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?"
            + "|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\.?";
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    /** 2024-03-05, 2024/03/05 */
    private static final Pattern YEAR_FIRST = Pattern.compile("\\b(\\d{4})([-/.])(\\d{1,2})\\2(\\d{1,2})\\b");
    /** 05.03.2024, 05/03/2024, 05-03-2024 (day first) */
    private static final Pattern DAY_FIRST_NUMERIC = Pattern.compile("\\b(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{4})\\b");
    /** Mar 5 2024, Mar 5, 2024, March 5th, 2024 (an optional weekday before it is simply skipped) */
    private static final Pattern MONTH_NAME_FIRST =
            Pattern.compile("\\b" + MONTH + "[\\s-]+(\\d{1,2})(?:st|nd|rd|th)?,?[\\s-]+(\\d{4})\\b", CI);
    /** 5 March 2024, 05-Mar-2024 */
    private static final Pattern DAY_FIRST_NAME =
            Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s-]+" + MONTH + ",?[\\s-]+(\\d{4})\\b", CI);

    /** Labels whose date we prefer, strongest first. */
    private static final List<Pattern> DATE_LABELS = List.of(
            Pattern.compile("\\b(?:invoice|purchase|order|bill|billing|transaction|sale|receipt)\\s*(?:date|dt)\\b"
                    + "|\\bdate\\s+of\\s+(?:purchase|invoice|sale)\\b|\\b(?:purchased|ordered|bought)\\s+on\\b", CI),
            Pattern.compile("(?m)^\\s*date\\b", CI));

    // ---- warranty ---------------------------------------------------------------------------------------------
    private static final String NUMBER = "(\\d{1,3}|one|two|three|four|five)";
    private static final String UNIT = "(months?|mos?|years?|yrs?)";
    /** Warranty: 2 years / warranty period 18 months / warranty of 1 year */
    private static final Pattern WARRANTY_LABEL_FIRST = Pattern.compile(
            "\\b(?:warranty|guarantee)(?:\\s+period)?(?:\\s+of)?\\s*[:\\-\u2013]?\\s*" + NUMBER + "\\s*-?\\s*" + UNIT + "\\b", CI);
    /** 1 year warranty / 2-year limited warranty / 12 months manufacturer's warranty */
    private static final Pattern WARRANTY_NUMBER_FIRST = Pattern.compile(
            "\\b" + NUMBER + "\\s*-?\\s*" + UNIT + "\\b[\\s-]*"
            + "(?:(?:limited|manufacturer'?s?|brand|standard|extended|on-?site)\\s+)*(?:warranty|guarantee)\\b", CI);
    private static final Map<String, Integer> NUMBER_WORDS = Map.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5);

    // ---- product ----------------------------------------------------------------------------------------------
    private static final int MAX_PRODUCT_LENGTH = 60;
    private static final Pattern GALAXY = Pattern.compile(
            "\\bGalaxy\\s*((?:Z\\s*)?(?:Fold|Flip)\\s*\\d?|Note\\s*\\d{1,2}|Tab\\s+[AS]\\d{0,2}|[SAMF]\\s?\\d{1,2}(?!\\d))"
            + "(\\s*\\+|\\s+(?:Plus|Ultra|FE|Lite|Pro)\\b)?", CI);
    private static final Pattern GALAXY_MODEL = Pattern.compile("[SAMF]\\d{1,2}", CI);
    /** Case-sensitive on purpose: "surface" or "pixel" in fine print must not become a product. */
    private static final Pattern PRODUCT_LINE = Pattern.compile(
            "\\b(?:iPhone|iPad|MacBook|iMac|AirPods|Apple Watch|Pixel|Redmi|OnePlus|ThinkPad|IdeaPad|Inspiron|Latitude"
            + "|XPS|Pavilion|EliteBook|Surface|PlayStation|Xbox|Kindle|Bravia)\\b(?:[ \\t]+[A-Za-z0-9+\\-]+){0,4}");
    private static final Pattern PRODUCT_LABEL = Pattern.compile(
            "\\b(?:product(?:\\s+name)?|item(?:\\s+name)?|description)\\s*:\\s*([A-Za-z][^\\r\\n|\\t]{1,80})", CI);

    // ---- model ------------------------------------------------------------------------------------------------
    private static final int MAX_MODEL_LENGTH = 30;
    private static final Pattern SAMSUNG_MODEL =
            Pattern.compile("\\bSM-[A-Z]\\d{3,4}[A-Z0-9]{0,4}(?:/[A-Z0-9]{1,4})?\\b", CI);
    private static final Pattern MODEL_LABEL = Pattern.compile(
            "\\b(?:model(?:\\s*(?:no\\.?|number|#))?|sku|part\\s*(?:no\\.?|number|#)|mpn)\\s*[:#.\\-]?\\s*"
            + "([A-Z0-9][A-Z0-9\\-/]{2,24})", CI);
    /** Codes like ABC1234 or WH-1000XM5. */
    private static final Pattern GENERIC_MODEL = Pattern.compile("\\b([A-Z]{2,4}-?\\d{3,}[A-Z0-9\\-]*)\\b");
    /** Lines whose codes are invoice/order/tax/serial numbers rather than models. */
    private static final Pattern NON_MODEL_LINE = Pattern.compile(
            "\\b(?:invoice|order|gstin?|bill|txn|transaction|receipt|serial|imei|s/n|phone|tel|hsn|sac|awb|tracking"
            + "|pan|cin)\\b", CI);
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private ReceiptParser() {}

    public static ParsedReceipt parse(String text) {
        if (text == null || text.isBlank()) return new ParsedReceipt(null, null, null, null, null);

        String product = null;
        String galaxyModel = null;
        Matcher galaxy = GALAXY.matcher(text);
        if (galaxy.find()) {
            String line = collapse(galaxy.group(1));
            String suffix = galaxy.group(2) == null ? "" : collapse(galaxy.group(2));
            product = "Galaxy " + line + (suffix.isEmpty() || suffix.equals("+") ? suffix : " " + suffix);
            String compact = line.replace(" ", "");
            if (GALAXY_MODEL.matcher(compact).matches()) galaxyModel = compact.toUpperCase(Locale.ROOT);
        }
        if (product == null) product = productLine(text);
        if (product == null) product = labelledProduct(text);

        String model = firstNonNull(samsungModel(text), labelledModel(text), galaxyModel, genericModel(text));

        return new ParsedReceipt(cap(product, MAX_PRODUCT_LENGTH), cap(model, MAX_MODEL_LENGTH), brandIn(text),
                purchaseDate(text), warrantyMonths(text));
    }

    // ---- brand ------------------------------------------------------------------------------------------------

    private static Brand toBrand(String name) {
        boolean ambiguous = name.equals("Target") || name.equals("HP") || name.equals("LG");
        Pattern p = ambiguous
                ? Pattern.compile("\\b(?:" + Pattern.quote(name) + "|" + Pattern.quote(name.toUpperCase(Locale.ROOT)) + ")\\b")
                : Pattern.compile("\\b" + Pattern.quote(name) + "\\b", CI);
        return new Brand(name, p);
    }

    private static String brandIn(String text) {
        for (Brand b : BRANDS) {
            if (b.pattern().matcher(text).find()) return b.name();
        }
        return null;
    }

    // ---- date -------------------------------------------------------------------------------------------------

    private record DateHit(int start, int end, LocalDate date) {}

    private static LocalDate purchaseDate(String text) {
        List<DateHit> hits = dates(text);
        if (hits.isEmpty()) return null;
        for (Pattern label : DATE_LABELS) {
            Matcher m = label.matcher(text);
            while (m.find()) {
                for (DateHit hit : hits) {
                    int gap = hit.start() - m.end();
                    if (gap >= 0 && gap <= LABEL_WINDOW) return hit.date();
                }
            }
        }
        return hits.get(0).date();
    }

    /** All valid dates in text order; impossible dates (2024-13-45) are skipped. */
    private static List<DateHit> dates(String text) {
        List<DateHit> all = new ArrayList<>();
        collect(all, YEAR_FIRST.matcher(text), m -> date(m.group(1), m.group(3), m.group(4)));
        collect(all, DAY_FIRST_NUMERIC.matcher(text), m -> {
            LocalDate dayFirst = date(m.group(4), m.group(3), m.group(1));
            // US-style 03/25/2024 can't be day-first; accept it month-first rather than dropping it.
            return dayFirst != null || !m.group(2).equals("/") ? dayFirst : date(m.group(4), m.group(1), m.group(3));
        });
        collect(all, MONTH_NAME_FIRST.matcher(text), m -> date(m.group(3), monthNumber(m.group(1)), m.group(2)));
        collect(all, DAY_FIRST_NAME.matcher(text), m -> date(m.group(3), monthNumber(m.group(2)), m.group(1)));
        all.sort(Comparator.comparingInt(DateHit::start));

        List<DateHit> distinct = new ArrayList<>();
        int lastEnd = -1;
        for (DateHit hit : all) {
            if (hit.start() >= lastEnd) {
                distinct.add(hit);
                lastEnd = hit.end();
            }
        }
        return distinct;
    }

    private interface DateReader { LocalDate read(Matcher m); }

    private static void collect(List<DateHit> out, Matcher m, DateReader reader) {
        while (m.find()) {
            LocalDate d = reader.read(m);
            if (d != null) out.add(new DateHit(m.start(), m.end(), d));
        }
    }

    private static String monthNumber(String name) {
        return String.valueOf(MONTHS.get(name.substring(0, 3).toLowerCase(Locale.ROOT)));
    }

    private static LocalDate date(String year, String month, String day) {
        try {
            int y = Integer.parseInt(year);
            if (y < MIN_YEAR || y > MAX_YEAR) return null;
            return LocalDate.of(y, Integer.parseInt(month), Integer.parseInt(day));
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    // ---- warranty ---------------------------------------------------------------------------------------------

    private static Integer warrantyMonths(String text) {
        Matcher a = WARRANTY_LABEL_FIRST.matcher(text);
        Matcher b = WARRANTY_NUMBER_FIRST.matcher(text);
        boolean hasA = a.find();
        boolean hasB = b.find();
        if (!hasA && !hasB) return null;
        Matcher m = hasA && (!hasB || a.start() <= b.start()) ? a : b;

        String n = m.group(1).toLowerCase(Locale.ROOT);
        int amount = NUMBER_WORDS.containsKey(n) ? NUMBER_WORDS.get(n) : Integer.parseInt(n);
        int months = m.group(2).toLowerCase(Locale.ROOT).startsWith("y") ? amount * 12 : amount;
        return months > 0 && months <= 240 ? months : null;
    }

    // ---- product ----------------------------------------------------------------------------------------------

    private static String productLine(String text) {
        Matcher m = PRODUCT_LINE.matcher(text);
        return m.find() ? collapse(m.group()) : null;
    }

    private static String labelledProduct(String text) {
        Matcher m = PRODUCT_LABEL.matcher(text);
        while (m.find()) {
            // Columns in PDF text are separated by runs of spaces; keep only the first cell.
            String value = m.group(1).split("\\s{2,}")[0].trim();
            if (!value.isEmpty() && !value.matches("[A-Z0-9\\-]{6,}")) return value;
        }
        return null;
    }

    // ---- model ------------------------------------------------------------------------------------------------

    private static String samsungModel(String text) {
        Matcher m = SAMSUNG_MODEL.matcher(text);
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : null;
    }

    private static String labelledModel(String text) {
        Matcher m = MODEL_LABEL.matcher(text);
        while (m.find()) {
            String value = m.group(1);
            if (HAS_DIGIT.matcher(value).find()) return value;
        }
        return null;
    }

    private static String genericModel(String text) {
        Matcher m = GENERIC_MODEL.matcher(text);
        while (m.find()) {
            if (!NON_MODEL_LINE.matcher(lineAt(text, m.start())).find()) return m.group(1);
        }
        return null;
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static String lineAt(String text, int index) {
        int start = Math.max(text.lastIndexOf('\n', index), text.lastIndexOf('\r', index)) + 1;
        int end = text.indexOf('\n', index);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    private static String collapse(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String cap(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max).trim();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }
}
