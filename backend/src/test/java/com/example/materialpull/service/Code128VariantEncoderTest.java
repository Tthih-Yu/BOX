package com.example.materialpull.service;

import com.google.zxing.Result;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Reader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Code128VariantEncoderTest {

    @Test
    void allSixDigitVariantsAreUniqueCompactAndDecodeToOriginalValue() throws Exception {
        String warehouseCode = "139315";
        Set<String> codewordPaths = new HashSet<>();
        int capacity = Code128VariantEncoder.sixDigitVariantCapacity();

        assertEquals(889, capacity);
        for (int variantNo = 0; variantNo < capacity; variantNo++) {
            Code128VariantEncoder.Variant variant = Code128VariantEncoder.encode(
                    warehouseCode, variantNo, "ignored", 616, 80);
            assertTrue(codewordPaths.add(variant.codewords().toString()), "变体路径重复：" + variantNo);
            assertTrue(variant.symbolModules() <= 134, "变体过长：" + variant.symbolModules());
            assertEquals(warehouseCode, decode(variant.matrix()));
            assertEquals(warehouseCode, decodeZplFieldData(variant.zplFieldData()));
        }
    }

    @Test
    void firstCurrentProductionCapacityUsesShortestPathsFirst() {
        int maxModules = 0;
        for (int variantNo = 0; variantNo < 113; variantNo++) {
            Code128VariantEncoder.Variant variant = Code128VariantEncoder.encode(
                    "139315", variantNo, "ignored", 616, 80);
            maxModules = Math.max(maxModules, variant.symbolModules());
        }
        assertEquals(112, maxModules);
    }

    @Test
    void differentPersistedNumbersProduceDifferentBarsButSameWarehouseCode() throws Exception {
        Code128VariantEncoder.Variant first = Code128VariantEncoder.encode(
                "139315", 0, "TASK-01", 616, 80);
        Code128VariantEncoder.Variant second = Code128VariantEncoder.encode(
                "139315", 1, "TASK-02", 616, 80);

        assertNotEquals(first.codewords(), second.codewords());
        assertNotEquals(first.zplFieldData(), second.zplFieldData());
        assertEquals("139315", decode(first.matrix()));
        assertEquals("139315", decode(second.matrix()));
    }

    @Test
    void samePersistedNumberKeepsStableBarcodeForPreviewAndPrinting() {
        Code128VariantEncoder.Variant preview = Code128VariantEncoder.encode(
                "139315", 37, "TASK-01", 616, 80);
        Code128VariantEncoder.Variant printing = Code128VariantEncoder.encode(
                "139315", 37, "DIFFERENT-SEED-IS-IGNORED", 616, 80);

        assertEquals(preview.codewords(), printing.codewords());
        assertEquals(preview.zplFieldData(), printing.zplFieldData());
    }

    @Test
    void persistedSequenceCyclesThroughCompactVariantPool() {
        int capacity = Code128VariantEncoder.sixDigitVariantCapacity();
        Code128VariantEncoder.Variant first = Code128VariantEncoder.encode(
                "139315", 0, "ignored", 616, 80);
        Code128VariantEncoder.Variant afterOneCycle = Code128VariantEncoder.encode(
                "139315", capacity, "ignored", 616, 80);
        Code128VariantEncoder.Variant secondAfterOneCycle = Code128VariantEncoder.encode(
                "139315", capacity + 1, "ignored", 616, 80);
        Code128VariantEncoder.Variant second = Code128VariantEncoder.encode(
                "139315", 1, "ignored", 616, 80);

        assertEquals(first.codewords(), afterOneCycle.codewords());
        assertEquals(second.codewords(), secondAfterOneCycle.codewords());
    }

    @Test
    void historicalNonSixDigitValueStillRoundTrips() throws Exception {
        Code128VariantEncoder.Variant variant = Code128VariantEncoder.encode(
                "WH-100", "RP-202609111030-01AA", 520, 80);

        assertEquals("WH-100", decode(variant.matrix()));
        assertEquals("WH-100", decodeZplFieldData(variant.zplFieldData()));
    }

    private String decode(BitMatrix matrix) throws Exception {
        BitArray row = matrix.getRow(matrix.getHeight() / 2, null);
        Result result = new Code128Reader().decodeRow(0, row, null);
        return result.getText();
    }

    /** 按 Zebra ^BC 的 A/B/C 调用码回读字段，独立校验 ZPL 不会改变扫码值。 */
    private String decodeZplFieldData(String fieldData) {
        int index = 2;
        CodeSet set;
        if (fieldData.startsWith(">9")) set = CodeSet.A;
        else if (fieldData.startsWith(">:")) set = CodeSet.B;
        else if (fieldData.startsWith(">;")) set = CodeSet.C;
        else throw new IllegalArgumentException("缺少 CODE_128 Start A/B/C 调用码");

        boolean shift = false;
        StringBuilder decoded = new StringBuilder();
        while (index < fieldData.length()) {
            if (fieldData.startsWith(">7", index)) {
                set = CodeSet.A;
                index += 2;
                continue;
            }
            if (fieldData.startsWith(">6", index)) {
                set = CodeSet.B;
                index += 2;
                continue;
            }
            if (fieldData.startsWith(">5", index)) {
                set = CodeSet.C;
                index += 2;
                continue;
            }
            if (fieldData.startsWith(">4", index)) {
                shift = true;
                index += 2;
                continue;
            }

            CodeSet dataSet = shift ? (set == CodeSet.A ? CodeSet.B : CodeSet.A) : set;
            shift = false;
            if (dataSet == CodeSet.C) {
                decoded.append(fieldData, index, index + 2);
                index += 2;
            } else if (dataSet == CodeSet.A) {
                int codeword = Integer.parseInt(fieldData.substring(index, index + 2));
                decoded.append((char) (codeword < 64 ? codeword + 32 : codeword - 64));
                index += 2;
            } else if (fieldData.startsWith(">0", index)) {
                decoded.append('>');
                index += 2;
            } else {
                decoded.append(fieldData.charAt(index++));
            }
        }
        return decoded.toString();
    }

    private enum CodeSet { A, B, C }
}
