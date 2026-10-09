package com.droidmaker.core.lang;

import com.droidmaker.core.runtime.Game;
import com.droidmaker.core.runtime.Instance;
import com.droidmaker.core.runtime.Vars;

import java.util.ArrayList;
import java.util.List;

/** Executable syntax tree. Expressions evaluate to values; statements return a flow code. */
public final class Ast {
    private Ast() {}

    public static final int NORMAL = 0, BREAK = 1, CONTINUE = 2, EXIT = 3;

    // ================================================================== expressions
    public abstract static class Expr {
        public int line;
        public abstract Object eval(Ctx c);
        public boolean isConst() { return false; }
    }

    /** Something that can be assigned to. */
    public abstract static class LExpr extends Expr {
        public abstract void set(Ctx c, Object v);
    }

    public static final class Const extends Expr {
        public final Object v;
        public Const(Object v) { this.v = v; }
        @Override public Object eval(Ctx c) { return v; }
        @Override public boolean isConst() { return true; }
    }

    public static final class Local extends LExpr {
        final int slot; final String name;
        public Local(int slot, String name) { this.slot = slot; this.name = name; }
        @Override public Object eval(Ctx c) { Object o = c.locals[slot]; return o == null ? V.ZERO : o; }
        @Override public void set(Ctx c, Object v) { c.locals[slot] = v; }
    }

    public static final class Arg extends LExpr {
        final int i;
        public Arg(int i) { this.i = i; }
        @Override public Object eval(Ctx c) { return i < c.args.length ? c.args[i] : V.ZERO; }
        @Override public void set(Ctx c, Object v) { if (i < c.args.length) c.args[i] = v; }
    }

    public static final class ArgIndex extends LExpr {
        final Expr idx;
        public ArgIndex(Expr idx) { this.idx = idx; }
        @Override public Object eval(Ctx c) {
            int i = V.toInt(idx.eval(c));
            return i >= 0 && i < c.args.length ? c.args[i] : V.ZERO;
        }
        @Override public void set(Ctx c, Object v) {
            int i = V.toInt(idx.eval(c));
            if (i >= 0 && i < c.args.length) c.args[i] = v;
        }
    }

    public static final class ArgCount extends Expr {
        @Override public Object eval(Ctx c) { return V.num(c.args.length); }
    }

    /** A user variable of the current instance. */
    public static final class SelfVar extends LExpr {
        final String name;
        public SelfVar(String name) { this.name = name.intern(); }
        @Override public Object eval(Ctx c) {
            if (c.self == null) return c.game.globalVar(name);
            return c.self.getVar(name);
        }
        @Override public void set(Ctx c, Object v) {
            if (c.self == null) c.game.setGlobalVar(name, v);
            else c.self.setVar(name, v);
        }
    }

    public static final class GlobalVar extends LExpr {
        final String name;
        public GlobalVar(String name) { this.name = name.intern(); }
        @Override public Object eval(Ctx c) { return c.game.globalVar(name); }
        @Override public void set(Ctx c, Object v) { c.game.setGlobalVar(name, v); }
    }

    /** Built-in instance variable (x, speed, alarm[n] ...) of self. */
    public static final class BuiltinVar extends LExpr {
        final int idx; final Expr index;
        public BuiltinVar(int idx, Expr index) { this.idx = idx; this.index = index; }
        @Override public Object eval(Ctx c) {
            if (c.self == null) throw new ScriptError("No instance for " + Vars.NAMES[idx], line);
            return Vars.getInst(c.self, idx, index == null ? 0 : V.toInt(index.eval(c)), c.game);
        }
        @Override public void set(Ctx c, Object v) {
            if (c.self == null) throw new ScriptError("No instance for " + Vars.NAMES[idx], line);
            Vars.setInst(c.self, idx, index == null ? 0 : V.toInt(index.eval(c)), v, c.game);
        }
    }

