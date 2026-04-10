import java.io.IOException;
import java.sql.SQLException;

public final class LoaderMain {
    private LoaderMain() {
    }

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromArgs(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            Main.printUsage();
            return;
        }

        try {
            new SqliteReplicaLoader(config).rebuildFromSqlite();
        } catch (IOException exception) {
            System.err.println("Could not read a required local file.");
            System.err.println(exception.getMessage());
        } catch (SQLException exception) {
            System.err.println("Database load failed.");
            System.err.println(exception.getMessage());
        }
    }
}
