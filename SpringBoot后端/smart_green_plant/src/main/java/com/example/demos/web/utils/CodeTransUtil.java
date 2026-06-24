package com.example.demos.web.utils;
import org.apache.commons.lang3.StringEscapeUtils;


public class CodeTransUtil {
    public static String decodeUnicode(String unicodeStr) {
        return StringEscapeUtils.unescapeJava(unicodeStr);
    }


}
