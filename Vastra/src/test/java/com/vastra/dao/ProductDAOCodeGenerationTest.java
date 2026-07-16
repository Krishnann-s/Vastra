package com.vastra.dao;

import com.vastra.model.Product;
import com.vastra.util.DBUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies that leaving Product Code blank auto-assigns a short, sequential,
 * prefixed code (e.g. "TST0007") instead of the old random 13-digit number,
 * and that the underlying counter never repeats or goes backward across
 * separate inserts - the property the whole feature depends on.
 */
class ProductDAOCodeGenerationTest {

    @BeforeAll
    static void setUpDb() throws Exception {
        DBUtil.init();
    }

    @Test
    void insertProduct_withoutCode_getsSequentialPrefixedCode() throws Exception {
        String prefix = "TST";
        StoreSettingsDAO.set("product_code_prefix", prefix);
        int startingSeq = Integer.parseInt(StoreSettingsDAO.get("next_product_code_seq", "1"));

        String id1 = ProductDAO.insertProduct("SeqTest A", "", 10000, 10000, 5000, 0, 1, 0,
                "TestCat", "TestBrand", "", "", 5, "", "", "");
        String id2 = ProductDAO.insertProduct("SeqTest B", "", 10000, 10000, 5000, 0, 1, 0,
                "TestCat", "TestBrand", "", "", 5, "", "", "");

        try {
            Product p1 = ProductDAO.findById(id1);
            Product p2 = ProductDAO.findById(id2);

            // Consecutive inserts must get consecutive numbers under the same prefix -
            // this is the "next batch continues where the last one left off" guarantee.
            assertEquals(prefix + String.format("%04d", startingSeq), p1.getBarcode());
            assertEquals(prefix + String.format("%04d", startingSeq + 1), p2.getBarcode());
            assertNotEquals(p1.getBarcode(), p2.getBarcode());
        } finally {
            ProductDAO.deactivateProduct(id1);
            ProductDAO.deactivateProduct(id2);
        }
    }
}
