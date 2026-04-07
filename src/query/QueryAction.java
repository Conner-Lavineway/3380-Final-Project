import java.sql.SQLException;

public interface QueryAction {
    String label();

    void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException;
}
