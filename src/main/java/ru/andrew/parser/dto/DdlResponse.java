package ru.andrew.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DdlResponse {
    private String tableName;
    private String ddl;
}
