package io.github.portaldalaran.taming.utils;


/**
 * @author aohee@163.com
 * @version 0.1
 */
public class CharSequenceUtils {
    /**
     * 首字母小写
     * @param str in string
     * @return string
     */
    public static String lowerFirst(String str) {
        char[] chars = str.toCharArray();
        //ASCII  A-Z  65-90  a-z  97-122
        if (chars[0] >= 65 && chars[0] <= 90) {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }

    /**
     * 首字母大写
     * @param str in string
     * @return string
     */
    public static String upperFirst(String str) {
        char[] chars = str.toCharArray();
        //ASCII  A-Z  65-90  a-z  97-122
        if (chars[0] >= 97 && chars[0] <= 122) {
            chars[0] -= 32;
        }
        return String.valueOf(chars);
    }

}
