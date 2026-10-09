package com.droidmaker.core.lang;

import com.droidmaker.core.lang.Ast.*;
import com.droidmaker.core.lang.Lexer.T;
import com.droidmaker.core.lang.Lexer.Tok;
import com.droidmaker.core.runtime.Vars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recursive-descent parser for the GML-like script language. Resolves names while parsing. */
final class Parser {
    private final Compiler comp;
    private final List<Tok> toks;
    private int p;
    private final Map<String, Integer> locals = new HashMap<>();

    Parser(Compiler comp, String src) {
        this.comp = comp;
        this.toks = Lexer.lex(src);
    }

    int localCount() { return locals.size(); }

    // ------------------------------------------------------------------ token helpers
    private Tok peek() { return toks.get(p); }
    private Tok peek(int k) { return toks.get(Math.min(p + k, toks.size() - 1)); }
    private Tok next() { Tok t = toks.get(p); if (p < toks.size() - 1) p++; return t; }
    private boolean at(String s) { return peek().is(s); }
    private boolean accept(String s) { if (at(s)) { next(); return true; } return false; }

    private Tok expect(String s) {
        if (!at(s)) throw err("Expected '" + s + "' but found " + peek());
        return next();
    }

    private ScriptError err(String m) { return new ScriptError(m, peek().line); }

    private String ident() {
        Tok t = peek();
        if (t.type != T.ID) throw err("Expected a name but found " + t);
        next();
        return t.text;
    }

    private void semis() { while (accept(";")) { /* skip */ } }

    // ------------------------------------------------------------------ statements
    Stmt parseProgram() {
        List<Stmt> out = new ArrayList<>();
        semis();
        while (peek().type != T.EOF) {
            out.add(statement());
            semis();
        }
        Block b = new Block(out.toArray(new Stmt[0]));
        b.line = 1;
        return b;
    }

    private Stmt block(String close) {
        int line = peek().line;
        List<Stmt> out = new ArrayList<>();
        semis();
        while (!at(close)) {
            if (peek().type == T.EOF) throw err("Missing '" + close + "' for block started on line " + line);
            out.add(statement());
            semis();
        }
        next();
        Block b = new Block(out.toArray(new Stmt[0]));
        b.line = line;
        return b;
    }

    private Stmt statement() {
        Tok t = peek();
        int line = t.line;
        Stmt s = statementInner();
        if (s.line == 0) s.line = line;
        return s;
    }

    private Stmt statementInner() {
        Tok t = peek();
        if (t.type == T.OP) {
            if (t.text.equals("{")) { next(); return block("}"); }
            if (t.text.equals(";")) { next(); return new Empty(); }
            if (t.text.equals("++") || t.text.equals("--")) {
                next();
                LExpr target = lvalue(postfix());
                return assign(target, t.text.equals("++") ? Ast.ADD : Ast.SUB, new Const(V.ONE));
            }
        }
        if (t.type == T.ID) {
            switch (t.text) {
                case "begin": next(); return block("end");
                case "var": return varDecl();
                case "globalvar": {
                    next();
                    List<String> names = new ArrayList<>();
                    do { String n = ident(); names.add(n); comp.globalvars.add(n); } while (accept(","));
                    return new GlobalDecl(names.toArray(new String[0]));
                }
                case "if": {
                    next();
                    Expr c = expr();
                    accept("then");
                    Stmt th = statement();
                    semis();
                    Stmt el = null;
                    if (accept("else")) el = statement();
                    return new If(c, th, el);
                }
                case "while": {
                    next();
                    Expr c = expr();
                    accept("do");
                    return new While(c, statement());
                }
                case "do": {
                    next();
                    Stmt body = statement();
                    semis();
                    expect("until");
                    return new DoUntil(body, expr());
                }
                case "for": {
                    next();
                    expect("(");
                    Stmt init = at(";") ? null : simpleStatement();
                    semis();
                    Expr cond = at(";") ? null : expr();
                    semis();
                    Stmt step = at(")") ? null : simpleStatement();
                    semis();
                    expect(")");
                    return new For(init, cond, step, statement());
                }
                case "repeat": {
                    next();
                    Expr n = expr();
                    return new Repeat(n, statement());
                }
                case "with": {
                    next();
                    Expr target = expr();
                    accept("do");
                    return new With(target, statement());
                }
                case "switch": return switchStmt();
                case "break": next(); return new Flow(Ast.BREAK);
                case "continue": next(); return new Flow(Ast.CONTINUE);
                case "exit": next(); return new Flow(Ast.EXIT);
                case "return": {
                    next();
                    if (at(";") || at("}") || peek().type == T.EOF || at("end")) return new Return(null);
                    return new Return(expr());
                }
                case "case": case "default": case "else": case "until": case "then": case "end":
                    throw err("Unexpected '" + t.text + "'");
            }
        }
        return simpleStatement();
    }

