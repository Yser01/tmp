package com.cestc.dc.apihandler.deptTree;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;

/**
 * @author Yibowen
 * @date 2023-04-02
 */
import java.util.*;

public class FuzzySearchTree {
    /**
     * TrieNode 类，包含子节点映射和部门名称 Set。
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        Set<String> data = new HashSet<>();
    }

    private final TrieNode root = new TrieNode();

    /**
     * 使用部门列表构建 FuzzySearchTree 实例。
     *
     * @param data         部门名称列表。
     * @param enablePinyin 是否启用拼音搜索。
     */
    public FuzzySearchTree(List<String> data, boolean enablePinyin) {
        data.forEach(word -> {
            if (StringUtils.isNotBlank(word)) {
                // 处理中文名称
                for (int i = 0; i < word.length(); i++) {
                    for (int j = i + 1; j <= word.length(); j++) {
                        insert(word.substring(i, j), word);
                    }
                }
                if (enablePinyin) {
                    // 处理拼音
                    String pinyin = convertToPinyin(word);
                    for (int i = 0; i < pinyin.length(); i++) {
                        for (int j = i + 1; j <= pinyin.length(); j++) {
                            insert(pinyin.substring(i, j), word);
                        }
                    }
                }
            }
        });
    }

    /**
     * 将部门名称的子字符串插入到 Trie 中。
     *
     * @param subString 部门名称的子字符串。
     * @param word      完整的部门名称。
     */
    private void insert(String subString, String word) {
        subString = subString.toLowerCase();
        TrieNode node = root;
        // 遍历子字符串的字符
        for (char c : subString.toCharArray()) {
            // 如果字符不存在，创建新节点
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        // 将部门名称添加到节点的部门集合中
        node.data.add(word);
    }

    /**
     * 根据查询字符串搜索匹配的部门名称。
     *
     * @param query 查询字符串。
     * @return 匹配的部门名称列表。
     */
    public List<String> search(String query) {
        query = query.toLowerCase();
        TrieNode node = root;
        // 遍历查询字符串的字符
        for (char c : query.toCharArray()) {
            // 获取子节点
            node = node.children.get(c);
            if (node == null) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>(node.data);
    }

    /**
     * 将中文部门名称转换为拼音。
     *
     * @param chinese 中文部门名称。
     * @return 拼音字符串。
     */
    private String convertToPinyin(String chinese) {
        HanyuPinyinOutputFormat outputFormat = new HanyuPinyinOutputFormat();
        outputFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        outputFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);

        StringBuilder pinyin = new StringBuilder();
        for (char ch : chinese.toCharArray()) {
            try {
                // 只转换中文字符，保留其他字符（如英文字母）
                if (Character.toString(ch).matches("[\\u4E00-\\u9FA5]+")) {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(ch, outputFormat);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        pinyin.append(pinyinArray[0]); // 只获取第一个拼音
                    }
                } else {
                    pinyin.append(ch);
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                e.printStackTrace();
            }
        }
        return pinyin.toString();
    }
}
