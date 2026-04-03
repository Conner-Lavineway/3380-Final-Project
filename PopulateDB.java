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
	
	private long startTime = 0;
	private long endTime = 0;

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
			/*Statement statement = connection.createStatement();
			ResultSet res = statement.executeQuery("SELECT * FROM drivers");
            while (res.next()) {
                System.out.println(res.getInt(1) + 
                " " + res.getString(2) +
                " " + res.getString(3) +
                " " + res.getString(4) +
                " " + res.getString(5));
            }*/
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
			System.out.println(e);
		}
	}

	public void repopulateTables()
	{
		try
		{
			String sql = """
						CREATE TABLE [dbo].[circuits] (
							[circuitID] INT  NOT NULL,
							[name]      TEXT NOT NULL,
							[city]      TEXT NOT NULL,
							[country]   TEXT NOT NULL,
							PRIMARY KEY CLUSTERED ([circuitID] ASC)
						);

						CREATE TABLE [dbo].[drivers] (
							[driverID]    INT  NOT NULL,
							[first]       TEXT NOT NULL,
							[last]        TEXT NOT NULL,
							[dob]         DATE NULL,
							[nationality] TEXT NULL,
							PRIMARY KEY CLUSTERED ([driverID] ASC)
						);

						CREATE TABLE [dbo].[teams] (
							[teamID]      INT  NOT NULL,
							[name]        TEXT NOT NULL,
							[nationality] TEXT NOT NULL,
							PRIMARY KEY CLUSTERED ([teamID] ASC)
						);

						CREATE TABLE [dbo].[status] (
							[statusID]    INT  NOT NULL,
							[description] TEXT NOT NULL,
							PRIMARY KEY CLUSTERED ([statusID] ASC)
						);

						CREATE TABLE [dbo].[race_weekend] (
							[year]      INT  NOT NULL,
							[round]     INT  NOT NULL,
							[circuitID] INT  NOT NULL,
							[prix_date] TEXT NOT NULL,
							[prix_time] TEXT NULL,
							[fp1_date]  TEXT NULL,
							[fp1_time]  TEXT NULL,
							[qual_date] TEXT NULL,
							[qual_time] TEXT NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC),
							FOREIGN KEY ([circuitID]) REFERENCES [dbo].[circuits] ([circuitID])
						);

						CREATE TABLE [dbo].[sprint_weekend] (
							[year]        INT  NOT NULL,
							[round]       INT  NOT NULL,
							[sprint_date] TEXT NOT NULL,
							[sprint_time] TEXT NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[regular_weekend] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[fp3_date] TEXT NOT NULL,
							[fp3_time] TEXT NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[qual_results] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[driverID] INT  NOT NULL,
							[q1]       TEXT NULL,
							[q2]       TEXT NULL,
							[q3]       TEXT NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC, [driverID] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[sprint_results] (
							[year]         INT  NOT NULL,
							[round]        INT  NOT NULL,
							[driverID]     INT  NOT NULL,
							[positionText] TEXT NULL,
							[milliseconds] INT  NULL,
							[lapNum]       INT  NULL,
							[fLapTime]     TEXT NULL,
							[fLapNum]      INT  NULL,
							[statusID]     INT  NOT NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC, [driverID] ASC, [statusID] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([statusID]) REFERENCES [dbo].[status] ([statusID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[sprint_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[prix_results] (
							[resultID]     INT  NOT NULL,
							[year]         INT  NOT NULL,
							[round]        INT  NOT NULL,
							[driverID]     INT  NOT NULL,
							[positionText] TEXT NULL,
							[milliseconds] INT  NULL,
							[lapNum]       INT  NULL,
							[fLapTime]     TEXT NULL,
							[fLapNum]      INT  NULL,
							[statusID]     INT  NOT NULL,
    						CONSTRAINT [PK_prix_results] PRIMARY KEY CLUSTERED ([resultID] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([statusID]) REFERENCES [dbo].[status] ([statusID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[lap_info] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[driverID] INT  NOT NULL,
							[lapNum]   INT  NOT NULL,
							[position] INT  NULL,
							[lap_time] TEXT NULL,
							PRIMARY KEY CLUSTERED ([year] ASC, [round] ASC, [driverID] ASC, [lapNum] ASC),
							FOREIGN KEY ([driverID]) REFERENCES [dbo].[drivers] ([driverID]),
							FOREIGN KEY ([year], [round]) REFERENCES [dbo].[race_weekend] ([year], [round])
						);

						CREATE TABLE [dbo].[pit_stop] (
							[year]     INT  NOT NULL,
							[round]    INT  NOT NULL,
							[driverID] INT  NOT NULL,
							[lapNum]   INT  NOT NULL,
							[time]     TEXT NULL,
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
			System.out.println(e);
		}
	}

	public void readFile(String file)
	{
		holder = new ArrayList<ArrayList<String>>();
		startTime = System.currentTimeMillis();
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

		endTime = System.currentTimeMillis();
		System.out.println("file: " + file + " read after: " + (endTime - startTime) + " milliseconds");
	}

	public void insertCircuits(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to circuits");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to circuits");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("circuits populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertDrivers(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to drivers");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to drivers");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("drivers populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void insertTeams(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to teams");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to teams");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("teams populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void insertStatus(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to status");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to status");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("status populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void insertRaceWeekend(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to race_weekend");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to race_weekend");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("race_weekend populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertSprintWeekend(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to race_weekend");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to race_weekend");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("race_weekend populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertRegularWeekend(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to regular_weekend");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to regular_weekend");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("regular_weekend populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertQualResults(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to qual_results");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to qual_results");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("qual_results populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertSprintResults(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
			line = "INSERT INTO sprint_results (year, round, driverID, positionText, milliseconds, lapNum, fLapTime, fLapNum, statusID) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
			preparedStatement = connection.prepareStatement(line);
			for(ArrayList<String>  row : holder)
			{
				preparedStatement.setInt(1, Integer.parseInt(row.get(0)));
				preparedStatement.setInt(2, Integer.parseInt(row.get(1)));
				preparedStatement.setInt(3, Integer.parseInt(row.get(2)));
				preparedStatement.setString(4, row.get(3));
				preparedStatement.setInt(5, Integer.parseInt(row.get(4)));
				preparedStatement.setInt(6, Integer.parseInt(row.get(5)));
				preparedStatement.setString(7, row.get(6));
				preparedStatement.setInt(8, Integer.parseInt(row.get(7)));
				preparedStatement.setInt(9, Integer.parseInt(row.get(8)));
	
				preparedStatement.addBatch();
				batchCount++;
				if(batchCount == BATCH_SIZE)
				{
					System.out.println("Adding " + batchCount + " rows to sprint_results");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to sprint_results");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("sprint_results populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertPrixResults(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
			line = "INSERT INTO prix_results (resultID, year, round, driverID, positionText, milliseconds, lapNum, fLapTime, fLapNum, statusID) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
					System.out.println("Adding " + batchCount + " rows to prix_results");
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to prix_results");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("prix_results populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertLapInfo(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to lap_info");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to lap_info");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("lap_info populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertPitStop(String file)
	{
		try(Statement statement = connection.createStatement();) {
			readFile(file);
			
			
			startTime = System.currentTimeMillis();
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
					System.out.println("Adding " + batchCount + " rows to pit_stop");
					preparedStatement.executeBatch();
					batchCount = 0;
				}
			
			}     
			System.out.println("Adding " + batchCount + " rows to pit_stop");
			preparedStatement.executeBatch();


			preparedStatement.close();
			endTime = System.currentTimeMillis();
			System.out.println("pit_stop populated after " + (endTime - startTime) + " milliseconds");
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}


}	
