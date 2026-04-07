import java.util.List;

// This is the single place to see which analyst queries exist and in what
// order they appear in the menu.
public final class QueryCatalog {
    private QueryCatalog() {
    }

    public static List<QueryAction> analystQueries() {
        return List.of(
                new SeasonCalendarQuery(),
                new CircuitFrequencyQuery(),
                new DriverCareerSummaryQuery(),
                new TeamSeasonWorkloadQuery(),
                new DriverTeamPartnershipsQuery(),
                new QualifyingPaceLeaderboardQuery(),
                new TeamReliabilityQuery(),
                new CircuitAttritionQuery(),
                new PitStopBurdenQuery(),
                new PitHeavyWeekendsQuery(),
                new SprintVsRegularSummaryQuery(),
                new LapConsistencyQuery());
    }
}
