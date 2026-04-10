import java.sql.SQLException;

// Each analyst query is one small class with a menu label and a run method.
public abstract class QueryAction {
    private final String label;

    protected QueryAction(String label) {
        this.label = label;
    }

    public final String label() {
        return label;
    }

    public abstract void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException;
}
