package com.baakio.ai;

import com.baakio.exception.ApiException;
import com.baakio.utils.MoneyAmounts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ScanDraftFactory {
    public ScanDraft create(ScanExtraction extraction) {
        if (!Boolean.TRUE.equals(extraction.isNewPurchase())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NOT_NEW_PURCHASE", "This image does not look like a new purchase bill. Add the purchase manually after checking it.");
        }
        if (extraction.items() == null || extraction.items().isEmpty() || extraction.items().size() > 100) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "OCR_NO_PURCHASE_LINES", "No usable purchase items were found. Add the purchase manually after checking the bill.");
        }
        List<String> flags = new ArrayList<>();
        LocalDate occurredOn = parseDate(extraction.occurredOn(), flags);
        BigDecimal discount = cents(extraction.discount());
        if (discount == null) {
            discount = BigDecimal.ZERO.setScale(2);
            if (extraction.discount() != null && !extraction.discount().isBlank()) flags.add("UNCERTAIN_DISCOUNT");
        }
        BigDecimal writtenTotal = cents(extraction.writtenTotal());
        if (writtenTotal == null && extraction.writtenTotal() != null && !extraction.writtenTotal().isBlank()) flags.add("UNCERTAIN_WRITTEN_TOTAL");
        List<ScanDraftLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        boolean complete = true;
        for (ScanExtractionLine input : extraction.items()) {
            List<String> lineFlags = new ArrayList<>();
            String name = clean(input.name());
            if (name == null || name.length() > 200 || low(input.confidenceName())) lineFlags.add("UNCERTAIN_NAME");
            String quantity = validQuantity(input.quantity()) ? decimalString(input.quantity()) : "";
            if (quantity.isEmpty() || low(input.confidenceQuantity())) lineFlags.add("UNCERTAIN_QUANTITY");
            String unitPrice = validCents(input.unitPrice()) ? cents(input.unitPrice()).toPlainString() : "";
            if (unitPrice.isEmpty() || low(input.confidenceUnitPrice())) lineFlags.add("UNCERTAIN_UNIT_PRICE");
            String unit = clean(input.unit());
            if (unit != null && unit.length() > 40) unit = null;
            String lineTotal = "";
            if (name != null && name.length() <= 200 && !quantity.isEmpty() && !unitPrice.isEmpty()) {
                lineTotal = MoneyAmounts.text(MoneyAmounts.lineTotal(new BigDecimal(quantity), new BigDecimal(unitPrice)));
                total = total.add(new BigDecimal(lineTotal)).setScale(2);
            } else {
                complete = false;
            }
            lines.add(new ScanDraftLine(name == null || name.length() > 200 ? "" : name, quantity,
                    unit, unitPrice, lineTotal, lineFlags));
            flags.addAll(lineFlags);
        }
        String calculated = "";
        if (complete) {
            total = total.subtract(discount).setScale(2);
            calculated = MoneyAmounts.text(total);
            if (total.signum() <= 0) flags.add("INVALID_CALCULATED_TOTAL");
            if (writtenTotal != null && writtenTotal.compareTo(total) != 0) flags.add("TOTAL_MISMATCH");
        } else {
            flags.add("TOTAL_INCOMPLETE");
        }
        if (occurredOn == null) flags.add("UNCERTAIN_DATE");
        String description = clean(extraction.description());
        if (description != null && description.length() > 500) description = description.substring(0, 500);
        return new ScanDraft(occurredOn, description, MoneyAmounts.text(discount),
                writtenTotal == null ? "" : MoneyAmounts.text(writtenTotal), calculated, lines,
                flags.stream().distinct().toList());
    }

    private LocalDate parseDate(String value, List<String> flags) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.trim()); }
        catch (DateTimeParseException exception) { flags.add("UNCERTAIN_DATE"); return null; }
    }

    private boolean validQuantity(String value) {
        try {
            BigDecimal amount = new BigDecimal(value);
            return amount.signum() > 0 && amount.scale() <= 3 && amount.precision() - amount.scale() <= 15;
        } catch (Exception exception) { return false; }
    }

    private boolean validCents(String value) {
        try { return value != null && MoneyAmounts.nonNegativeCents(new BigDecimal(value), "INVALID_OCR_VALUE") != null; }
        catch (Exception exception) { return false; }
    }

    private BigDecimal cents(String value) {
        if (value == null || value.isBlank()) return null;
        try { return MoneyAmounts.nonNegativeCents(new BigDecimal(value), "INVALID_OCR_VALUE"); }
        catch (Exception exception) { return null; }
    }

    private boolean low(Double confidence) { return confidence == null || confidence < 0.75; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String decimalString(String value) { return new BigDecimal(value).stripTrailingZeros().toPlainString(); }
}