    /** Built-in global variable (score, room, mouse_x, view_xview[n] ...). */
    public static final class GlobalBuiltin extends LExpr {
        final int idx; final Expr index;
        public GlobalBuiltin(int idx, Expr index) { this.idx = idx; this.index = index; }
        @Override public Object eval(Ctx c) {
            return Vars.getGlobal(c.game, idx, index == null ? 0 : V.toInt(index.eval(c)), c);
        }
        @Override public void set(Ctx c, Object v) {
            Vars.setGlobal(c.game, idx, index == null ? 0 : V.toInt(index.eval(c)), v, c);
        }
    }

    /** target.name — a user variable on another instance/object. */
    public static final class Field extends LExpr {
        final Expr target; final String name;
        public Field(Expr target, String name) { this.target = target; this.name = name.intern(); }
        @Override public Object eval(Ctx c) {
            Instance i = c.game.firstTarget(target.eval(c), c);
            if (i == null) throw new ScriptError("No instance found to read '" + name + "'", line);
            return i.getVar(name);
        }
        @Override public void set(Ctx c, Object v) {
            Object t = target.eval(c);
            for (Instance i : c.game.targets(t, c)) i.setVar(name, v);
        }
    }

    public static final class FieldBuiltin extends LExpr {
        final Expr target; final int idx; final Expr index;
        public FieldBuiltin(Expr target, int idx, Expr index) { this.target = target; this.idx = idx; this.index = index; }
        @Override public Object eval(Ctx c) {
            Instance i = c.game.firstTarget(target.eval(c), c);
            if (i == null) throw new ScriptError("No instance found to read '" + Vars.NAMES[idx] + "'", line);
            return Vars.getInst(i, idx, index == null ? 0 : V.toInt(index.eval(c)), c.game);
        }
        @Override public void set(Ctx c, Object v) {
            Object t = target.eval(c);
            int k = index == null ? 0 : V.toInt(index.eval(c));
            for (Instance i : c.game.targets(t, c)) Vars.setInst(i, idx, k, v, c.game);
        }
    }

    /** base[i] or base[i, j] on an array value. */
    public static final class Index extends LExpr {
        final Expr base, i, j;
        public Index(Expr base, Expr i, Expr j) { this.base = base; this.i = i; this.j = j; }
        @Override public Object eval(Ctx c) {
            Object a = base.eval(c);
            int ii = V.toInt(i.eval(c));
            if (a instanceof GArray) {
                if (j == null) return ((GArray) a).get(0, ii);
                return ((GArray) a).get(ii, V.toInt(j.eval(c)));
            }
            if (a instanceof String && j == null) {
                String s = (String) a;
                return ii >= 0 && ii < s.length() ? String.valueOf(s.charAt(ii)) : "";
            }
            // GM8 quirk: a plain variable read as v[0] is the variable itself
            if (ii == 0 && (j == null || V.toInt(j.eval(c)) == 0)) return a;
            return V.ZERO;
        }
        @Override public void set(Ctx c, Object v) {
            if (!(base instanceof LExpr)) throw new ScriptError("Cannot assign to this array", line);
            Object a = base.eval(c);
            GArray arr;
            if (a instanceof GArray) arr = (GArray) a;
            else {
                arr = new GArray();
                if (a != null && !(a instanceof Double && (Double) a == 0)) arr.set(0, 0, a);
                ((LExpr) base).set(c, arr);
            }
            int ii = V.toInt(i.eval(c));
            if (j == null) arr.set(0, ii, v);
            else arr.set(ii, V.toInt(j.eval(c)), v);
        }
    }

    public static final class ArrayLit extends Expr {
        final Expr[] items;
        public ArrayLit(Expr[] items) { this.items = items; }
        @Override public Object eval(Ctx c) {
            GArray a = new GArray();
            for (int k = 0; k < items.length; k++) a.set(0, k, items[k].eval(c));
            if (items.length == 0) return a;
            return a;
        }
    }

