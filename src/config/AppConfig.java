import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record AppConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean encrypt,
        boolean trustServerCertificate,
        int loginTimeoutSeconds,
        Path projectRoot,
        Path sqliteReplicaPath,
        Path schemaPath,
        int batchSize) {

    public static AppConfig fromArgs(String[] args) {
        Map<String, String> options = parseOptions(args);

        String host = option(options, "host", env("F1_DB_HOST", "localhost"));
        int port = parseInt(option(options, "port", env("F1_DB_PORT", "1433")), "port");
        String database = option(options, "database", env("F1_DB_NAME", "cs338015"));
        String username = option(options, "username", env("F1_DB_USER", "sa"));
        String password = option(options, "password", env("F1_DB_PASSWORD", "LocalSqlServerPassw0rd!"));
        boolean encrypt = parseBoolean(option(options, "encrypt", env("F1_DB_ENCRYPT", "true")), "encrypt");
        boolean trustServerCertificate = parseBoolean(
                option(options, "trust-server-certificate", env("F1_DB_TRUST_SERVER_CERTIFICATE", "true")),
                "trust-server-certificate");
        int loginTimeoutSeconds = parseInt(
                option(options, "login-timeout", env("F1_DB_LOGIN_TIMEOUT", "30")),
                "login-timeout");

        Path projectRoot = Paths.get(option(options, "project-root",
                env("F1_PROJECT_ROOT", Paths.get("").toAbsolutePath().normalize().toString())));
        Path sqliteReplicaPath = Paths.get(option(options, "sqlite-replica",
                env("F1_SQLITE_REPLICA", projectRoot.resolve("dbs").resolve("F1_refactored.db").toString())));
        Path schemaPath = Paths.get(option(options, "schema",
                env("F1_SCHEMA_PATH", projectRoot.resolve("schema").resolve("refactored_race_entry_schema.sql").toString())));
        int batchSize = parseInt(option(options, "batch-size", env("F1_DB_BATCH_SIZE", "500")), "batch-size");

        return new AppConfig(
                host,
                port,
                database,
                username,
                password,
                encrypt,
                trustServerCertificate,
                loginTimeoutSeconds,
                projectRoot.normalize(),
                sqliteReplicaPath.normalize(),
                schemaPath.normalize(),
                batchSize);
    }

    public String jdbcUrl() {
        return jdbcUrlForDatabase(database);
    }

    public String jdbcUrlForDatabase(String databaseName) {
        return String.format(
                Locale.ROOT,
                "jdbc:sqlserver://%s:%d;database=%s;user=%s;password=%s;encrypt=%s;trustServerCertificate=%s;loginTimeout=%d;",
                host,
                port,
                databaseName,
                username,
                password,
                Boolean.toString(encrypt),
                Boolean.toString(trustServerCertificate),
                loginTimeoutSeconds);
    }

    public String displaySummary() {
        return "%s:%d / %s as %s".formatted(host, port, database, username);
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }

            String body = arg.substring(2);
            int equalsIndex = body.indexOf('=');
            if (equalsIndex >= 0) {
                options.put(body.substring(0, equalsIndex), body.substring(equalsIndex + 1));
                continue;
            }

            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for argument: " + arg);
            }

            options.put(body, args[++i]);
        }
        return options;
    }

    private static String option(Map<String, String> options, String key, String fallback) {
        String value = options.getOrDefault(key, fallback);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration value for " + key);
        }
        return value;
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for " + key + ": " + value, exception);
        }
    }

    private static boolean parseBoolean(String value, String key) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean for " + key + ": " + value);
        };
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
