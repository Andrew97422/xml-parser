# XML Parser Service

Сервис для парсинга XML-файлов и синхронизации данных с PostgreSQL базой данных.

## Описание

Сервис предназначен для обработки XML-файлов по заданному URL и синхронизации данных с базой данных PostgreSQL. Основные функции:

- Парсинг XML с использованием groovy.xml.XmlSlurper
- Автоматическое определение структуры таблиц
- Создание DDL-запросов для таблиц
- Обновление данных в базе данных через JDBC
- Поддержка динамических структур данных

## Основные функции

### Получение имен таблиц
```java
List<String> getTableNames()
```
Возвращает список имен таблиц, извлеченных из XML-файла (currency, categories, offers).

### Генерация DDL
```java
String getTableDDL(String tableName)
```
Создает SQL-запрос для создания таблицы на основе структуры XML.

### Обновление данных
```java
void update()
void update(String tableName)
```
Обновляет данные в таблицах базы данных. При изменении структуры таблицы выбрасывает исключение.

### Дополнительные функции

```java
List<String> getColumnNames(String tableName)
```
Возвращает наименования столбцов таблицы, определенные динамически.

```java
boolean isColumnId(String tableName, String columnName)
```
Проверяет, является ли столбец уникальным (не имеет повторяющихся значений).

```java
String getDDLChange(String tableName)
```
Возвращает изменения структуры таблицы, допускаются только добавления новых столбцов.

## Технологии

- Java 8+
- Groovy XmlSlurper
- PostgreSQL
- JDBC
- Maven
- Docker

## Запуск

1. Сборка проекта:
```bash
mvn clean install
```

2. Запуск с Docker Compose:
```bash
docker-compose up
```

3. Интерактивный интерфейс - [Swagger](http://localhost:8082/swagger-ui/index.html):

## Конфигурация

Конфигурация сервиса задается через environment переменные:
- `XML_URL` - URL для загрузки XML файла (по умолчанию: https://expro.ru/bitrix/catalog_export/export_Sai.xml)
- `DB_HOST` - хост PostgreSQL (по умолчанию: localhost)
- `DB_PORT` - порт PostgreSQL (по умолчанию: 5432)
- `DB_NAME` - имя базы данных (по умолчанию: xml_parser)
- `DB_USER` - пользователь базы данных (по умолчанию: postgres)
- `DB_PASSWORD` - пароль пользователя (по умолчанию: postgres)

## Тестовое задание

Это тестовое задание выполнено для компании СофтМоушен.

Сервис обрабатывает XML файл с сайта https://expro.ru/bitrix/catalog_export/export_Sai.xml используя библиотеку groovy.xml.XmlSlurper.

Реализованы все требуемые функции:
- Получение имен таблиц из XML
- Динамическая генерация DDL для таблиц
- Обновление данных в базе данных через JDBC
- Интерактивный консольный интерфейс
- Поддержка уникального столбца offers.vendorCode

Docker-compose файл настроен для запуска приложения вместе с PostgreSQL базой данных.