    private Stmt varDecl() {
        next();
        List<Integer> slots = new ArrayList<>();
        List<Expr> inits = new ArrayList<>();
        do {
            String n = ident();
            if (Builtins.isReservedName(n)) throw err("'" + n + "' is a built-in name and cannot be a local variable");
            Integer slot = locals.get(n);
            if (slot == null) { slot = locals.size(); locals.put(n, slot); }
            slots.add(slot);
            inits.add(accept("=") || accept(":=") ? expr() : null);
        } while (accept(","));
        int[] s = new int[slots.size()];
        for (int k = 0; k < s.length; k++) s[k] = slots.get(k);
        return new VarInit(s, inits.toArray(new Expr[0]));
    }

    private Stmt switchStmt() {
        next();
        Expr v = expr();
        expect("{");
        List<Expr> cases = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Stmt> body = new ArrayList<>();
        int def = -1;
        semis();
        while (!accept("}")) {
            if (peek().type == T.EOF) throw err("Missing '}' at end of switch");
            if (accept("case")) {
                cases.add(expr());
                starts.add(body.size());
                expect(":");
            } else if (accept("default")) {
                def = body.size();
                expect(":");
            } else body.add(statement());
            semis();
        }
        int[] st = new int[starts.size()];
        for (int k = 0; k < st.length; k++) st[k] = starts.get(k);
        return new Switch(v, cases.toArray(new Expr[0]), st, def, body.toArray(new Stmt[0]));
    }

    /** Assignment, increment or function call. */
    private Stmt simpleStatement() {
        int line = peek().line;
        if (at("var")) return varDecl();
        if (at("++") || at("--")) {
            boolean inc = next().text.equals("++");
            return assign(lvalue(postfix()), inc ? Ast.ADD : Ast.SUB, new Const(V.ONE));
        }
        Expr e = postfix();
        Tok t = peek();
        if (t.type == T.OP) {
            int op = -1;
            switch (t.text) {
                case "=": case ":=": op = 0; break;
                case "+=": op = Ast.ADD; break;
                case "-=": op = Ast.SUB; break;
                case "*=": op = Ast.MUL; break;
                case "/=": op = Ast.DIV; break;
                case "|=": op = Ast.BOR; break;
                case "&=": op = Ast.BAND; break;
                case "^=": op = Ast.BXOR; break;
                case "%=": op = Ast.MOD; break;
                case "<<=": op = Ast.SHL; break;
                case ">>=": op = Ast.SHR; break;
                case "++": next(); return assign(lvalue(e), Ast.ADD, new Const(V.ONE));
                case "--": next(); return assign(lvalue(e), Ast.SUB, new Const(V.ONE));
            }
            if (op >= 0) {
                next();
                LExpr target = lvalue(e);
                Stmt s = assign(target, op, expr());
                s.line = line;
                return s;
            }
        }
        if (e instanceof Call || e instanceof ScriptCall) {
            ExprStmt s = new ExprStmt(e);
            s.line = line;
            return s;
        }
        if (e instanceof SelfVar && t.type == T.ID) throw err("Unknown statement '" + ((SelfVar) e).name + "' (missing '=' or '('?)");
        throw err("Expected an assignment or function call");
    }

    private Stmt assign(LExpr target, int op, Expr value) {
        Assign a = new Assign(target, op, value);
        a.line = target.line;
        return a;
    }

    private LExpr lvalue(Expr e) {
        if (e instanceof LExpr) return (LExpr) e;
        if (e instanceof Const) throw new ScriptError("Cannot assign to a constant", e.line);
        throw new ScriptError("Cannot assign to this expression", e.line);
    }

    // ------------------------------------------------------------------ expressions
    Expr expr() { return ternary(); }

    private Expr mk(Expr e, int line) { e.line = line; return e; }

    private Expr ternary() {
        Expr c = or();
        if (at("?")) {
            int line = next().line;
            Expr a = ternary();
            expect(":");
            Expr b = ternary();
            return mk(new Ternary(c, a, b), line);
        }
        return c;
    }

    private Expr or() {
        Expr a = xor();
        while (at("||") || at("or")) { int l = next().line; a = fold(new Binary(Ast.OR, a, xor()), l); }
        return a;
    }

