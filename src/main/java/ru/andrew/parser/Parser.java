package ru.andrew.parser;

import org.xml.sax.SAXException;
import ru.andrew.parser.dto.*;

import java.io.IOException;
import java.util.List;

public interface Parser {
    List<String> getTableNames() throws IOException, SAXException;

    DdlResponse getTableDDLResponse(String tableName);

    UpdateResponse updateResponse(String tableName);

    UpdateResponse updateAllResponse();

    List<String> getColumnNames(String tableName);

    ColumnIdResponse getColumnIdResponse(String tableName, String columnName);

    DdlChangeResponse getDDLChangeResponse(String tableName);

    StatusResponse getStatusResponse();
}
