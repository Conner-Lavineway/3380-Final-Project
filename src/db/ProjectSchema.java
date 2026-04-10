import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectSchema {
    public static final List<String> TABLE_ORDER = List.of(
            "circuits",
            "drivers",
            "teams",
            "status",
            "race_weekend",
            "sprint_weekend",
            "regular_weekend",
            "race_entry",
            "qual_result",
            "sprint_perf",
            "prix_perf",
            "lap_info",
            "pit_stop");

    public static final List<String> DROP_ORDER = List.of(
            "pit_stop",
            "lap_info",
            "prix_perf",
            "sprint_perf",
            "qual_result",
            "race_entry",
            "regular_weekend",
            "sprint_weekend",
            "race_weekend",
            "status",
            "teams",
            "drivers",
            "circuits");

    public static final List<String> INDEX_STATEMENTS = List.of(
            "CREATE INDEX IX001 ON race_entry (year, round)",
            "CREATE INDEX IX002 ON race_entry (driver_ref)",
            "CREATE INDEX IX003 ON race_entry (team_ref)",
            "CREATE INDEX IX004 ON sprint_perf (status_id)",
            "CREATE INDEX IX005 ON prix_perf (status_id)",
            "CREATE INDEX IX006 ON lap_info (lap_num)",
            "CREATE INDEX IX007 ON pit_stop (entry_id, lap_num)");

    public static final Map<String, String> TABLE_ORDER_BY = buildTableOrderBy();

    private ProjectSchema() {
    }

    private static Map<String, String> buildTableOrderBy() {
        Map<String, String> orderBy = new LinkedHashMap<>();
        orderBy.put("circuits", "circuit_id");
        orderBy.put("drivers", "name, driver_ref");
        orderBy.put("teams", "name, team_ref");
        orderBy.put("status", "status_id");
        orderBy.put("race_weekend", "year DESC, round ASC");
        orderBy.put("sprint_weekend", "year DESC, round ASC");
        orderBy.put("regular_weekend", "year DESC, round ASC");
        orderBy.put("race_entry", "year DESC, round ASC, entry_id");
        orderBy.put("qual_result", "entry_id");
        orderBy.put("sprint_perf", "entry_id");
        orderBy.put("prix_perf", "entry_id");
        orderBy.put("lap_info", "entry_id, lap_num");
        orderBy.put("pit_stop", "entry_id, stop_no");
        return Map.copyOf(orderBy);
    }
}
