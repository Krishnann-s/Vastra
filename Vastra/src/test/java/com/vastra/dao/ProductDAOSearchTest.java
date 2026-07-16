package com.vastra.dao;

import com.vastra.model.Product;
import com.vastra.util.DBUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the free-text product search introduced to replace SKU-based
 * lookup: a cashier should be able to find a product by describing it
 * (category + size) in any word order, without remembering its SKU/barcode.
 */
class ProductDAOSearchTest {

    @BeforeAll
    static void setUpDb() throws Exception {
        DBUtil.init();
    }

    @Test
    void searchByName_matchesOnCategoryPlusSizeRegardlessOfWordOrder() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String tshirtId = ProductDAO.insertProduct(
                "Style-" + unique, "", 89900, 89900, 65000, 5, 10, 0,
                "Half Hand Jersey Tshirt", "SomeBrand", "", "", 5, "",
                "32", "Blue");
        String jeansId = ProductDAO.insertProduct(
                "OtherStyle-" + unique, "", 199900, 199900, 65000, 5, 10, 0,
                "Boot Cut Jeans", "SomeBrand", "", "", 5, "",
                "32", "Black");

        try {
            // The user's own example: "size" is filler and gets stripped, the
            // rest ("half", "hand", "jersey", "tshirt", "32") must all match
            // somewhere in the product's combined text.
            List<Product> results = ProductDAO.searchByName("half hand jersey tshirt size 32");
            assertTrue(containsId(results, tshirtId), "expected the tshirt to match on category+size");
            assertFalse(containsId(results, jeansId), "jeans should not match a tshirt search");

            // Word order shouldn't matter - it's an AND of tokens, not a phrase match.
            List<Product> reordered = ProductDAO.searchByName("32 tshirt jersey half hand");
            assertTrue(containsId(reordered, tshirtId), "word order should not affect matching");

            // A size-only query should match both, since both are size 32.
            List<Product> bothBySize = ProductDAO.searchByName("32");
            assertTrue(containsId(bothBySize, tshirtId));
            assertTrue(containsId(bothBySize, jeansId));
        } finally {
            ProductDAO.deactivateProduct(tshirtId);
            ProductDAO.deactivateProduct(jeansId);
        }
    }

    private boolean containsId(List<Product> products, String id) {
        return products.stream().anyMatch(p -> p.getId().equals(id));
    }
}