    private Expr xor() {
        Expr a = and();
        while (at("^^") || at("xor")) { int l = next().line; a = fold(new Binary(Ast.XOR, a, and()), l); }
        return a;
    }

    private Expr and() {
        Expr a = compare();
        while (at("&&") || at("and")) { int l = next().line; a = fold(new Binary(Ast.AND, a, compare()), l); }
        return a;
    }

    private Expr compare() {
        Expr a = bitwise();
        while (true) {
            Tok t = peek();
            if (t.type != T.OP) return a;
            int op;
            switch (t.text) {
                case "==": case "=": op = Ast.EQ; break;
                case "!=": case "<>": op = Ast.NE; break;
                case "<": op = Ast.LT; break;
                case "<=": op = Ast.LE; break;
                case ">": op = Ast.GT; break;
                case ">=": op = Ast.GE; break;
                default: return a;
            }
            next();
            a = fold(new Binary(op, a, bitwise()), t.line);
        }
    }

    private Expr bitwise() {
        Expr a = shift();
        while (true) {
            Tok t = peek();
            int op;
            if (t.is("&")) op = Ast.BAND;
            else if (t.is("|")) op = Ast.BOR;
            else if (t.is("^")) op = Ast.BXOR;
            else return a;
            next();
            a = fold(new Binary(op, a, shift()), t.line);
        }
    }

    private Expr shift() {
        Expr a = additive();
        while (at("<<") || at(">>")) {
            Tok t = next();
            a = fold(new Binary(t.text.equals("<<") ? Ast.SHL : Ast.SHR, a, additive()), t.line);
        }
        return a;
    }

    private Expr additive() {
        Expr a = multiplicative();
        while (at("+") || at("-")) {
            Tok t = next();
            a = fold(new Binary(t.text.equals("+") ? Ast.ADD : Ast.SUB, a, multiplicative()), t.line);
        }
        return a;
    }

    private Expr multiplicative() {
        Expr a = unary();
        while (true) {
            Tok t = peek();
            int op;
            if (t.is("*")) op = Ast.MUL;
            else if (t.is("/")) op = Ast.DIV;
            else if (t.is("div")) op = Ast.IDIV;
            else if (t.is("mod") || t.is("%")) op = Ast.MOD;
            else return a;
            next();
            a = fold(new Binary(op, a, unary()), t.line);
        }
    }

    private Expr unary() {
        Tok t = peek();
        if (t.is("-")) { next(); return fold(new Unary(Ast.NEG, unary()), t.line); }
        if (t.is("+")) { next(); return unary(); }
        if (t.is("!") || t.is("not")) { next(); return fold(new Unary(Ast.NOT, unary()), t.line); }
        if (t.is("~")) { next(); return fold(new Unary(Ast.BITNOT, unary()), t.line); }
        return postfix();
    }

    /** Constant folding for operations on constants. */
    private Expr fold(Expr e, int line) {
        e.line = line;
        try {
            if (e instanceof Binary) {
                Binary b = (Binary) e;
                if (b.a.isConst() && b.b.isConst()) return mk(new Const(b.eval(null)), line);
            } else if (e instanceof Unary) {
                Unary u = (Unary) e;
                if (u.e.isConst()) return mk(new Const(u.eval(null)), line);
            }
        } catch (ScriptError ignored) {
            // leave the error for runtime
        }
        return e;
    }

    private Expr postfix() {
        Expr e = primary();
        while (true) {
            Tok t = peek();
            if (t.is("[")) {
                next();
                Expr i = expr();
                Expr j = accept(",") ? expr() : null;
                expect("]");
                e = indexInto(e, i, j, t.line);
            } else if (t.is(".")) {
                next();
                String name = ident();
                e = field(e, name, t.line);
            } else return e;
        }
    }

    private Expr indexInto(Expr e, Expr i, Expr j, int line) {
        if (e instanceof BuiltinVar && ((BuiltinVar) e).index == null && Vars.instArray(((BuiltinVar) e).idx))
            return mk(new BuiltinVar(((BuiltinVar) e).idx, i), line);
        if (e instanceof GlobalBuiltin && ((GlobalBuiltin) e).index == null && Vars.globalArray(((GlobalBuiltin) e).idx))
            return mk(new GlobalBuiltin(((GlobalBuiltin) e).idx, i), line);
        if (e instanceof FieldBuiltin && ((FieldBuiltin) e).index == null && Vars.instArray(((FieldBuiltin) e).idx)) {
            FieldBuiltin f = (FieldBuiltin) e;
            return mk(new FieldBuiltin(f.target, f.idx, i), line);
        }
        if (e instanceof ArgMarker) return mk(new ArgIndex(i), line);
        return mk(new Index(e, i, j), line);
    }

