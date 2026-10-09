package com.droidmaker.core.lang;

import java.util.ArrayList;
import java.util.List;

/** Splits script source into tokens. */
public final class Lexer {
    public enum T { NUM, STR, ID, OP, EOF }

    public static final class Tok {
        public final T type;
        public final String text;
        public final double num;
        public final int line, pos;

        Tok(T type, String text, double num, int line, int pos) {
            this.type = type; this.text = text; this.num = num; this.line = line; this.pos = pos;
        }

        public boolean is(String s) { return (type == T.OP || type == T.ID) && text.equals(s); }

        @Override public String toString() { return type == T.EOF ? "end of code" : type == T.STR ? "\"" + text + "\"" : text; }
    }

    private static final String[] OPS3 = {"<<=", ">>="};
    private static final String[] OPS2 = {"==", "!=", "<=", ">=", "&&", "||", "^^", "+=", "-=", "*=", "/=", "|=", "&=", "^=",
            "++", "--", "<<", ">>", ":=", "<>", "%="};
    private static final String OPS1 = "+-*/%()[]{},;.=<>!~&|^?:@";

    public static List<Tok> lex(String src) {
        List<Tok> out = new ArrayList<>();
        int i = 0, n = src.length(), line = 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') line++;
                    i++;
                }
                i += 2;
                continue;
            }
            int start = i;
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                if (c == '0' && i + 1 < n && (src.charAt(i + 1) == 'x' || src.charAt(i + 1) == 'X')) {
                    i += 2;
                    int hs = i;
                    while (i < n && isHex(src.charAt(i))) i++;
                    if (hs == i) throw new ScriptError("Bad hex number", line);
                    out.add(new Tok(T.NUM, src.substring(start, i), Long.parseLong(src.substring(hs, i), 16), line, start));
                    continue;
                }
                while (i < n && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '.')) i++;
                if (i < n && (src.charAt(i) == 'e' || src.charAt(i) == 'E') && i + 1 < n
                        && (Character.isDigit(src.charAt(i + 1)) || src.charAt(i + 1) == '-' || src.charAt(i + 1) == '+')) {
                    i += 2;
                    while (i < n && Character.isDigit(src.charAt(i))) i++;
                }
                String t = src.substring(start, i);
                double v;
                try { v = Double.parseDouble(t); } catch (NumberFormatException e) { throw new ScriptError("Bad number '" + t + "'", line); }
                out.add(new Tok(T.NUM, t, v, line, start));
                continue;
            }
            if (c == '$' && i + 1 < n && isHex(src.charAt(i + 1))) {
                i++;
                int hs = i;
                while (i < n && isHex(src.charAt(i))) i++;
                out.add(new Tok(T.NUM, src.substring(start, i), Long.parseLong(src.substring(hs, i), 16), line, start));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                out.add(new Tok(T.ID, src.substring(start, i), 0, line, start));
                continue;
            }
            if (c == '"' || c == '\'') {
                int sl = line;
                StringBuilder b = new StringBuilder();
                i++;
                while (true) {
                    if (i >= n) throw new ScriptError("Unterminated string", sl);
                    char d = src.charAt(i++);
                    if (d == c) break;
                    if (d == '\n') line++;
                    if (d == '\\' && i < n) {
                        char e = src.charAt(i++);
                        switch (e) {
                            case 'n': b.append('\n'); break;
                            case 't': b.append('\t'); break;
                            case 'r': b.append('\r'); break;
                            case '\\': b.append('\\'); break;
                            case '"': b.append('"'); break;
                            case '\'': b.append('\''); break;
                            default: b.append('\\').append(e);
                        }
                    } else b.append(d);
                }
                out.add(new Tok(T.STR, b.toString(), 0, sl, start));
                continue;
            }
            String op = null;
            for (String o : OPS3) if (src.startsWith(o, i)) { op = o; break; }
            if (op == null) for (String o : OPS2) if (src.startsWith(o, i)) { op = o; break; }
            if (op == null && OPS1.indexOf(c) >= 0) op = String.valueOf(c);
            if (op == null) throw new ScriptError("Unexpected character '" + c + "'", line);
            i += op.length();
            out.add(new Tok(T.OP, op, 0, line, start));
        }
        out.add(new Tok(T.EOF, "", 0, line, n));
        return out;
    }

    private static boolean isHex(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