    public static final class Ternary extends Expr {
        final Expr cond, a, b;
        public Ternary(Expr cond, Expr a, Expr b) { this.cond = cond; this.a = a; this.b = b; }
        @Override public Object eval(Ctx c) { return V.truth(cond.eval(c)) ? a.eval(c) : b.eval(c); }
    }

    public static final int NEG = 1, NOT = 2, BITNOT = 3;

    public static final class Unary extends Expr {
        final int op; final Expr e;
        public Unary(int op, Expr e) { this.op = op; this.e = e; }
        @Override public Object eval(Ctx c) {
            Object v = e.eval(c);
            switch (op) {
                case NEG:
                    if (v instanceof String) throw new ScriptError("Cannot negate a string", line);
                    return V.num(-V.toNum(v));
                case NOT: return V.bool(!V.truth(v));
                default: return V.num(~(long) V.toNum(v));
            }
        }
    }

    public static final int ADD = 1, SUB = 2, MUL = 3, DIV = 4, IDIV = 5, MOD = 6, AND = 7, OR = 8, XOR = 9,
            BAND = 10, BOR = 11, BXOR = 12, SHL = 13, SHR = 14, EQ = 15, NE = 16, LT = 17, LE = 18, GT = 19, GE = 20;

    public static final class Binary extends Expr {
        final int op; final Expr a, b;
        public Binary(int op, Expr a, Expr b) { this.op = op; this.a = a; this.b = b; }
        @Override public Object eval(Ctx c) {
            switch (op) {
                case AND: return V.bool(V.truth(a.eval(c)) && V.truth(b.eval(c)));
                case OR: return V.bool(V.truth(a.eval(c)) || V.truth(b.eval(c)));
                default: return apply(op, a.eval(c), b.eval(c), line);
            }
        }
    }

