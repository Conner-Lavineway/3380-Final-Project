import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DBConsole 
{
    public static void main(String[] args) 
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
        
        try (Connection connection = DriverManager.getConnection(connectionUrl);
                Statement statement = connection.createStatement();) 
        {
            if(connection != null)
                {
                    System.out.println(connection);
                    final PopulateDB POPULATOR = new PopulateDB(connection);
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
                                        "F1-Data/pit_edited.csv",
                    };
                    POPULATOR.populate(fileList);
            }

        }catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    
}
