/*
 * 
 */
package org.jivesoftware.database;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.IMConfig;
import org.jivesoftware.util.Log;

import java.io.*;
import java.sql.*;

/**
 * Central manager of database connections. All methods are static so that they
 * can be easily accessed throughout the classes in the database package.<p>
 *
 * This class also provides a set of utility methods that abstract out
 * operations that may not work on all databases such as setting the max number
 * or rows that a query should return.
 *
 * @author Jive Software
 * @see ConnectionProvider
 */
public class DbConnectionManager {

    private static ConnectionProvider connectionProvider;
    private static final Object providerLock = new Object();

    // True if connection profiling is turned on. Always false by default.
    private static boolean profilingEnabled = false;

    // True if the database support transactions.
    private static boolean transactionsSupported;
    // True if the database requires large text fields to be streamed.
    private static boolean streamTextRequired;
    // True if the database supports the Statement.setMaxRows() method.
    private static boolean maxRowsSupported;
    // True if the database supports the Statement.setFetchSize() method.
    private static boolean fetchSizeSupported;
    // True if the database supports correlated subqueries.
    private static boolean subqueriesSupported;
    // True if the database supports scroll-insensitive results.
    private static boolean scrollResultsSupported;
    // True if the database supports batch updates.
    private static boolean batchUpdatesSupported;

    private static DatabaseType databaseType = DatabaseType.unknown;

    /**
     * Returns a database connection from the currently active connection
     * provider. (auto commit is set to true).
     */
    public static Connection getConnection() throws SQLException {
        if (connectionProvider == null) {
            synchronized (providerLock) {
                if (connectionProvider == null) {
                    // Attempt to load the connection provider classname as
                    // a Jive property.
                    String className = IMConfig.CONNECTION_PROVIDER_CLASSNAME.getString(); 
                    if (className != null) {
                        // Attempt to load the class.
                        try {
                            Class conClass = ClassUtils.forName(className);
                            setConnectionProvider((ConnectionProvider)conClass.newInstance());
                        }
                        catch (Exception e) {
                            Log.error("Error: failed to create the " +
                                    "connection provider specified by connection" +
                                    "Provider.className. Using the default pool.", e);
                        }
                    }
                    else {
                        Log.error("Error: could not find the connectionProvider.className entry, please fix wildfire.xml");
                    }
                }
            }
        }
        Connection con = connectionProvider.getConnection();

        if (con == null) {
            Log.error("WARNING: ConnectionManager.getConnection() " +
                    "failed to obtain a connection.");
        }
        // See if profiling is enabled. If yes, wrap the connection with a
        // profiled connection.
        if (profilingEnabled) {
            return (Connection) new ProfiledConnection(con);
        }
        else {
            return con;
        }
    }

    /**
     * Returns a Connection from the currently active connection provider that
     * is ready to participate in transactions (auto commit is set to false).
     */
    public static Connection getTransactionConnection() throws SQLException {
        Connection con = getConnection();
        if (isTransactionsSupported()) {
            con.setAutoCommit(false);
        }
        return con;
    }

