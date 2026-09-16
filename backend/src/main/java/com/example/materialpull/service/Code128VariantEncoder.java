package com.example.materialpull.service;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Reader;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为相同明文生成不同、但解码结果完全相同的 CODE_128 编码。
 *
 * <p>仓库标签使用六位纯数字。六位数字优先枚举 Code A/B/C 的紧凑合法路径，
 * 每条路径最多使用三个 SHIFT/字符集切换码，并按总模块数从短到长排序。
 * 这样既能为同一仓库代号提供大量互不相同的条纹，又不会像逐字符随机切换那样
 * 把条码挤压到 203dpi 下只有一两个打印点宽。</p>
 */
final class Code128VariantEncoder {
    private static final int CODE_SHIFT = 98;
    private static final int CODE_CODE_C = 99;
    private static final int CODE_CODE_B = 100;
    private static final int CODE_CODE_A = 101;
    private static final int CODE_START_A = 103;
    private static final int CODE_START_B = 104;
    private static final int CODE_START_C = 105;
    private static final int CODE_STOP = 106;

    /** CODE_128 左右各保留 10 个窄模块静区。 */
    private static final int QUIET_ZONE_MODULES = 10;
    /** 203dpi 标签按 4 点/窄模块输出，约等于 0.50mm。 */
    private static final int PRINT_MODULE_DOTS = 4;
    private static final int MAX_COMPACT_CONTROLS = 3;
    private static final int[][] CODE_PATTERNS = loadCodePatterns();
    private static final List<EncodingPlan> SIX_DIGIT_PLANS = buildSixDigitPlans();

    private Code128VariantEncoder() {}

    /** 兼容旧调用：任务号只作为紧凑变体池索引种子，不再逐字符制造随机控制码。 */
    static Variant encode(String text, String variantKey, int width, int height) {
        return encode(text, null, variantKey, width, height);
    }

    static Variant encode(String text, Integer variantNo, String variantKey, int width, int height) {
        if (!supports(text)) {
            throw new IllegalArgumentException("CODE_128 变体仅支持可打印 ASCII（^、~ 除外）");
        }
        if (isSixDigit(text)) {
            if (variantNo != null && variantNo < 0) {
                throw new IllegalArgumentException("六位仓库代号条码变体编号不能为负数：" + variantNo);
            }
            int index = variantNo == null
                    ? hashIndex(variantKey == null ? "" : variantKey, SIX_DIGIT_PLANS.size())
                    : variantNo % SIX_DIGIT_PLANS.size();
            return encodePlan(text, SIX_DIGIT_PLANS.get(index), width, height);
        }
        return encodeLegacy(text, variantKey, width, height);
    }

    static String zplFieldData(String text, Integer variantNo, String variantKey) {
        if (!supports(text) || variantKey == null && variantNo == null) return null;
        return encode(text, variantNo, variantKey, 1, 1).zplFieldData();
    }

    static String zplFieldData(String text, String variantKey) {
        return zplFieldData(text, null, variantKey);
    }

    static int sixDigitVariantCapacity() {
        return SIX_DIGIT_PLANS.size();
    }

