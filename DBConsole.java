import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Scanner;

public class DBConsole 
{
    private static Connection connection;
    private static String query;
    private static Statement state;
    private static PreparedStatement pState;
    public static void main(String[] args) 
    {        
        connectDB();
        runConsole();
    }

    public static void connectDB()
    {
        Properties prop = new Properties();
        String fileName = "auth.cfg";
        try {
            FileInputStream configFile = new FileInputStream(fileName);
            prop.load(configFile);
            configFile.close();
        } catch (FileNotFoundException ex) {
            System.out.println("Could not find config file.");
            System.exit(1);
        } catch (IOException ex) {
            System.out.println("Error reading config file.");
            System.exit(1);
        }
        String username = (prop.getProperty("username"));
        String password = (prop.getProperty("password"));

        if (username == null || password == null){
            System.out.println("Username or password not provided.");
            System.exit(1);
        }

        String connectionUrl =
                "jdbc:sqlserver://uranium.cs.umanitoba.ca:1433;"
                + "database=cs338015;"
                + "user=" + username + ";"
                + "password="+ password +";"
                + "encrypt=false;"
                + "trustServerCertificate=false;"
                + "loginTimeout=30;";

        try 
        {
            connection = DriverManager.getConnection(connectionUrl);
            state = connection.createStatement();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void repopulateDB()
    {
        try 
        {
            if(connection != null)
                {
                    PopulateDB populator = new PopulateDB(connection);
                    String[] fileList = {
                                        "F1-Data/circuits.csv",
                                        "F1-Data/drivers.csv",
                                        "F1-Data/teams.csv",
                                        "F1-Data/status.csv",
                                        "F1-Data/races.csv",
                                        "F1-Data/sprints.csv",
                                        "F1-Data/regular.csv",
                                        "F1-Data/qualifying_edited.csv",
                                        "F1-Data/sprint_edited.csv",
                                        "F1-Data/results_edited.csv",
                                        "F1-Data/laps_edited.csv",
                                        "F1-Data/pit_edited.csv"
                    };
                    populator.populate(fileList);
            }

        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void runConsole()
    {
        Scanner console = new Scanner(System.in);
		System.out.print("Welcome! Type h for help. ");
		System.out.print("db > ");
		String line = console.nextLine();
		String[] parts;
		String arg = "";
        while (line != null && !line.equals("q")) {
			parts = line.split("\\s+");
			if (line.indexOf(" ") > 0)
				arg = line.substring(line.indexOf(" ")).trim();

            if (parts[0].equals("h"))
				printHelp();
            else if (parts[0].equals("i"))
				printInteresting();
            else if (parts[0].equals("r"))
				repopulateDB();

            else if (parts[0].equals("d"))
            {
                try {
					if (parts.length <= 3)
                        driversByID(arg);
				} catch (Exception e) {
					System.out.println("id must be an integer");
				}
                if(parts.length == 1)
				    drivers();
            }
            else if (parts[0].equals("fl"))
                fastestCircuitLap();
            else if (parts[0].equals("iw"))
                winsByNation();
            

            else
				System.out.println("Command not recognized, please press h to consult the help menu");

            System.out.print("db > ");
            line = console.nextLine();
        }

		console.close();
	}


    private static void printHelp() 
    {
        System.out.println("F1 database");
        System.out.println("Commands:");
        System.out.println("h - Get help");
        System.out.println("r - Delete and repopulate data");
        System.out.println("i - Premade interesting queries list");
        System.out.println("d - Drivers and their IDs");
        System.out.println("d <num> - Drivers by ID");
        System.out.println("fl - Fastest lap on each circuit");

        System.out.println("q - Exit the program");

        System.out.println("---- end help ----- ");
    }

        private static void printInteresting() 
    {
        System.out.println("Interesting Queries");
        System.out.println("Commands:");
        System.out.println("iw - Number of wins by drivers working for a team with the same nationality");
        


        System.out.println("---- end list ----- ");
    }

    private static void winsByNation()
    {
        query = """
                SELECT d.first, d.last, t.name, d.nationality, count(*) wins FROM prix_results p
                JOIN teams t ON p.teamID = t.teamID
                JOIN drivers d ON d.driverId = p.driverId 
                WHERE t.nationality = d.nationality AND positionText LIKE '1' 
                GROUP BY d.first, d.last, t.name, d.nationality
                ORDER BY wins DESC
                """;
        try 
        {
            ResultSet resultSet = state.executeQuery(query);
            System.out.println("First Last Team Nationality Wins");
            while(resultSet.next())
            {
                System.out.println(
                    resultSet.getString(1) + " " + resultSet.getString(2)
                    + " " + resultSet.getString(3) + " "
                    + resultSet.getString(4) + " " + resultSet.getInt(5)
                ); 
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void fastestCircuitLap()
    {
        query = """
                WITH fastestLaps AS (
                SELECT p.[year], p.[round], circuitID, MIN(p.fLapTime) AS lapTime 
                FROM prix_results p JOIN race_weekend r ON r.[year] = p.[year] AND r.[round] = p.[round]
                WHERE NOT fLapTime = '0' 
                GROUP BY p.year, p.round, circuitID)

                SELECT c.name, MIN(lapTime) AS time FROM fastestLaps f JOIN circuits c  ON c.circuitID = f.circuitID
                GROUP BY f.circuitID, c.name
                ORDER BY time
                """;
        try 
        {
            ResultSet resultSet = state.executeQuery(query);
            System.out.println("Circuit, Time");
            while(resultSet.next())
            {
                System.out.println(
                    resultSet.getString(1) + ", " + resultSet.getString(2)
                ); 
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void drivers()
    {
        query = """
                SELECT driverID, first, last FROM drivers
                ORDER BY driverID
                """;
        try 
        {
            ResultSet resultSet = state.executeQuery(query);
            System.out.printf("%s: %-1s %-40s%s: %-1s %s\n", "ID", "First", "Last", "ID", "First", "Last");
            int extraLines = 0;
            while(resultSet.next())
            {
                int nameLen = (resultSet.getString(3).length());
                int spaces =  (nameLen % 8);
                extraLines++;
                if(extraLines == 2)
                {
                    System.out.printf("\t%20d: %s %s\n",
                    resultSet.getInt(1), resultSet.getString(2), resultSet.getString(3)
                    ); 
                    extraLines = 0;
                }
                else
                {
                    if(spaces != 0)
                    {
                        System.out.printf("%d: %s %-" + spaces + "s",
                        resultSet.getInt(1), resultSet.getString(2), resultSet.getString(3)
                        ); 
                    }
                    else
                    {
                        System.out.printf("%d: %s %s",
                        resultSet.getInt(1), resultSet.getString(2), resultSet.getString(3)
                        ); 
                    }
                }
            }
            System.out.println("");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void driversByID(String id) throws NumberFormatException, SQLException
    {
        query = """
                SELECT driverID, first, last FROM drivers
                WHERE driverID = ?
                """;
        pState = connection.prepareStatement(query);
        pState.setInt(1, Integer.parseInt(id));
        
        ResultSet resultSet = pState.executeQuery();
        while (resultSet.next()) 
        {    
            System.out.println(resultSet.getInt(1) + ": " + resultSet.getString(2) + " " + resultSet.getString(3));
        }
    }
}
