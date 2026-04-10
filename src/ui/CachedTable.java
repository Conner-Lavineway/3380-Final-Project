import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public record CachedTable(List<String> columns, List<List<String>> rows) {

    public static CachedTable fromResultSet(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<String> columns = new ArrayList<>(metadata.getColumnCount());
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            columns.add(metadata.getColumnLabel(index));
        }

        List<List<String>> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<String> row = new ArrayList<>(metadata.getColumnCount());
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                Object value = resultSet.getObject(index);
                row.add(value == null ? "NULL" : value.toString());
            }
            rows.add(row);
        }

        return new CachedTable(columns, rows);
    }
}
