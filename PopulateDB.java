import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class PopulateDB {
    private static final int BATCH_SIZE = 20000;

	private Connection connection;
	private int batchCount;

	private ArrayList<ArrayList<String>> holder;
	private String line;
	private String csvSplitBy = ",";

	private PreparedStatement preparedStatement;

	public PopulateDB(Connection connection)
	{
		this.connection = connection;
		batchCount = 0;
	}
		
	public void populate(String[] filePath)
	{
		//IMPORTANT: Some of the csv files are set up so that the year is the last column,
		//			 This is done to trick the file reader into reading all entries including 
		//			 null entries

		wipeTables();
		System.out.println("Database has been wiped");
		repopulateTables();
		try {
			connection.setAutoCommit(false);
			insertCircuits(filePath[0]);
			batchCount = 0;
			insertDrivers(filePath[1]);
			batchCount = 0;
			insertTeams(filePath[2]);
			batchCount = 0;
			insertStatus(filePath[3]);
			batchCount = 0;
			insertRaceWeekend(filePath[4]);
			batchCount = 0;
			insertSprintWeekend(filePath[5]);
			batchCount = 0;
			insertRegularWeekend(filePath[6]);
			batchCount = 0;
			insertQualResults(filePath[7]);
			batchCount = 0;
			insertSprintResults(filePath[8]);
			batchCount = 0;
			insertPrixResults(filePath[9]);
			batchCount = 0;
			insertLapInfo(filePath[10]);
			batchCount = 0;
			insertPitStop(filePath[11]);

			connection.setAutoCommit(true);
			System.out.println("Database has been repopulated");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	} 

	public void wipeTables()
	{
		try
		{
			String[] tables = {"pit_stop", "lap_info", "prix_results", "sprint_results", "qual_results", "regular_weekend", "sprint_weekend", "race_weekend", "status", "teams", "drivers", "circuits"};

			for (int i = 0; i < tables.length; i++)
			{
				connection.prepareStatement("DROP TABLE IF EXISTS " + tables[i]).executeUpdate();
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}

	public void repopulateTables()
	{
		try
		{
			String sql = """
						CREATE TABLE [dbo].[circuits] (
							[circuitID] INT  NOT NULL,
							[name]      VARCHAR(100) NOT NULL,
							[city]      VARCHAR(100) NOT NULL,
							[country]   VARCHAR(100) NOT NULL,
							PRIMARY KEY CLUSTERED ([circuitID] ASC)
						);

						CREATE TABLE [dbo].[drivers] (
							[driverID]    INT  NOT NULL,
							[first]       VARCHAR(100) NOT NULL,
							[last]        VARCHAR(100) NOT NULL,
							[dob]         DATE NULL,
							[nationality] VARCHAR(100) NULL,
							PRIMARY KEY CLUSTERED ([driverID] ASC)
						);

						CREATE TABLE [dbo].[teams] (
							[teamID]      INT  NOT NULL,
							[name]        VARCHAR(100) NOT NULL,
							[nationality] VARCHAR(100) NOT NULL,
							PRIMARY KEY CLUSTERED ([teamID] ASC)
						);

						CREATE TABLE [dbo].[status] (
							[statusID]    INT  NOT NULL,
							[description] VARCHAR(100) NOT NULL,
							PRIMARY KEY CLUSTERED ([statusID] ASC)
						);

						CREATE TABLE [dbo].[race_weekend] (
							[year]      INT  NOT NULL,
							[round]     INT  NOT NULL,
							[circuitID] INT  NOT NULL,
							[prix_date] VARCHAR(100) NOT NULL,
							[prix_time] VARCHAR(100) NULL,
							[fp1_date]  VARCHAR(100) NULL,
							[fp1_time]  VARCHAR(100) NULL,
							[qual_date] VARCHAR(100) NULL,
							[qual_time] VARCHAR(100) NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC),
							FOREIGN KEY ([circuitID]) REFERENCES [dbo].[circuits] ([circuitID])
						);

						CREATE TABLE [dbo].[sprint_weekend] (
							[year]        INT  NOT NULL,
							[round]       INT  NOT NULL,
							[sprint_date] VARCHAR(100) NOT NULL,
							[sprint_time] VARCHAR(100) NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[regular_weekend] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[fp3_date] VARCHAR(100) NOT NULL,
							[fp3_time] VARCHAR(100) NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[qual_results] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[driverID] INT  NOT NULL,
							[q1]       VARCHAR(100) NULL,
							[q2]       VARCHAR(100) NULL,
							[q3]       VARCHAR(100) NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC, [driverID] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[sprint_results] (
							[year]         INT  NOT NULL,
							[round]        INT  NOT NULL,
							[driverID]     INT  NOT NULL,
							[teamID]	   INT NOT NULL,
							[positionText] VARCHAR(100) NULL,
							[milliseconds] INT  NULL,
							[lapNum]       INT  NULL,
							[fLapTime]     VARCHAR(100) NULL,
							[fLapNum]      INT  NULL,
							[statusID]     INT  NOT NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC, [driverID] ASC, [statusID] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([statusID]) REFERENCES [dbo].[status] ([statusID]),
							FOREIGN KEY ([teamID]) REFERENCES [dbo].[teams] ([teamID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[sprint_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[prix_results] (
							[resultID]     INT  NOT NULL,
							[year]         INT  NOT NULL,
							[round]        INT  NOT NULL,
							[driverID]     INT  NOT NULL,
							[teamID]	   INT NOT NULL,
							[positionText] VARCHAR(100) NULL,
							[milliseconds] INT  NULL,
							[lapNum]       INT  NULL,
							[fLapTime]     VARCHAR(100) NULL,
							[fLapNum]      INT  NULL,
							[statusID]     INT  NOT NULL,
    						CONSTRAINT [PK_prix_results] PRIMARY KEY CLUSTERED ([resultID] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([statusID]) REFERENCES [dbo].[status] ([statusID]),
							FOREIGN KEY ([teamID]) REFERENCES [dbo].[teams] ([teamID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[lap_info] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[driverID] INT  NOT NULL,
							[lapNum]   INT  NOT NULL,
							[position] INT  NULL,
							[lap_time] VARCHAR(100) NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC, [driverID] ASC, [lapNum] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[pit_stop] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[driverID] INT  NOT NULL,
							[lapNum]   INT  NOT NULL,
							[time]     VARCHAR(100) NULL,
							[duration] REAL NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC, [driverID] ASC, [lapNum] ASC),
							FOREIGN KEY ([year], [round], [driverID], [lapNum]) REFERENCES [dbo].[lap_info] ([year], [round], [driverID], [lapNum])
						);
						""";

				Statement stmt = connection.createStatement();
				stmt.executeUpdate(sql);

		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}

	public void readFile(String file)
	{
		holder = new ArrayList<ArrayList<String>>();
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			br.readLine(); //Skip header line
			while ((line = br.readLine()) != null) {
				String[] data = line.split(csvSplitBy);
				ArrayList<String> row = new ArrayList<String>();
				for (String value : data) {
					row.add(value);
				}
				holder.add(row);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void insertCircuits(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO circuits (circuitID, name, city, country) VALUES (?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, (int) Integer.parseInt(row.get(0)));
				preparedStatement.setString(2, (String) row.get(1));
				preparedStatement.setString(3, (String) row.get(2));
				preparedStatement.setString(4, (String) row.get(3));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertDrivers(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO drivers (driverID, first, last, dob, nationality) VALUES (?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setString(2, row.get(1));
				preparedStatement.setString(3, row.get(2));
				preparedStatement.setString(4, row.get(3));
				preparedStatement.setString(5, row.get(4));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void insertTeams(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO teams (teamID, name, nationality) VALUES (?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setString(2, row.get(1));
				preparedStatement.setString(3, row.get(2));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void insertStatus(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO status (statusID, description) VALUES (?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setString(2, row.get(1));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void insertRaceWeekend(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO race_weekend (year, round, circuitID, prix_date, prix_time, fp1_date, fp1_time, qual_date, qual_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(8)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(0)));
				preparedStatement.setInt(3, Integer.parseInt(row.get(1)));
				preparedStatement.setString(4, row.get(2));
				preparedStatement.setString(5, row.get(3));
				preparedStatement.setString(6, row.get(4));
				preparedStatement.setString(7, row.get(5));
				preparedStatement.setString(8, row.get(6));
				preparedStatement.setString(9, row.get(7));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertSprintWeekend(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO sprint_weekend (year, round, sprint_date, sprint_time) VALUES(?, ?, ?, ?) ";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(3)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(0)));
				preparedStatement.setString(3, row.get(1));
				preparedStatement.setString(4, row.get(2));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertRegularWeekend(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO regular_weekend (year, round, fp3_date, fp3_time) VALUES(?, ?, ?, ?) ";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(3)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(0)));
				preparedStatement.setString(3, row.get(1));
				preparedStatement.setString(4, row.get(2));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertQualResults(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO qual_results (year, round, driverID, q1, q2, q3) VALUES(?, ?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(5)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(0)));
				preparedStatement.setInt(3, Integer.parseInt(row.get(1)));
				preparedStatement.setString(4, row.get(2));
				preparedStatement.setString(5, row.get(3));
				preparedStatement.setString(6, row.get(4));
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertSprintResults(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO sprint_results (year, round, driverID, teamID, positionText, milliseconds, lapNum, fLapTime, fLapNum, statusID) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(1)));
				preparedStatement.setInt(3, Integer.parseInt(row.get(2)));
				preparedStatement.setInt(4, Integer.parseInt(row.get(3)));
				preparedStatement.setString(5, row.get(4));
				preparedStatement.setInt(6, Integer.parseInt(row.get(5)));
				preparedStatement.setInt(7, Integer.parseInt(row.get(6)));
				preparedStatement.setString(8, row.get(7));
				preparedStatement.setInt(9, Integer.parseInt(row.get(8)));
				preparedStatement.setInt(10, Integer.parseInt(row.get(9)));
	
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertPrixResults(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO prix_results (resultID, year, round, driverID, teamID, positionText, milliseconds, lapNum, fLapTime, fLapNum, statusID) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(1)));
				preparedStatement.setInt(3, Integer.parseInt(row.get(2)));
				preparedStatement.setInt(4, Integer.parseInt(row.get(3)));
				preparedStatement.setInt(5, Integer.parseInt(row.get(4)));
				preparedStatement.setString(6, row.get(5));
				preparedStatement.setInt(7, Integer.parseInt(row.get(6)));
				preparedStatement.setInt(8, Integer.parseInt(row.get(7)));
				preparedStatement.setString(9, row.get(8));
				preparedStatement.setInt(10, Integer.parseInt(row.get(9)));
				preparedStatement.setInt(11, Integer.parseInt(row.get(10)));
	
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertLapInfo(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO lap_info (year, round, driverID, lapNum, position, lap_time) VALUES(?, ?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(1)));
				preparedStatement.setInt(3, Integer.parseInt(row.get(2)));
				preparedStatement.setInt(4, Integer.parseInt(row.get(3)));
				preparedStatement.setInt(5, Integer.parseInt(row.get(4)));
				preparedStatement.setString(6, row.get(5));
	
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertPitStop(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			line = "INSERT INTO pit_stop (year, round, driverID, lapNum, time, duration) VALUES(?, ?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(1)));
				preparedStatement.setInt(3, Integer.parseInt(row.get(2)));
				preparedStatement.setInt(4, Integer.parseInt(row.get(3)));
				preparedStatement.setString(5, row.get(4));
				preparedStatement.setInt(6, Integer.parseInt(row.get(5)));
	
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			preparedStatement.executeBatch();


			preparedStatement.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}


}	
