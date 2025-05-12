package io.github.tt432.machinemax.external.htlike;

import java.util.Stack;
import java.util.ArrayList;
import java.util.List;

public class HtmlLikeParser {

    public static HtNode parse(String input) {
        input = "<root>" + input + "</root>";

        Stack<TagHtNode> stack = new Stack<>();
        StringBuilder textBuffer = new StringBuilder();
        HtNode root = null;
        int index = 0;
        int length = input.length();

        while (index < length) {
            char currentChar = input.charAt(index);

            if (currentChar == '<') {
                if (textBuffer.length() > 0) {
                    String text = textBuffer.toString().trim();
                    if (!text.isEmpty()) {
                        addTextNode(stack, text);
                    }
                    textBuffer.setLength(0);
                }

                boolean isClosingTag = false;
                index++;

                if (index < length && input.charAt(index) == '/') {
                    isClosingTag = true;
                    index++;
                }

                int endIndex = input.indexOf('>', index);
                if (endIndex == -1) throw new IllegalArgumentException("未闭合的标签");
                String tagName = input.substring(index, endIndex).trim();
                index = endIndex + 1;

                if (isClosingTag) {
                    if (stack.isEmpty()) throw new IllegalArgumentException("多余的关闭标签: </" + tagName + ">");
                    TagHtNode currentTag = stack.pop();

                    if (!currentTag.getTagName().equals(tagName)) {
                        throw new IllegalArgumentException("标签不匹配: <" + currentTag.getTagName() + "> vs </" + tagName + ">");
                    }

                    if (stack.isEmpty()) {
                        root = currentTag;
                    }
                    // 删除错误添加子节点的代码
                } else {
                    TagHtNode newTag = new TagHtNode(tagName);
                    if (!stack.isEmpty()) {
                        stack.peek().addChild(newTag);
                    }
                    stack.push(newTag);
                }
            } else {
                textBuffer.append(currentChar);
                index++;
            }
        }

        if (textBuffer.length() > 0) {
            String text = textBuffer.toString().trim();
            if (!text.isEmpty()) {
                addTextNode(stack, text);
            }
        }

        if (root == null && !stack.isEmpty()) {
            throw new IllegalArgumentException("存在未闭合的标签: <" + stack.peek().getTagName() + ">");
        }
        return root;
    }

    private static void addTextNode(Stack<TagHtNode> stack, String text) {
        if (!stack.isEmpty()) {
            List<String> enclosingTags = new ArrayList<>();
            for (TagHtNode tag : stack) {
                enclosingTags.add(tag.getTagName());
            }
            stack.peek().addChild(new TextHtNode(text, enclosingTags));
        } else {
            throw new IllegalArgumentException("文本不在标签内: \"" + text + "\"");
        }
    }

    public static void main(String[] args) {
        String input = "<italic><test_font>This is a test</test_font><tag2>I am text2</tag2></italic>";
        try {
            HtNode root = parse(input);
            printTree(root, 0);
        } catch (Exception e) {
            System.err.println("解析失败: " + e.getMessage());
        }
    }

    private static void printTree(HtNode htNode, int indent) {
        String padding = "  ".repeat(indent);
        if (htNode.isText()) {
            TextHtNode textNode = (TextHtNode) htNode;
            System.out.println(padding + "Text: \"" + textNode.getText() + "\"");
            System.out.println(padding + "Enclosing Tags: " + textNode.getEnclosingTags());
        } else {
            TagHtNode tagNode = (TagHtNode) htNode;
            System.out.println(padding + "Tag: <" + tagNode.getTagName() + ">");
            for (HtNode child : tagNode.getChildren()) {
                printTree(child, indent + 1);
            }
        }
    }
}