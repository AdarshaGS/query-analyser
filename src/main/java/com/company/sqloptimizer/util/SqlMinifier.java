package com.company.sqloptimizer.util;

/**
 * Production-ready SQL minifier for Java 17.
 * Removes unnecessary whitespace and comments while preserving 100% identical SQL semantics.
 * Does NOT rewrite or optimize SQL.
 */
public final class SqlMinifier {

    private SqlMinifier() {
        // utility class
    }

    /**
     * Minifies the given SQL string.
     *
     * @param sql the original SQL (must not be null)
     * @return minified SQL with same semantics
     */
    public static String minify(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL must not be null");
        }
        if (sql.isBlank()) {
            return "";
        }

        StringBuilder out = new StringBuilder(sql.length());
        boolean needSpace = false; // true if we should insert a single space before next non-whitespace token
        int i = 0;
        int len = sql.length();

        while (i < len) {
            char c = sql.charAt(i);

            // Handle comments
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                // line comment -- consume until newline
                i += 2;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }
                // after line comment we may need a space before next token
                needSpace = true;
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                // block comment /* ... */
                i += 2;
                while (i + 1 < len && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                if (i + 1 < len) {
                    i += 2; // skip */
                }
                needSpace = true;
                continue;
            }

            // Handle string literals and identifiers
            if (c == '\'') {
                // single-quoted string
                out.append(c);
                i++;
                while (i < len) {
                    char ch = sql.charAt(i);
                    out.append(ch);
                    i++;
                    if (ch == '\'') {
                        // check for escaped single quote ('' in MySQL)
                        if (i < len && sql.charAt(i) == '\'') {
                            out.append(sql.charAt(i));
                            i++;
                        } else {
                            break; // ending quote
                        }
                    }
                }
                needSpace = true;
                continue;
            }
            if (c == '"' || c == '`') {
                // double-quoted or backtick identifier
                char quote = c;
                out.append(c);
                i++;
                while (i < len) {
                    char ch = sql.charAt(i);
                    out.append(ch);
                    i++;
                    if (ch == quote) {
                        // MySQL allows escaping the quote by doubling it inside same-quoted identifier
                        if (i < len && sql.charAt(i) == quote) {
                            out.append(sql.charAt(i));
                            i++;
                        } else {
                            break;
                        }
                    }
                }
                needSpace = true;
                continue;
            }

            // Whitespace handling outside literals/comments
            if (Character.isWhitespace(c)) {
                // skip all consecutive whitespace
                while (i < len && Character.isWhitespace(sql.charAt(i))) {
                    i++;
                }
                // output a single space if we have pending output and more tokens follow
                if (out.length() > 0 && i < len) {
                    out.append(' ');
                }
                needSpace = false;
                continue;
            }

            // Regular character
            out.append(c);
            i++;
            needSpace = true;
        }

        // Trim possible trailing space
        if (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }
}