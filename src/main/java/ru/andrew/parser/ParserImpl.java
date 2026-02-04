package ru.andrew.parser;

import groovy.xml.XmlSlurper;
import groovy.xml.slurpersupport.GPathResult;
import groovy.xml.slurpersupport.NodeChild;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ru.andrew.config.Config;
import ru.andrew.database.DatabaseConfig;
import ru.andrew.parser.dto.ColumnIdResponse;
import ru.andrew.parser.dto.DdlChangeResponse;
import ru.andrew.parser.dto.DdlResponse;
import ru.andrew.parser.dto.StatusResponse;
import ru.andrew.parser.dto.UpdateResponse;

import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ParserImpl implements Parser {

    private final Config config;
    private final XmlSlurper slurper;
    private final DatabaseConfig databaseConfig;
    private final DataSource dataSource;
    private String textXml;
    private GPathResult parsedXml;

    @Autowired
    public ParserImpl(Config config, DatabaseConfig databaseConfig, DataSource dataSource) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();

            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            this.config = config;
            this.databaseConfig = databaseConfig;
            this.dataSource = dataSource;
            slurper = new XmlSlurper(factory.newSAXParser());
            slurper.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            
            loadXml();
            
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException("Failed to initialize XML parser", e);
        }
    }

    private void loadXml() {
        textXml = getXml();
        if (!textXml.isEmpty()) {
            try {
                parsedXml = slurper.parseText(textXml);
                if (parsedXml == null || parsedXml.isEmpty()) {
                    throw new RuntimeException("Parsed XML is null or empty");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse XML", e);
            }
        } else {
            throw new RuntimeException("Fetched XML is empty");
        }
    }

    private String getXml() {
        try {
            URI path = URI.create(config.getPath());
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(path)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch XML: HTTP " + response.statusCode());
            }

            String body = response.body();
            if (body == null || body.trim().isEmpty()) {
                throw new RuntimeException("XML response is empty");
            }
            
            return body;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch XML from " + config.getPath(), e);
        }
    }

    @Override
    public List<String> getTableNames() {
        if (parsedXml == null) {
            loadXml();
        }
        
        if (parsedXml == null) {
            throw new RuntimeException("XML not loaded");
        }
        
        List<String> tableNames = new ArrayList<>();
        GPathResult root = parsedXml;
        
        GPathResult shop = null;
        try {
            shop = (GPathResult) root.getProperty("shop");
        } catch (Exception ignored) {
        }
        
        GPathResult target = (shop != null && !shop.isEmpty()) ? shop : root;
        
        Iterable<?> childrenObj = target.children();
        if (childrenObj != null) {
            for (Object child : childrenObj) {
                if (child instanceof GPathResult childNode) {
                    String name = childNode.name();
                    if (name != null && !name.isEmpty()) {
                        try {
                            Iterable<?> childChildren = childNode.children();
                            if (childChildren != null && childChildren.iterator().hasNext()) {
                                tableNames.add(name);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        
        return tableNames;
    }

    @Override
    public DdlResponse getTableDDLResponse(String tableName) {
        if (parsedXml == null) {
            throw new RuntimeException("XML not loaded");
        }
        
        List<String> columns = getColumnNames(tableName);
        if (columns.isEmpty()) {
            return new DdlResponse(tableName, "");
        }
        
        String ddl = generateTableDDL(tableName, columns);
        return new DdlResponse(tableName, ddl);
    }

    @Override
    public UpdateResponse updateResponse(String tableName) {
        try {
            validateTableStructure(tableName);
            List<Map<String, Object>> data = extractTableData(tableName);
            
            if (!data.isEmpty()) {
                String primaryKey = determinePrimaryKey(tableName, getColumnNames(tableName));
                
                if (!tableExists(tableName)) {
                    String ddl = generateTableDDL(tableName, getColumnNames(tableName));
                    executeDDL(ddl);
                }
                
                updateTableData(tableName, data, primaryKey);
            }
            
            return new UpdateResponse("success", "Таблица " + tableName + " успешно обновлена");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("structure changed")) {
                return new UpdateResponse("error", "Структура таблицы изменилась: " + e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обновлении таблицы: " + e.getMessage(), e);
        }
    }

    @Override
    public UpdateResponse updateAllResponse() {
        try {
            List<String> tableNames = getTableNames();
            for (String tableName : tableNames) {
                UpdateResponse response = updateResponse(tableName);
                if ("error".equals(response.getStatus())) {
                    return response;
                }
            }
            return new UpdateResponse("success", "Все таблицы успешно обновлены");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обновлении таблиц: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getColumnNames(String tableName) {
        if (parsedXml == null) {
            throw new RuntimeException("XML not loaded");
        }
        
        Set<String> columns = new LinkedHashSet<>();
        
        try {
            GPathResult root = parsedXml;

            GPathResult shop = (GPathResult) root.getProperty("shop");
            GPathResult target = (shop != null && !shop.isEmpty()) ? shop : root;

            GPathResult tableNode = (GPathResult) target.getProperty(tableName);
            if (tableNode == null || tableNode.isEmpty()) {
                return new ArrayList<>();
            }
            
            if ("currencies".equalsIgnoreCase(tableName) || "categories".equalsIgnoreCase(tableName)) {
                Object tableChildren = tableNode.children();
                if (tableChildren instanceof Iterable<?> && ((Iterable<?>) tableChildren).iterator().hasNext()) {
                    Iterator<?> iterator = ((Iterable<?>) tableChildren).iterator();
                    if (iterator.hasNext()) {
                        Object firstElement = iterator.next();
                        if (firstElement instanceof NodeChild firstElementNode) {
                            Map attributes = firstElementNode.attributes();
                            if (attributes != null) {
                                columns.addAll(attributes.keySet());
                            }
                        }
                    }
                }
                if ("categories".equalsIgnoreCase(tableName)) {
                    columns.add("name");
                }
            } 
            else if ("offers".equalsIgnoreCase(tableName)) {
                Object offersChildren = tableNode.children();
                if (offersChildren instanceof Iterable<?> && ((Iterable<?>) offersChildren).iterator().hasNext()) {
                    Iterator<?> iterator = ((Iterable<?>) offersChildren).iterator();
                    if (iterator.hasNext()) {
                        Object firstOffer = iterator.next();
                        if (firstOffer instanceof NodeChild firstOfferNode) {
                            Map attributes = firstOfferNode.attributes();
                            if (attributes != null) {
                                columns.addAll(attributes.keySet());
                            }
                            for (Object child : firstOfferNode.children()) {
                                if (child instanceof GPathResult childNode) {
                                    String name = childNode.name();
                                    if (name != null && !name.isEmpty()) {
                                        columns.add(name);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get column names for table: " + tableName, e);
        }
        
        return new ArrayList<>(columns);
    }

    @Override
    public ColumnIdResponse getColumnIdResponse(String tableName, String columnName) {
        try {
            boolean isId = isColumnUnique(tableName, columnName);
            String description = isId ? "Столбец уникален" : "Столбец содержит повторяющиеся значения";
            return new ColumnIdResponse(tableName, columnName, isId, description);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check column uniqueness", e);
        }
    }

    @Override
    public DdlChangeResponse getDDLChangeResponse(String tableName) {
        try {
            if (!tableExists(tableName)) {
                String ddl = generateTableDDL(tableName, getColumnNames(tableName));
                return new DdlChangeResponse(tableName, ddl, true);
            }
            
            List<String> xmlColumns = getColumnNames(tableName);
            List<String> dbColumns = getTableColumns(tableName);
            
            List<String> newColumns = xmlColumns.stream()
                    .map(String::toLowerCase)
                    .filter(col -> !dbColumns.contains(col.toLowerCase()))
                    .toList();
            
            if (newColumns.isEmpty()) {
                return new DdlChangeResponse(tableName, "Изменений не требуется", false);
            }
            
            StringBuilder ddl = new StringBuilder();
            for (String column : newColumns) {
                ddl.append("ALTER TABLE ").append(tableName.toLowerCase())
                   .append(" ADD COLUMN ").append(column).append(" TEXT;\n");
            }
            
            return new DdlChangeResponse(tableName, ddl.toString(), true);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get DDL changes", e);
        }
    }

    @Override
    public StatusResponse getStatusResponse() {
        return new StatusResponse("ok", "XML Parser API работает");
    }

    private String generateTableDDL(String tableName, List<String> columns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableName.toLowerCase()).append(" (\n");
        
        String primaryKey = determinePrimaryKey(tableName, columns);
        boolean hasPrimaryKey = false;
        
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            ddl.append("    ").append(column.toLowerCase()).append(" TEXT");
            
            if (column.equalsIgnoreCase(primaryKey) && !hasPrimaryKey) {
                ddl.append(" PRIMARY KEY");
                hasPrimaryKey = true;
            }
            
            if (i < columns.size() - 1) {
                ddl.append(",");
            }
            ddl.append("\n");
        }
        
        ddl.append(");");
        
        return ddl.toString();
    }

    private String determinePrimaryKey(String tableName, List<String> columns) {
        if (columns.contains("id")) {
            return "id";
        }

        if ("offers".equalsIgnoreCase(tableName) && columns.contains("vendorCode")) {
            return "vendorCode";
        }
        
        try {
            if (tableExists(tableName)) {
                for (String column : columns) {
                    if (isColumnUnique(tableName, column)) {
                        return column;
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        
        return columns.isEmpty() ? "id" : columns.get(0);
    }

    private List<Map<String, Object>> extractTableData(String tableName) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        try {
            GPathResult tableNode = getGPathResult(tableName);

            if (tableNode == null || tableNode.isEmpty()) {
                return data;
            }
            
            for (Object item : tableNode) {
                if (item instanceof NodeChild itemNode) {
                    Map<String, Object> row = new LinkedHashMap<>();

                    Map attributes = itemNode.attributes();
                    if (attributes != null) {
                        row.putAll(attributes);
                    }
                    
                    if ("categories".equalsIgnoreCase(tableName)) {
                        String text = itemNode.text();
                        if (text != null && !text.trim().isEmpty()) {
                            row.put("name", text.trim());
                        }
                    } else {
                        for (Object child : itemNode.children()) {
                            if (child instanceof GPathResult childNode) {
                                String name = childNode.name();
                                if (name != null && !name.isEmpty()) {
                                    String value = childNode.text();
                                    row.put(name, value != null ? value.trim() : null);
                                }
                            }
                        }
                    }
                    
                    if (!row.isEmpty()) {
                        data.add(row);
                    }
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract data for table: " + tableName, e);
        }
        
        return data;
    }

    private GPathResult getGPathResult(String tableName) {
        GPathResult root = parsedXml;

        GPathResult shop = (GPathResult) root.getProperty("shop");
        GPathResult target = (shop != null && !shop.isEmpty()) ? shop : root;

        return (GPathResult) target.getProperty(tableName);
    }

    private void validateTableStructure(String tableName) throws SQLException {
        if (!tableExists(tableName)) {
            return;
        }
        
        List<String> xmlColumns = getColumnNames(tableName);
        List<String> dbColumns = getTableColumns(tableName);
        
        Set<String> xmlColumnsLower = xmlColumns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        
        for (String dbColumn : dbColumns) {
            if (!xmlColumnsLower.contains(dbColumn.toLowerCase())) {
                throw new RuntimeException(
                    "Table structure changed: column '" + dbColumn + 
                    "' exists in database but not in XML for table '" + tableName + "'"
                );
            }
        }
    }

    private void updateTableData(String tableName, List<Map<String, Object>> data, String primaryKey) throws SQLException {
        if (data.isEmpty()) {
            return;
        }
        
        List<String> columns = getColumnNames(tableName);
        String tableNameLower = tableName.toLowerCase();
        String primaryKeyLower = primaryKey.toLowerCase();
        
        ensureUniqueConstraint(tableNameLower, primaryKeyLower);
        
        StringBuilder insertSql = new StringBuilder();
        makeInsert(columns, tableNameLower, insertSql);
        insertSql.append(") ON CONFLICT (").append(primaryKeyLower).append(") DO UPDATE SET ");
        
        List<String> updateColumns = columns.stream()
                .filter(c -> !c.equalsIgnoreCase(primaryKey))
                .map(c -> c.toLowerCase() + " = EXCLUDED." + c.toLowerCase())
                .collect(Collectors.toList());
        
        if (updateColumns.isEmpty()) {
            insertSql = new StringBuilder();
            makeInsert(columns, tableNameLower, insertSql);
            insertSql.append(") ON CONFLICT (").append(primaryKeyLower).append(") DO NOTHING");
        } else {
            insertSql.append(String.join(", ", updateColumns));
        }
        
        List<Object[]> batchParams = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Object[] params = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                params[i] = row.get(columns.get(i));
            }
            batchParams.add(params);
        }
        
        executeBatch(insertSql.toString(), batchParams);
    }

    private void makeInsert(List<String> columns, String tableNameLower, StringBuilder insertSql) {
        insertSql.append("INSERT INTO ").append(tableNameLower).append(" (");
        insertSql.append(columns.stream().map(String::toLowerCase).collect(Collectors.joining(", ")));
        insertSql.append(") VALUES (");
        insertSql.append(columns.stream().map(c -> "?").collect(Collectors.joining(", ")));
    }

    private void ensureUniqueConstraint(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            boolean hasPrimaryKey = false;
            try (ResultSet pkRs = metaData.getPrimaryKeys(null, null, tableName)) {
                while (pkRs.next()) {
                    if (columnName.equalsIgnoreCase(pkRs.getString("COLUMN_NAME"))) {
                        hasPrimaryKey = true;
                        break;
                    }
                }
            }
            
            if (!hasPrimaryKey) {
                boolean hasUniqueIndex = false;
                try (ResultSet indexRs = metaData.getIndexInfo(null, null, tableName, false, false)) {
                    while (indexRs.next()) {
                        if (!indexRs.getBoolean("NON_UNIQUE") && 
                            columnName.equalsIgnoreCase(indexRs.getString("COLUMN_NAME"))) {
                            hasUniqueIndex = true;
                            break;
                        }
                    }
                }
                
                if (!hasUniqueIndex) {
                    String createIndexSql = String.format(
                        "CREATE UNIQUE INDEX IF NOT EXISTS idx_%s_%s ON %s (%s)",
                        tableName, columnName, tableName, columnName
                    );
                    executeDDL(createIndexSql);
                }
            }
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, tableName.toLowerCase(), null);
            return tables.next();
        }
    }

    private List<String> getTableColumns(String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getColumns(null, null, tableName.toLowerCase(), null);
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private boolean isColumnUnique(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String tableNameLower = tableName.toLowerCase();
            String columnNameLower = columnName.toLowerCase();
            
            if ("offers".equalsIgnoreCase(tableName) && "vendorcode".equals(columnNameLower)) {
                return true;
            }
            
            String sql = String.format(
                "SELECT COUNT(*) as total, COUNT(DISTINCT %s) as distinct_count FROM %s",
                columnName, tableNameLower
            );
            
            ResultSet rs = statement.executeQuery(sql);
            if (rs.next()) {
                int total = rs.getInt("total");
                int distinct = rs.getInt("distinct_count");
                return total == distinct && total > 0;
            }
            return false;
        }
    }

    private void executeDDL(String ddl) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private void executeBatch(String sql, List<Object[]> batchParams) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            for (Object[] params : batchParams) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                statement.addBatch();
            }
            
            statement.executeBatch();
        }
    }
}
