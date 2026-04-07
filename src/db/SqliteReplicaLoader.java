import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SqliteReplicaLoader {
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private final AppConfig config;

    public SqliteReplicaLoader(AppConfig config) {
        this.config = config;
    }

    public void rebuildFromSqlite() throws IOException, SQLException {
        validateConfig();
        ensureDatabaseExists();

        try (Connection source = openSource();
             Connection target = openTarget()) {
            target.setAutoCommit(false);
            try {
                rebuildSchema(target);
                loadAllTables(source, target);
                createIndexes(target);
                target.commit();
            } catch (SQLException | IOException exception) {
                target.rollback();
                throw exception;
            } finally {
                target.setAutoCommit(true);
            }
        }

        System.out.println("Import complete. Target database: " + config.database());
    }

    private void validateConfig() throws IOException {
        if (!DATABASE_NAME_PATTERN.matcher(config.database()).matches()) {
            throw new IllegalArgumentException("Database name must use only letters, numbers, and underscores.");
        }
        if (!Files.isRegularFile(config.sqliteReplicaPath())) {
            throw new IOException("SQLite replica not found: " + config.sqliteReplicaPath());
        }
        if (!Files.isRegularFile(config.schemaPath())) {
            throw new IOException("Schema file not found: " + config.schemaPath());
        }
        if (config.batchSize() <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than zero.");
        }
    }

    private Connection openSource() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + config.sqliteReplicaPath());
    }

    private Connection openTarget() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl());
    }

    private void ensureDatabaseExists() throws SQLException {
        try (Connection master = DriverManager.getConnection(config.jdbcUrlForDatabase("master"));
             PreparedStatement existsStatement = master.prepareStatement("SELECT DB_ID(?)");
             Statement createStatement = master.createStatement()) {
            existsStatement.setString(1, config.database());
            try (ResultSet resultSet = existsStatement.executeQuery()) {
                resultSet.next();
                if (resultSet.getObject(1) != null) {
                    return;
                }
            }

            createStatement.execute("CREATE DATABASE " + quotedIdentifier(config.database()));
        }

        waitForDatabaseOnline();
    }

    private void waitForDatabaseOnline() throws SQLException {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            try (Connection master = DriverManager.getConnection(config.jdbcUrlForDatabase("master"));
                 PreparedStatement statement = master.prepareStatement(
                         "SELECT state_desc FROM sys.databases WHERE name = ?")) {
                statement.setString(1, config.database());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next() && "ONLINE".equals(resultSet.getString(1))) {
                        return;
                    }
                }
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for the database to become available.", exception);
            }
        }

        throw new SQLException("Timed out waiting for database " + config.database() + " to become ONLINE.");
    }

    private void rebuildSchema(Connection target) throws IOException, SQLException {
        Instant start = Instant.now();

        for (String tableName : ProjectSchema.DROP_ORDER) {
            try (Statement statement = target.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + quotedIdentifier(tableName));
            }
        }

        for (String statementText : splitSqlStatements(Files.readString(config.schemaPath()))) {
            try (Statement statement = target.createStatement()) {
                statement.execute(statementText);
            }
        }

        System.out.println("Schema rebuilt in " + formatElapsed(start) + ".");
    }

    private void loadAllTables(Connection source, Connection target) throws SQLException {
        for (String tableName : ProjectSchema.TABLE_ORDER) {
            Instant start = Instant.now();
            int rowCount = loadTable(tableName, source, target);
            System.out.println("Loaded " + rowCount + " rows into " + tableName + " in " + formatElapsed(start) + ".");
        }
    }

    private int loadTable(String tableName, Connection source, Connection target) throws SQLException {
        List<String> columns = loadTableColumns(source, tableName);
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String columnList = String.join(", ", columns.stream().map(SqliteReplicaLoader::quotedIdentifier).toList());
        String insertSql = "INSERT INTO " + quotedIdentifier(tableName) + " (" + columnList + ") VALUES (" + placeholders + ")";

        int rowCount = 0;
        int pendingBatch = 0;

        try (Statement sourceStatement = source.createStatement();
             ResultSet resultSet = sourceStatement.executeQuery("SELECT * FROM " + tableName);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            while (resultSet.next()) {
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    insert.setObject(columnIndex + 1, resultSet.getObject(columnIndex + 1));
                }
                insert.addBatch();
                rowCount++;
                pendingBatch++;

                if (pendingBatch >= config.batchSize()) {
                    insert.executeBatch();
                    pendingBatch = 0;
                }
            }

            if (pendingBatch > 0) {
                insert.executeBatch();
            }
        }

        return rowCount;
    }

    private List<String> loadTableColumns(Connection source, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = source.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private void createIndexes(Connection target) throws SQLException {
        Instant start = Instant.now();
        for (String statementText : ProjectSchema.INDEX_STATEMENTS) {
            try (Statement statement = target.createStatement()) {
                statement.execute(statementText);
            }
        }
        System.out.println("Indexes created in " + formatElapsed(start) + ".");
    }

    private static List<String> splitSqlStatements(String sqlText) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : sqlText.split("\\R")) {
            String stripped = line.trim();
            if (stripped.isEmpty() || stripped.startsWith("--")) {
                continue;
            }

            if (current.length() > 0) {
                current.append(System.lineSeparator());
            }
            current.append(line);

            if (stripped.endsWith(";")) {
                String statement = current.toString().trim();
                statements.add(statement.substring(0, statement.length() - 1));
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            statements.add(current.toString().trim());
        }

        return statements;
    }

    private static String quotedIdentifier(String name) {
        return "[" + name + "]";
    }

    private static String formatElapsed(Instant start) {
        long millis = Duration.between(start, Instant.now()).toMillis();
        return String.format(Locale.ROOT, "%d ms", millis);
    }
}
