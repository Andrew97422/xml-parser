package ru.andrew.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.andrew.parser.Parser;
import ru.andrew.parser.dto.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "XML Parser API", description = "API для работы с XML парсером и базой данных")
public class MainController {

    private final Parser parser;

    @GetMapping
    @Operation(summary = "Проверка работы API", description = "Возвращает статус работы API")
    public ResponseEntity<StatusResponse> index() {
        return ResponseEntity.ok(parser.getStatusResponse());
    }

    @GetMapping("/tables")
    @Operation(summary = "Получить список таблиц", 
               description = "Возвращает названия таблиц из XML (currency, categories, offers)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список таблиц успешно получен"),
        @ApiResponse(responseCode = "500", description = "Ошибка при обработке XML")
    })
    public ResponseEntity<List<String>> getTableNames() {
        try {
            return ResponseEntity.ok(parser.getTableNames());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении списка таблиц: " + e.getMessage(), e);
        }
    }

    @GetMapping("/tables/{tableName}/columns")
    @Operation(summary = "Получить список столбцов таблицы", 
               description = "Возвращает наименования столбцов таблицы из XML")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список столбцов успешно получен"),
        @ApiResponse(responseCode = "500", description = "Ошибка при обработке XML")
    })
    public ResponseEntity<List<String>> getColumnNames(
            @Parameter(description = "Название таблицы", required = true, example = "offers")
            @PathVariable String tableName) {
        try {
            return ResponseEntity.ok(parser.getColumnNames(tableName));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении столбцов: " + e.getMessage(), e);
        }
    }

    @GetMapping("/tables/{tableName}/ddl")
    @Operation(summary = "Получить DDL для создания таблицы", 
               description = "Создает SQL для создания таблицы динамически из XML")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DDL успешно сгенерирован"),
        @ApiResponse(responseCode = "500", description = "Ошибка при генерации DDL")
    })
    public ResponseEntity<DdlResponse> getTableDDL(
            @Parameter(description = "Название таблицы", required = true, example = "offers")
            @PathVariable String tableName) {
        try {
            return ResponseEntity.ok(parser.getTableDDLResponse(tableName));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации DDL: " + e.getMessage(), e);
        }
    }

    @GetMapping("/tables/{tableName}/ddl-change")
    @Operation(summary = "Получить DDL для изменения таблицы", 
               description = "Возвращает SQL для добавления новых столбцов (допустимо только добавление)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DDL изменений успешно сгенерирован"),
        @ApiResponse(responseCode = "500", description = "Ошибка при генерации DDL")
    })
    public ResponseEntity<DdlChangeResponse> getDDLChange(
            @Parameter(description = "Название таблицы", required = true, example = "offers")
            @PathVariable String tableName) {
        try {
            return ResponseEntity.ok(parser.getDDLChangeResponse(tableName));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации DDL изменений: " + e.getMessage(), e);
        }
    }

    @GetMapping("/tables/{tableName}/columns/{columnName}/is-id")
    @Operation(summary = "Проверить уникальность столбца", 
               description = "Возвращает true если столбец не имеет повторяющихся значений")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Проверка выполнена успешно"),
        @ApiResponse(responseCode = "500", description = "Ошибка при проверке")
    })
    public ResponseEntity<ColumnIdResponse> isColumnId(
            @Parameter(description = "Название таблицы", required = true, example = "offers")
            @PathVariable String tableName,
            @Parameter(description = "Название столбца", required = true, example = "vendorCode")
            @PathVariable String columnName) {
        try {
            return ResponseEntity.ok(parser.getColumnIdResponse(tableName, columnName));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при проверке уникальности: " + e.getMessage(), e);
        }
    }

    @PostMapping("/tables/{tableName}/update")
    @Operation(summary = "Обновить данные в таблице", 
               description = "Обновляет данные в таблице на основе XML. Если изменилась структура - выдает exception")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Данные успешно обновлены"),
        @ApiResponse(responseCode = "400", description = "Изменена структура таблицы"),
        @ApiResponse(responseCode = "500", description = "Ошибка при обновлении")
    })
    public ResponseEntity<UpdateResponse> updateTable(
            @Parameter(description = "Название таблицы", required = true, example = "offers")
            @PathVariable String tableName) {
        try {
            UpdateResponse response = parser.updateResponse(tableName);
            if ("error".equals(response.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обновлении таблицы: " + e.getMessage(), e);
        }
    }

    @PostMapping("/tables/update-all")
    @Operation(summary = "Обновить все таблицы", 
               description = "Обновляет данные во всех таблицах на основе XML")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Все таблицы успешно обновлены"),
        @ApiResponse(responseCode = "500", description = "Ошибка при обновлении")
    })
    public ResponseEntity<UpdateResponse> updateAll() {
        try {
            return ResponseEntity.ok(parser.updateAllResponse());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обновлении таблиц: " + e.getMessage(), e);
        }
    }
}

