package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.lexer.MolangLexer;
import cn.solarmoon.spark_core.molang.lexer.TokenKind;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.StringQueryBinding;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** 校验 HUD 业务命名空间，阻止引擎对未绑定属性及省略索引的静默兜底。 */
public final class HudExpression {
    private static final Set<String> NAMESPACES = Set.of("hud", "weapon", "tube", "belt", "local", "global");
    private HudExpression() {}

    public static void validate(String expression, HudMolangContext context, String collection) {
        validate(expression, context, collection, null);
    }

    public static void validate(String expression, HudMolangContext context, String collection, String alias) {
        try (var lexer = MolangLexer.lexer(expression)) {
            var tokens = lexer.tokenizeAll();
            for (int i = 0; i + 2 < tokens.size(); i++) {
                if (tokens.get(i).kind() != TokenKind.IDENTIFIER || tokens.get(i + 1).kind() != TokenKind.DOT
                        || tokens.get(i + 2).kind() != TokenKind.IDENTIFIER) continue;
                String ns = tokens.get(i).value().toLowerCase(Locale.ROOT);
                String name = tokens.get(i + 2).value().toLowerCase(Locale.ROOT);
                if (alias != null && (ns.equals("variable") || ns.equals("v")) && alias.equals(name)
                        && i + 3 < tokens.size() && tokens.get(i + 3).kind() == TokenKind.EQ) {
                    throw new IllegalArgumentException("重复项下标为只读变量: " + alias);
                }
                if (!NAMESPACES.contains(ns)) continue;
                if (ns.equals("tube") && !"weapon.tubes".equals(collection)
                        || ns.equals("belt") && !"weapon.belts".equals(collection)) {
                    throw new IllegalArgumentException(ns + ".* 缺少对应的当前项作用域");
                }
                int arguments = 0;
                if (i + 3 < tokens.size() && tokens.get(i + 3).kind() == TokenKind.LPAREN) {
                    int depth = 0;
                    boolean content = false;
                    for (int j = i + 4; j < tokens.size(); j++) {
                        var token = tokens.get(j).kind();
                        if (token == TokenKind.RPAREN && depth == 0) { if (content) arguments++; break; }
                        if (token == TokenKind.COMMA && depth == 0) arguments++;
                        else content = true;
                        if (token == TokenKind.LPAREN || token == TokenKind.LBRACE || token == TokenKind.LBRACKET) depth++;
                        if (token == TokenKind.RPAREN || token == TokenKind.RBRACE || token == TokenKind.RBRACKET) depth--;
                    }
                }
                boolean bound = false;
                for (var method : context.getClass().getMethods()) {
                    var number = method.getAnnotation(QueryBinding.class);
                    var string = method.getAnnotation(StringQueryBinding.class);
                    if (method.getParameterCount() == arguments
                            && (number != null && number.namespace().equalsIgnoreCase(ns) && number.value().equalsIgnoreCase(name)
                            || string != null && string.namespace().equalsIgnoreCase(ns) && string.value().equalsIgnoreCase(name))) {
                        bound = true; break;
                    }
                }
                if (!bound) throw new IllegalArgumentException("HUD 查询未绑定或参数数量错误: " + ns + "." + name);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("HUD 表达式解析失败", e);
        }
    }
}
