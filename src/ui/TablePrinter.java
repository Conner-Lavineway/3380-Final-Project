import java.util.ArrayList;
import java.util.List;

public final class TablePrinter {
    private static final int MAX_COLUMN_WIDTH = 32;

    private TablePrinter() {
    }

    public static void print(CachedTable table) {
        if (table.columns().isEmpty()) {
            System.out.println("(no columns)");
            return;
        }

        List<Integer> widths = new ArrayList<>(table.columns().size());
        for (int index = 0; index < table.columns().size(); index++) {
            int width = Math.min(MAX_COLUMN_WIDTH, table.columns().get(index).length());
            for (List<String> row : table.rows()) {
                width = Math.max(width, Math.min(MAX_COLUMN_WIDTH, row.get(index).length()));
            }
            widths.add(width);
        }

        printRow(table.columns(), widths);
        printSeparator(widths);
        for (List<String> row : table.rows()) {
            printRow(row, widths);
        }
        if (table.rows().isEmpty()) {
            System.out.println("(0 rows)");
        } else {
            System.out.printf("(%d rows)%n", table.rows().size());
        }
    }

    private static void printRow(List<String> values, List<Integer> widths) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            builder.append(pad(truncate(value), widths.get(index)));
            if (index < values.size() - 1) {
                builder.append(" | ");
            }
        }
        System.out.println(builder);
    }

    private static void printSeparator(List<Integer> widths) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < widths.size(); index++) {
            builder.append("-".repeat(widths.get(index)));
            if (index < widths.size() - 1) {
                builder.append("-+-");
            }
        }
        System.out.println(builder);
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_COLUMN_WIDTH) {
            return value;
        }
        return value.substring(0, MAX_COLUMN_WIDTH - 3) + "...";
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(Math.max(0, width - value.length()));
    }
}
