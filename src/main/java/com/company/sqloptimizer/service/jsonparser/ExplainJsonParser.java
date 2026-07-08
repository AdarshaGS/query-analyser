package com.company.sqloptimizer.service.jsonparser;

import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses MySQL EXPLAIN FORMAT=JSON output.
 * Keeps parsing responsibility only - no scoring or recommendations.
 */
@Component
public class ExplainJsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses the EXPLAIN JSON string into an ExplainAnalysisResult.
     *
     * @param explainJson the EXPLAIN JSON output
     * @return the analysis result
     */
    public ExplainAnalysisResult parse(String explainJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(explainJson);

            // Handle the format provided by the user:
            // [
            //   {
            //     "id": 1,
            //     "select_type": "PRIMARY",
            //     "table": "ml",
            //     ...
            //   },
            //   {
            //     "id": 1,
            //     "select_type": "PRIMARY",
            //     "table": "cd",
            //     ...
            //   }
            // ]
            if (rootNode.isArray()) {
                // Check if this looks like the traditional EXPLAIN format
                // (has fields like id, select_type, table, type, etc.)
                boolean isTraditionalExplainFormat = true;
                if (rootNode.size() > 0) {
                    JsonNode firstElement = rootNode.get(0);
                    if (!firstElement.has("id") || !firstElement.has("select_type") ||
                        !firstElement.has("table") || !firstElement.has("type")) {
                        isTraditionalExplainFormat = false;
                    }
                }

                if (isTraditionalExplainFormat) {
                    // Convert traditional EXPLAIN format to our internal table node format
                    JsonNode tablesNode = convertTraditionalExplainToTableNodes(rootNode);
                    return analyzeTables(tablesNode);
                }
            }

            // Handle both formats:
            // 1. Standard MySQL EXPLAIN FORMAT=JSON: { "query_block": { ... } }
            // 2. Simplified format (just the table array): [ {...}, {...} ] or { "table": [...] }
            JsonNode queryBlock;

            if (rootNode.has("query_block")) {
                queryBlock = rootNode.path("query_block");
            } else if (rootNode.isArray()) {
                // If the root is an array, treat it as the table array directly
                // We need to create a mock queryBlock structure
                queryBlock = createMockQueryBlockFromArray(rootNode);
            } else {
                // Assume the root node itself contains the table data
                queryBlock = rootNode;
            }

            // Extract tables and their access information - handle multiple possible locations
            JsonNode tablesNode = extractTablesNode(queryBlock);
            return analyzeTables(tablesNode);
        } catch (Exception e) {
            // Return empty result if parsing fails
            return ExplainAnalysisResult.builder().build();
        }
    }

    /**
     * Extracts the tables node from various possible locations in the EXPLAIN JSON structure.
     * Handles:
     * 1. Standard location: query_block.table (array)
     * 2. Nested loop location: query_block.ordering_operation.nested_loop[*].table
     * 3. Direct array format: [ {...}, {...} ]
     * 4. Direct object with table field: { "table": [...] }
     */
    private JsonNode extractTablesNode(JsonNode queryBlock) {
        // First try the standard location: query_block.table
        JsonNode tablesNode = queryBlock.path("table");
        if (!tablesNode.isMissingNode() && tablesNode.isArray()) {
            return tablesNode;
        }

        // Try to find tables in nested_loop structures (common in complex queries)
        JsonNode orderingOperation = queryBlock.path("ordering_operation");
        if (!orderingOperation.isMissingNode() && orderingOperation.isObject()) {
            JsonNode nestedLoop = orderingOperation.path("nested_loop");
            if (!nestedLoop.isMissingNode() && nestedLoop.isArray()) {
                // Extract all table objects from nested_loop entries
                var objectNode = objectMapper.createObjectNode();
                var tableArrayNode = objectMapper.createArrayNode();

                for (JsonNode nestedLoopEntry : nestedLoop) {
                    JsonNode tableNode = nestedLoopEntry.path("table");
                    if (!tableNode.isMissingNode() && tableNode.isObject()) {
                        tableArrayNode.add(tableNode);
                    }
                }

                objectNode.set("table", tableArrayNode);
                return objectNode;
            }
        }

        // If we still don't have tables, check if the queryBlock itself is an array of tables
        if (queryBlock.isArray()) {
            // Create a mock structure with the array as table data
            var objectNode = objectMapper.createObjectNode();
            objectNode.set("table", queryBlock);
            return objectNode;
        }

        // Fallback: return empty structure
        var objectNode = objectMapper.createObjectNode();
        objectNode.set("table", objectMapper.createArrayNode());
        return objectNode;
    }

    /**
     * Converts traditional EXPLAIN output format to our internal table node format.
     *
     * Input format (traditional EXPLAIN as JSON array):
     * [
     *   {
     *     "id": 1,
     *     "select_type": "PRIMARY",
     *     "table": "ml",
     *     "type": "ALL",
     *     "possible_keys": "FKB6F935D87179A0CB",
     *     "key": null,
     *     "key_len": null,
     *     "ref": null,
     *     "rows": 88921,
     *     "filtered": 100.00,
     *     "Extra": "Using where"
     *   },
     *   {
     *     "id": 1,
     *     "select_type": "PRIMARY",
     *     "table": "cd",
     *     "type": "eq_ref",
     *     "possible_keys": "PRIMARY",
     *     "key": "PRIMARY",
     *     "key_len": "8",
     *     "ref": "CREDILA_LMS_PREPROD_12_03_2026.ml.id",
     *     "rows": 1,
     *     "filtered": 100.00,
     *     "Extra": null
     *   }
     * ]
     *
     * Output format (our internal format):
     * {
     *   "table": [
     *     {
     *       "table_name": "ml",
     *       "access_type": "ALL",
     *       "possible_keys": "FKB6F935D87179A0CB",
     *       "key": null,
     *       "key_len": null,
     *       "ref": null,
     *       "rows": 88921,
     *       "filtered": 100.00,
     *       "Extra": "Using where"
     *     },
     *     {
     *       "table_name": "cd",
     *       "access_type": "eq_ref",
     *       "possible_keys": "PRIMARY",
     *       "key": "PRIMARY",
     *       "key_len": "8",
     *       "ref": "CREDILA_LMS_PREPROD_12_03_2026.ml.id",
     *       "rows": 1,
     *       "filtered": 100.00,
     *       "Extra": null
     *     }
     *   ]
     * }
     */
    private JsonNode convertTraditionalExplainToTableNodes(JsonNode traditionalExplainArray) {
        try {
            // Create a JSON object that mimics our internal structure
            var objectNode = objectMapper.createObjectNode();
            var tableArrayNode = objectMapper.createArrayNode();

            // Convert each row in the traditional EXPLAIN output to our table node format
            for (JsonNode row : traditionalExplainArray) {
                var tableNode = objectMapper.createObjectNode();

                // Map the fields from traditional EXPLAIN format to our format
                tableNode.put("table_name", row.path("table").asText(""));
                tableNode.put("access_type", row.path("type").asText(""));
                tableNode.put("possible_keys", row.path("possible_keys").isNull() ? null : row.path("possible_keys").asText());
                tableNode.put("key", row.path("key").isNull() ? null : row.path("key").asText());
                tableNode.put("key_len", row.path("key_len").isNull() ? null : row.path("key_len").asText());
                tableNode.put("ref", row.path("ref").isNull() ? null : row.path("ref").asText());
                tableNode.put("rows", row.path("rows").isNull() ? 0 : row.path("rows").asLong());
                tableNode.put("filtered", row.path("filtered").isNull() ? 0.0 : row.path("filtered").asDouble());
                tableNode.put("Extra", row.path("Extra").isNull() ? "" : row.path("Extra").asText());

                // Handle joined_table if present (for JOIN operations)
                // In traditional EXPLAIN, joined tables appear as separate rows with same id
                // We'll keep it simple for now and not handle joined_table in this conversion

                tableArrayNode.add(tableNode);
            }

            objectNode.set("table", tableArrayNode);
            return objectNode;
        } catch (Exception e) {
            // Fallback to empty structure
            var objectNode = objectMapper.createObjectNode();
            objectNode.set("table", objectMapper.createArrayNode());
            return objectNode;
        }
    }

    /**
     * Analyzes the table nodes to extract execution plan information.
     */
    private ExplainAnalysisResult analyzeTables(JsonNode tablesNode) {
        // Extract table names
        // Note: We're not using the tables list in the current implementation,
        // but we extract it here in case it's needed for future enhancements

        // Analyze the execution plan for issues
        boolean fullScanDetected = detectFullScan(tablesNode);
        boolean tempTableDetected = detectTempTable(tablesNode);
        boolean fileSortDetected = detectFileSort(tablesNode);
        boolean nestedLoopDetected = detectNestedLoop(tablesNode);
        boolean rowExplosionDetected = detectRowExplosion(tablesNode);

        // Build issues and recommendations
        List<IssueDto> issues = new ArrayList<>();
        List<RecommendationDto> recommendations = new ArrayList<>();

        if (fullScanDetected) {
            issues.add(IssueDto.builder()
                    .issue("Full table scan detected")
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Consider adding appropriate indexes to avoid full table scans")
                    .build());
        }

        if (tempTableDetected) {
            issues.add(IssueDto.builder()
                    .issue("Temporary table created")
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Optimize query to avoid temporary tables, consider indexing columns used in GROUP BY or ORDER BY")
                    .build());
        }

        if (fileSortDetected) {
            issues.add(IssueDto.builder()
                    .issue("Filesort detected")
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Consider adding indexes to avoid filesort, especially on ORDER BY columns")
                    .build());
        }

        if (nestedLoopDetected) {
            issues.add(IssueDto.builder()
                    .issue("Nested loop join detected")
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Review join conditions and ensure proper indexing for join columns")
                    .build());
        }

        if (rowExplosionDetected) {
            issues.add(IssueDto.builder()
                    .issue("Potential row explosion in join")
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Review join conditions and consider adding WHERE clauses to limit rows early")
                    .build());
        }

        return ExplainAnalysisResult.builder()
                .fullScanDetected(fullScanDetected)
                .tempTableDetected(tempTableDetected)
                .fileSortDetected(fileSortDetected)
                .nestedLoopDetected(nestedLoopDetected)
                .rowExplosionDetected(rowExplosionDetected)
                .issues(issues)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Creates a mock query block structure from a table array.
     */
    private JsonNode createMockQueryBlockFromArray(JsonNode tableArray) {
        try {
            // Create a JSON object that mimics the query_block structure
            var objectNode = objectMapper.createObjectNode();
            objectNode.set("table", tableArray);
            return objectNode;
        } catch (Exception e) {
            // Fallback to a simple object with the array as table
            var objectNode = objectMapper.createObjectNode();
            var tableArrayNode = objectMapper.createArrayNode();
            if (tableArray.isArray()) {
                for (JsonNode element : tableArray) {
                    tableArrayNode.add(element);
                }
            }
            objectNode.set("table", tableArrayNode);
            return objectNode;
        }
    }

    /**
     * Detects if a full table scan is present in the execution plan.
     */
    private boolean detectFullScan(JsonNode tablesNode) {
        if (tablesNode.isArray()) {
            for (JsonNode table : tablesNode) {
                String accessType = table.path("access_type").asText();
                if ("ALL".equalsIgnoreCase(accessType) || "index".equalsIgnoreCase(accessType)) {
                    return true;
                }
            }
        } else if (tablesNode.isObject()) {
            String accessType = tablesNode.path("access_type").asText();
            return "ALL".equalsIgnoreCase(accessType) || "index".equalsIgnoreCase(accessType);
        }
        return false;
    }

    /**
     * Detects if a temporary table is created.
     * Checks the "Extra" field for "Using temporary" string.
     */
    private boolean detectTempTable(JsonNode tablesNode) {
        if (tablesNode.isArray()) {
            for (JsonNode table : tablesNode) {
                String extra = table.path("Extra").asText();
                if (extra.contains("Using temporary")) {
                    return true;
                }
            }
        } else if (tablesNode.isObject()) {
            String extra = tablesNode.path("Extra").asText();
            if (extra.contains("Using temporary")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects if filesort is used.
     * Checks the "Extra" field for "Using filesort" string.
     */
    private boolean detectFileSort(JsonNode tablesNode) {
        if (tablesNode.isArray()) {
            for (JsonNode table : tablesNode) {
                String extra = table.path("Extra").asText();
                if (extra.contains("Using filesort")) {
                    return true;
                }
            }
        } else if (tablesNode.isObject()) {
            String extra = tablesNode.path("Extra").asText();
            if (extra.contains("Using filesort")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects if nested loop join is used.
     */
    private boolean detectNestedLoop(JsonNode tablesNode) {
        if (tablesNode.isArray()) {
            for (JsonNode table : tablesNode) {
                JsonNode joinedTable = table.path("joined_table");
                if (!joinedTable.isMissingNode()) {
                    String joinType = joinedTable.path("join_type").asText();
                    if ("NESTEDLOOP".equalsIgnoreCase(joinType)) {
                        return true;
                    }
                }
            }
        } else if (tablesNode.isObject()) {
            JsonNode joinedTable = tablesNode.path("joined_table");
            if (!joinedTable.isMissingNode()) {
                String joinType = joinedTable.path("join_type").asText();
                return "NESTEDLOOP".equalsIgnoreCase(joinType);
            }
        }
        return false;
    }

    /**
     * Detects potential row explosion in joins.
     * This is a simplified check - in reality, we'd need to examine row estimates.
     */
    private boolean detectRowExplosion(JsonNode tablesNode) {
        // For now, we'll consider it detected if we have multiple tables without proper join conditions
        // A more sophisticated implementation would examine the accessed_rows or similar metrics
        int tableCount = 0;

        if (tablesNode.isArray()) {
            tableCount = tablesNode.size();
        } else if (tablesNode.isObject()) {
            tableCount = 1;
            // Check if there's a joined table
            JsonNode joinedTable = tablesNode.path("joined_table");
            if (!joinedTable.isMissingNode()) {
                tableCount++;
            }
        }

        return tableCount > 2; // Simplified check
    }
}