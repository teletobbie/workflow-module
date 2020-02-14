package com.vaadin;


import java.sql.*;
import java.util.HashMap;

public class Db {
    private Connection connection = null;

    public void start() {
        try {
            //jdbc:mysql://localhost:3306/
            String url = "jdbc:mysql://localhost:3306/rainbow";
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
