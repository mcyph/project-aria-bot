// update codename ARIA - Administrate, Relay, Identify, Attest.
package com.github.echo2124

import java.net.URI
import java.util.Date
import java.util.HashMap
import java.sql.Timestamp
import java.text.SimpleDateFormat

import net.dv8tion.jda.api.EmbedBuilder
import com.github.echo2124.Main.constants.activityLog

class Database {
    private var DB_URL: String? = null
    private var USERNAME: String? = null
    private var PASSWORD: String? = null

    // this class is used for all instances of communication between db and application
    init {
        checkEnv()
        val tempConnection: Connection? = connect()
        if (!tableExists("CERT_MODULE", tempConnection)) {
            setupDB(tempConnection)
        }
        disconnect(tempConnection)
    }

    fun disconnect(connection: Connection?) {
        try {
            connection.close()
        } catch (e: SQLException) {
            System.out.println("Unable to disconnect from db")
        }
    }

    fun checkEnv() {
        try {
            val dbUri = URI(System.getenv("DATABASE_URL"))
            if (dbUri != null) {
                DB_URL = "jdbc:postgresql://" + dbUri.getHost() + ':' + dbUri.getPort() + dbUri.getPath() + "?sslmode=require"
                USERNAME = dbUri.getUserInfo().split(":").get(0)
                PASSWORD = dbUri.getUserInfo().split(":").get(1)
            } else {
                throw Exception()
            }
        } catch (e: Exception) {
            DB_URL = "localhost:5432/postgres"
            USERNAME = "postgres"
            PASSWORD = "2008"
        }
    }

    fun tableExists(tableName: String, conn: Connection?): Boolean {
        var found = false
        try {
            val databaseMetaData: DatabaseMetaData = conn.getMetaData()
            val rs: ResultSet = databaseMetaData.getTables(null, null, tableName, null)
            while (rs.next()) {
                val name: String = rs.getString("CERT_MODULE")
                if (tableName.equals(name)) {
                    found = true
                    break
                }
            }
        } catch (e: Exception) {
            System.out.println("Error thrown trying to see if table exists. Error: " + e.getMessage())
        }
        return found
    }

