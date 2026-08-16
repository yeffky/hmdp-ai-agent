import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 检查 tb_voucher_order 的唯一索引是否生效。
 * 用法: java -cp <mysql-connector.jar> CheckVoucherOrderIndex.java
 * 连接参数从环境变量读取: DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
 */
public class CheckVoucherOrderIndex {

    public static void main(String[] args) throws Exception {
        String host = env("DB_HOST", "<server-host>");
        String port = env("DB_PORT", "3307");
        String db = env("DB_NAME", "hmdp");
        String user = env("DB_USER", "root");
        String password = env("DB_PASSWORD", null);
        if (password == null) {
            System.err.println("缺少环境变量 DB_PASSWORD");
            System.exit(2);
        }

        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        Class.forName("com.mysql.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("== 连接成功: " + host + ":" + port + "/" + db + " ==");

            // 1. tb_voucher_order 上的全部索引
            System.out.println("\n== tb_voucher_order 索引清单 ==");
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, INDEX_TYPE "
                                 + "FROM information_schema.STATISTICS "
                                 + "WHERE TABLE_SCHEMA='" + db + "' AND TABLE_NAME='tb_voucher_order' "
                                 + "GROUP BY INDEX_NAME, NON_UNIQUE, INDEX_TYPE ORDER BY INDEX_NAME")) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  %-20s non_unique=%-3s type=%-8s cols=%s%n",
                            rs.getString("INDEX_NAME"),
                            rs.getInt("NON_UNIQUE"),
                            rs.getString("INDEX_TYPE"),
                            rs.getString("cols"));
                }
                if (!any) System.out.println("  (无索引)");
            }

            // 2. 检查 (user_id, voucher_id) 唯一索引是否存在
            System.out.println("\n== 唯一索引 (user_id, voucher_id) 检查 ==");
            String checkSql =
                    "SELECT COUNT(*) FROM information_schema.STATISTICS s "
                            + "WHERE s.TABLE_SCHEMA='" + db + "' AND s.TABLE_NAME='tb_voucher_order' "
                            + "AND s.NON_UNIQUE=0 AND s.COLUMN_NAME IN ('user_id','voucher_id') "
                            + "GROUP BY s.INDEX_NAME HAVING COUNT(DISTINCT s.COLUMN_NAME)=2";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(checkSql)) {
                if (rs.next()) {
                    System.out.println("  ✓ 已存在覆盖 (user_id, voucher_id) 的唯一索引");
                } else {
                    System.out.println("  ✗ 不存在覆盖 (user_id, voucher_id) 的唯一索引！");
                }
            }
        }
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
