import java.sql.SQLException;

public abstract class BaseQueryAction implements QueryAction {
    private final String label;

    protected BaseQueryAction(String label) {
        this.label = label;
    }

    @Override
    public final String label() {
        return label;
    }

    @Override
    public abstract void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException;
}
