/*
 * Copyright (c) 2020. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

package com.tools;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 司徒彬
 * @date 2020/6/21 16:19
 */
@Component
public class CreatePojo implements CommandLineRunner {
    public static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");
    List<String> pojo;
    List<String> column;
    Map<String, String> dataType;
    String root_path;
    Config config;

    @Override
    public void run(String... args) {
        root_path = System.getProperty("user.dir");
        if (args.length != 0) {
            root_path = args[0];
        }
        try {
            /**
             * bigint
             * varchar
             * datetime
             * decimal
             * bit
             * int
             * double
             */

            dataType = new HashMap<>();

            dataType.put("bigint", "Long");
            dataType.put("varchar", "String");
            dataType.put("tinytext", "String");
            dataType.put("longtext", "String");
            dataType.put("char", "String");
            dataType.put("datetime", "java.sql.Timestamp");
            dataType.put("decimal", "java.math.BigDecimal");
            dataType.put("bit", "Boolean");
            dataType.put("int", "Integer");
            dataType.put("integer", "Integer");
            dataType.put("tinyint", "Integer");
            dataType.put("double", "Double");
            dataType.put("float", "Float");

            config = getConfig(root_path);


            pojo = getPojoTemplate();
            column = getColumnTemplate();
            for (schema schema : config.getSchemas()) {
                write(schema);
            }
            System.out.println("finished");
        } catch (Exception ex) {

        }
    }

