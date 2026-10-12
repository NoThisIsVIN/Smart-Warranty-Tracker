package org.example.receipt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParserTest {

    private static final LocalDate MARCH_5 = LocalDate.of(2024, 3, 5);

    @ParameterizedTest
    @ValueSource(strings = {
            "Invoice Date: 2024-03-05",
            "Invoice Date: 2024/03/05",
            "Invoice Date: 05.03.2024",
            "Invoice Date: 05/03/2024",
            "Invoice Date: 05-03-2024",
            "Order placed Mar 5 2024",
            "Order placed Mar 5, 2024",
            "Order placed March 5, 2024",
            "Order placed 5 March 2024",
            "Purchase date: Tue, Mar 5, 2024",
            "Purchased on Tuesday, 5 March 2024"
    })
    void parsesSupportedDateFormats(String text) {
        assertThat(ReceiptParser.parse(text).purchaseDate()).isEqualTo(MARCH_5);
    }

    @Test
    void ignoresImpossibleDates() {
        ParsedReceipt r = ReceiptParser.parse("Invoice Date: 2024-13-45\nDelivered 2024-03-05");
        assertThat(r.purchaseDate()).isEqualTo(MARCH_5);

        assertThat(ReceiptParser.parse("Date: 2024-13-45").purchaseDate()).isNull();
    }

    @Test
    void prefersLabelledPurchaseDate() {
        String text = "Printed 2024-06-01\nDue Date: 2024-07-01\nInvoice Date: 2024-03-05";
        assertThat(ReceiptParser.parse(text).purchaseDate()).isEqualTo(MARCH_5);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Warranty: 2 years|24",
            "warranty period 18 months|18",
            "Comes with 1 year warranty|12",
            "Includes 2-year limited warranty|24",
            "12 months manufacturer's warranty|12",
            "Guarantee of one year|12"
    })
    void parsesWarrantyPhrasings(String text, int months) {
        assertThat(ReceiptParser.parse(text).warrantyMonths()).isEqualTo(months);
    }

    @Test
    void warrantyIsNullWhenAbsent() {
        assertThat(ReceiptParser.parse("Samsung Galaxy S23, qty 1").warrantyMonths()).isNull();
    }

    @Test
    void parsesGalaxyProductAndSamsungModelCode() {
        String text = """
                SAMSUNG Electronics
                Invoice No: INV20240305
                Item: Samsung Galaxy S23 5G (Phantom Black, 128 GB)
                Model: SM-S911B/DS
                Invoice Date: 05.03.2024
                """;
        ParsedReceipt r = ReceiptParser.parse(text);
        assertThat(r.productName()).isEqualTo("Galaxy S23");
        assertThat(r.modelName()).isEqualTo("SM-S911B/DS");
        assertThat(r.retailer()).isEqualTo("Samsung");
        assertThat(r.purchaseDate()).isEqualTo(MARCH_5);
    }

    @Test
    void galaxyModelUsedWhenNoCodePresent() {
        ParsedReceipt r = ReceiptParser.parse("Galaxy A54 smartphone");
        assertThat(r.productName()).isEqualTo("Galaxy A54");
        assertThat(r.modelName()).isEqualTo("A54");
    }

    @Test
    void parsesProductLineAndLabelledModel() {
        ParsedReceipt r = ReceiptParser.parse("Apple Store\niPhone 15 Pro\nModel Number: MTUV3HN/A");
        assertThat(r.productName()).isEqualTo("iPhone 15 Pro");
        assertThat(r.modelName()).isEqualTo("MTUV3HN/A");
        assertThat(r.retailer()).isEqualTo("Apple");
    }

    @Test
    void fallsBackToProductLabelAndGenericModel() {
        ParsedReceipt r = ReceiptParser.parse("Order #: ORD12345\nDescription: Noise cancelling headphones\nWH-1000XM5 black");
        assertThat(r.productName()).isEqualTo("Noise cancelling headphones");
        assertThat(r.modelName()).isEqualTo("WH-1000XM5");
    }

    @Test
    void doesNotTreatRandomShortTokensAsModels() {
        assertThat(ReceiptParser.parse("Paid with card ending S12 at counter A3").modelName()).isNull();
    }

    @Test
    void detectsBrandsOnWordBoundaries() {
        assertThat(ReceiptParser.parse("Sold by Best Buy").retailer()).isEqualTo("Best Buy");
        assertThat(ReceiptParser.parse("DELL INSPIRON 15").retailer()).isEqualTo("Dell");
        assertThat(ReceiptParser.parse("Shipping to Delloway Street").retailer()).isNull();
        assertThat(ReceiptParser.parse("Our target is customer happiness").retailer()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    void blankTextGivesAllNulls(String text) {
        assertThat(ReceiptParser.parse(text)).isEqualTo(new ParsedReceipt(null, null, null, null, null));
        assertThat(ReceiptParser.parse(null)).isEqualTo(new ParsedReceipt(null, null, null, null, null));
    }

    @Test
    void capsLongProductNames() {
        ParsedReceipt r = ReceiptParser.parse("Product: " + "Very long product name ".repeat(10));
        assertThat(r.productName()).hasSizeLessThanOrEqualTo(60);
    }
}