    static boolean isSixDigit(String text) {
        if (text == null || text.length() != 6) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') return false;
        }
        return true;
    }

    static boolean supports(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // ^ 与 ~ 是 ZPL 命令前缀；仓库代号正常为六位数字。
            if (c < 32 || c > 126 || c == '^' || c == '~') return false;
        }
        return true;
    }

    private static Variant encodePlan(String text, EncodingPlan plan, int width, int height) {
        List<Integer> codewords = new ArrayList<>();
        StringBuilder zpl = new StringBuilder(32);
        codewords.add(startCode(plan.startSet()));
        zpl.append(startInvocation(plan.startSet()));

        for (PlanOp op : plan.operations()) {
            switch (op.kind()) {
                case CONTROL -> {
                    codewords.add(op.value());
                    zpl.append(controlInvocation(op.value()));
                }
                case CHARACTER -> {
                    char c = text.charAt(op.position());
                    codewords.add(c - 32);
                    if (op.dataSet() == CodeSet.A) {
                        int value = c - 32;
                        if (value < 10) zpl.append('0');
                        zpl.append(value);
                    } else {
                        zpl.append(c);
                    }
                }
                case DIGIT_PAIR -> {
                    String pair = text.substring(op.position(), op.position() + 2);
                    codewords.add(Integer.parseInt(pair));
                    zpl.append(pair);
                }
            }
        }
        appendChecksumAndStop(codewords);
        return variant(codewords, zpl.toString(), width, height, true);
    }

    /**
     * 非六位历史数据沿用 A/B 合法路径；生产仓库码均走上面的紧凑数字路径。
     */
    private static Variant encodeLegacy(String text, String variantKey, int width, int height) {
        BitSource bits = new BitSource(sha256(variantKey == null ? "" : variantKey));
        boolean codeA = text.charAt(0) <= 95 && bits.next();
        List<Integer> codewords = new ArrayList<>();
        StringBuilder zpl = new StringBuilder(text.length() * 4 + 2);
        codewords.add(codeA ? CODE_START_A : CODE_START_B);
        zpl.append(codeA ? ">9" : ">:");

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean sharedByAAndB = c <= 95;
            if (sharedByAAndB) {
                boolean wantedA = bits.next();
                boolean switched = wantedA != codeA;
                if (switched) {
                    codeA = wantedA;
                    codewords.add(codeA ? CODE_CODE_A : CODE_CODE_B);
                    zpl.append(codeA ? ">7" : ">6");
                }
                boolean shifted = !switched && bits.next();
                if (shifted) {
                    codewords.add(CODE_SHIFT);
                    zpl.append(">4");
                }
                appendDataCodeword(codewords, c, shifted ? !codeA : codeA);
                appendZplCharacter(zpl, c, shifted ? !codeA : codeA);
            } else {
                if (codeA) {
                    codeA = false;
                    codewords.add(CODE_CODE_B);
                    zpl.append(">6");
                }
                appendDataCodeword(codewords, c, false);
                appendZplCharacter(zpl, c, false);
            }
        }
        appendChecksumAndStop(codewords);
        return variant(codewords, zpl.toString(), width, height, false);
    }

    private static List<EncodingPlan> buildSixDigitPlans() {
        Map<String, EncodingPlan> unique = new LinkedHashMap<>();
        for (CodeSet start : CodeSet.values()) {
            enumeratePlans(0, start, start, 0, new ArrayList<>(), unique);
        }
        List<EncodingPlan> plans = new ArrayList<>(unique.values());
        plans.sort(Comparator.comparingInt(EncodingPlan::symbolModules)
                .thenComparing(EncodingPlan::signature));
        return List.copyOf(plans);
    }

    private static void enumeratePlans(int position, CodeSet startSet, CodeSet set, int controls,
                                       List<PlanOp> operations, Map<String, EncodingPlan> unique) {
        if (position == 6) {
            EncodingPlan plan = new EncodingPlan(startSet, List.copyOf(operations));
            String signature = planSignature(plan);
            unique.putIfAbsent(signature, plan.withSignature(signature));
            return;
        }

        if (set == CodeSet.C && position + 1 < 6) {
            addAndWalk(new PlanOp(OpKind.DIGIT_PAIR, position, 0, CodeSet.C),
                    position + 2, startSet, set, controls, operations, unique);
        }
        if (set != CodeSet.C) {
            addAndWalk(new PlanOp(OpKind.CHARACTER, position, 0, set),
                    position + 1, startSet, set, controls, operations, unique);
            if (controls < MAX_COMPACT_CONTROLS) {
                operations.add(new PlanOp(OpKind.CONTROL, -1, CODE_SHIFT, null));
                addAndWalk(new PlanOp(OpKind.CHARACTER, position, 0,
                                set == CodeSet.A ? CodeSet.B : CodeSet.A),
                        position + 1, startSet, set, controls + 1, operations, unique);
                operations.remove(operations.size() - 1);
            }
        }
        if (controls < MAX_COMPACT_CONTROLS) {
            for (CodeSet next : CodeSet.values()) {
                if (next == set) continue;
                int control = latchCode(next);
                operations.add(new PlanOp(OpKind.CONTROL, -1, control, null));
                if (next == CodeSet.C) {
                    if (position + 1 < 6) {
                        addAndWalk(new PlanOp(OpKind.DIGIT_PAIR, position, 0, CodeSet.C),
                                position + 2, startSet, next, controls + 1, operations, unique);
                    }
                } else {
                    addAndWalk(new PlanOp(OpKind.CHARACTER, position, 0, next),
                            position + 1, startSet, next, controls + 1, operations, unique);
                }
                operations.remove(operations.size() - 1);
            }
        }
    }

    private static void addAndWalk(PlanOp operation, int nextPosition, CodeSet startSet,
                                   CodeSet nextSet, int controls, List<PlanOp> operations,
                                   Map<String, EncodingPlan> unique) {
        operations.add(operation);
        enumeratePlans(nextPosition, startSet, nextSet, controls, operations, unique);
        operations.remove(operations.size() - 1);
    }

    private static String planSignature(EncodingPlan plan) {
        StringBuilder signature = new StringBuilder();
        signature.append(startCode(plan.startSet()));
        for (PlanOp op : plan.operations()) {
            signature.append(',');
            if (op.kind() == OpKind.CONTROL) signature.append(op.value());
            else if (op.kind() == OpKind.DIGIT_PAIR) signature.append('P').append(op.position());
            else signature.append(op.dataSet() == CodeSet.A ? 'A' : 'B').append(op.position());
        }
        return signature.toString();
    }

    private static int startCode(CodeSet set) {
        return switch (set) {
            case A -> CODE_START_A;
            case B -> CODE_START_B;
            case C -> CODE_START_C;
        };
    }

    private static String startInvocation(CodeSet set) {
        return switch (set) {
            case A -> ">9";
            case B -> ">:";
            case C -> ">;";
        };
    }

    private static int latchCode(CodeSet set) {
        return switch (set) {
            case A -> CODE_CODE_A;
            case B -> CODE_CODE_B;
            case C -> CODE_CODE_C;
        };
    }

    private static String controlInvocation(int codeword) {
        return switch (codeword) {
            case CODE_SHIFT -> ">4";
            case CODE_CODE_C -> ">5";
            case CODE_CODE_B -> ">6";
            case CODE_CODE_A -> ">7";
            default -> throw new IllegalArgumentException("未知 CODE_128 控制码：" + codeword);
        };
    }

    private static void appendChecksumAndStop(List<Integer> codewords) {
        int checksum = codewords.get(0);
        for (int i = 1; i < codewords.size(); i++) checksum += codewords.get(i) * i;
        codewords.add(checksum % 103);
        codewords.add(CODE_STOP);
    }

    private static Variant variant(List<Integer> codewords, String zpl, int width, int height,
                                   boolean fixedPrintModule) {
        boolean[] pattern = toPattern(codewords);
        return new Variant(render(pattern, width, height, fixedPrintModule), zpl,
                List.copyOf(codewords), pattern.length, QUIET_ZONE_MODULES, PRINT_MODULE_DOTS);
    }

    private static void appendDataCodeword(List<Integer> codewords, char c, boolean codeA) {
        int value = codeA && c < 32 ? c + 64 : c - 32;
        codewords.add(value);
    }

    private static void appendZplCharacter(StringBuilder zpl, char c, boolean codeA) {
        if (codeA) {
            int codeword = c < 32 ? c + 64 : c - 32;
            if (codeword < 10) zpl.append('0');
            zpl.append(codeword);
        } else if (c == '>') {
            zpl.append(">0");
        } else {
            zpl.append(c);
        }
    }

    private static boolean[] toPattern(List<Integer> codewords) {
        int width = 0;
        for (int codeword : codewords) {
            for (int run : CODE_PATTERNS[codeword]) width += run;
        }
        boolean[] result = new boolean[width];
        int offset = 0;
        for (int codeword : codewords) {
            boolean black = true;
            for (int run : CODE_PATTERNS[codeword]) {
                for (int i = 0; i < run; i++) result[offset++] = black;
                black = !black;
            }
        }
        return result;
    }

    private static BitMatrix render(boolean[] pattern, int requestedWidth, int requestedHeight,
                                    boolean fixedPrintModule) {
        int quietWidth = QUIET_ZONE_MODULES * 2;
        int multiple;
        int outputWidth;
        if (fixedPrintModule) {
            multiple = PRINT_MODULE_DOTS;
            outputWidth = Math.max(requestedWidth, (pattern.length + quietWidth) * multiple);
        } else {
            int fullWidth = pattern.length + quietWidth;
            outputWidth = Math.max(requestedWidth, fullWidth);
            multiple = Math.max(1, outputWidth / fullWidth);
        }
        int outputHeight = Math.max(1, requestedHeight);
        int leftPadding = (outputWidth - pattern.length * multiple) / 2;
        BitMatrix matrix = new BitMatrix(outputWidth, outputHeight);
        for (int inputX = 0, outputX = leftPadding; inputX < pattern.length; inputX++, outputX += multiple) {
            if (pattern[inputX]) matrix.setRegion(outputX, 0, multiple, outputHeight);
        }
        return matrix;
    }

    private static int hashIndex(String value, int bound) {
        byte[] hash = sha256(value);
        long number = 0;
        for (int i = 0; i < Long.BYTES; i++) number = (number << 8) | (hash[i] & 0xffL);
        return (int) Math.floorMod(number, bound);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private static int[][] loadCodePatterns() {
        try {
            Field field = Code128Reader.class.getDeclaredField("CODE_PATTERNS");
            field.setAccessible(true);
            return (int[][]) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取 ZXing CODE_128 标准码表", e);
        }
    }

    record Variant(BitMatrix matrix, String zplFieldData, List<Integer> codewords,
                   int symbolModules, int quietZoneModules, int moduleDots) {}

    private enum CodeSet { A, B, C }
    private enum OpKind { CONTROL, CHARACTER, DIGIT_PAIR }
    private record PlanOp(OpKind kind, int position, int value, CodeSet dataSet) {}
    private record EncodingPlan(CodeSet startSet, List<PlanOp> operations,
                                String signature, int symbolModules) {
        private EncodingPlan(CodeSet startSet, List<PlanOp> operations) {
            this(startSet, operations, "", (1 + operations.size() + 1) * 11 + 13);
        }

        private EncodingPlan withSignature(String value) {
            return new EncodingPlan(startSet, operations, value, symbolModules);
        }
    }

    /** 仅供保留的非六位历史条码路径使用。 */
    private static final class BitSource {
        private final byte[] bytes;
        private int bitIndex;

        private BitSource(byte[] bytes) { this.bytes = bytes; }

        private boolean next() {
            int index = bitIndex++;
            return ((bytes[(index / 8) % bytes.length] >>> (index % 8)) & 1) == 1;
        }
    }

}