    /**
     * Closes a PreparedStatement and Connection. However, it first rolls back the transaction or
     * commits it depending on the value of <code>abortTransaction</code>.
     */
    public static void closeTransactionConnection(PreparedStatement pstmt, Connection con,
            boolean abortTransaction)
    {
        try {
            if (pstmt != null) {
                pstmt.close();
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        closeTransactionConnection(con, abortTransaction);
    }

    /**
     * Closes a Connection. However, it first rolls back the transaction or
     * commits it depending on the value of <code>abortTransaction</code>.
     */
    public static void closeTransactionConnection(Connection con, boolean abortTransaction) {
        // test to see if the connection passed in is null
        if (con == null) {
            return;
        }

        // Rollback or commit the transaction
        if (isTransactionsSupported()) {
            try {
                if (abortTransaction) {
                    con.rollback();
                }
                else {
                    con.commit();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        try {
            // Reset the connection to auto-commit mode.
            if (isTransactionsSupported()) {
                con.setAutoCommit(true);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        try {
            // Close the db connection.
            con.close();
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Closes a result set. This method should be called within the finally section of
     * your database logic, as in the following example:
     *
     * <pre>
     *  public void doSomething(Connection con) {
     *      ResultSet rs = null;
     *      PreparedStatement pstmt = null;
     *      try {
     *          pstmt = con.prepareStatement("select * from blah");
     *          rs = pstmt.executeQuery();
     *          ....
     *      }
     *      catch (SQLException sqle) {
     *          Log.error(sqle);
     *      }
     *      finally {
     *          ConnectionManager.closeResultSet(rs);
     *          ConnectionManager.closePreparedStatement(pstmt);
     *      }
     * } </pre>
     */
    public static void closeResultSet(ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
    }

    /**
     * Closes a statement. This method should be called within the finally section of
     * your database logic, as in the following example:
     *
     * <pre>
     *  public void doSomething(Connection con) {
     *      PreparedStatement pstmt = null;
     *      try {
     *          pstmt = con.prepareStatement("select * from blah");
     *          ....
     *      }
     *      catch (SQLException sqle) {
     *          Log.error(sqle);
     *      }
     *      finally {
     *          ConnectionManager.closePreparedStatement(pstmt);
     *      }
     * } </pre>
     *
     * @param stmt the statement.
     */
    public static void closeStatement(Statement stmt) {
        try {
            if (stmt != null) {
                stmt.close();
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Closes a result set, statement and database connection (returning the connection to
     * the connection pool). This method should be called within the finally section of
     * your database logic, as in the following example:
     *
     * <pre>
     * Connection con = null;
     * PrepatedStatment pstmt = null;
     * ResultSet rs = null;
     * try {
     *     con = ConnectionManager.getConnection();
     *     pstmt = con.prepareStatement("select * from blah");
     *     rs = psmt.executeQuery();
     *     ....
     * }
     * catch (SQLException sqle) {
     *     Log.error(sqle);
     * }
     * finally {
     *     ConnectionManager.closeConnection(rs, pstmt, con);
     * }</pre>
     *
     * @param stmt the statement.
     * @param con the connection.
     */
    public static void closeConnection(ResultSet rs, Statement stmt, Connection con) {
        closeResultSet(rs);
        closeStatement(stmt);
        closeConnection(con);
    }

    /**
     * Closes a statement and database connection (returning the connection to
     * the connection pool). This method should be called within the finally section of
     * your database logic, as in the following example:
     * <p/>
     * <pre>
     * Connection con = null;
     * PrepatedStatment pstmt = null;
     * try {
     *     con = ConnectionManager.getConnection();
     *     pstmt = con.prepareStatement("select * from blah");
     *     ....
     * }
     * catch (SQLException sqle) {
     *     Log.error(sqle);
     * }
     * finally {
     *     DbConnectionManager.closeConnection(pstmt, con);
     * }</pre>
     *
     * @param stmt the statement.
     * @param con the connection.
     */
    public static void closeConnection(Statement stmt, Connection con) {
        try {
            if (stmt != null) {
                stmt.close();
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        closeConnection(con);
    }

    /**
     * Closes a database connection (returning the connection to the connection pool). Any
     * statements associated with the connection should be closed before calling this method.
     * This method should be called within the finally section of your database logic, as
     * in the following example:
     * <p/>
     * <pre>
     * Connection con = null;
     * try {
     *     con = ConnectionManager.getConnection();
     *     ....
     * }
     * catch (SQLException sqle) {
     *     Log.error(sqle);
     * }
     * finally {
     *     DbConnectionManager.closeConnection(con);
     * }</pre>
     *
     * @param con the connection.
     */
    public static void closeConnection(Connection con) {
        try {
            if (con != null) {
                con.close();
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Creates a scroll insensitive Statement if the JDBC driver supports it, or a normal
     * Statement otherwise.
     *
     * @param con the database connection.
     * @return a Statement
     * @throws SQLException if an error occurs.
     */
    public static Statement createScrollableStatement(Connection con) throws SQLException {
        if (isScrollResultsSupported()) {
            return con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
        }
        else {
            return con.createStatement();
        }
    }

    /**
     * Creates a scroll insensitive PreparedStatement if the JDBC driver supports it, or a normal
     * PreparedStatement otherwise.
     *
     * @param con the database connection.
     * @param sql the SQL to create the PreparedStatement with.
     * @return a PreparedStatement
     * @throws java.sql.SQLException if an error occurs.
     */
    public static PreparedStatement createScrollablePreparedStatement(Connection con, String sql)
            throws SQLException {
        if (isScrollResultsSupported()) {
            return con.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
        }
        else {
            return con.prepareStatement(sql);
        }
    }

    /**
     * Scrolls forward in a result set the specified number of rows. If the JDBC driver
     * supports the feature, the cursor will be moved directly. Otherwise, we scroll
     * through results one by one manually by calling <tt>rs.next()</tt>.
     *
     * @param rs the ResultSet object to scroll.
     * @param rowNumber the row number to scroll forward to.
     * @throws SQLException if an error occurs.
     */
    public static void scrollResultSet(ResultSet rs, int rowNumber) throws SQLException {
        // If the driver supports scrollable result sets, use that feature.
        if (isScrollResultsSupported()) {
            if (rowNumber > 0) {
                rs.setFetchDirection(ResultSet.FETCH_FORWARD);

                // We will attempt to do a relative fetch. This may fail in SQL Server if
                // <resultset-navigation-strategy> is set to absolute. It would need to be
                // set to looping to work correctly.
                // If so, manually scroll to the correct row.
                try {
                    rs.relative(rowNumber);
                }
                catch (SQLException e) {
                    for (int i = 0; i < rowNumber; i++) {
                        rs.next();
                    }
                }
            }
        }
        // Otherwise, manually scroll to the correct row.
        else {
            for (int i = 0; i < rowNumber; i++) {
                rs.next();
            }
        }
    }

    /**
     * Returns the current connection provider. The only case in which this
     * method should be called is if more information about the current
     * connection provider is needed. Database connections should always be
     * obtained by calling the getConnection method of this class.
     */
    public static ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    /**
     * Sets the connection provider. The old provider (if it exists) is shut
     * down before the new one is started. A connection provider <b>should
     * not</b> be started before being passed to the connection manager
     * because the manager will call the start() method automatically.
     *
     * @param provider the ConnectionProvider that the manager should obtain
     *                 connections from.
     */
    public static void setConnectionProvider(ConnectionProvider provider) {
        synchronized (providerLock) {
            if (connectionProvider != null) {
                connectionProvider.destroy();
                connectionProvider = null;
            }
            connectionProvider = provider;
            connectionProvider.start();
            // Now, get a connection to determine meta data.
            Connection con = null;
            try {
                con = connectionProvider.getConnection();
                setMetaData(con);
            }
            catch (Exception e) {
                Log.error(e);
            }
            finally {
                try {
                    if (con != null) {
                        con.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
        }
    }

    /**
     * Destroys the currennt connection provider. Future calls to
     * {@link #getConnectionProvider()} will return <tt>null</tt> until a new
     * ConnectionProvider is set, or one is automatically loaded by a call to
     * {@link #getConnection()}.
     */
    public static void destroyConnectionProvider() {
        synchronized (providerLock) {
            if (connectionProvider != null) {
                connectionProvider.destroy();
                connectionProvider = null;
            }
        }
    }

    /**
     * Retrives a large text column from a result set, automatically performing
     * streaming if the JDBC driver requires it. This is necessary because
     * different JDBC drivers have different capabilities and methods for
     * retrieving large text values.
     *
     * @param rs          the ResultSet to retrieve the text field from.
     * @param columnIndex the column in the ResultSet of the text field.
     * @return the String value of the text field.
     */
    public static String getLargeTextField(ResultSet rs, int columnIndex) throws SQLException {
        if (isStreamTextRequired()) {
            Reader bodyReader = null;
            String value = null;
            try {
                bodyReader = rs.getCharacterStream(columnIndex);
                if (bodyReader == null) {
                    return null;
                }
                char[] buf = new char[256];
                int len;
                StringWriter out = new StringWriter(256);
                while ((len = bodyReader.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }
                value = out.toString();
                out.close();
            }
            catch (Exception e) {
                Log.error(e);
                throw new SQLException("Failed to load text field");
            }
            finally {
                try {
                    if (bodyReader != null) {
                        bodyReader.close();
                    }
                }
                catch (Exception e) {
                    // Ignore.
                }
            }
            return value;
        }
        else {
            return rs.getString(columnIndex);
        }
    }

    /**
     * Sets a large text column in a result set, automatically performing
     * streaming if the JDBC driver requires it. This is necessary because
     * different JDBC drivers have different capabilities and methods for
     * setting large text values.
     *
     * @param pstmt the PreparedStatement to set the text field in.
     * @param parameterIndex the index corresponding to the text field.
     * @param value the String to set.
     */
    public static void setLargeTextField(PreparedStatement pstmt, int parameterIndex,
                                         String value) throws SQLException {
        if (isStreamTextRequired()) {
            Reader bodyReader;
            try {
                bodyReader = new StringReader(value);
                pstmt.setCharacterStream(parameterIndex, bodyReader, value.length());
            }
            catch (Exception e) {
                Log.error(e);
                throw new SQLException("Failed to set text field.");
            }
            // Leave bodyReader open so that the db can read from it. It *should*
            // be garbage collected after it's done without needing to call close.
        }
        else {
            pstmt.setString(parameterIndex, value);
        }
    }

    /**
     * Sets the max number of rows that should be returned from executing a
     * statement. The operation is automatically bypassed if Jive knows that the
     * the JDBC driver or database doesn't support it.
     *
     * @param stmt    the Statement to set the max number of rows for.
     * @param maxRows the max number of rows to return.
     */
    public static void setMaxRows(Statement stmt, int maxRows) {
        if (isMaxRowsSupported()) {
            try {
                stmt.setMaxRows(maxRows);
            }
            catch (Throwable t) {
                // Ignore. Exception may happen if the driver doesn't support
                // this operation and we didn't set meta-data correctly.
                // However, it is a good idea to update the meta-data so that
                // we don't have to incur the cost of catching an exception
                // each time.
                maxRowsSupported = false;
            }
        }
    }

    /**
     * Sets the number of rows that the JDBC driver should buffer at a time.
     * The operation is automatically bypassed if Jive knows that the
     * the JDBC driver or database doesn't support it.
     *
     * @param rs the ResultSet to set the fetch size for.
     * @param fetchSize the fetchSize.
     */
    public static void setFetchSize(ResultSet rs, int fetchSize) {
        if (isFetchSizeSupported()) {
            try {
                rs.setFetchSize(fetchSize);
            }
            catch (Throwable t) {
                // Ignore. Exception may happen if the driver doesn't support
                // this operation and we didn't set meta-data correctly.
                // However, it is a good idea to update the meta-data so that
                // we don't have to incur the cost of catching an exception
                // each time.
                fetchSizeSupported = false;
            }
        }
    }

    /**
     * Uses a connection from the database to set meta data information about
     * what different JDBC drivers and databases support.
     */
    private static void setMetaData(Connection con) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        // Supports transactions?
        transactionsSupported = metaData.supportsTransactions();
        // Supports subqueries?
        subqueriesSupported = metaData.supportsCorrelatedSubqueries();
        // Supports scroll insensitive result sets? Try/catch block is a
        // workaround for DB2 JDBC driver, which throws an exception on
        // the method call.
        try {
            scrollResultsSupported = metaData.supportsResultSetType(
                    ResultSet.TYPE_SCROLL_INSENSITIVE);
        }
        catch (Exception e) {
            scrollResultsSupported = false;
        }
        // Supports batch updates
        batchUpdatesSupported = metaData.supportsBatchUpdates();

        // Set defaults for other meta properties
        streamTextRequired = false;
        maxRowsSupported = true;
        fetchSizeSupported = true;

        // Get the database name so that we can perform meta data settings.
        String dbName = metaData.getDatabaseProductName().toLowerCase();
        String driverName = metaData.getDriverName().toLowerCase();

        // Oracle properties.
        if (dbName.indexOf("oracle") != -1) {
            databaseType = DatabaseType.oracle;
            streamTextRequired = true;
            scrollResultsSupported = false;
            // The i-net AUGURO JDBC driver
            if (driverName.indexOf("auguro") != -1) {
                streamTextRequired = false;
                fetchSizeSupported = true;
                maxRowsSupported = false;
            }
        }
        // Postgres properties
        else if (dbName.indexOf("postgres") != -1) {
            databaseType = DatabaseType.postgresql;
            // Postgres blows, so disable scrolling result sets.
            scrollResultsSupported = false;
            fetchSizeSupported = false;
        }
        // Interbase properties
        else if (dbName.indexOf("interbase") != -1) {
            databaseType = DatabaseType.interbase;
            fetchSizeSupported = false;
            maxRowsSupported = false;
        }
        // SQLServer
        else if (dbName.indexOf("sql server") != -1) {
            databaseType = DatabaseType.sqlserver;
            // JDBC driver i-net UNA properties
            if (driverName.indexOf("una") != -1) {
                fetchSizeSupported = true;
                maxRowsSupported = false;
            }
        }
        // MySQL properties
        else if (dbName.indexOf("mysql") != -1) {
            databaseType = DatabaseType.mysql;
            transactionsSupported = false;
        }
        // HSQL properties
        else if (dbName.indexOf("hsql") != -1) {
            databaseType = DatabaseType.hsqldb;
            scrollResultsSupported = false;
        }
        // DB2 properties.
        else if (dbName.indexOf("db2") != 1) {
            databaseType = DatabaseType.db2;
        }
    }

    /**
     * Returns the database type. The possible types are constants of the
     * DatabaseType class. Any database that doesn't have its own constant
     * falls into the "Other" category.
     *
     * @return the database type.
     */
    public static DatabaseType getDatabaseType() {
        return databaseType;
    }

    /**
     * Returns true if connection profiling is turned on. You can collect
     * profiling statistics by using the static methods of the ProfiledConnection
     * class.
     *
     * @return true if connection profiling is enabled.
     */
    public static boolean isProfilingEnabled() {
        return profilingEnabled;
    }

    /**
     * Turns connection profiling on or off. You can collect profiling
     * statistics by using the static methods of the ProfiledConnection
     * class.
     *
     * @param enable true to enable profiling; false to disable.
     */
    public static void setProfilingEnabled(boolean enable) {
        // If enabling profiling, call the start method on ProfiledConnection
        if (!profilingEnabled && enable) {
            ProfiledConnection.start();
        }
        // Otherwise, if turning off, call stop method.
        else if (profilingEnabled && !enable) {
            ProfiledConnection.stop();
        }
        profilingEnabled = enable;
    }

    public static boolean isTransactionsSupported() {
        return transactionsSupported;
    }

    public static boolean isStreamTextRequired() {
        return streamTextRequired;
    }

    public static boolean isMaxRowsSupported() {
        return maxRowsSupported;
    }

    public static boolean isFetchSizeSupported() {

        return fetchSizeSupported;
    }

    public static boolean isSubqueriesSupported() {
        return subqueriesSupported;
    }

    public static boolean isScrollResultsSupported() {
        return scrollResultsSupported;
    }

    public static boolean isBatchUpdatesSupported() {
        return batchUpdatesSupported;
    }

    /**
     * A class that identifies the type of the database that Jive is connected
     * to. In most cases, we don't want to make any database specific calls
     * and have no need to know the type of database we're using. However,
     * there are certain cases where it's critical to know the database for
     * performance reasons.
     */
    public static enum DatabaseType {

        oracle,

        postgresql,

        mysql,

        hsqldb,

        db2,

        sqlserver,

        interbase,

        unknown
    }

    private DbConnectionManager() {
        // Not instantiable.
    }
}