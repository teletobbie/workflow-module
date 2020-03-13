package com.vaadin;

import java.sql.*;

public class Database {
    private Connection connection = null;

    public void start() {
        try {
            String serverTimezone = "?serverTimezone=UTC"; //needs to be configured because mysql only works with UTC timezone
            String url = "jdbc:mysql://localhost:3306/rainbow"+ serverTimezone;
            String username = "root";
            String password = "root";
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection(url, username, password);
        }
        catch (SQLException | ClassNotFoundException sqlEx) {
            sqlEx.printStackTrace();
        }
    }

    public ResultSet queryStatement(String query) {
        ResultSet resultSet = null;
        try {
            Statement statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
        return resultSet;
    }

    public void stop() {
        try {
            if(connection != null) {
                connection.close();
            }
        }
        catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }

    }

}
