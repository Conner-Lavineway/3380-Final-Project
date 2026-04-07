import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// This class owns the live SQL Server connection used by the CLI.
// It contains the small set of DB operations the rest of the app needs.
public final class DatabaseClient implements AutoCloseable {
    private final AppConfig config;
    private final Connection connection;

    public DatabaseClient(AppConfig config) throws SQLException {
        this.config = config;
        this.connection = DriverManager.getConnection(config.jdbcUrl());
    }

    public AppConfig config() {
        return config;
    }

    public CachedTable query(String sql, StatementBinder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return CachedTable.fromResultSet(resultSet);
            }
        }
    }

    public int executeUpdate(String sql, StatementBinder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            return statement.executeUpdate();
        }
    }

    public void executeStatement(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }

    public void commit() throws SQLException {
        connection.commit();
        connection.setAutoCommit(true);
    }

    public void rollbackQuietly() {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Best effort rollback for destructive maintenance operations.
        }
    }

    public List<Option> listYears() throws SQLException {
        return optionsFromQuery(
                """
                SELECT DISTINCT year AS id, CAST(year AS varchar(10)) AS label
                FROM race_weekend
                ORDER BY year DESC
                """,
                null);
    }

    public List<Option> listRoundsForYear(int year) throws SQLException {
        return optionsFromQuery(
                """
                SELECT CAST(rw.round AS varchar(10)) AS id,
                       CONCAT('Round ', rw.round, ' - ', c.name, ' (', c.country, ')') AS label
                FROM race_weekend rw
                JOIN circuits c ON c.circuit_id = rw.circuit_id
                WHERE rw.year = ?
                ORDER BY rw.round
                """,
                statement -> statement.setInt(1, year));
    }

    public List<Option> searchDrivers(String searchTerm) throws SQLException {
        return optionsFromQuery(
                """
                SELECT TOP 25 driver_ref AS id,
                       CONCAT(name, ' [', driver_ref, ']') AS label
                FROM drivers
                WHERE ? = '' OR name LIKE ? OR driver_ref LIKE ?
                ORDER BY name
                """,
                statement -> bindSearch(statement, searchTerm));
    }

    public List<Option> searchTeams(String searchTerm) throws SQLException {
        return optionsFromQuery(
                """
                SELECT TOP 25 team_ref AS id,
                       CONCAT(name, ' [', team_ref, ']') AS label
                FROM teams
                WHERE ? = '' OR name LIKE ? OR team_ref LIKE ?
                ORDER BY name
                """,
                statement -> bindSearch(statement, searchTerm));
    }

    public List<Option> listTables() throws SQLException {
        return optionsFromQuery(
                """
                SELECT TABLE_NAME AS id, TABLE_NAME AS label
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """,
                null);
    }

    public long countRows(String tableName) throws SQLException {
        validateTableName(tableName);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM [" + tableName + "]")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    public CachedTable inspectTableColumns(String tableName) throws SQLException {
        validateTableName(tableName);
        return query(
                """
                SELECT COLUMN_NAME,
                       DATA_TYPE,
                       IS_NULLABLE,
                       COALESCE(CAST(CHARACTER_MAXIMUM_LENGTH AS varchar(12)), '-') AS max_length
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """,
                statement -> statement.setString(1, tableName));
    }

    public CachedTable browseTable(String tableName, String orderBy, int pageSize, int offset) throws SQLException {
        validateTableName(tableName);
        String sql = """
                SELECT *
                FROM [%s]
                ORDER BY %s
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.formatted(tableName, orderBy);
        return query(sql, statement -> {
            statement.setInt(1, offset);
            statement.setInt(2, pageSize);
        });
    }

    public void deleteAllData(List<String> deleteOrder) throws SQLException {
        beginTransaction();
        try {
            for (String tableName : deleteOrder) {
                validateTableName(tableName);
                executeStatement("DELETE FROM [" + tableName + "]");
            }
            commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            throw exception;
        }
    }

    // Menu prompts use small query result lists, so this helper converts a
    // two-column result set into Option objects that can be shown to the user.
    private List<Option> optionsFromQuery(String sql, StatementBinder binder) throws SQLException {
        CachedTable table = query(sql, binder);
        List<Option> options = new ArrayList<>(table.rows().size());
        for (List<String> row : table.rows()) {
            options.add(new Option(row.get(0), row.get(1)));
        }
        return options;
    }

    private void bindSearch(PreparedStatement statement, String searchTerm) throws SQLException {
        String normalized = searchTerm == null ? "" : searchTerm.trim();
        String pattern = "%" + normalized + "%";
        statement.setString(1, normalized);
        statement.setString(2, pattern);
        statement.setString(3, pattern);
    }

    private void validateTableName(String tableName) {
        if (!tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    @FunctionalInterface
    public interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    public record Option(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
