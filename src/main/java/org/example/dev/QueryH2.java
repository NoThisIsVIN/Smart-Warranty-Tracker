package org.example.dev;

import java.sql.*;

public class QueryH2 {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:./data/smartwarranty";
        System.out.println("Connecting to H2 URL: " + url);
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            DatabaseMetaData md = c.getMetaData();
            System.out.println("DB product: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            String tbl = "WARRANTY";
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT * FROM " + tbl);
                ResultSetMetaData rm = rs.getMetaData();
                int cols = rm.getColumnCount();
                while (rs.next()) {
                    System.out.println("--- Row ---");
                    for (int i = 1; i <= cols; i++) {
                        String name = rm.getColumnLabel(i);
                        Object val = rs.getObject(i);
                        System.out.println(name + " = " + val);
                    }
                }
            } catch (SQLException e) {
                System.out.println("Error querying table WARRANTY: " + e.getMessage());

                try (ResultSet tables = md.getTables(null, null, "%", null)) {
                    System.out.println("Tables available:");
                    while (tables.next()) {
                        System.out.println("  " + tables.getString("TABLE_NAME"));
                    }
                }
            }
        }
    }
}
