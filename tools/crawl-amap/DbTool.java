import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 远程 MySQL 工具：把 crawl-amap 生成的 dist/seed_amap.sql 按「名称+坐标」去重后追加入库。
 *
 * 用法（工作目录 = tools/crawl-amap）：
 *   java -cp ".;<mysql-connector-java-5.1.47.jar>" DbTool status   # 只读：DB 现状 + 与种子重叠检测
 *   java -cp ".;<mysql-connector-java-5.1.47.jar>" DbTool import   # 追加非重复的新店
 *
 * 连接参数从 hm-dianping/src/main/resources/application.yaml 运行时读取（不硬编码密码）。
 * 坐标按 6 位小数归一化做去重键（名称+经度+纬度），避免同一店重复入库。
 */
public class DbTool {

    private static final Path YAML = Paths.get("../../hm-dianping/src/main/resources/application.yaml");
    private static final Path SEED = Paths.get("dist/seed_amap.sql");
    private static final String COLS = "(`id`, `name`, `type_id`, `district_id`, `food_category`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`, `queue_enabled`, `description`, `has_parking`, `child_friendly`, `pet_friendly`, `max_seats`, `create_time`, `update_time`)";

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "status";
        Class.forName("com.mysql.jdbc.Driver");
        String url = yaml("url", "jdbc:mysql://<server-host>:3306/hmdp?useSSL=false&serverTimezone=UTC");
        String user = yaml("username", "root");
        String pass = yaml("password", "");
        if (!url.contains("characterEncoding")) url += (url.contains("?") ? "&" : "?") + "characterEncoding=UTF-8";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            switch (cmd) {
                case "status" -> status(conn);
                case "import" -> importSeed(conn);
                case "test" -> testInsert(conn);
                case "dump-shops" -> dumpShops(conn, args.length > 1 ? args[1] : "dist/shops_new.tsv");
                case "exec" -> {
                    if (args.length < 2) System.out.println("用法: DbTool exec <sqlfile>");
                    else exec(conn, args[1]);
                }
                case "stats" -> stats(conn);
                case "query" -> {
                    if (args.length < 2) System.out.println("用法: DbTool query <sql>");
                    else query(conn, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                }
                default -> System.out.println("用法: DbTool [status|import|test|dump-shops <out.tsv>|exec <sqlfile>|stats|query <sql>]");
            }
        }
    }

    /** 从 application.yaml 提取 spring.datasource 下某键的值（去除引号）。 */
    private static String yaml(String key, String def) throws IOException {
        String text = new String(Files.readAllBytes(YAML), StandardCharsets.UTF_8);
        int dsIdx = text.indexOf("datasource:");
        if (dsIdx < 0) return def;
        int endIdx = text.indexOf("hikari:");
        if (endIdx < 0 || endIdx < dsIdx) endIdx = text.length();
        String ds = text.substring(dsIdx, endIdx);
        for (String line : ds.split("\n")) {
            String t = line.trim();
            if (t.startsWith(key + ":")) {
                return t.substring(t.indexOf(':') + 1).trim().replaceAll("^['\"]|['\"]$", "");
            }
        }
        return def;
    }

    /** 解析 seed_amap.sql 为元组文本列表（尊重引号/括号）。 */
    private static List<String> parseTuples(String sql) {
        int idx = sql.indexOf("VALUES");
        if (idx < 0) throw new IllegalArgumentException("seed SQL 中未找到 VALUES");
        String body = sql.substring(idx + 6).replaceFirst(";\\s*$", "");
        List<String> tuples = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        char quote = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (inStr) { cur.append(ch); if (ch == quote) inStr = false; continue; }
            if (ch == '\'' || ch == '"') { inStr = true; quote = ch; cur.append(ch); continue; }
            if (ch == '(') { depth++; if (depth == 1) cur.setLength(0); else cur.append(ch); continue; }
            if (ch == ')') { depth--; if (depth == 0) { tuples.add(cur.toString()); continue; } cur.append(ch); continue; }
            cur.append(ch);
        }
        return tuples;
    }

    /** 按顶层逗号切分元组（字符串内逗号不切）。 */
    private static List<String> splitTop(String t) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        char quote = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (q) { cur.append(ch); if (ch == quote) q = false; continue; }
            if (ch == '\'' || ch == '"') { q = true; quote = ch; cur.append(ch); continue; }
            if (ch == ',') { out.add(cur.toString().trim()); cur.setLength(0); continue; }
            cur.append(ch);
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.equals("NULL")) return null;
        if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        return s;
    }

    private static String fmt6(double v) { return String.format("%.6f", v); }

    /** 种子行去重键：名称 + 6 位坐标。 */
    private static String seedKey(List<String> cols) {
        String name = unquote(cols.get(1));
        double x = Double.parseDouble(cols.get(8));
        double y = Double.parseDouble(cols.get(9));
        return name + "|" + fmt6(x) + "|" + fmt6(y);
    }

    /** 读取 DB 中既有店铺的键集合。 */
    private static Set<String> existingKeys(Connection conn) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, ROUND(x,6), ROUND(y,6) FROM tb_shop")) {
            while (rs.next()) keys.add(rs.getString(1) + "|" + fmt6(rs.getDouble(2)) + "|" + fmt6(rs.getDouble(3)));
        }
        return keys;
    }

    private static void status(Connection conn) throws Exception {
        List<String> tuples = parseTuples(new String(Files.readAllBytes(SEED), StandardCharsets.UTF_8));
        Set<String> existing = existingKeys(conn);

        try (Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM tb_shop");
            rs.next();
            System.out.println("DB tb_shop 总行数: " + rs.getInt(1));

            System.out.print("按 district_id 分布: ");
            try (ResultSet r2 = st.executeQuery("SELECT district_id, COUNT(*) FROM tb_shop GROUP BY district_id ORDER BY district_id")) {
                while (r2.next()) System.out.print(r2.getInt(1) + "=" + r2.getInt(2) + "  ");
            }
            System.out.println();

            try {
                ResultSet r3 = st.executeQuery("SELECT COUNT(*) FROM tb_shop WHERE food_category IS NOT NULL");
                r3.next();
                System.out.println("已有 food_category 的行数: " + r3.getInt(1));
            } catch (SQLException e) {
                System.out.println("⚠ food_category 列不存在（迁移未应用？）: " + e.getMessage());
            }
        }

        int overlap = 0;
        List<String> dupExample = new ArrayList<>();
        for (String t : tuples) {
            List<String> cols = splitTop(t);
            if (cols.size() != 23) continue;
            if (existing.contains(seedKey(cols))) {
                overlap++;
                if (dupExample.size() < 5) dupExample.add(unquote(cols.get(1)));
            }
        }
        System.out.println("\nseed_amap.sql 元组数: " + tuples.size());
        System.out.println("与 DB 重名/重坐标（会跳过）: " + overlap + " 家");
        System.out.println("实际可新增: " + (tuples.size() - overlap) + " 家");
        if (!dupExample.isEmpty()) System.out.println("重复示例: " + dupExample);
    }

    private static void importSeed(Connection conn) throws Exception {
        String sqlText = new String(Files.readAllBytes(SEED), StandardCharsets.UTF_8);
        List<String> tuples = parseTuples(sqlText);
        Set<String> existing = existingKeys(conn);

        List<String> keep = new ArrayList<>();
        for (String t : tuples) {
            List<String> cols = splitTop(t);
            if (cols.size() != 23) { System.out.println("跳过异常元组（列数 " + cols.size() + "）: " + t.substring(0, Math.min(60, t.length()))); continue; }
            if (!existing.contains(seedKey(cols))) keep.add(t);
        }
        if (keep.isEmpty()) { System.out.println("无新增店铺，跳过。"); return; }

        // 逐条插入：定位失败行，避免单条巨型语句整体失败
        int ok = 0;
        long first = -1, last = -1;
        boolean dumped = false;
        try (Statement st = conn.createStatement()) {
            for (String t : keep) {
                try {
                    boolean hasKey = st.execute("INSERT INTO `tb_shop` " + COLS + " VALUES (" + t + ");", Statement.RETURN_GENERATED_KEYS);
                    if (!hasKey) {
                        try (ResultSet keys = st.getGeneratedKeys()) {
                            while (keys.next()) { if (first < 0) first = keys.getLong(1); last = keys.getLong(1); }
                        }
                    }
                    ok++;
                } catch (SQLException e) {
                    String msg = e.getMessage().replaceAll("\\s+", " ").substring(0, Math.min(160, e.getMessage().length()));
                    System.out.println("✗ 插入失败: " + unquote(splitTop(t).get(1)) + "  " + msg);
                    if (!dumped) {
                        dumped = true;
                        Files.write(Paths.get("dist/first_failed.txt"), t.getBytes(StandardCharsets.UTF_8));
                        System.out.println("  首条失败元组已写入 dist/first_failed.txt（" + t.length() + " 字符）");
                    }
                }
            }
        }
        System.out.println("完成：成功 " + ok + " 家，失败 " + (keep.size() - ok) + " 家");
        if (first > 0) System.out.println("新 id 范围: " + first + " ~ " + last);
    }

    /** 隔离测试：定位是哪个字段/哪种字符导致 INSERT 语法错误（自动清理测试数据）。 */
    private static void testInsert(Connection conn) throws Exception {
        String[] cases = {
            "ascii:INSERT INTO `tb_shop` (`name`,`type_id`,`address`,`x`,`y`,`sold`,`comments`,`score`) VALUES ('TestShop',1,'addr',120.0,30.0,0,0,40)",
            "chinese:INSERT INTO `tb_shop` (`name`,`type_id`,`address`,`x`,`y`,`sold`,`comments`,`score`) VALUES ('测试中文店',1,'测试地址',120.0,30.0,0,0,40)",
            "placeholder:INSERT INTO `tb_shop` (`name`,`type_id`,`images`,`address`,`x`,`y`,`sold`,`comments`,`score`) VALUES ('占位图店',1,'data:image/svg+xml;utf8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%3C%2Fsvg%3E','addr',120.0,30.0,0,0,40)",
            "full:INSERT INTO `tb_shop` (`name`,`type_id`,`district_id`,`food_category`,`images`,`area`,`address`,`x`,`y`,`avg_price`,`sold`,`comments`,`score`,`open_hours`,`queue_enabled`,`description`,`has_parking`,`child_friendly`,`pet_friendly`,`max_seats`) VALUES ('完整测试店',1,1,'快餐小吃','data:image/svg+xml;utf8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%3C%2Fsvg%3E','上塘','测试地址',120.0,30.0,100,0,0,40,'10:00-22:00',1,'主打炸物小食，干净整洁。',0,0,0,4)"
        };
        try (Statement st = conn.createStatement()) {
            for (String c : cases) {
                String[] kv = c.split(":", 2);
                try {
                    st.executeUpdate(kv[1]);
                    System.out.println("✓ " + kv[0] + "  成功");
                } catch (SQLException e) {
                    System.out.println("✗ " + kv[0] + "  失败: " + e.getMessage().replaceAll("\\s+", " ").substring(0, Math.min(140, e.getMessage().length())));
                }
            }
            st.executeUpdate("DELETE FROM `tb_shop` WHERE `name` IN ('TestShop','测试中文店','占位图店','完整测试店')");
            System.out.println("已清理测试数据");
        }
    }

    /** 导出新店（id>314，即本轮爬虫追加的）为 tsv：id\ttype_id\tname\t首图\taddress */
    private static void dumpShops(Connection conn, String outFile) throws Exception {
        StringBuilder sb = new StringBuilder();
        long maxVoucher = 0, maxComment = 0;
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT id, type_id, name, SUBSTRING_INDEX(images,',',1), address FROM tb_shop WHERE id > 314 ORDER BY id")) {
                while (rs.next()) {
                    sb.append(rs.getLong(1)).append('\t')
                      .append(rs.getLong(2)).append('\t')
                      .append(rs.getString(3) == null ? "" : rs.getString(3)).append('\t')
                      .append(rs.getString(4) == null ? "" : rs.getString(4)).append('\t')
                      .append(rs.getString(5) == null ? "" : rs.getString(5)).append('\n');
                }
            }
            try (ResultSet r = st.executeQuery("SELECT COALESCE(MAX(id),0) FROM tb_voucher")) { r.next(); maxVoucher = r.getLong(1); }
            try (ResultSet r = st.executeQuery("SELECT COALESCE(MAX(id),0) FROM tb_shop_comment")) { r.next(); maxComment = r.getLong(1); }
        }
        Files.write(Paths.get(outFile), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("已导出新店 tsv → " + outFile);
        System.out.println("tb_voucher 当前 MAX(id) = " + maxVoucher);
        System.out.println("tb_shop_comment 当前 MAX(id) = " + maxComment);
    }

    /** 执行一个 SQL 文件（按顶层分号拆分语句，跳过 -- 注释），用于导入种子 SQL。 */
    private static void exec(Connection conn, String sqlFile) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(sqlFile)), StandardCharsets.UTF_8);
        text = text.replaceAll("(?m)^--.*$", "");   // 去掉 -- 注释行
        List<String> stmts = splitStatements(text);
        int ok = 0;
        try (Statement st = conn.createStatement()) {
            for (String s : stmts) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                try {
                    int n = st.executeUpdate(t);
                    System.out.println("✓ 影响 " + n + " 行: " + t.replaceAll("\\s+", " ").substring(0, Math.min(60, t.length())));
                    ok++;
                } catch (SQLException e) {
                    System.out.println("✗ 失败: " + e.getMessage().replaceAll("\\s+", " ").substring(0, Math.min(180, e.getMessage().length())));
                }
            }
        }
        System.out.println("完成：成功 " + ok + " / " + stmts.size() + " 条语句");
    }

    /** 打印各表计数，核验种子数据。 */
    private static void stats(Connection conn) throws Exception {
        String[][] qs = {
            {"tb_shop 总行数", "SELECT COUNT(*) FROM tb_shop"},
            {"tb_shop 带 food_category", "SELECT COUNT(*) FROM tb_shop WHERE food_category IS NOT NULL"},
            {"tb_voucher 总行数", "SELECT COUNT(*) FROM tb_voucher"},
            {"tb_voucher id 范围", "SELECT CONCAT(MIN(id),'-',MAX(id)) FROM tb_voucher"},
            {"tb_seckill_voucher 秒杀数", "SELECT COUNT(*) FROM tb_seckill_voucher"},
            {"tb_shop_comment 总行数", "SELECT COUNT(*) FROM tb_shop_comment"},
            {"新店(id>314) 评价数分布(最小-最大)", "SELECT CONCAT(MIN(c),'-',MAX(c)) FROM (SELECT COUNT(*) c FROM tb_shop_comment WHERE shop_id>314 GROUP BY shop_id) t"},
            {"新店 带团购券数", "SELECT COUNT(DISTINCT shop_id) FROM tb_voucher WHERE shop_id>314"}
        };
        try (Statement st = conn.createStatement()) {
            for (String[] q : qs) {
                try (ResultSet rs = st.executeQuery(q[1])) {
                    rs.next();
                    System.out.println(q[0] + ": " + rs.getString(1));
                } catch (SQLException e) {
                    System.out.println(q[0] + ": ERROR " + e.getMessage().replaceAll("\\s+", " ").substring(0, Math.min(120, e.getMessage().length())));
                }
            }
        }
    }

    /** 执行单条 SELECT 并打印结果（制表符分隔）。 */
    private static void query(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) sb.append('\t');
                    sb.append(rs.getString(i));
                }
                System.out.println(sb);
            }
        } catch (SQLException e) {
            System.out.println("查询失败: " + e.getMessage().replaceAll("\\s+", " ").substring(0, Math.min(180, e.getMessage().length())));
        }
    }

    /** 按顶层分号拆分语句（字符串内分号不拆，'' 转义撇号正确处理）。 */
    private static List<String> splitStatements(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (q) {
                cur.append(c);
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') { cur.append(text.charAt(i + 1)); i++; }
                    else q = false;
                }
                continue;
            }
            if (c == '\'') { q = true; cur.append(c); continue; }
            if (c == ';') { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        if (cur.toString().trim().length() > 0) out.add(cur.toString());
        return out;
    }
}
