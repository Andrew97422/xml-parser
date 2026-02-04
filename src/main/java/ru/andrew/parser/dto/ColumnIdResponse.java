package ru.andrew.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnIdResponse {
    private String tableName;
    private String columnName;
    private boolean isId;
    private String description;
}
