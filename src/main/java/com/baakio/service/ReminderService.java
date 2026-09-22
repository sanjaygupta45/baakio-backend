package com.baakio.service;

import com.baakio.dto.customer.CustomerResponse;
import com.baakio.dto.customer.CommitmentSummary;
import com.baakio.dto.ledger.LedgerMovement;
import com.baakio.dto.ledger.LedgerResponse;
import com.baakio.dto.reminder.ReminderRequest;
import com.baakio.dto.reminder.ReminderResponse;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.model.Business;
import com.baakio.repository.BusinessRepository;
import com.baakio.repository.CommitmentRepository;
import com.baakio.utils.MoneyAmounts;
import com.baakio.exception.ApiException;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ReminderService {
    private final CustomerService customers;
    private final LedgerReadService reads;
    private final BusinessRepository businesses;
    private final CommitmentRepository commitments;

    public ReminderService(CustomerService customers, LedgerReadService reads,
                           BusinessRepository businesses, CommitmentRepository commitments) {
        this.customers = customers;
        this.reads = reads;
        this.businesses = businesses;
        this.commitments = commitments;
    }

    public ReminderResponse preview(AccountHolderPrincipal principal, long customerId, ReminderRequest request) {
        CustomerResponse customer = customers.get(principal.businessId(), customerId);
        Business business = businesses.findById(principal.businessId()).orElse(null);
        if (business == null) throw ApiException.notFound();
        ZoneId zone = ZoneId.of(business.getTimezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate from = request.periodStart() == null ? today.withDayOfMonth(1) : request.periodStart();
        LocalDate to = request.periodEnd() == null ? today : request.periodEnd();
        LedgerResponse period = reads.statement(principal.businessId(), customerId, from, to);
        boolean hindi = "hi-IN".equals(request.locale());
        Locale locale = hindi ? Locale.forLanguageTag("hi-IN") : Locale.forLanguageTag("en-IN");
        Instant generated = Instant.now();
        String asOf = DateTimeFormatter.ofPattern(hindi ? "d MMM uuuu, HH:mm 'IST'" : "d MMM uuuu, HH:mm 'IST'", locale)
                .format(generated.atZone(zone));
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d MMM uuuu", locale);
        String displayFrom = dateFormat.format(from);
        String displayTo = dateFormat.format(to);
        String opening = MoneyAmounts.text(new java.math.BigDecimal(period.openingAtFrom()));
        String purchases = MoneyAmounts.text(new java.math.BigDecimal(period.purchasesInPeriod()));
        String payments = MoneyAmounts.text(new java.math.BigDecimal(period.paymentsInPeriod()));
        String closing = MoneyAmounts.text(new java.math.BigDecimal(period.closingAtTo()));
        String outstanding = customer.outstanding();
        String promise = nextPromise(principal.businessId(), customerId, hindi, dateFormat, today);
        String message = buildMessage(business, customer, request.format(), hindi, asOf, displayFrom, displayTo,
                opening, purchases, payments, closing, outstanding, promise, period.movements());
        String recipient = customer.phoneE164();
        String digits = recipient == null ? "" : recipient.replaceAll("\\D", "");
        String url = "https://wa.me/" + digits + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return new ReminderResponse(request.locale(), request.format(), generated, recipient,
                from.toString(), to.toString(), opening, purchases, payments, closing, outstanding, message, url, false);
    }

    private String buildMessage(Business payee, CustomerResponse customer, String format,
            boolean hindi, String asOf, String from, String to, String opening, String purchases,
            String payments, String closing, String outstanding, String promise,
            List<LedgerMovement> movements) {
        String title = hindi ? "खाता विवरण" : "Account statement";
        String customerLabel = hindi ? "ग्राहक" : "Customer";
        String periodLabel = hindi ? "अवधि" : "Period";
        String openingLabel = hindi ? "शुरुआती बकाया" : "Opening balance";
        String purchasesLabel = hindi ? "इस अवधि की खरीद" : "Purchases in period";
        String paymentsLabel = hindi ? "इस अवधि में मिले भुगतान" : "Payments received in period";
        String closingLabel = hindi ? "अवधि का बाकी" : "Period closing balance";
        String outstandingLabel = hindi ? "अभी कुल बकाया" : "Current total outstanding";
        StringBuilder message = new StringBuilder()
                .append(payee.getName()).append(" | ").append(title).append(" · ").append(asOf).append('\n')
                .append(customerLabel).append(": ").append(customer.displayName()).append('\n')
                .append(periodLabel).append(": ").append(from).append(hindi ? " से " : " – ").append(to).append('\n')
                .append(openingLabel).append(": ₹").append(opening).append('\n')
                .append(purchasesLabel).append(": ₹").append(purchases).append('\n')
                .append(paymentsLabel).append(": ₹").append(payments).append('\n')
                .append(closingLabel).append(": ₹").append(closing).append('\n')
                .append(outstandingLabel).append(": ₹").append(outstanding);
        if (promise != null) message.append('\n').append(promise);
        if ("DETAILED".equals(format)) {
            message.append("\n\n").append(hindi ? "खरीद और भुगतान का इतिहास:" : "Purchase and payment history:");
            for (LedgerMovement movement : movements) {
                String label = switch (movement.type()) {
                    case "OPENING_BALANCE" -> hindi ? "शुरुआती शेष" : "Opening balance";
                    case "PURCHASE" -> hindi ? "खरीद" : "Purchase";
                    case "PAYMENT" -> hindi ? "भुगतान" : "Payment";
                    default -> movement.type();
                };
                String date = DateTimeFormatter.ofPattern("d MMM uuuu", hindi ? Locale.forLanguageTag("hi-IN") : Locale.forLanguageTag("en-IN"))
                        .format(movement.occurredOn());
                message.append('\n').append(date).append(" · ").append(label).append(" · ")
                        .append("CREDIT".equals(movement.direction()) ? "−" : "+")
                        .append("₹").append(movement.amount());
                if (movement.description() != null && !movement.description().isBlank()) {
                    message.append(" · ").append(movement.description());
                }
            }
        }
        if (payee.getPaymentDetailsAccountHolderConfirmedAt() != null && payee.getUpiVpa() != null) {
            message.append("\n").append(hindi ? "भुगतान UPI ID पर करें" : "Pay via UPI ID").append(": ").append(payee.getUpiVpa());
        } else if (payee.getPaymentDetailsAccountHolderConfirmedAt() != null && payee.getUpiLinkedMobileE164() != null) {
            message.append("\n").append(hindi ? "भुगतान UPI-लिंक मोबाइल नंबर पर करें" : "Pay via UPI-linked mobile number").append(": ").append(payee.getUpiLinkedMobileE164());
        }
        if (payee.getPaymentDetailsAccountHolderConfirmedAt() != null && payee.getUpiPayeeName() != null && !payee.getUpiPayeeName().isBlank()) {
            message.append("\n").append(hindi ? "प्राप्तकर्ता का नाम" : "Payee name").append(": ").append(payee.getUpiPayeeName());
        }
        message.append("\n\n").append(hindi
                ? "यदि आप भुगतान कर चुके हैं, तो कृपया लेन-देन का संदर्भ साझा करें।"
                : "If you have already paid, please share the transaction reference.");
        return message.toString();
    }

    private String nextPromise(String businessId, long customerId, boolean hindi, DateTimeFormatter dateFormat,
                               LocalDate today) {
        CommitmentSummary commitment = commitments.findActiveSummaries(businessId, customerId, today)
                .stream().findFirst().orElse(null);
        if (commitment == null) return null;
        String prefix = hindi ? "अगला भुगतान वादा" : "Next payment promise";
        return prefix + ": ₹" + commitment.remaining()
                + (hindi ? " तक " : " by ") + dateFormat.format(commitment.dueDate());
    }
}
