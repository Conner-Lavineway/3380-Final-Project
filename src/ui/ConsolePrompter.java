import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;

public final class ConsolePrompter {
    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    public String prompt(String message) {
        while (true) {
            System.out.print(message);
            try {
                String line = reader.readLine();
                if (line == null) {
                    return "0";
                }
                return line.trim();
            } catch (IOException exception) {
                System.out.println("Could not read input. Try again.");
            }
        }
    }

    public int promptMenu(String title, List<String> options, boolean includeBack) {
        while (true) {
            System.out.println();
            System.out.println(title);
            for (int index = 0; index < options.size(); index++) {
                System.out.printf("  %d. %s%n", index + 1, options.get(index));
            }
            System.out.printf("  0. %s%n", includeBack ? "Back" : "Exit");

            String input = prompt("> ");
            try {
                int selected = Integer.parseInt(input);
                int max = options.size();
                if (selected >= 0 && selected <= max) {
                    return selected;
                }
            } catch (NumberFormatException ignored) {
                // Handled below.
            }
            System.out.println("Enter a number from the menu.");
        }
    }

    public int promptSelection(String title, List<String> options) {
        return promptMenu(title, options, true);
    }

    public Integer promptPositiveInt(String message, boolean allowBlank) {
        while (true) {
            String input = prompt(message);
            if (allowBlank && input.isBlank()) {
                return null;
            }

            try {
                int value = Integer.parseInt(input);
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Handled below.
            }
            System.out.println("Enter a positive integer.");
        }
    }

    public boolean confirm(String message) {
        while (true) {
            String input = prompt(message + " [y/n] ").toLowerCase(Locale.ROOT);
            if (input.equals("y") || input.equals("yes")) {
                return true;
            }
            if (input.equals("n") || input.equals("no")) {
                return false;
            }
            System.out.println("Enter y or n.");
        }
    }
}
