package io.github.sweetzonzi.machinemax.external;

public class CommentRemover {
    public static String removeComments(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false; // 是否在字符串内（双引号中）
        boolean isEscaped = false; // 当前字符是否为转义字符（如 \\"）
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            // 处理转义字符
            if (c == '\\' && !isEscaped) {
                isEscaped = true;
                result.append(c);
                continue;
            }
            
            // 处理字符串边界
            if (c == '"' && !isEscaped) {
                inString = !inString;
            }
            
            // 检测注释开始（不在字符串内）
            if (!inString && c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                // 跳过当前行剩余字符
                while (i < json.length() && json.charAt(i) != '\n') {
                    i++;
                }
                if (i < json.length()) {
                    result.append('\n'); // 保留换行符
                }
                continue;
            }
            
            result.append(c);
            isEscaped = false;
        }
        return result.toString();
    }
}