    fun connect(): Connection? {
        var connect: Connection? = null
        try {
            connect = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD)
        } catch (e: Exception) {
            e.printStackTrace()
            System.err.println(e.getClass().getName() + ": " + e.getMessage())
            System.exit(0)
        }
        activityLog.sendActivityMsg("[DATABASE] Connected Successfully", 1)
        System.out.println("Opened database successfully")
        return connect
    }

    fun setupDB(connect: Connection?) {
        // handles setting up database if blank
        try {
            // sanitisation not needed here as no inputs are received
            val stmt: Statement = connect.createStatement()
            val sqlQuery = """
                CREATE TABLE WARN_MODULE (
                    discordID bigint, 
                    issuerID bigint, 
                    warnDesc text, 
                    issueTime TIMESTAMP
                );
                CREATE TABLE CERT_MODULE (
                    discordID bigint, 
                    name VARCHAR(2048), 
                    emailAddr VARCHAR(100), 
                    isVerified bool, 
                    verifiedTime TIMESTAMP
                );
                CREATE TABLE NEWS (
                    origin VARCHAR(50), 
                    lastTitle text
                );
                CREATE TABLE EXPOSURE (
                    origin VARCHAR(50), 
                    len NUMERIC(15)
                );
                CREATE TABLE ONCAMPUS (
                    discordID bigint
                );
            """
            stmt.executeUpdate(sqlQuery)
            stmt.close()
        } catch (e: Exception) {
            if (!e.getMessage().contains("warn_module")) {
                System.err.println("Unable to setup DB " + e.getMessage())
                activityLog.sendActivityMsg("[DATABASE] Unable to setup DB", 2)
            }
        }
    }

    fun modifyDB(originModule: String?, action: String, data: HashMap) {
        var sqlQuery: PreparedStatement? = null
        val connection: Connection? = connect()
        val date = Date()
        val ts = Timestamp(date.getTime())
        when (originModule) {
            "CERT" -> if (action.equals("add")) {
                try {
                    activityLog.sendActivityMsg("[DATABASE] Inserting verify data into verify table", 1)
                    sqlQuery = connection.prepareStatement("INSERT INTO CERT_MODULE VALUES (?,?,?,?,?)")
                    sqlQuery.setLong(1, Long.parseLong(data.get("discordID").toString()))
                    sqlQuery.setString(2, data.get("name").toString())
                    sqlQuery.setString(3, data.get("emailAddr").toString())
                    sqlQuery.setBoolean(4, Boolean.parseBoolean(data.get("isVerified").toString()))
                    sqlQuery.setTimestamp(5, ts)
                } catch (e: Exception) {
                    System.out.println("Unable to Modify DB: " + e.getMessage())
                }
            }
            "NEWS" -> try {
                if (Boolean.parseBoolean(getDBEntry("NEWS_CHECK_CATEGORY", action))) {
                    sqlQuery = connection.prepareStatement("DELETE FROM NEWS WHERE origin=?")
                    sqlQuery.setString(1, action)
                    sqlQuery.execute()
                }
                activityLog.sendActivityMsg("[DATABASE] Updating news data in news table", 1)
                sqlQuery = connection.prepareStatement("INSERT INTO NEWS VALUES (?,?)")
                sqlQuery.setString(1, action)
                sqlQuery.setString(2, data.get("title").toString())
            } catch (e: Exception) {
                System.out.println("Unable to Modify DB: " + e.getMessage())
            }
            "EXPOSURE_SITE" -> try {
                activityLog.sendActivityMsg("[DATABASE] Inserting exposure data into exposure table", 1)
                sqlQuery = connection.prepareStatement("UPDATE exposure SET len=? WHERE origin='EXPOSURE_SITE'")
                sqlQuery.setInt(1, Integer.parseInt(data.get("size").toString()))
            } catch (e: Exception) {
                System.out.println("UNABLE TO MODIFY EXPOSURE_SITE MSG:" + e.getMessage())
            }
            else -> System.out.println("[DB] Invalid Origin Module")
        }
        try {
            if (sqlQuery != null) {
                sqlQuery.execute()
            }
            disconnect(connection)
            activityLog.sendActivityMsg("[DATABASE] Connection closed", 1)
        } catch (e: Exception) {
            activityLog.sendActivityMsg("[DATABASE] Failed to modify: " + e.getMessage(), 3)
            System.err.println(this.getClass().getName() + "Modify DB failed" + e.getMessage())
        }
    }

    fun getDBEntry(originModule: String?, req: String): String? {
        System.out.println("Grabbing DB Entry")
        var ret: String? = ""
        val sqlQuery: PreparedStatement
        val connection: Connection? = connect()
        try {
            when (originModule) {
                "CERT" -> {
                    activityLog.sendActivityMsg("[DATABASE] Fetching verify data from verify table", 1)
                    sqlQuery = connection.prepareStatement("SELECT * FROM CERT_MODULE WHERE discordID=?")
                    sqlQuery.setLong(1, Long.parseLong(req))
                    if (sqlQuery != null) {
                        val rs: ResultSet = sqlQuery.executeQuery()
                        System.out.println("Ran query")
                        // loop through the result set
                        while (rs.next()) {
                            ret = """
                                ${"Name: " + rs.getString(2)}
                                
                                """.trimIndent()
                            ret += """
                                ${"Verified Status: " + rs.getBoolean(4)}
                                
                                """.trimIndent()
                            ret += """
                                ${"Time of Verification: " + rs.getTimestamp(5)}
                                
                                """.trimIndent()
                        }
                        System.out.println("Query result: \n$req")
                        if (ret === "") {
                            ret = "No results found"
                        }
                    }
                }
                "NEWS_CHECK_CATEGORY" -> {
                    activityLog.sendActivityMsg("[DATABASE] Fetching news data from news table", 1)
                    sqlQuery = connection.prepareStatement("SELECT * FROM NEWS WHERE origin=?")
                    sqlQuery.setString(1, req)
                    if (sqlQuery != null) {
                        val rs: ResultSet = sqlQuery.executeQuery()
                        while (rs.next()) {
                            ret = rs.getString(1)
                        }
                        ret = if (ret!!.equals(req)) {
                            "true"
                        } else {
                            "false"
                        }
                        System.out.println("[Database] News Category Exists=$ret")
                    }
                }
                "NEWS_CHECK_LASTITLE" -> {
                    sqlQuery = connection.prepareStatement("SELECT * FROM NEWS WHERE origin=? AND lastTitle=?")
                    val parsed: Array<String> = req.split("##")
                    System.out.println("[Database] Split value origin: " + parsed[0])
                    System.out.println("[Database] Split value lastTitle: " + parsed[1])
                    sqlQuery.setString(1, parsed[0])
                    sqlQuery.setString(2, parsed[1])
                    if (sqlQuery != null) {
                        val rs: ResultSet = sqlQuery.executeQuery()
                        while (rs.next()) {
                            ret = rs.getString(2)
                        }
                        ret = if (ret!!.equals(parsed[1])) {
                            "true"
                        } else {
                            "false"
                        }
                        System.out.println("[Database] Last News Title Exists=$ret")
                    }
                }
                "CHECK_EXPOSURE_INDEX" -> {
                    activityLog.sendActivityMsg("[DATABASE] Fetching exposure data from exposure table", 1)
                    //TODO check for origin instead (there is probably an issue with the current method of checking for a table which is causing these sorts of problems that exist currently)
                    val rs: ResultSet = connection.prepareStatement("SELECT EXISTS ( SELECT FROM pg_tables WHERE tablename='exposure');").executeQuery()
                    while (rs.next()) {
                        if (rs.getBoolean(1)) {
                            System.out.println("[Database] checking db for exposure info")
                            sqlQuery = connection.prepareStatement("SELECT len FROM EXPOSURE WHERE origin=?")
                            sqlQuery.setString(1, req)
                            if (sqlQuery != null) {
                                val res: ResultSet = sqlQuery.executeQuery()
                                while (res.next()) {
                                    ret = String.valueOf(res.getInt(1))
                                }
                                if (ret == null || ret === "") {
                                    ret = "0"
                                }
                            }
                        } else {
                            System.out.println("[Database] exposure table doesn't exist. creating...")
                            // ADD TABLE TO DB (EXPOSURE)
                            connection.prepareStatement("CREATE TABLE EXPOSURE (origin VARCHAR(50), len NUMERIC(15));").executeQuery()
                            ret = "0"
                        }
                        break
                    }
                }
            }
        } catch (e: SQLException) {
            System.err.println(this.getClass().getName() + "Unable to get Entry" + e.getMessage())
        }
        activityLog.sendActivityMsg("[DATABASE] Connection closed", 1)
        disconnect(connection)
        return ret
    }
}