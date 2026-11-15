package io.github.portaldalaran.taming.builder;

import io.github.portaldalaran.taming.core.QueryCriteriaException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把一条 having 子句重写成只含「一个白名单字段 + 比较字面量」的 SQL。
 * 比较值、IN 列表和函数的其它参数必须是数字或单引号字面量，子句末尾不允许再接布尔条件。
 */
final class HavingClauseRewriter {

    private static final int MAX_DEPTH = 8;

    /**
     * 语句关键字，以及会休眠、读文件或借报错外带数据的例程。
     * 只在函数名位置拦截；同名实体字段仍可作比较左值。
     */
    private static final Set<String> BLOCKED_FUNCTIONS = Set.of(
            "and", "or", "not", "select", "union", "from", "where",
            "insert", "update", "delete", "drop", "alter", "truncate",
            "exec", "execute", "into", "outfile", "dumpfile",
            "case", "when", "then", "else", "exists", "xor",
            "having", "group", "order", "limit", "offset",
            "procedure", "analyse", "analyze", "information_schema",
            "sleep", "benchmark", "get_lock", "release_lock", "is_free_lock", "is_used_lock",
            "extractvalue", "updatexml", "load_file",
            "pg_sleep", "pg_read_file", "xp_cmdshell");

    private HavingClauseRewriter() {
    }

    static String rewrite(String clause, BuildHelper<?> buildHelper) {
        Parser parser = new Parser(clause, buildHelper);
        return parser.parse();
    }

    private static final class Parser {
        private final String clause;
        private final BuildHelper<?> buildHelper;
        private int index;
        private int depth;
        private boolean sawField;

        private Parser(String clause, BuildHelper<?> buildHelper) {
            this.clause = clause;
            this.buildHelper = buildHelper;
        }

        private String parse() {
            String expr = parseExpr();
            if (!sawField) {
                throw invalid();
            }
            String operator = parseOperator();
            String value = isInOperator(operator) ? parseInList() : parseLiteral();
            skipWs();
            if (index != clause.length()) {
                throw invalid();
            }
            return expr + operator + value;
        }

        private String parseExpr() {
            if (++depth > MAX_DEPTH) {
                throw invalid();
            }
            try {
                skipWs();
                String name = parseIdentifier();
                skipWs();
                if (peek('(')) {
                    if (BLOCKED_FUNCTIONS.contains(name.toLowerCase(Locale.ROOT))) {
                        throw invalid();
                    }
                    index++;
                    List<String> args = new ArrayList<>();
                    args.add(parseArg());
                    while (consume(',')) {
                        args.add(parseArg());
                    }
                    expect(')');
                    return name + "(" + String.join(",", args) + ")";
                }
                return resolveField(name);
            } finally {
                depth--;
            }
        }

        private String parseArg() {
            skipWs();
            if (peek('\'')) {
                return parseString();
            }
            if (isNumberStart()) {
                return parseNumber();
            }
            return parseExpr();
        }

        private String resolveField(String token) {
            if (sawField) {
                throw invalid();
            }
            String column = buildHelper.getColumn(token);
            if (StringUtils.isBlank(column) || !buildHelper.checkEntityAttribute(token) || !isAsciiIdent(column)) {
                throw new QueryCriteriaException("Invalid having attribute: " + token);
            }
            sawField = true;
            return column;
        }

        private String parseOperator() {
            skipWs();
            if (matchWord("not")) {
                skipWs();
                if (matchWord("in")) {
                    return " not in ";
                }
                if (matchWord("like")) {
                    return " not like ";
                }
                throw invalid();
            }
            if (matchWord("in")) {
                return " in ";
            }
            if (matchWord("like")) {
                return " like ";
            }
            if (match(">=", "<=" , "<>", "!=")) {
                String operator = clause.substring(index, index + 2);
                index += 2;
                return operator;
            }
            if (match(">", "<", "=")) {
                String operator = clause.substring(index, index + 1);
                index++;
                return operator;
            }
            throw invalid();
        }

        private String parseInList() {
            expect('(');
            List<String> literals = new ArrayList<>();
            literals.add(parseLiteral());
            while (consume(',')) {
                literals.add(parseLiteral());
            }
            expect(')');
            return "(" + String.join(",", literals) + ")";
        }

        private String parseLiteral() {
            skipWs();
            if (peek('\'')) {
                return parseString();
            }
            return parseNumber();
        }

        private String parseString() {
            if (!peek('\'')) {
                throw invalid();
            }
            index++;
            StringBuilder body = new StringBuilder();
            while (index < clause.length()) {
                char current = clause.charAt(index);
                if (current == '\'') {
                    if (index + 1 < clause.length() && clause.charAt(index + 1) == '\'') {
                        body.append("''");
                        index += 2;
                        continue;
                    }
                    index++;
                    return "'" + body + "'";
                }
                if (current == '\\' || current == '\0') {
                    throw invalid();
                }
                body.append(current);
                index++;
            }
            throw invalid();
        }

        private String parseNumber() {
            int start = index;
            if (peek('+') || peek('-')) {
                index++;
            }
            if (!peekDigit()) {
                throw invalid();
            }
            while (peekDigit()) {
                index++;
            }
            if (peek('.')) {
                index++;
                if (!peekDigit()) {
                    throw invalid();
                }
                while (peekDigit()) {
                    index++;
                }
            }
            return clause.substring(start, index);
        }

        private boolean isNumberStart() {
            if (peekDigit()) {
                return true;
            }
            if ((peek('+') || peek('-')) && index + 1 < clause.length() && isDigit(clause.charAt(index + 1))) {
                return true;
            }
            return false;
        }

        private String parseIdentifier() {
            if (!peekIdentStart()) {
                throw invalid();
            }
            int start = index;
            index++;
            while (peekIdentPart()) {
                index++;
            }
            return clause.substring(start, index);
        }

        private boolean matchWord(String word) {
            if (!clause.regionMatches(true, index, word, 0, word.length())) {
                return false;
            }
            int end = index + word.length();
            if (end < clause.length() && isIdentPart(clause.charAt(end))) {
                return false;
            }
            index = end;
            return true;
        }

        private boolean match(String... tokens) {
            for (String token : tokens) {
                if (clause.regionMatches(index, token, 0, token.length())) {
                    return true;
                }
            }
            return false;
        }

        private boolean consume(char expected) {
            skipWs();
            if (!peek(expected)) {
                return false;
            }
            index++;
            return true;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw invalid();
            }
        }

        private void skipWs() {
            while (index < clause.length() && Character.isWhitespace(clause.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < clause.length() && clause.charAt(index) == expected;
        }

        private boolean peekDigit() {
            return index < clause.length() && isDigit(clause.charAt(index));
        }

        private boolean peekIdentStart() {
            return index < clause.length() && isIdentStart(clause.charAt(index));
        }

        private boolean peekIdentPart() {
            return index < clause.length() && isIdentPart(clause.charAt(index));
        }

        private QueryCriteriaException invalid() {
            return new QueryCriteriaException("Invalid having expression: " + clause);
        }
    }

    private static boolean isInOperator(String operator) {
        return " in ".equals(operator) || " not in ".equals(operator);
    }

    private static boolean isAsciiIdent(String value) {
        if (value.isEmpty() || !isIdentStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!isIdentPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
