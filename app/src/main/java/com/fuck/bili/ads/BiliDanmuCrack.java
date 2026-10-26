package com.fuck.bili.ads;

import java.util.ArrayList;
import java.util.List;

public class BiliDanmuCrack {
    private static final long CRCPOLYNOMIAL = 0xEDB88320L;
    private static final long[] crctable = new long[256];

    static {
        for (int i = 0; i < 256; i++) {
            long crcreg = i;
            for (int j = 0; j < 8; j++) {
                if ((crcreg & 1) != 0) {
                    crcreg = CRCPOLYNOMIAL ^ (crcreg >>> 1);
                } else {
                    crcreg >>>= 1;
                }
            }
            crctable[i] = crcreg;
        }
    }

    private static long crc32(byte[] input, int len) {
        long crcstart = 0xFFFFFFFFL;
        for (int i = 0; i < len; ++i) {
            int index = (int) ((crcstart ^ input[i]) & 0xFF);
            crcstart = (crcstart >>> 8) ^ crctable[index];
        }
        return crcstart;
    }

    private static int crc32lastindex(byte[] input, int len) {
        long crcstart = 0xFFFFFFFFL;
        int index = 0;
        for (int i = 0; i < len; ++i) {
            index = (int) ((crcstart ^ input[i]) & 0xFF);
            crcstart = (crcstart >>> 8) ^ crctable[index];
        }
        return index;
    }

    private static int getcrcindex(long t) {
        for (int i = 0; i < 256; i++) {
            if ((crctable[i] >>> 24) == t) {
                return i;
            }
        }
        return -1;
    }

    private static String deepCheck(byte[] iBytes, int len, int[] index) {
        long hash = crc32(iBytes, len);
        long tc = (hash & 0xFF) ^ index[2];
        if (!(tc <= 57 && tc >= 48)) return null;

        StringBuilder str = new StringBuilder();
        str.append((char) tc);

        hash = crctable[index[2]] ^ (hash >>> 8);
        tc = (hash & 0xFF) ^ index[1];
        if (!(tc <= 57 && tc >= 48)) return null;
        str.append((char) tc);

        hash = crctable[index[1]] ^ (hash >>> 8);
        tc = (hash & 0xFF) ^ index[0];
        if (!(tc <= 57 && tc >= 48)) return null;
        str.append((char) tc);

        return str.toString();
    }

    // 核心修改：改为返回 List<String> 局部变量，防止状态污染
    public static List<String> crack(String input) {
        java.util.LinkedHashSet<String> resultSet = new java.util.LinkedHashSet<>();
        List<String> resultList = new ArrayList<>();
        if (input == null || !input.trim().matches("(?i)[0-9a-f]{1,8}")) {
            return resultList;
        }
        input = input.trim();
        long ht;
        try {
            ht = Long.parseLong(input, 16) ^ 0xFFFFFFFFL;
        } catch (Exception e) {
            MainHook.log(e.toString());
            return resultList;
        }

        int[] index = new int[4];
        for (int i = 3; i >= 0; i--) {
            index[3 - i] = getcrcindex(ht >>> (i * 8));
            if (index[3 - i] < 0) {
                return resultList;
            }
            long snum = crctable[index[3 - i]];
            ht ^= (snum >>> ((3 - i) * 8));
        }

        // The reverse-prefix algorithm appends three digits, so check shorter UIDs separately.
        for (int uid = 1; uid <= 999; uid++) {
            String candidate = String.valueOf(uid);
            if (matches(candidate, input)) {
                resultSet.add(candidate);
            }
        }
        byte[] bytes = new byte[10];

        for (int i = 0; i < 100000000; i++) {
            int len = 0;
            int temp = i;

            if (temp == 0) {
                bytes[0] = '0';
                len = 1;
            } else {
                int p = 0;
                while (temp > 0) {
                    bytes[p++] = (byte) ('0' + (temp % 10));
                    temp /= 10;
                }
                len = p;
                for (int l = 0, r = len - 1; l < r; l++, r--) {
                    byte b = bytes[l];
                    bytes[l] = bytes[r];
                    bytes[r] = b;
                }
            }

            int lastindex = crc32lastindex(bytes, len);
            if (lastindex == index[3]) {
                String deepCheckData = deepCheck(bytes, len, index);
                if (deepCheckData != null) {
                    String candidate = i + deepCheckData;
                    if (candidate.charAt(0) != '0' && matches(candidate, input)) {
                        resultSet.add(candidate);
                    }
                }
            }
        }
        resultList.addAll(resultSet);
        return resultList;
    }

    public static boolean matches(String uid, String hash) {
        if (uid == null || hash == null || !uid.matches("[1-9][0-9]*")) {
            return false;
        }
        try {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(uid.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return crc.getValue() == Long.parseLong(hash, 16);
        } catch (Exception e) {
            return false;
        }
    }
}