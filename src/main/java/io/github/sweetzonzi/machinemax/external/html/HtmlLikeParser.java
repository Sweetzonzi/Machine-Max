package io.github.sweetzonzi.machinemax.external.html;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class HtmlLikeParser {

    public static HtNode parse(String input) {
        input = "<root>" + input + "</root>"; // 包裹根标签

        Stack<TagHtNode> stack = new Stack<>();
        StringBuilder textBuffer = new StringBuilder();
        HtNode root = null;
        int index = 0;
        int length = input.length();

        while (index < length) {
            char currentChar = input.charAt(index);

            if (currentChar == '<') {
                // 遇到标签时，处理已缓存的文本内容
                if (textBuffer.length() > 0) {
                    String text = textBuffer.toString(); // 删除.trim()保留原始空格
                    addTextNode(stack, text);
                    textBuffer.setLength(0);
                }

                // 处理标签
                boolean isClosingTag = false;
                index++; // 跳过'<'

                // 判断是否为闭合标签
                if (index < length && input.charAt(index) == '/') {
                    isClosingTag = true;
                    index++;
                }

                // 提取标签名（标签内部仍保留trim处理）
                int endIndex = input.indexOf('>', index);
                if (endIndex == -1) throw new IllegalArgumentException("Unclosed tag");
                String tagName = input.substring(index, endIndex).trim(); // 标签名去前后空格
                index = endIndex + 1;

                if (isClosingTag) {
                    // 闭合标签：校验栈并构建层级
                    if (stack.isEmpty()) throw new IllegalArgumentException("Extra closing tag: </" + tagName + ">");
                    TagHtNode currentTag = stack.pop();

                    if (!currentTag.getTagName().equals(tagName)) {
                        throw new IllegalArgumentException("Tag mismatch: <" + currentTag.getTagName() + "> vs </" + tagName + ">");
                    }

                    if (stack.isEmpty()) root = currentTag;
                } else {
                    // 开始标签：入栈并建立父子关系
                    TagHtNode newTag = new TagHtNode(tagName);
                    if (!stack.isEmpty()) stack.peek().addChild(newTag);
                    stack.push(newTag);
                }
            } else {
                // 非标签字符：缓存文本内容
                textBuffer.append(currentChar);
                index++;
            }
        }

        // 处理末尾的文本内容
        if (textBuffer.length() > 0) {
            String text = textBuffer.toString();
            addTextNode(stack, text);
        }

        // 校验未闭合标签
        if (root == null && !stack.isEmpty()) {
            throw new IllegalArgumentException("Unclosed tag: <" + stack.peek().getTagName() + ">");
        }
        return root;
    }

    private static void addTextNode(Stack<TagHtNode> stack, String text) {
        if (!stack.isEmpty()) {
            // 记录文本所在的所有外层标签
            List<String> enclosingTags = new ArrayList<>();
            for (TagHtNode tag : stack) enclosingTags.add(tag.getTagName());
            stack.peek().addChild(new TextHtNode(text, enclosingTags));
        } else {
            throw new IllegalArgumentException("Orphan text: \"" + text + "\"");
        }
    }

    public static void main(String[] args) {
        String input = "<italic><test_font>        This is a test   </test_font><tag2>I am text2</tag2></italic>";
        try {
            HtNode root = parse(input);
            printTree(root, 0);
        } catch (Exception e) {
            System.err.println("Parse failed: " + e.getMessage());
        }
    }

    private static void printTree(HtNode node, int indent) {
        String padding = "  ".repeat(indent);
        if (node.isText()) {
            TextHtNode textNode = (TextHtNode) node;
            System.out.println(padding + "Text: \"" + textNode.getText() + "\"");
            System.out.println(padding + "Enclosing Tags: " + textNode.getEnclosingTags());
        } else {
            TagHtNode tagNode = (TagHtNode) node;
            System.out.println(padding + "Tag: <" + tagNode.getTagName() + ">");
            for (HtNode child : tagNode.getChildren()) printTree(child, indent + 1);
        }
    }
}