    private void write(schema schemaConfig) {
        try {
            System.out.println(schemaConfig.toString());
            String ip = schemaConfig.getIp();
            String port = schemaConfig.getPort();
            String schema = schemaConfig.getSchema();
            String user = schemaConfig.getSchema();
            String password = schemaConfig.getPassword();
            String packageName = schemaConfig.getPackageName();

            Assert.hasText(ip, "ip 必须配置");
            Assert.hasText(port, "port 必须配置");
            Assert.hasText(schema, "schema 必须配置");
            Assert.hasText(user, "user 必须配置");
            Assert.hasText(password, "password 必须配置");
            Assert.hasText(packageName, "package 必须配置");

            List<Map<String, Object>> schemaInfo = getSchemaInfo(schemaConfig);

            schemaInfo.stream()
                    .collect(Collectors.groupingBy(map -> map.get("TABLE_NAME").toString()))
                    .entrySet()
                    .stream()
                    .forEach(entry -> {
                        String TABLE_NAME = toString(entry.getValue().get(0).get("TABLE_NAME"));
                        String java_name = getClassName(TABLE_NAME);
                        String path = schemaConfig.getPackageName().replace(".", "/");
                        path = root_path.concat("/src/main/java/").concat(path).concat("/");

                        List<String> content = getPojo(schemaConfig, entry);
                        try {
                            Files.createDirectories(Paths.get(path));
                            path = path.concat(java_name).concat(".java");
                            Files.write(Paths.get(path), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                            System.out.println("created:" + path);
                        } catch (IOException e) {
                            System.err.println("error:" + path);
                        }

                    });
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
        } catch (SQLException ex) {
            System.err.println(ex.getMessage());
        } catch (ClassNotFoundException ex) {
            System.err.println(ex.getMessage());
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }

    }

    private List<String> getPojo(schema schemaConfig, Map.Entry<String, List<Map<String, Object>>> entry) {
        String userName = System.getProperty("user.name");

        Map<String, Object> pojoSetting = entry.getValue().get(0);
        String TABLE_NAME = toString(pojoSetting.get("TABLE_NAME"));
        String java_name = getClassName(TABLE_NAME);
        String TABLE_COMMENT = toString(pojoSetting.get("TABLE_COMMENT"));
        List<String> content = pojo.stream().map(p -> {
            String text = new String(p.getBytes());
            text = text.replace("${package}", schemaConfig.getPackageName());
            text = text.replace("${table_comment}", TABLE_COMMENT);
            text = text.replace("${author}", userName);
            text = text.replace("${date}", new Timestamp(System.currentTimeMillis()).toString());
            text = text.replace("${table_name}", TABLE_NAME);
            text = text.replace("${java_name}", java_name);
            return text;
        }).collect(Collectors.toList());

        List<String> columns = new ArrayList<>();
        for (Map<String, Object> column : entry.getValue()) {
            String key = TABLE_NAME.concat(":") + column.get("COLUMN_NAME");
            if (columns.contains(key)) {
                continue;
            }
            columns.add(key);
            content.addAll(getColumn(column));
        }
        content.add("}");
        return content;
    }

    private List<String> getColumn(Map<String, Object> row) {
        String COLUMN_NAME = toString(row.get("COLUMN_NAME"));
        String COLUMN_KEY = toString(row.get("COLUMN_KEY"));
        String DATA_TYPE = toString(row.get("DATA_TYPE"));
        String IS_NULLABLE = toString(row.get("IS_NULLABLE"));
        String CHARACTER_MAXIMUM_LENGTH = toString(row.get("CHARACTER_MAXIMUM_LENGTH"));
        String NUMERIC_PRECISION = toString(row.get("NUMERIC_PRECISION"));
        String NUMERIC_SCALE = toString(row.get("NUMERIC_SCALE"));
        String COLUMN_COMMENT = toString(row.get("COLUMN_COMMENT"));
        String EXTRA = toString(row.get("EXTRA"));

        String data_type;
        Optional<Map.Entry<String, String>> first = dataType.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(DATA_TYPE))
                .findFirst();
        if (!Optional.empty().equals(first)) {
            data_type = first.get().getValue();
        } else {
            data_type = "String";
        }
        //@Id @Basic
        String primary_id = COLUMN_KEY.equalsIgnoreCase("pri") ? "@Id" : "@Basic";
        // @Column(name = "merchantId", nullable = false, precision = 1, scale = 2, length = 160)
        List<String> columnSetting = new ArrayList<>();
        columnSetting.add("name = \"".concat(COLUMN_NAME).concat("\""));
        IS_NULLABLE = IS_NULLABLE.equals("YES") ? "true" : "false";
        columnSetting.add(" nullable = ".concat(IS_NULLABLE));
        if (data_type.equalsIgnoreCase("java.math.BigDecimal")) {
            if (NUMERIC_PRECISION.length() != 0) {
                columnSetting.add(" precision = ".concat(NUMERIC_PRECISION));
            }
            if (NUMERIC_SCALE.length() != 0) {
                columnSetting.add(" scale = ".concat(NUMERIC_SCALE));
            }
        }
        if (COLUMN_KEY.equalsIgnoreCase("UNI")) {
            columnSetting.add(" unique = true");
        } else {
            columnSetting.add(" unique = false");
        }

        if (data_type.equalsIgnoreCase("string") && DATA_TYPE.contains("char")) {
            columnSetting.add(" length = ".concat(CHARACTER_MAXIMUM_LENGTH));
        }
        String column_setting = columnSetting.stream().collect(Collectors.joining(","));

        String generated_value;
        if (EXTRA.equalsIgnoreCase("auto_increment")) {
            generated_value = "@GeneratedValue(strategy = GenerationType.AUTO)";
        } else {
            generated_value = "";
        }
        String field = getFieldName(COLUMN_NAME);
        return column.stream().map(c -> {
            String text = new String(c.getBytes());
            text = text.replace("${column_comment}", COLUMN_COMMENT);
            text = text.replace("${primary_id}", primary_id);
            if (generated_value.length() == 0 && text.contains("${generated_value}")) {
                return null;
            } else {
                text = text.replace("${generated_value}", generated_value);
            }
            text = text.replace("${column_setting}", column_setting);
            text = text.replace("${data_type}", data_type);
            text = text.replace("${field}", field);
            return text;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }


    private String toString(Object value) {
        return Objects.isNull(value) ? "" : value.toString();
    }


    private Config getConfig(String rootPath) throws IOException {
        try {
            String configPath = rootPath + "/pojo_config.json";
            System.out.println("config_path:" + configPath);
            Path path = Paths.get(configPath);
            if (Files.notExists(path)) {
                StringBuilder comment = new StringBuilder();
                comment.append("请在项目根目录配置 pojo_config.json 文件，格式如下：").append(LINE_SEPARATOR);
                comment.append("{ ").append(LINE_SEPARATOR);
                comment.append("   \"prefix\": \"\", ").append(LINE_SEPARATOR);
                comment.append("   \"suffix\": \"Entity\", ").append(LINE_SEPARATOR);
                comment.append("   \"schemas\": [ ").append(LINE_SEPARATOR);
                comment.append("     { ").append(LINE_SEPARATOR);
                comment.append("       \"ip\": \"127.0.0.1\", ").append(LINE_SEPARATOR);
                comment.append("       \"port\": \"3306\", ").append(LINE_SEPARATOR);
                comment.append("       \"user\": \"root\", ").append(LINE_SEPARATOR);
                comment.append("       \"password\": \"xxxx\", ").append(LINE_SEPARATOR);
                comment.append("       \"schema\": \"master\", ").append(LINE_SEPARATOR);
                comment.append("       \"package\": \"com.ims.entity.po.master\", ").append(LINE_SEPARATOR);
                comment.append("       \"filter\": \"tb_user,tb_action\" ").append(LINE_SEPARATOR);
                comment.append("     }, ").append(LINE_SEPARATOR);
                comment.append("     { ").append(LINE_SEPARATOR);
                comment.append("       \"ip\": \"127.0.0.2\", ").append(LINE_SEPARATOR);
                comment.append("       \"port\": \"3306\", ").append(LINE_SEPARATOR);
                comment.append("       \"user\": \"root\", ").append(LINE_SEPARATOR);
                comment.append("       \"password\": \"xxxx\", ").append(LINE_SEPARATOR);
                comment.append("       \"schema\": \"master\", ").append(LINE_SEPARATOR);
                comment.append("       \"package\": \"com.ims.entity.po.master\" ").append(LINE_SEPARATOR);
                comment.append("     } ").append(LINE_SEPARATOR);
                comment.append("   ] ").append(LINE_SEPARATOR);
                comment.append(" } ").append(LINE_SEPARATOR);
                System.err.println(comment.toString());
                Files.write(Paths.get(configPath), comment.toString().getBytes(Charset.forName("UTF-8")));
                throw new NoSuchFileException("path:" + configPath);
            } else {
                String setting = Files.readAllLines(Paths.get(configPath), Charset.forName("UTF-8")).stream().collect(Collectors.joining());
                Config config = new Gson().fromJson(setting, Config.class);
                return config;

            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            throw ex;
        }
    }

    @Setter
    @Getter
    private class Config {
        private String prefix;
        private String suffix;
        private List<schema> schemas;
    }

    @Setter
    @Getter
    private class schema {
        String ip = "127.0.0.1";
        String port = "3306";
        String schema = "schema";
        String user = "root";
        String password = "123456";
        @SerializedName("package")
        String packageName = "";
        String filter = "";

        @Override
        public String toString() {
            return "Config{" +
                    "ip='" + ip + '\'' +
                    ", port='" + port + '\'' +
                    ", schema='" + schema + '\'' +
                    ", user='" + user + '\'' +
                    ", password='" + password + '\'' +
                    ", packageName='" + packageName + '\'' +
                    '}';
        }
    }

    private List<String> getPojoTemplate() throws IOException {
        return getResourcePath("pojo.temp");
    }

    private List<String> getColumnTemplate() throws IOException {
        return getResourcePath("column.temp");
    }

    private List<String> getResourcePath(String path) throws IOException {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("classpath:".concat(path));
        List<String> content = new ArrayList<>();
        if (resource.exists()) {
            InputStream inputStream = null;
            InputStreamReader read = null;
            BufferedReader bufferedReader = null;
            try {
                inputStream = resource.getInputStream();
                read = new InputStreamReader(
                        inputStream, "UTF-8");//考虑到编码格式
                bufferedReader = new BufferedReader(read);
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    content.add(line);
                }
            } catch (Exception ex) {
                throw ex;
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (read != null) {
                    read.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            }

            return content;
        } else {
            System.err.println("模板文件丢失");
            throw new NoSuchFileException("file:".concat(path).concat(" is missing."));
        }
    }

    private String getClassName(String tableName) {
        String prefix = config.getPrefix();
        String suffix = config.getSuffix();
        return StringUtils.capitalize(prefix)
                .concat(Arrays.stream(tableName.split("_")).map(StringUtils::capitalize).collect(Collectors.joining()))
                .concat(StringUtils.capitalize(suffix));
    }

    private String getFieldName(String filedName) {
        return StringUtils.uncapitalize(Arrays.stream(filedName.split("_")).map(StringUtils::capitalize).collect(Collectors.joining()));
    }


    private List<Map<String, Object>> getSchemaInfo(schema schemaConfig) throws SQLException, ClassNotFoundException {
        String ip = schemaConfig.ip;
        String port = schemaConfig.port;
        String schema = schemaConfig.schema;
        String user = schemaConfig.user;
        String password = schemaConfig.password;
        String url = "jdbc:mysql://" + ip.trim() + ":" + port.trim() + "/" + schema.trim();

        System.out.println("connect to:" + url);
        Connection conn = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // 2.获得数据库链接
            conn = DriverManager.getConnection(url, user, password);
            // 3.通过数据库的连接操作数据库，实现增删改查（使用Statement类）
            String name = "张三";
            //预编译
            StringBuilder sbSql = new StringBuilder();
            sbSql.append(" SELECT TABLES.TABLE_NAME, ");
            sbSql.append("        TABLES.TABLE_COMMENT, ");
            sbSql.append("        COLUMN_NAME, ");
            sbSql.append("        IS_NULLABLE, ");
            sbSql.append("        DATA_TYPE, ");
            sbSql.append("        CHARACTER_MAXIMUM_LENGTH, ");
            sbSql.append("        NUMERIC_PRECISION, ");
            sbSql.append("        NUMERIC_SCALE, ");
            sbSql.append("        COLUMN_TYPE, ");
            sbSql.append("        COLUMN_KEY, ");
            sbSql.append("        EXTRA, ");
            sbSql.append("        COLUMN_COMMENT ");
            sbSql.append(" FROM information_schema.`TABLES` TABLES ");
            sbSql.append("          INNER JOIN (SELECT COLUMNS.IS_NULLABLE, ");
            sbSql.append("                             COLUMNS.DATA_TYPE, ");
            sbSql.append("                             CHARACTER_MAXIMUM_LENGTH, ");
            sbSql.append("                             COLUMNS.COLUMN_NAME, ");
            sbSql.append("                             NUMERIC_PRECISION, ");
            sbSql.append("                             NUMERIC_SCALE, ");
            sbSql.append("                             COLUMN_TYPE, ");
            sbSql.append("                             COLUMN_KEY, ");
            sbSql.append("                             COLUMN_COMMENT, ");
            sbSql.append("                             EXTRA, ");
            sbSql.append("                             TABLE_NAME ");
            sbSql.append("                      FROM information_schema.`COLUMNS`) COLUMNS ");
            sbSql.append("                     ON COLUMNS.TABLE_NAME = TABLES.TABLE_NAME ");
            sbSql.append(" WHERE TABLES.TABLE_SCHEMA = ? ");
            String filter = schemaConfig.getFilter();
            if (!StringUtils.isEmpty(filter)) {
                filter = Arrays.stream(filter.split(","))
                        .map(str -> "'".concat(str).concat("'")).collect(Collectors.joining(","));
                sbSql.append("  AND TABLES.TABLE_NAME IN (" + filter + "); ");
            }
            statement = conn.prepareStatement(sbSql.toString());
            statement.setString(1, schema);
            resultSet = statement.executeQuery();
            List<Map<String, Object>> data = getMapList(resultSet);
            return data;

        } catch (Exception ex) {
            throw ex;
        } finally {
            if (conn != null) {
                conn.close();
            }
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        }
    }

    /**
     * Gets map list.
     *
     * @param resultSet the result set
     * @return the map list
     * @throws SQLException the sql exception
     */
    private final List<Map<String, Object>> getMapList(ResultSet resultSet) throws SQLException {
        try {
            List<Map<String, Object>> mapList = new ArrayList<>();
            while (resultSet.next()) {
                mapList.add(doCreateRow(resultSet));
            }
            return mapList;
        } catch (Exception ex) {
            throw ex;
        } finally {
            resultSet.close();
        }
    }

    /**
     * 将执行 SQL 语句的结果放在 Map 中
     *
     * @param resultSet 语句返回的结果集
     * @return map
     * @throws SQLException
     */
    private final Map<String, Object> doCreateRow(ResultSet resultSet) throws SQLException {
        try {
            Map<String, Object> map = new HashedMap();
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int count = resultSetMetaData.getColumnCount();
            for (int i = 0; i < count; i++) {
                String label = resultSetMetaData.getColumnLabel(i + 1);
                Object value = resultSet.getObject(i + 1);
                map.put(label, value);
            }
            return map;
        } catch (SQLException e) {
            throw e;
        }

    }

}