    public static Object apply(int op, Object x, Object y, int line) {
        switch (op) {
            case ADD: return V.add(x, y);
            case EQ: return V.bool(V.equal(x, y));
            case NE: return V.bool(!V.equal(x, y));
            case LT: return V.bool(V.compare(x, y) < 0);
            case LE: return V.bool(V.compare(x, y) <= 0);
            case GT: return V.bool(V.compare(x, y) > 0);
            case GE: return V.bool(V.compare(x, y) >= 0);
            case XOR: return V.bool(V.truth(x) ^ V.truth(y));
            case AND: return V.bool(V.truth(x) && V.truth(y));
            case OR: return V.bool(V.truth(x) || V.truth(y));
        }
        if (x instanceof String || y instanceof String) {
            if (op == MUL && x instanceof String && y instanceof Double) { // "ab" * 3 = "ababab"
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < (int) (double) (Double) y && k < 100000; k++) b.append(x);
                return b.toString();
            }
            throw new ScriptError("Wrong type of arguments to " + opName(op) + " (string)", line);
        }
        double p = V.toNum(x), q = V.toNum(y);
        switch (op) {
            case SUB: return V.num(p - q);
            case MUL: return V.num(p * q);
            case DIV:
                if (q == 0) throw new ScriptError("Division by zero", line);
                return V.num(p / q);
            case IDIV:
                if (q == 0) throw new ScriptError("Division by zero", line);
                return V.num((double) (long) (p / q));
            case MOD:
                if (q == 0) throw new ScriptError("Division by zero", line);
                return V.num(p % q);
            case BAND: return V.num((long) p & (long) q);
            case BOR: return V.num((long) p | (long) q);
            case BXOR: return V.num((long) p ^ (long) q);
            case SHL: return V.num((long) p << (long) q);
            case SHR: return V.num((long) p >> (long) q);
        }
        throw new ScriptError("Bad operator", line);
    }

    static String opName(int op) {
        switch (op) {
            case SUB: return "-"; case MUL: return "*"; case DIV: return "/"; case IDIV: return "div";
            case MOD: return "mod"; case BAND: return "&"; case BOR: return "|"; case BXOR: return "^";
            case SHL: return "<<"; case SHR: return ">>";
        }
        return "operator";
    }

    /** Call of a built-in function. */
    public static final class Call extends Expr {
        final Builtins.Fn fn; final Expr[] args; final String name;
        public Call(String name, Builtins.Fn fn, Expr[] args) { this.name = name; this.fn = fn; this.args = args; }
        @Override public Object eval(Ctx c) {
            Object[] v = new Object[args.length];
            for (int k = 0; k < args.length; k++) v[k] = args[k].eval(c);
            try {
                Object r = fn.call(c, v);
                return r == null ? V.ZERO : r;
            } catch (ScriptError e) {
                throw e.at(line);
            } catch (RuntimeException e) {
                throw new ScriptError("In " + name + "(): " + e, line);
            }
        }
    }

    /** Call of a project script, resolved after all scripts are known. */
    public static final class ScriptCall extends Expr {
        final Compiler.ScriptRef ref; final Expr[] args;
        public ScriptCall(Compiler.ScriptRef ref, Expr[] args) { this.ref = ref; this.args = args; }
        @Override public Object eval(Ctx c) {
            Object[] v = new Object[args.length];
            for (int k = 0; k < args.length; k++) v[k] = args[k].eval(c);
            if (ref.code == null) throw new ScriptError("Script " + ref.name + " failed to compile", line);
            return ref.code.call(c, v);
        }
    }

    // ================================================================== statements
    public abstract static class Stmt {
        public int line;
        public abstract int exec(Ctx c);
    }

    public static final class Block extends Stmt {
        final Stmt[] body;
        public Block(Stmt[] body) { this.body = body; }
        @Override public int exec(Ctx c) {
            for (Stmt s : body) {
                int r = s.exec(c);
                if (r != NORMAL) return r;
            }
            return NORMAL;
        }
    }

    public static final class ExprStmt extends Stmt {
        final Expr e;
        public ExprStmt(Expr e) { this.e = e; }
        @Override public int exec(Ctx c) { e.eval(c); return NORMAL; }
    }

    public static final class Assign extends Stmt {
        final LExpr target; final int op; final Expr value;
        public Assign(LExpr target, int op, Expr value) { this.target = target; this.op = op; this.value = value; }
        @Override public int exec(Ctx c) {
            try {
                if (op == 0) target.set(c, value.eval(c));
                else target.set(c, apply(op, target.eval(c), value.eval(c), line));
            } catch (ScriptError e) {
                throw e.at(line);
            }
            return NORMAL;
        }
    }

    public static final class If extends Stmt {
        final Expr cond; final Stmt then, els;
        public If(Expr cond, Stmt then, Stmt els) { this.cond = cond; this.then = then; this.els = els; }
        @Override public int exec(Ctx c) {
            if (V.truth(cond.eval(c))) return then.exec(c);
            return els == null ? NORMAL : els.exec(c);
        }
    }

    public static final class While extends Stmt {
        final Expr cond; final Stmt body;
        public While(Expr cond, Stmt body) { this.cond = cond; this.body = body; }
        @Override public int exec(Ctx c) {
            while (V.truth(cond.eval(c))) {
                c.tick(line);
                int r = body.exec(c);
                if (r == BREAK) break;
                if (r == EXIT) return EXIT;
            }
            return NORMAL;
        }
    }

    public static final class DoUntil extends Stmt {
        final Stmt body; final Expr cond;
        public DoUntil(Stmt body, Expr cond) { this.body = body; this.cond = cond; }
        @Override public int exec(Ctx c) {
            do {
                c.tick(line);
                int r = body.exec(c);
                if (r == BREAK) break;
                if (r == EXIT) return EXIT;
            } while (!V.truth(cond.eval(c)));
            return NORMAL;
        }
    }

    public static final class For extends Stmt {
        final Stmt init, step, body; final Expr cond;
        public For(Stmt init, Expr cond, Stmt step, Stmt body) { this.init = init; this.cond = cond; this.step = step; this.body = body; }
        @Override public int exec(Ctx c) {
            if (init != null) init.exec(c);
            while (cond == null || V.truth(cond.eval(c))) {
                c.tick(line);
                int r = body.exec(c);
                if (r == BREAK) break;
                if (r == EXIT) return EXIT;
                if (step != null) step.exec(c);
            }
            return NORMAL;
        }
    }

    public static final class Repeat extends Stmt {
        final Expr count; final Stmt body;
        public Repeat(Expr count, Stmt body) { this.count = count; this.body = body; }
        @Override public int exec(Ctx c) {
            long n = Math.round(V.toNum(count.eval(c)));
            for (long k = 0; k < n; k++) {
                c.tick(line);
                int r = body.exec(c);
                if (r == BREAK) break;
                if (r == EXIT) return EXIT;
            }
            return NORMAL;
        }
    }

    public static final class With extends Stmt {
        final Expr target; final Stmt body;
        public With(Expr target, Stmt body) { this.target = target; this.body = body; }
        @Override public int exec(Ctx c) {
            Object t = target.eval(c);
            List<Instance> list = new ArrayList<>(c.game.targets(t, c));
            Instance oldSelf = c.self, oldOther = c.other;
            try {
                for (Instance i : list) {
                    if (i.destroyed) continue;
                    c.other = oldSelf;
                    c.self = i;
                    int r = body.exec(c);
                    if (r == BREAK) break;
                    if (r == EXIT) return EXIT;
                }
            } finally {
                c.self = oldSelf;
                c.other = oldOther;
            }
            return NORMAL;
        }
    }

    public static final class Switch extends Stmt {
        final Expr value; final Expr[] cases; final int[] caseStart; final int defaultStart; final Stmt[] body;
        public Switch(Expr value, Expr[] cases, int[] caseStart, int defaultStart, Stmt[] body) {
            this.value = value; this.cases = cases; this.caseStart = caseStart; this.defaultStart = defaultStart; this.body = body;
        }
        @Override public int exec(Ctx c) {
            Object v = value.eval(c);
            int start = -1;
            for (int k = 0; k < cases.length; k++) if (V.equal(v, cases[k].eval(c))) { start = caseStart[k]; break; }
            if (start < 0) start = defaultStart;
            if (start < 0) return NORMAL;
            for (int k = start; k < body.length; k++) {
                int r = body[k].exec(c);
                if (r == BREAK) break;
                if (r != NORMAL) return r;
            }
            return NORMAL;
        }
    }

    public static final class Flow extends Stmt {
        final int code;
        public Flow(int code) { this.code = code; }
        @Override public int exec(Ctx c) { return code; }
    }

    public static final class Return extends Stmt {
        final Expr e;
        public Return(Expr e) { this.e = e; }
        @Override public int exec(Ctx c) { c.ret = e == null ? V.ZERO : e.eval(c); return EXIT; }
    }

    public static final class Empty extends Stmt {
        @Override public int exec(Ctx c) { return NORMAL; }
    }

    /** Initializes declared locals ("var a, b = 2;"). */
    public static final class VarInit extends Stmt {
        final int[] slots; final Expr[] inits;
        public VarInit(int[] slots, Expr[] inits) { this.slots = slots; this.inits = inits; }
        @Override public int exec(Ctx c) {
            for (int k = 0; k < slots.length; k++)
                c.locals[slots[k]] = inits[k] == null ? V.ZERO : inits[k].eval(c);
            return NORMAL;
        }
    }

    /** globalvar declarations at runtime: ensure the global exists. */
    public static final class GlobalDecl extends Stmt {
        final String[] names;
        public GlobalDecl(String[] names) { this.names = names; }
        @Override public int exec(Ctx c) {
            Game g = c.game;
            for (String n : names) if (!g.hasGlobalVar(n)) g.setGlobalVar(n, V.ZERO);
            return NORMAL;
        }
    }
}