    private Expr field(Expr target, String name, int line) {
        if (target instanceof ArgMarker) throw new ScriptError("Use argument[n]", line);
        if (target instanceof Const && V.toNum(((Const) target).v) == -5) { // global.
            int g = Vars.globalIndex(name);
            if (g >= 0) return mk(new GlobalBuiltin(g, null), line);
            return mk(new GlobalVar(name), line);
        }
        int bi = Vars.instIndex(name);
        if (bi >= 0) return mk(new FieldBuiltin(target, bi, null), line);
        return mk(new Field(target, name), line);
    }

    /** Placeholder for the bare word "argument" until it is indexed. */
    private static final class ArgMarker extends Expr {
        @Override public Object eval(Ctx c) { throw new ScriptError("Use argument[n]", line); }
    }

    private Expr primary() {
        Tok t = next();
        int line = t.line;
        switch (t.type) {
            case NUM: return mk(new Const(V.num(t.num)), line);
            case STR: return mk(new Const(t.text), line);
            case OP:
                if (t.text.equals("(")) {
                    Expr e = expr();
                    expect(")");
                    return e;
                }
                if (t.text.equals("[")) {
                    List<Expr> items = new ArrayList<>();
                    if (!at("]")) do { items.add(expr()); } while (accept(","));
                    expect("]");
                    return mk(new ArrayLit(items.toArray(new Expr[0])), line);
                }
                throw new ScriptError("Unexpected '" + t.text + "'", line);
            case EOF:
                throw new ScriptError("Unexpected end of code", line);
            default:
                break;
        }
        String name = t.text;
        if (at("(")) return call(name, line);
        return resolve(name, line);
    }

    private Expr call(String name, int line) {
        expect("(");
        List<Expr> args = new ArrayList<>();
        if (!at(")")) do { args.add(expr()); } while (accept(","));
        expect(")");
        Expr[] a = args.toArray(new Expr[0]);
        Compiler.ScriptRef sr = comp.script(name);
        if (sr != null) return mk(new ScriptCall(sr, a), line);
        Builtins.Info f = Builtins.function(name);
        if (f == null) throw new ScriptError("Unknown function or script '" + name + "'", line);
        if (a.length < f.min) throw new ScriptError(name + "() needs at least " + f.min + " argument(s): " + f.signature, line);
        if (a.length > f.max) throw new ScriptError(name + "() takes at most " + f.max + " argument(s): " + f.signature, line);
        return mk(new Call(name, f.fn, a), line);
    }

    private Expr resolve(String name, int line) {
        Integer slot = locals.get(name);
        if (slot != null) return mk(new Local(slot, name), line);
        switch (name) {
            case "self": return mk(new Const(V.num(-1)), line);
            case "other": return mk(new Const(V.num(-2)), line);
            case "all": return mk(new Const(V.num(-3)), line);
            case "noone": return mk(new Const(V.num(-4)), line);
            case "global": return mk(new Const(V.num(-5)), line);
            case "true": return mk(new Const(V.ONE), line);
            case "false": return mk(new Const(V.ZERO), line);
            case "argument": return mk(new ArgMarker(), line);
            case "argument_count": return mk(new ArgCount(), line);
        }
        if (name.startsWith("argument") && name.length() <= 10) {
            try {
                int i = Integer.parseInt(name.substring(8));
                if (i >= 0 && i < 16) return mk(new Arg(i), line);
            } catch (NumberFormatException ignored) { /* not an argument */ }
        }
        if (Builtins.KEYWORDS.contains(name)) throw new ScriptError("Unexpected keyword '" + name + "'", line);
        int bi = Vars.instIndex(name);
        if (bi >= 0) return mk(new BuiltinVar(bi, null), line);
        int gi = Vars.globalIndex(name);
        if (gi >= 0) return mk(new GlobalBuiltin(gi, null), line);
        Object k = Builtins.constantValue(name);
        if (k != null) return mk(new Const(k), line);
        Double r = comp.resources.get(name);
        if (r != null) return mk(new Const(V.num(r)), line);
        if (comp.globalvars.contains(name)) return mk(new GlobalVar(name), line);
        return mk(new SelfVar(name), line);
    }
}
