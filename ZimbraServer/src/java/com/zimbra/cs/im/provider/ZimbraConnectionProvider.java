/*
 * 
 */
package com.zimbra.cs.im.provider;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;

import org.jivesoftware.database.ConnectionProvider;
import org.apache.commons.dbcp.DelegatingConnection;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbPool;

public class ZimbraConnectionProvider implements ConnectionProvider {

    public boolean isPooled() {
        return true;
    }
    
    public Connection getConnection() throws SQLException {
        try {
            DbPool.Connection conn = DbPool.getConnection();
            if (conn == null)
                return null;
            return new ZimbraConnection(conn);
        } catch (ServiceException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SQLException) 
                throw (SQLException)cause;
            throw new SQLException("Caught ServiceException: "+ex.toString());
        }
    }

    public void restart() {
    }
    public void start() {
    }
    public void destroy() {
    }
    
    private static class ZimbraConnection extends DelegatingConnection {
        private DbPool.Connection mConnection;
        
        ZimbraConnection(DbPool.Connection conn) {
            super(conn.getConnection());
            mConnection = conn;
        }

        public void close() throws SQLException {
            try {
                mConnection.close();
            } catch (ServiceException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof SQLException) 
                    throw (SQLException)cause;
                throw new SQLException("Caught ServiceException "+ex.toString());
            }
        }

        public void commit() throws SQLException {
            try {
                mConnection.commit();
            } catch (ServiceException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof SQLException) 
                    throw (SQLException)cause;
                throw new SQLException("Caught ServiceException "+ex.toString());
            }
        }

        public void rollback() throws SQLException {
            try {
                mConnection.rollback();
            } catch (ServiceException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof SQLException) 
                    throw (SQLException)cause;
                throw new SQLException("Caught ServiceException: "+ex);
            }
        }

        public void setTransactionIsolation(int level) throws SQLException {
            try {
                mConnection.setTransactionIsolation(level);
            } catch (ServiceException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof SQLException) 
                    throw (SQLException)cause;
                throw new SQLException("Caught ServiceException: "+ex);
            }
        }
    }
}
